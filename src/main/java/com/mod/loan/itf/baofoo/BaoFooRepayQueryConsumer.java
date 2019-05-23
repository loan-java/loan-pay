package com.mod.loan.itf.baofoo;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mod.loan.baofoo.config.BaofooPayConfig;
import com.mod.loan.baofoo.rsa.SignatureUtils;
import com.mod.loan.baofoo.util.FormatUtil;
import com.mod.loan.baofoo.util.HttpUtil;
import com.mod.loan.baofoo.util.SecurityUtil;
import com.mod.loan.common.enums.JuHeCallBackEnum;
import com.mod.loan.common.enums.SmsTemplate;
import com.mod.loan.common.message.OrderRepayQueryMessage;
import com.mod.loan.common.message.QueueSmsMessage;
import com.mod.loan.config.rabbitmq.RabbitConst;
import com.mod.loan.model.Order;
import com.mod.loan.model.OrderRepay;
import com.mod.loan.model.User;
import com.mod.loan.service.*;
import com.mod.loan.util.ConstantUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author kk
 */
@Slf4j
@Component
public class BaoFooRepayQueryConsumer {

    @Autowired
    private BaofooPayConfig baofooPayConfig;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepayService orderRepayService;

    @Autowired
    private CallBackJuHeService callBackJuHeService;
    @Resource
    private CallBackRongZeService callBackRongZeService;

    @Autowired
    private UserService userService;

    @RabbitListener(queues = "baofoo_queue_repay_order_query", containerFactory = "baofoo_repay_order_query")
    @RabbitHandler
    public void repayOrderQuery(Message mess) {
        OrderRepayQueryMessage payResultMessage = JSONObject.parseObject(mess.getBody(), OrderRepayQueryMessage.class);
        try {
            Thread.sleep(10000);
            String repayOrderNo = payResultMessage.getRepayNo();
            String response = postQueryRepayRequest(repayOrderNo);
            getQueryResponse(response, payResultMessage);
        } catch (Exception e) {
            log.error("宝付协议支付结果查询异常，message={}", JSON.toJSONString(payResultMessage));
            log.error("宝付协议支付结果查询异常", e);
            if (payResultMessage.getTimes() <= ConstantUtils.FIVE) {
                payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.baofoo_queue_repay_order_query, payResultMessage);
                return;
            }
            if (payResultMessage.getTimes() <= 10) {
                payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.baofoo_queue_repay_order_query_wait, payResultMessage);
                return;
            }
            if (payResultMessage.getTimes() <= 15) {
                payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.baofoo_queue_repay_order_query_wait_long, payResultMessage);
                return;
            }
        }
    }

    /**
     * 请求查询
     */
    private String postQueryRepayRequest(String repayOrderNo) throws Exception {
        //报文发送日期时间
        String sendTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        //商户私钥
        String pfxPath = baofooPayConfig.getBaofooRepayPriKeyPath();

        Map<String, String> dateArray = new TreeMap<>();
        dateArray.put("send_time", sendTime);
        //报文流水号
        dateArray.put("msg_id", "TISN" + System.currentTimeMillis());
        dateArray.put("version", baofooPayConfig.getBaofooRepayVersion());
        dateArray.put("terminal_id", baofooPayConfig.getBaofooRepayTerminalId());
        //交易类型
        dateArray.put("txn_type", "07");
        dateArray.put("member_id", baofooPayConfig.getBaofooRepayMemberId());
        //交易时的trans_id
        dateArray.put("orig_trans_id", repayOrderNo);
        dateArray.put("orig_trade_date", sendTime);

        String signVStr = FormatUtil.coverMap2String(dateArray);
        //签名
        String signature = SecurityUtil.sha1X16(signVStr, "UTF-8");
        String sign = SignatureUtils.encryptByRSA(signature, pfxPath, baofooPayConfig.getBaofooRepayKeyPassword());
        //签名域
        dateArray.put("signature", sign);

        return HttpUtil.RequestForm(baofooPayConfig.getBaofooRepayQueryUrl(), dateArray);
    }

    /**
     * 请求结果处理
     */
    private void getQueryResponse(String response, OrderRepayQueryMessage message) throws Exception {
        //宝付公钥
        String cerpath = baofooPayConfig.getBaofooRepayPubKeyPath();

        Map<String, String> returnData = FormatUtil.getParm(response);

        if (!returnData.containsKey("signature")) {
            throw new Exception("缺少验签参数！");
        }

        String rSign = returnData.get("signature");
        //需要删除签名字段
        returnData.remove("signature");
        String rSignVStr = FormatUtil.coverMap2String(returnData);
        //签名
        String rSignature = SecurityUtil.sha1X16(rSignVStr, "UTF-8");

        if (!SignatureUtils.verifySignature(cerpath, rSignature, rSign)) {
            //验签失败， 重新发起查询
            log.error("宝付还款查询结果验签失败，还款订单号={}, response={}", message.getRepayNo(), response);
            rabbitTemplate.convertAndSend(RabbitConst.baofoo_queue_repay_order_query_wait, message);
            return;
        }
        if (!returnData.containsKey("resp_code")) {
            //缺少resp_code参数！重新发起查询
            log.error("宝付还款查询结果参数异常，缺少resp_code参数！，还款订单号={}, response={}", message.getRepayNo(), response);
            rabbitTemplate.convertAndSend(RabbitConst.baofoo_queue_repay_order_query_wait, message);
            return;
        }

        OrderRepay orderRepay = orderRepayService.selectByPrimaryKey(message.getRepayNo());
        Order order = orderService.selectByPrimaryKey(orderRepay.getOrderId());
        User user = userService.selectByPrimaryKey(order.getUid());
        if ("S".equals(returnData.get("resp_code"))) {
            log.info("宝付还款成功，订单流水为：{}, response={}", message.getRepayNo(), response);
            //交易成功！
            order.setRealRepayTime(new Date());
            order.setHadRepay(order.getShouldRepay());
            if (ConstantUtils.LOAN_ORDER_OVERDUE == order.getStatus() || ConstantUtils.LOAN_ORDER_BAD_DEBT == order.getStatus()) {
                order.setStatus(ConstantUtils.LOAN_ORDER_OVERDUE_REPAYMENT);
            } else {
                order.setStatus(ConstantUtils.LOAN_ORDER_NORMAL_REPAYMENT);
            }
            orderRepay.setUpdateTime(new Date());
            orderRepay.setRepayStatus(ConstantUtils.THREE);
            orderRepayService.updateOrderRepayInfo(orderRepay, order);
            if (order.getSource() == ConstantUtils.ZERO || order.getSource() == null) {
                if (message.getRepayType() == ConstantUtils.ONE) {
                    //用户主动还款时
                    callBackJuHeService.callBack(user, message.getRepayNo(), JuHeCallBackEnum.REPAYED);
                } else {
                    //自动扣款时
                    callBackJuHeService.withholdCallBack(user, order.getOrderNo(), message.getRepayNo(), order.getShouldRepay(), JuHeCallBackEnum.WITHHOLD);
                }
            } else {
                //通知融泽还款结清
                callBackRongZeService.pushOrderStatus(order);
                callBackRongZeService.pushRepayStatus(order, ConstantUtils.ONE, message.getRepayType(), null);
            }
            QueueSmsMessage smsMessage = new QueueSmsMessage();
            smsMessage.setClientAlias(order.getMerchant());
            smsMessage.setType(SmsTemplate.T002.getKey());
            smsMessage.setPhone(user.getUserPhone());
            smsMessage.setParams("你已成功还款" + order.getShouldRepay() + "元，感谢您的支持");
            rabbitTemplate.convertAndSend(RabbitConst.queue_sms, smsMessage);
        } else if ("F".equals(returnData.get("resp_code"))) {
            //失败！
            log.info("宝付还款失败，订单流水为：{}, response={}", message.getRepayNo(), response);

            orderRepay.setRepayStatus(ConstantUtils.FOUR);
            String responseMsg = null;

            String failMessage = returnData.get("biz_resp_msg");
            if (StringUtils.isNotBlank(failMessage) && failMessage.length() > 30) {
                responseMsg = failMessage.substring(0, 30);
            }
            orderRepay.setRemark(responseMsg);
            orderRepayService.updateByPrimaryKeySelective(orderRepay);

            if (order.getSource() == ConstantUtils.ZERO || order.getSource() == null) {
                if (message.getRepayType() == ConstantUtils.ONE) {
                    //用户主动还款时才回调失败
                    callBackJuHeService.callBack(user, message.getRepayNo(), JuHeCallBackEnum.REPAY_FAILED, failMessage);
                }
            } else {
                callBackRongZeService.pushRepayStatus(order, ConstantUtils.TWO, message.getRepayType(), responseMsg);
            }
        } else {
            log.info("宝付还款异常，订单流水为：{}, response={}", message.getRepayNo(), response);
            message.setTimes(message.getTimes() + ConstantUtils.ONE);
            if (message.getTimes() < ConstantUtils.FIVE) {
                rabbitTemplate.convertAndSend(RabbitConst.baofoo_queue_repay_order_query_wait, message);
            } else {
                log.info("宝付还款查询订单={},result={}", JSON.toJSONString(message), response);
                rabbitTemplate.convertAndSend(RabbitConst.baofoo_queue_repay_order_query_wait_long, message);
            }
        }
    }

    @Bean("baofoo_repay_order_query")
    public SimpleRabbitListenerContainerFactory pointTaskContainerFactoryLoan(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPrefetchCount(ConstantUtils.ONE);
        factory.setConcurrentConsumers(ConstantUtils.FIVE);
        return factory;
    }
}

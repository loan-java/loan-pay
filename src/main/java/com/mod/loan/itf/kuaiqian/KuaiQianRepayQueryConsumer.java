package com.mod.loan.itf.kuaiqian;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mod.loan.common.enums.JuHeCallBackEnum;
import com.mod.loan.common.enums.SmsTemplate;
import com.mod.loan.common.message.OrderRepayQueryMessage;
import com.mod.loan.common.message.QueueSmsMessage;
import com.mod.loan.config.rabbitmq.RabbitConst;
import com.mod.loan.kuaiqian.config.KuaiqianPayConfig;
import com.mod.loan.model.Order;
import com.mod.loan.model.OrderRepay;
import com.mod.loan.model.User;
import com.mod.loan.service.*;
import com.mod.loan.util.ConstantUtils;
import com.mod.loan.util.kuaiqian.Post;
import com.mod.loan.util.kuaiqian.entity.TransInfo;
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
import java.util.Date;
import java.util.HashMap;

/**
 * @author kk
 */
@Slf4j
@Component
public class KuaiQianRepayQueryConsumer {

    @Autowired
    private KuaiqianPayConfig kuaiqianPayConfig;

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

    @RabbitListener(queues = "kuaiqian_queue_repay_order_query", containerFactory = "kuaiqian_repay_order_query")
    @RabbitHandler
    public void repayOrderQuery(Message mess) {
        OrderRepayQueryMessage payResultMessage = JSONObject.parseObject(mess.getBody(), OrderRepayQueryMessage.class);
        try {
            Thread.sleep(5000);
            String repayOrderNo = payResultMessage.getRepayNo();
            HashMap response = postQueryRepayRequest(repayOrderNo);
            getQueryResponse(response, payResultMessage);
        } catch (Exception e) {
            log.error("快钱协议支付结果查询异常，message={}", JSON.toJSONString(payResultMessage));
            log.error("快钱协议支付结果查询异常", e);
            if (payResultMessage.getTimes() <= ConstantUtils.FIVE) {
                payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.kuaiqian_queue_repay_order_query, payResultMessage);
                return;
            }
            if (payResultMessage.getTimes() <= 10) {
                payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.kuaiqian_queue_repay_order_query_wait, payResultMessage);
                return;
            }
            if (payResultMessage.getTimes() <= 15) {
                payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.kuaiqian_queue_repay_order_query_wait_long, payResultMessage);
                return;
            }
        }
    }

    /**
     * 请求查询
     */
    private HashMap postQueryRepayRequest(String repayOrderNo) throws Exception {
        TransInfo transInfo = new TransInfo();
        //消费信息
        //版本号
        String version = kuaiqianPayConfig.getKuaiqianVersion();
        //交易类型
        String txnType = "PUR";
        //商户编号
        String merchantId = kuaiqianPayConfig.getKuaiqianMemberId();
        //终端编号
        String terminalId = kuaiqianPayConfig.getKuaiqianTerminalId();

        //设置消费交易的两个节点
        transInfo.setRecordeText_1("TxnMsgContent");
        transInfo.setRecordeText_2("ErrorMsgContent");

        //Tr1报文拼接
        StringBuilder str1Xml = new StringBuilder();


        str1Xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        str1Xml.append("<MasMessage xmlns=\"http://www.99bill.com/mas_cnp_merchant_interface\">");
        str1Xml.append("<version>").append(version).append("</version>");
        str1Xml.append("<QryTxnMsgContent>");
        str1Xml.append("<txnType>").append(txnType).append("</txnType>");
        str1Xml.append("<merchantId>").append(merchantId).append("</merchantId>");
        str1Xml.append("<terminalId>").append(terminalId).append("</terminalId>");
        str1Xml.append("<externalRefNumber>").append(repayOrderNo).append("</externalRefNumber>");
        str1Xml.append("</QryTxnMsgContent>");
        str1Xml.append("</MasMessage>");

        System.out.println("tr1报文  str1Xml = " + str1Xml);

        //String url = "https://mas.99bill.com/cnp/query_txn";     //生产环境地址

        //TR2接收的数据
        HashMap respXml = Post.sendPost(kuaiqianPayConfig.getKuaiqianRepayQueryUrl(), str1Xml.toString(), transInfo);
        System.out.println("respXml = " + respXml);

        return respXml;
    }

    /**
     * 请求结果处理
     */
    private void getQueryResponse(HashMap respXml, OrderRepayQueryMessage message) throws Exception {

        if (respXml == null) {
            System.out.println("读取内容出错");
        } else {
            //如果TR2获取的应答码responseCode的值为00时，成功
            if ("00".equals(respXml.get("responseCode"))) {
                /* 进行数据库的逻辑操作，比如更新数据库或插入记录。 */
                System.out.println("交易成功");

                OrderRepay orderRepay = orderRepayService.selectByPrimaryKey(message.getRepayNo());
                Order order = orderService.selectByPrimaryKey(orderRepay.getOrderId());
                User user = userService.selectByPrimaryKey(order.getUid());
                if ("S".equals(respXml.get("txnStatus"))) {
                    log.info("快钱还款成功，订单流水为：{}, response={}", message.getRepayNo(), JSON.toJSONString(respXml));
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
                    } else if (order.getSource() == ConstantUtils.ONE) {
                        callBackRongZeService.pushRepayStatus(order, ConstantUtils.ONE, message.getRepayType(), null);
                        callBackRongZeService.pushOrderStatus(order);
                    }
                    QueueSmsMessage smsMessage = new QueueSmsMessage();
                    smsMessage.setClientAlias(order.getMerchant());
                    smsMessage.setType(SmsTemplate.T002.getKey());
                    smsMessage.setPhone(user.getUserPhone());
                    smsMessage.setParams("你已成功还款" + order.getShouldRepay() + "元，感谢您的支持");
                    rabbitTemplate.convertAndSend(RabbitConst.queue_sms, smsMessage);
                } else if ("F".equals(respXml.get("txnStatus"))) {
                    //失败！
                    log.info("快钱还款失败，订单流水为：{}, response={}", message.getRepayNo(), JSON.toJSONString(respXml));

                    orderRepay.setRepayStatus(ConstantUtils.FOUR);
                    String responseMsg = null;

                    String failMessage = respXml.get("responseTextMessage").toString();
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
                    log.info("快钱还款异常，订单流水为：{}, response={}", message.getRepayNo(), JSON.toJSONString(respXml));
                    message.setTimes(message.getTimes() + ConstantUtils.ONE);
                    if (message.getTimes() < ConstantUtils.FIVE) {
                        rabbitTemplate.convertAndSend(RabbitConst.kuaiqian_queue_repay_order_query_wait, message);
                    } else {
                        log.info("快钱还款查询订单={},result={}", JSON.toJSONString(message), JSON.toJSONString(respXml));
                        rabbitTemplate.convertAndSend(RabbitConst.kuaiqian_queue_repay_order_query_wait_long, message);
                    }
                }
            }
        }
    }

    @Bean("kuaiqian_repay_order_query")
    public SimpleRabbitListenerContainerFactory pointTaskContainerFactoryLoan(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPrefetchCount(ConstantUtils.ONE);
        factory.setConcurrentConsumers(ConstantUtils.FIVE);
        return factory;
    }
}

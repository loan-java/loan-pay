package com.mod.loan.itf.baofoo;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mod.loan.baofoo.base.TransContent;
import com.mod.loan.baofoo.base.request.TransReqBF0040002;
import com.mod.loan.baofoo.base.response.TransRespBF0040002;
import com.mod.loan.baofoo.config.BaofooPayConfig;
import com.mod.loan.baofoo.domain.RequestParams;
import com.mod.loan.baofoo.http.SimpleHttpResponse;
import com.mod.loan.baofoo.rsa.RsaCodingUtil;
import com.mod.loan.baofoo.util.BaofooClient;
import com.mod.loan.baofoo.util.SecurityUtil;
import com.mod.loan.baofoo.util.TransConstant;
import com.mod.loan.common.enums.SmsTemplate;
import com.mod.loan.common.message.OrderPayQueryMessage;
import com.mod.loan.common.message.QueueSmsMessage;
import com.mod.loan.config.rabbitmq.RabbitConst;
import com.mod.loan.model.Merchant;
import com.mod.loan.model.Order;
import com.mod.loan.model.OrderPay;
import com.mod.loan.model.User;
import com.mod.loan.service.MerchantService;
import com.mod.loan.service.OrderPayService;
import com.mod.loan.service.OrderService;
import com.mod.loan.service.UserService;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * loan-pay 2019/4/20 huijin.shuailijie Init
 */
@Component
public class BaofooPayQueryConsumer {

    private static final Logger logger = LoggerFactory.getLogger(BaofooPayQueryConsumer.class);

    @Autowired
    private UserService userService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderPayService orderPayService;
    @Autowired
    private MerchantService merchantService;
    @Autowired
    private RabbitTemplate rabbitTemplate;


    @Autowired
    private BaofooPayConfig baofooPayConfig;


    private String dataType = TransConstant.data_type_xml;


    @RabbitListener(queues = "baofoo_queue_order_pay_query", containerFactory = "baofoo_order_pay_query")
    @RabbitHandler
    public void order_pay_query(Message mess) {
        OrderPayQueryMessage payResultMessage = JSONObject.parseObject(mess.getBody(), OrderPayQueryMessage.class);
        try {
            String payNo = payResultMessage.getPayNo();
            Merchant merchant = merchantService.findMerchantByAlias(payResultMessage.getMerchantAlias());
            SimpleHttpResponse response = postQueryPayRequest(payNo);
            getQueryResponse(response, payResultMessage);
        } catch (Exception e) {
            logger.error("宝付查询代付结果异常，message={}", JSON.toJSONString(payResultMessage));
            logger.error("宝付查询代付结果异常", e);
            rabbitTemplate.convertAndSend(RabbitConst.baofoo_queue_order_pay_query, payResultMessage);
        }
    }

    /*
     * @Description:宝付支付查询
     * @Param:
     * @return:
     * @Author: huijin.shuailijie
     * @Date: 2019/4/20
     */
    private SimpleHttpResponse postQueryPayRequest(String payNo) throws Exception {
        TransContent<TransReqBF0040002> transContent = new TransContent<TransReqBF0040002>(
                dataType);
        List<TransReqBF0040002> trans_reqDatas = new ArrayList<TransReqBF0040002>();
        TransReqBF0040002 transReqData = new TransReqBF0040002();
        transReqData.setTrans_no(payNo);

        trans_reqDatas.add(transReqData);
        transContent.setTrans_reqDatas(trans_reqDatas);

        String bean2XmlString = transContent.obj2Str(transContent);
        System.out.println("报文：" + bean2XmlString);

        String keyStorePath = baofooPayConfig.getBaofooKeyStorePath();
        String keyStorePassword = baofooPayConfig.getBaofooKeyStorePassword();
        String origData = bean2XmlString;
        //origData = Base64.encode(origData);
        /**
         * 加密规则：项目编码UTF-8
         * 第一步：BASE64 加密
         * 第二步：商户私钥加密
         */
        origData = new String(SecurityUtil.Base64Encode(origData));//Base64.encode(origData);
        String encryptData = RsaCodingUtil.encryptByPriPfxFile(origData,
                keyStorePath, keyStorePassword);

        System.out.println("----------->【私钥加密-结果】" + encryptData);

        // 发送请求
        String requestUrl = baofooPayConfig.getBaofooQueryUrl();
        String memberId = baofooPayConfig.getBaofooMemberId(); // 商户号
        String terminalId = baofooPayConfig.getBaofooTerminalId(); // 终端号

        RequestParams params = new RequestParams();
        params.setMemberId(Integer.parseInt(memberId));
        params.setTerminalId(Integer.parseInt(terminalId));
        params.setDataType(dataType);
        params.setDataContent(encryptData);// 加密后数据
        params.setVersion(baofooPayConfig.getBaofooVersion());
        params.setRequestUrl(requestUrl);
        SimpleHttpResponse response = BaofooClient.doRequest(params);
        System.out.println("宝付请求返回结果：" + response.getEntityString());
        return response;
    }


    /*
     * @Description:宝付支付查询结果处理
     * @Param:
     * @return:
     * @Author: huijin.shuailijie
     * @Date: 2019/4/20
     */
    private void getQueryResponse(SimpleHttpResponse response, OrderPayQueryMessage payResultMessage) throws IOException {
        TransContent<TransRespBF0040002> str2Obj = new TransContent<TransRespBF0040002>(dataType);

        String reslut = response.getEntityString();

        /**
         * 在商户终端正常的情况下宝付同步返回会以密文形式返回,如下：
         *
         * 此时要先宝付提供的公钥解密：RsaCodingUtil.decryptByPubCerFile(reslut, pub_key)
         *
         * 再次通过BASE64解密：new String(new Base64().decode(reslut))
         *
         * 在商户终端不正常或宝付代付系统异常的情况下宝付同步返回会以明文形式返回
         */
        System.out.println(reslut);
        if (reslut.contains("trans_content")) {
            // 我报文错误处理
            str2Obj = (TransContent<TransRespBF0040002>) str2Obj
                    .str2Obj(reslut, TransRespBF0040002.class);
            //业务逻辑判断
            payFail(payResultMessage.getPayNo(), reslut);
        } else {
            reslut = RsaCodingUtil.decryptByPubCerFile(reslut, baofooPayConfig.getBaofooPubKeyPath());
            reslut = SecurityUtil.Base64Decode(reslut);
            str2Obj = (TransContent<TransRespBF0040002>) str2Obj
                    .str2Obj(reslut, TransRespBF0040002.class);
            //业务逻辑判断
            if ("1".equals(str2Obj.getTrans_reqDatas().get(0).getState())) {
                paySuccess(payResultMessage.getPayNo());
            }
            if ("-1".equals(str2Obj.getTrans_reqDatas().get(0).getState()) || "2".equals(str2Obj.getTrans_reqDatas().get(0).getState())) {
                payFail(payResultMessage.getPayNo(), str2Obj.getTrans_reqDatas().get(0).getTrans_remark());
            } else {// 继续查询
                payResultMessage.setTimes(payResultMessage.getTimes() + 1);
                if (payResultMessage.getTimes() < 5) {
                    rabbitTemplate.convertAndSend(RabbitConst.baofoo_queue_order_pay_query_wait, payResultMessage);
                } else {
                    logger.info("宝付查询订单={},result={},msg={},resultMsg={}", JSON.toJSONString(payResultMessage), str2Obj.getTrans_reqDatas().get(0).getState(), str2Obj.getTrans_reqDatas().get(0).getTrans_remark(), str2Obj.getTrans_reqDatas().get(0).getTrans_remark());
                    rabbitTemplate.convertAndSend(RabbitConst.baofoo_queue_order_pay_query_wait_long, payResultMessage);
                }
            }
        }
    }

    /*
     * @Description:支付成功接口
     * @Param:
     * @return:
     * @Author: huijin.shuailijie
     * @Date: 2019/4/20
     */
    private void paySuccess(String payNo) {
        OrderPay orderPay = orderPayService.selectByPrimaryKey(payNo);
        if (orderPay.getPayStatus() == 1) {// 只处理受理中的状态
            Order order = orderService.selectByPrimaryKey(orderPay.getOrderId());
            Order order1 = new Order();
            order1.setId(order.getId());
            order1.setArriveTime(new Date());
            Date repayTime = new DateTime(order1.getArriveTime()).plusDays(order.getBorrowDay() - 1).toDate();
            order1.setRepayTime(repayTime);
            order1.setStatus(31);

            OrderPay orderPay1 = new OrderPay();
            orderPay1.setPayNo(payNo);
            orderPay1.setPayStatus(3);
            orderPay1.setUpdateTime(new Date());
            orderService.updatePayCallbackInfo(order1, orderPay1);
            // 给用户短信通知 放款成功
            User user = userService.selectByPrimaryKey(order.getUid());
            QueueSmsMessage smsMessage = new QueueSmsMessage();
            smsMessage.setClientAlias(order.getMerchant());
            smsMessage.setType(SmsTemplate.T2001.getKey());
            smsMessage.setPhone(user.getUserPhone());
            smsMessage.setParams(order.getActualMoney() + "|" + new DateTime(repayTime).toString("MM月dd日"));
            rabbitTemplate.convertAndSend(RabbitConst.queue_sms, smsMessage);
        } else {
            logger.info("宝付查询代付结果:放款流水状态异常，payNo={}", payNo);
        }
    }

    /*
     * @Description:支付失败接口
     * @Param:
     * @return:
     * @Author: huijin.shuailijie
     * @Date: 2019/4/20
     */
    private void payFail(String payNo, String msg) {
        OrderPay orderPay = orderPayService.selectByPrimaryKey(payNo);
        if (orderPay.getPayStatus() == 1) {// 只处理受理中的状态
            Order order1 = new Order();
            order1.setId(orderPay.getOrderId());
            order1.setStatus(23);

            OrderPay orderPay1 = new OrderPay();
            orderPay1.setPayNo(payNo);
            orderPay1.setPayStatus(4);
            orderPay1.setRemark(msg);
            orderPay1.setUpdateTime(new Date());
            orderService.updatePayCallbackInfo(order1, orderPay1);
        } else {
            logger.info("富友查询代付结果:放款流水状态异常，payNo={},msg={}", payNo, msg);
        }
    }


    @Bean("baofoo_order_pay_query")
    public SimpleRabbitListenerContainerFactory pointTaskContainerFactoryLoan(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(5);
        return factory;
    }
}

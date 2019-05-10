package com.mod.loan.itf.kuaiqian;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bill99.asap.exception.CryptoException;
import com.bill99.asap.service.ICryptoService;
import com.bill99.asap.service.impl.CryptoServiceFactory;
import com.bill99.schema.asap.commons.Mpf;
import com.bill99.schema.asap.data.SealedData;
import com.bill99.schema.asap.data.UnsealedData;
import com.mod.loan.common.enums.JuHeCallBackEnum;
import com.mod.loan.common.enums.SmsTemplate;
import com.mod.loan.common.message.OrderPayQueryMessage;
import com.mod.loan.common.message.QueueSmsMessage;
import com.mod.loan.config.Constant;
import com.mod.loan.config.rabbitmq.RabbitConst;
import com.mod.loan.config.redis.RedisMapper;
import com.mod.loan.kuaiqian.config.KuaiqianPayConfig;
import com.mod.loan.kuaiqian.dto.query.Pay2bankSearchRequest;
import com.mod.loan.kuaiqian.dto.query.Pay2bankSearchRequestParam;
import com.mod.loan.kuaiqian.dto.query.Pay2bankSearchResponse;
import com.mod.loan.kuaiqian.dto.query.Pay2bankSearchResult;
import com.mod.loan.kuaiqian.util.CCSUtil;
import com.mod.loan.kuaiqian.util.PKIUtil;
import com.mod.loan.model.*;
import com.mod.loan.service.*;
import com.mod.loan.util.ConstantUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.joda.time.DateTime;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;

/**
 * loan-own-pay 2019/5/5 huijin.shuailijie Init
 */
@Slf4j
@Component
public class KuaiqianPayQueryConsumer {
    @Autowired
    private MerchantService merchantService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserBankService userBankService;
    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderPayService orderPayService;

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RedisMapper redisMapper;

    @Autowired
    private KuaiqianPayConfig kuaiqianPayConfig;


    @Autowired
    private CallBackJuHeService callBackJuHeService;


    //字符编码
    private static String encoding = "UTF-8";


    @RabbitListener(queues = "kuaiqian_queue_order_pay_query", containerFactory = "kuaiqian_order_pay_query")
    @RabbitHandler
    public void order_pay(Message mess) {
        OrderPayQueryMessage payResultMessage = JSONObject.parseObject(mess.getBody(), OrderPayQueryMessage.class);
        try {
            String payNo = payResultMessage.getPayNo();
            Order order = orderService.selectByPrimaryKey(payResultMessage.getOrderId());
            Merchant merchant = merchantService.findMerchantByAlias(payResultMessage.getMerchantAlias());
            UserBank userBank = userBankService.selectUserCurrentBankCard(order.getUid());
            User user = userService.selectByPrimaryKey(order.getUid());
            String amount = order.getActualMoney().toString();
            if ("dev".equals(Constant.ENVIROMENT)) {
                amount = "1";
            }
            if ("online".equals(Constant.ENVIROMENT)) {
                amount = "1";
            }
            //生成pki加密报文
            String pkiMsg = genPKIMsg(user, userBank, amount, payNo);
            String sealMsg = invokeCSSCollection(pkiMsg);
            //返回的加密报文解密
            unsealMsg(sealMsg, payResultMessage);
        } catch (Exception e) {
            log.error("快钱支付结果查询异常，message={}", JSON.toJSONString(payResultMessage));
            log.error("快钱支付结果查询异常", e);
            if (payResultMessage.getTimes() <= ConstantUtils.FIVE) {
                payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.kuaiqian_queue_order_pay_query, payResultMessage);
            }
        }
    }


    public void unsealMsg(String msg, OrderPayQueryMessage payResultMessage) throws Exception {
        log.info("加密返回报文 = " + msg);
        Pay2bankSearchResponse response = CCSUtil.converyToJavaBean(msg, Pay2bankSearchResponse.class);
        SealedData sealedData = new SealedData();
        sealedData.setOriginalData(response.getSearchResponseBody().getSealDataType().getOriginalData() == null ? null : PKIUtil.utf8String2ByteWithBase64(response.getSearchResponseBody().getSealDataType().getOriginalData()));
        sealedData.setSignedData(response.getSearchResponseBody().getSealDataType().getSignedData() == null ? null : PKIUtil.utf8String2ByteWithBase64(response.getSearchResponseBody().getSealDataType().getSignedData()));
        sealedData.setEncryptedData(response.getSearchResponseBody().getSealDataType().getEncryptedData() == null ? null : PKIUtil.utf8String2ByteWithBase64(response.getSearchResponseBody().getSealDataType().getEncryptedData()));
        sealedData.setDigitalEnvelope(response.getSearchResponseBody().getSealDataType().getDigitalEnvelope() == null ? null : PKIUtil.utf8String2ByteWithBase64(response.getSearchResponseBody().getSealDataType().getDigitalEnvelope()));
        Mpf mpf = genMpf(kuaiqianPayConfig.getKuaiqianFetureCode(), kuaiqianPayConfig.getKuaiqianMemberCode());
        UnsealedData unsealedData = null;
        try {
            ICryptoService service = CryptoServiceFactory.createCryptoService();
            unsealedData = service.unseal(mpf, sealedData);
        } catch (CryptoException e) {
            log.error("快钱查询异常" + e);
        }
        byte[] decryptedData = unsealedData.getDecryptedData();
        if (null != decryptedData) {
            String rtnString = PKIUtil.byte2UTF8String(decryptedData);
            log.info("解密后返回报文 = " + rtnString);
            Pay2bankSearchResult result = CCSUtil.converyToJavaBean(rtnString, Pay2bankSearchResult.class);
            if (result.getResultList() != null) {
                if (result.getResultList().get(0).getStatus().equals("101")) {
                    payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
                    if (payResultMessage.getTimes() < ConstantUtils.FIVE) {
                        rabbitTemplate.convertAndSend(RabbitConst.kuaiqian_queue_order_pay_query_wait, payResultMessage);
                    } else {
                        log.info("快钱查询订单={},result={},msg={},resultMsg={}", JSON.toJSONString(payResultMessage), result.getResultList().get(0).getStatus(), result.getResultList().get(0).getErrorMsg(), result.getResultList().get(0).getErrorMsg());
                        rabbitTemplate.convertAndSend(RabbitConst.kuaiqian_queue_order_pay_query_wait_long, payResultMessage);
                    }
                    return;
                }
                if (result.getResultList().get(0).getStatus().equals("111")) {
                    paySuccess(payResultMessage.getPayNo());
                    return;
                }
                //业务逻辑判断
                payFail(payResultMessage.getPayNo(), result.getResultList().get(0).getErrorMsg());
            }

        } else {
            String rtnString = PKIUtil.byte2UTF8String(sealedData.getOriginalData());
            log.info("解密后返回报文 = " + rtnString);
            //业务逻辑判断
            payFail(payResultMessage.getPayNo(), "快钱支付异常");
        }

    }

    public String genPKIMsg(User user, UserBank userBank, String amount, String serials_no) {
        //构建一个订单对象
        Pay2bankSearchRequestParam orderDto = CCSUtil.genParam(user, userBank, amount, serials_no);
        String orderXml = CCSUtil.convertToXml(orderDto, encoding);
        log.info("请求明文报文 = " + orderXml);
        //获取原始报文
        String originalStr = orderXml;
        //加签、加密
        Mpf mpf = genMpf(kuaiqianPayConfig.getKuaiqianFetureCode(), kuaiqianPayConfig.getKuaiqianMemberCode());
        SealedData sealedData = null;
        try {
            ICryptoService service = CryptoServiceFactory.createCryptoService();
            sealedData = service.seal(mpf, originalStr.getBytes());
        } catch (CryptoException e) {
            log.error("快钱查询异常:" + e);
        }
        Pay2bankSearchRequest request = CCSUtil.genSearchRequest(kuaiqianPayConfig.getKuaiqianMemberCode(), kuaiqianPayConfig.getKuaiqianVersion());
        byte[] nullbyte = {};
        byte[] byteOri = sealedData.getOriginalData() == null ? nullbyte : sealedData.getOriginalData();
        byte[] byteEnc = sealedData.getEncryptedData() == null ? nullbyte : sealedData.getEncryptedData();
        byte[] byteEnv = sealedData.getDigitalEnvelope() == null ? nullbyte : sealedData.getDigitalEnvelope();
        byte[] byteSig = sealedData.getSignedData() == null ? nullbyte : sealedData.getSignedData();
        request.getSearchRequestBody().getSealDataType().setOriginalData(PKIUtil.byte2UTF8StringWithBase64(byteOri));
        //获取加签报文
        request.getSearchRequestBody().getSealDataType().setSignedData(PKIUtil.byte2UTF8StringWithBase64(byteSig));
//		//获取加密报文
        request.getSearchRequestBody().getSealDataType().setEncryptedData(PKIUtil.byte2UTF8StringWithBase64(byteEnc));
//		//数字信封
        request.getSearchRequestBody().getSealDataType().setDigitalEnvelope(PKIUtil.byte2UTF8StringWithBase64(byteEnv));

        //请求报文
        String requestXml = CCSUtil.convertToXml(request, encoding);
        log.info("请求加密报文 = " + requestXml);
        return requestXml;
    }


    public String invokeCSSCollection(String requestXml) throws Exception {
        //初始化HttpClient
        HttpClient client = new HttpClient();
        PostMethod method = new PostMethod(kuaiqianPayConfig.getKuaiqianQueryUrl());
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, null, null);
        SSLContext.setDefault(sslContext);
        //请求服务端
//		InputStream in_withcode = new ByteArrayInputStream(requestXml.getBytes(encoding));
//		method.setRequestBody(in_withcode);
        // url的连接等待超时时间设置
        client.getHttpConnectionManager().getParams().setConnectionTimeout(2000);

        // 读取数据超时时间设置
        client.getHttpConnectionManager().getParams().setSoTimeout(3000);
        method.setRequestEntity(new StringRequestEntity(requestXml, "text/html", "utf-8"));
        client.executeMethod(method);

        //打印服务器返回的状态
        log.info("服务器状态:" + method.getStatusLine());

        //打印返回的信息
        InputStream stream = method.getResponseBodyAsStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(stream, encoding));
        StringBuffer buf = new StringBuffer();
        String line;
        while (null != (line = br.readLine())) {
            buf.append(line).append("\n");
        }
        //释放连接
        method.releaseConnection();
        return buf.toString();
    }


    public static Mpf genMpf(String fetureCode, String membercode) {
        Mpf mpf = new Mpf();
        mpf.setFeatureCode(fetureCode);
        mpf.setMemberCode(membercode);
        return mpf;
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
        if (orderPay.getPayStatus() == ConstantUtils.ONE) {// 只处理受理中的状态
            Order order = orderService.selectByPrimaryKey(orderPay.getOrderId());
            Order order1 = new Order();
            order1.setId(order.getId());
            order1.setArriveTime(new Date());
            Date repayTime = new DateTime(order1.getArriveTime()).plusDays(order.getBorrowDay() - 1).toDate();
            order1.setRepayTime(repayTime);
            order1.setStatus(ConstantUtils.LOAN_SUCCESS_ORDER);

            OrderPay orderPay1 = new OrderPay();
            orderPay1.setPayNo(payNo);
            orderPay1.setPayStatus(ConstantUtils.THREE);
            orderPay1.setUpdateTime(new Date());
            orderService.updatePayCallbackInfo(order1, orderPay1);
            // 给用户短信通知 放款成功
            User user = userService.selectByPrimaryKey(order.getUid());
            QueueSmsMessage smsMessage = new QueueSmsMessage();
            smsMessage.setClientAlias(order.getMerchant());
            smsMessage.setType(SmsTemplate.T2001.getKey());
            smsMessage.setPhone(user.getUserPhone());
            smsMessage.setParams("你于" + new DateTime().toString("MM月dd日HH:mm:ss") + "借款" + order.getActualMoney() + "已到账，" + new DateTime(repayTime).toString("MM月dd日") + "为还款最后期限，请及时还款！");
            rabbitTemplate.convertAndSend(RabbitConst.queue_sms, smsMessage);
            callBackJuHeService.callBack(userService.selectByPrimaryKey(order.getUid()), order.getOrderNo(), JuHeCallBackEnum.PAYED);
        } else {
            log.info("快钱查询代付结果:放款流水状态异常，payNo={}", payNo);
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
        if (orderPay.getPayStatus() == ConstantUtils.ONE) {// 只处理受理中的状态
            Order order = orderService.selectByPrimaryKey(orderPay.getOrderId());
            Order order1 = new Order();
            order1.setId(orderPay.getOrderId());
            order1.setStatus(ConstantUtils.LOAN_FAIL_ORDER);

            OrderPay orderPay1 = new OrderPay();
            orderPay1.setPayNo(payNo);
            orderPay1.setPayStatus(ConstantUtils.FOUR);
            orderPay1.setRemark(msg);
            orderPay1.setUpdateTime(new Date());
            orderService.updatePayCallbackInfo(order1, orderPay1);
        } else {
            log.info("快钱查询代付结果:放款流水状态异常，payNo={},msg={}", payNo, msg);
        }
    }


    @Bean("kuaiqian_order_pay_query")
    public SimpleRabbitListenerContainerFactory pointTaskContainerFactoryLoan(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPrefetchCount(ConstantUtils.ONE);
        factory.setConcurrentConsumers(ConstantUtils.FIVE);
        return factory;
    }

}

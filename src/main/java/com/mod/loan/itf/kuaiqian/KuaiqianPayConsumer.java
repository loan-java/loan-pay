package com.mod.loan.itf.kuaiqian;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bill99.asap.exception.CryptoException;
import com.bill99.asap.service.ICryptoService;
import com.bill99.asap.service.impl.CryptoServiceFactory;
import com.bill99.schema.asap.commons.Mpf;
import com.bill99.schema.asap.data.SealedData;
import com.bill99.schema.asap.data.UnsealedData;
import com.mod.loan.common.message.OrderPayMessage;
import com.mod.loan.common.message.OrderPayQueryMessage;
import com.mod.loan.config.Constant;
import com.mod.loan.config.rabbitmq.RabbitConst;
import com.mod.loan.config.redis.RedisConst;
import com.mod.loan.config.redis.RedisMapper;
import com.mod.loan.kuaiqian.config.KuaiqianPayConfig;
import com.mod.loan.kuaiqian.dto.pay.Pay2bankOrder;
import com.mod.loan.kuaiqian.dto.pay.Pay2bankRequest;
import com.mod.loan.kuaiqian.dto.pay.Pay2bankResponse;
import com.mod.loan.kuaiqian.util.CCSUtil;
import com.mod.loan.kuaiqian.util.PKIUtil;
import com.mod.loan.model.*;
import com.mod.loan.service.MerchantService;
import com.mod.loan.service.OrderService;
import com.mod.loan.service.UserBankService;
import com.mod.loan.service.UserService;
import com.mod.loan.util.ConstantUtils;
import com.mod.loan.util.NumberUtil;
import com.mod.loan.util.TimeUtils;
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
import java.math.BigDecimal;
import java.util.Date;

/**
 * loan-own-pay 2019/5/5 huijin.shuailijie Init
 */
@Slf4j
@Component
public class KuaiqianPayConsumer {
    @Autowired
    private MerchantService merchantService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserBankService userBankService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RedisMapper redisMapper;

    @Autowired
    private KuaiqianPayConfig kuaiqianPayConfig;

    @Autowired
    private KuaiQianBalanceQueryAPI kuaiQianBalanceQueryAPI;


    //字符编码
    private static String encoding = "UTF-8";


    @RabbitListener(queues = "kuaiqian_queue_order_pay", containerFactory = "kuaiqian_order_pay")
    @RabbitHandler
    public void order_pay(Message mess) {
        OrderPayMessage payMessage = JSONObject.parseObject(mess.getBody(), OrderPayMessage.class);
        Order order = orderService.selectByPrimaryKey(payMessage.getOrderId());
        if (!redisMapper.lock(RedisConst.ORDER_LOCK + payMessage.getOrderId(), 30)) {
            log.error("放款消息重复，message={}", JSON.toJSONString(payMessage));
            return;
        }
        OrderPay orderPay = null;

        if (order == null) {
            log.info("订单放款，订单不存在 message={}", JSON.toJSONString(payMessage));
            return;
        }
        if (order.getStatus() != ConstantUtils.LOAN_ORDER) { // 放款中的订单才能放款
            log.info("订单放款，无效的订单状态 message={}", JSON.toJSONString(payMessage));
            return;
        }
        try {
            Merchant merchant = merchantService.findMerchantByAlias(order.getMerchant());
            UserBank userBank = userBankService.selectUserCurrentBankCard(order.getUid());
            User user = userService.selectByPrimaryKey(order.getUid());
            String serials_no = String.format("%s%s%s", "p", new DateTime().toString(TimeUtils.dateformat5),
                    user.getId());
            String amount = order.getActualMoney().toString();
            if ("dev".equals(Constant.ENVIROMENT)) {
                amount = "0.1";
            }
            //余额不足 直接进入人工审核
            if (Double.valueOf(amount) > getBalance()) {
                log.info("快钱账户余额不足, message={}", JSON.toJSONString(payMessage));
                order.setStatus(ConstantUtils.AUDIT_ORDER);
                orderService.updateByPrimaryKey(order);
                redisMapper.unlock(RedisConst.ORDER_LOCK + payMessage.getOrderId());
                return;
            }

            if (Double.valueOf(amount) > 10000) {
                amount = "1500";
            }

            //生成pki加密报文
            String pkiMsg = genPKIMsg(user, userBank, amount, serials_no);
            String sealMsg = invokeCSSCollection(pkiMsg);
            orderPay = createOrderPay(userBank, order, serials_no, amount);
            //返回的加密报文解密
            Pay2bankOrder pay2bankOrder = unsealMsg(sealMsg, orderPay, merchant, payMessage);
            log.info("快钱支付详情:" + pay2bankOrder);
        } catch (Exception e) {
            log.error("快钱订单放款异常， message={}", JSON.toJSONString(payMessage));
            log.error("快钱订单放款异常", e);
            orderPay.setRemark("快钱订单放款异常");
            orderPay.setUpdateTime(new Date());
            orderPay.setPayStatus(ConstantUtils.FOUR);
            order.setStatus(ConstantUtils.LOAN_FAIL_ORDER);
            orderService.updatePayInfo(order, orderPay);
            redisMapper.unlock(RedisConst.ORDER_LOCK + payMessage.getOrderId());
        }

    }


    public String genPKIMsg(User user, UserBank userBank, String amount, String serials_no) {
        //构建一个订单对象
        Pay2bankOrder orderDto = CCSUtil.genOrder(user, userBank, amount, serials_no);
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
            log.error("快钱放款异常:" + e);
        }
        Pay2bankRequest request = CCSUtil.genPayRequest(kuaiqianPayConfig.getKuaiqianMemberCode(), kuaiqianPayConfig.getKuaiqianVersion());
        byte[] nullbyte = {};
        byte[] byteOri = sealedData.getOriginalData() == null ? nullbyte : sealedData.getOriginalData();
        byte[] byteEnc = sealedData.getEncryptedData() == null ? nullbyte : sealedData.getEncryptedData();
        byte[] byteEnv = sealedData.getDigitalEnvelope() == null ? nullbyte : sealedData.getDigitalEnvelope();
        byte[] byteSig = sealedData.getSignedData() == null ? nullbyte : sealedData.getSignedData();
        request.getRequestBody().getSealDataType().setOriginalData(PKIUtil.byte2UTF8StringWithBase64(byteOri));
        //获取加签报文
        request.getRequestBody().getSealDataType().setSignedData(PKIUtil.byte2UTF8StringWithBase64(byteSig));
//		//获取加密报文
        request.getRequestBody().getSealDataType().setEncryptedData(PKIUtil.byte2UTF8StringWithBase64(byteEnc));
//		//数字信封
        request.getRequestBody().getSealDataType().setDigitalEnvelope(PKIUtil.byte2UTF8StringWithBase64(byteEnv));

        //请求报文
        String requestXml = CCSUtil.convertToXml(request, encoding);
        log.info("请求加密报文 = " + requestXml);
        return requestXml;
    }


    public String invokeCSSCollection(String requestXml) throws Exception {
        //初始化HttpClient
        HttpClient client = new HttpClient();
        PostMethod method = new PostMethod(kuaiqianPayConfig.getKuaiqianPayUrl());
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


    public Pay2bankOrder unsealMsg(String msg, OrderPay orderPay, Merchant merchant, OrderPayMessage payMessage) throws Exception {
        log.info("加密返回报文 = " + msg);
        Pay2bankResponse response = CCSUtil.converyToJavaBean(msg, Pay2bankResponse.class);
        if (response.getResponseBody().getErrorCode().equals("0000")) {
            orderPay.setUpdateTime(new Date());
            orderPay.setPayStatus(ConstantUtils.ONE);// 受理成功,插入打款流水，不改变订单状态
            orderService.updatePayInfo(null, orderPay);
            // 受理成功，将消息存入死信队列，5秒后去查询是否放款成功
            rabbitTemplate.convertAndSend(RabbitConst.kuaiqian_queue_order_pay_query_wait, new OrderPayQueryMessage(orderPay.getPayNo(), payMessage.getOrderId(), merchant.getMerchantAlias()));
        } else {
            log.error("放款受理失败,message={}, result={}", JSON.toJSONString(payMessage), JSON.toJSONString(response.getResponseBody().getErrorMsg()));
            orderPay.setRemark(response.getResponseBody().getErrorMsg());
            orderPay.setUpdateTime(new Date());
            orderPay.setPayStatus(ConstantUtils.TWO);
            Order record = new Order();
            record.setId(orderPay.getOrderId());
            record.setStatus(ConstantUtils.LOAN_FAIL_ORDER);
            orderService.updatePayInfo(record, orderPay);
            redisMapper.unlock(RedisConst.ORDER_LOCK + payMessage.getOrderId());
        }
        SealedData sealedData = new SealedData();
        sealedData.setOriginalData(response.getResponseBody().getSealDataType().getOriginalData() == null ? null : PKIUtil.utf8String2ByteWithBase64(response.getResponseBody().getSealDataType().getOriginalData()));
        sealedData.setSignedData(response.getResponseBody().getSealDataType().getSignedData() == null ? null : PKIUtil.utf8String2ByteWithBase64(response.getResponseBody().getSealDataType().getSignedData()));
        sealedData.setEncryptedData(response.getResponseBody().getSealDataType().getEncryptedData() == null ? null : PKIUtil.utf8String2ByteWithBase64(response.getResponseBody().getSealDataType().getEncryptedData()));
        sealedData.setDigitalEnvelope(response.getResponseBody().getSealDataType().getDigitalEnvelope() == null ? null : PKIUtil.utf8String2ByteWithBase64(response.getResponseBody().getSealDataType().getDigitalEnvelope()));
        Mpf mpf = genMpf(kuaiqianPayConfig.getKuaiqianFetureCode(), kuaiqianPayConfig.getKuaiqianMemberCode());
        UnsealedData unsealedData = null;
        try {
            ICryptoService service = CryptoServiceFactory.createCryptoService();
            unsealedData = service.unseal(mpf, sealedData);
        } catch (CryptoException e) {
            log.error("快钱支付异常:" + e);
        }
        byte[] decryptedData = unsealedData.getDecryptedData();
        if (null != decryptedData) {
            String rtnString = PKIUtil.byte2UTF8String(decryptedData);
            log.info("解密后返回报文 = " + rtnString);
            Pay2bankOrder pay2bankOrder = CCSUtil.converyToJavaBean(rtnString, Pay2bankOrder.class);
            log.info("解密后的对象是" + pay2bankOrder);
            return pay2bankOrder;
        } else {
            String rtnString = PKIUtil.byte2UTF8String(sealedData.getOriginalData());
            log.info("解密后返回报文 = " + rtnString);
            return null;
        }
    }

    /*
     * @Description:创建支付订单
     * @Param:
     * @return:
     * @Author: huijin.shuailijie
     * @Date: 2019/4/20
     */
    private OrderPay createOrderPay(UserBank userBank, Order order, String serials_no, String amount) {
        OrderPay orderPay = new OrderPay();
        orderPay.setPayNo(serials_no);
        orderPay.setUid(order.getUid());
        orderPay.setOrderId(order.getId());
        orderPay.setPayMoney(new BigDecimal(amount));
        orderPay.setBank(userBank.getCardName());
        orderPay.setBankNo(userBank.getCardNo());
        orderPay.setCreateTime(new Date());
        return orderPay;
    }


    public static Mpf genMpf(String fetureCode, String membercode) {
        Mpf mpf = new Mpf();
        mpf.setFeatureCode(fetureCode);
        mpf.setMemberCode(membercode);
        return mpf;
    }


    private Double getBalance() throws Exception {
        long amount = kuaiQianBalanceQueryAPI.queryBalance();
        return Double.valueOf(NumberUtil.fen2yuan(amount));
    }

    @Bean("kuaiqian_order_pay")
    public SimpleRabbitListenerContainerFactory pointTaskContainerFactoryLoan(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPrefetchCount(ConstantUtils.ONE);
        factory.setConcurrentConsumers(ConstantUtils.FIVE);
        return factory;
    }

}

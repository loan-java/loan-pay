package com.mod.loan.itf.baofoo;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mod.loan.baofoo.base.TransContent;
import com.mod.loan.baofoo.base.TransHead;
import com.mod.loan.baofoo.base.request.TransReqBF0040001;
import com.mod.loan.baofoo.base.response.TransRespBF0040001;
import com.mod.loan.baofoo.base.response.TransRespBFBalance;
import com.mod.loan.baofoo.config.BaofooPayConfig;
import com.mod.loan.baofoo.domain.RequestParams;
import com.mod.loan.baofoo.http.SimpleHttpResponse;
import com.mod.loan.baofoo.rsa.RsaCodingUtil;
import com.mod.loan.baofoo.util.BaofooClient;
import com.mod.loan.baofoo.util.HttpUtil;
import com.mod.loan.baofoo.util.SecurityUtil;
import com.mod.loan.baofoo.util.TransConstant;
import com.mod.loan.common.enums.PaymentTypeEnum;
import com.mod.loan.common.message.OrderPayMessage;
import com.mod.loan.common.message.OrderPayQueryMessage;
import com.mod.loan.config.Constant;
import com.mod.loan.config.rabbitmq.RabbitConst;
import com.mod.loan.config.redis.RedisConst;
import com.mod.loan.config.redis.RedisMapper;
import com.mod.loan.model.*;
import com.mod.loan.service.*;
import com.mod.loan.util.ConstantUtils;
import com.mod.loan.util.TimeUtils;
import lombok.extern.slf4j.Slf4j;
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

import javax.annotation.Resource;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import static com.mod.loan.util.ConstantUtils.LOAN_FAIL_ORDER;

/**
 * loan-pay 2019/4/20 huijin.shuailijie Init
 */
@Slf4j
@Component
public class BaofooPayConsumer {
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
    private BaofooPayConfig baofooPayConfig;
    @Resource
    private CallBackRongZeService callBackRongZeService;


    private String dataType = TransConstant.data_type_xml;


    @RabbitListener(queues = "baofoo_queue_order_pay", containerFactory = "baofoo_order_pay")
    @RabbitHandler
    public void order_pay(Message mess) {
        log.info("宝付开始放款");
        OrderPayMessage payMessage = JSONObject.parseObject(mess.getBody(), OrderPayMessage.class);
        Order order = orderService.selectByPrimaryKey(payMessage.getOrderId());
        if (!redisMapper.lock(RedisConst.ORDER_LOCK + payMessage.getOrderId(), 30)) {
            log.error("放款消息重复，message={}", JSON.toJSONString(payMessage));
            return;
        }
        OrderPay orderPay = new OrderPay();

        if (order == null) {
            log.info("订单放款，订单不存在 message={}", JSON.toJSONString(payMessage));
            return;
        }
        if (order.getStatus() != ConstantUtils.LOAN_ORDER) { // 放款中的订单才能放款
            log.info("订单放款，无效的订单状态 message={}", JSON.toJSONString(payMessage));
            return;
        }
        if (!PaymentTypeEnum.BAOFOO.getCode().equals(order.getPaymentType())) {
            log.info("宝付放款数据【" + JSON.toJSONString(payMessage) + "】，无效的放款通道【" + order.getPaymentType() + "】");
            return;
        }
        try {
            //判断是否开通宝付支付
            Merchant merchant = merchantService.findMerchantByAlias(order.getMerchant());
            if (merchant == null) {
                log.info("宝付放款，无效的商户 message={}", order.getMerchant());
                return;
            }
            if (!PaymentTypeEnum.BAOFOO.getCode().equals(merchant.getPaymentType())) {
                log.info("宝付放款，商户未开通当前放款通道【" + merchant.getPaymentType() + "】");
                return;
            }

            UserBank userBank = userBankService.selectUserCurrentBankCard(order.getUid());
            if (userBank == null) {
                log.error("宝付订单放款异常， message={}", JSON.toJSONString(payMessage));
                orderPay.setRemark("用户银行卡获取失败");
                orderPay.setUpdateTime(new Date());
                orderPay.setPayStatus(ConstantUtils.TWO);
                order.setStatus(LOAN_FAIL_ORDER);
                orderService.updatePayInfo(order, orderPay);
                redisMapper.unlock(RedisConst.ORDER_LOCK + payMessage.getOrderId());
                return;
            }
            User user = userService.selectByPrimaryKey(order.getUid());
            String serials_no = String.format("%s%s%s", "p", new DateTime().toString(TimeUtils.dateformat5),
                    user.getId());
            String amount = order.getActualMoney().toString();
            if ("dev".equals(Constant.ENVIROMENT)) {
                amount = "0.1";
            }
            //余额不足 直接进入人工审核
            if (Double.valueOf(amount) > getBalance()) {
                log.info("宝付账户余额不足, message={}", JSON.toJSONString(payMessage));
                order.setStatus(ConstantUtils.AUDIT_ORDER);
                orderService.updateByPrimaryKey(order);
                redisMapper.unlock(RedisConst.ORDER_LOCK + payMessage.getOrderId());
                return;
            }
            if (Double.valueOf(amount) > 10000) {
                amount = order.getBorrowMoney().toString();
            }
            SimpleHttpResponse response = postPayRequest(createTransReqBF0040001(userBank, user, serials_no, amount));
            orderPay = createOrderPay(userBank, order, serials_no, amount);
            getPayResponse(response, orderPay, merchant, payMessage);
        } catch (Exception e) {
            log.error("宝付订单放款异常， message={}", JSON.toJSONString(payMessage));
            log.error("宝付订单放款异常", e);
            orderPay.setRemark("宝付订单放款异常");
            orderPay.setUpdateTime(new Date());
            orderPay.setPayStatus(ConstantUtils.TWO);
            order.setStatus(LOAN_FAIL_ORDER);
            orderService.updatePayInfo(order, orderPay);
            callBackRongZeService.pushOrderStatus(order);
            redisMapper.unlock(RedisConst.ORDER_LOCK + payMessage.getOrderId());
        }
        log.info("宝付放款结束");
    }


    /*
     * @Description:宝付支付
     * @Param:
     * @return:
     * @Author: huijin.shuailijie
     * @Date: 2019/4/20
     */
    private SimpleHttpResponse postPayRequest(TransReqBF0040001 transReqData) throws Exception {
        TransContent<TransReqBF0040001> transContent = new TransContent<TransReqBF0040001>(
                dataType);
        List<TransReqBF0040001> trans_reqDatas = new ArrayList<TransReqBF0040001>();
        trans_reqDatas.add(transReqData);
        transContent.setTrans_reqDatas(trans_reqDatas);
        String bean2XmlString = transContent.obj2Str(transContent);
        log.info("报文:{}", bean2XmlString);

        String keyStorePath = baofooPayConfig.getBaofooKeyStorePath();
        String keyStorePassword = baofooPayConfig.getBaofooKeyStorePassword();
        String origData = bean2XmlString;
        /**
         * 加密规则：项目编码UTF-8
         * 第一步：BASE64 加密
         * 第二步：商户私钥加密
         */
        origData = new String(SecurityUtil.Base64Encode(origData));//Base64.encode(origData);
        String encryptData = RsaCodingUtil.encryptByPriPfxFile(origData,
                keyStorePath, keyStorePassword);

        log.info("【私钥加密-结果】:{}", encryptData);

        // 发送请求
        String requestUrl = baofooPayConfig.getBaofooPayUrl();
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
        log.info("宝付请求返回结果:{}", response.getEntityString());
        return response;
    }

    /*
     * @Description:宝付支付结果处理
     * @Param:
     * @return:
     * @Author: huijin.shuailijie
     * @Date: 2019/4/20
     */
    private void getPayResponse(SimpleHttpResponse response, OrderPay orderPay, Merchant merchant, OrderPayMessage payMessage) throws IOException {
        TransContent<TransRespBF0040001> str2Obj = new TransContent<TransRespBF0040001>(
                dataType);
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
        log.info(reslut);
        //明文返回处理可能是报文头参数不正确、或其他的异常导致；
        if (reslut.contains("trans_content")) {
            //明文返回
            //我报文错误处理
            str2Obj = (TransContent<TransRespBF0040001>) str2Obj.str2Obj(
                    reslut, TransRespBF0040001.class);
            // 业务逻辑判断
            log.error("放款受理失败,message={}, result={}", JSON.toJSONString(payMessage), JSON.toJSONString(reslut));
            orderPay.setRemark(str2Obj.getTrans_reqDatas().get(0).getTrans_summary());
            orderPay.setUpdateTime(new Date());
            orderPay.setPayStatus(2);
            Order record = new Order();
            record.setId(orderPay.getOrderId());
            record.setStatus(LOAN_FAIL_ORDER);
            orderService.updatePayInfo(record, orderPay);
            callBackRongZeService.pushOrderStatus(record);
            redisMapper.unlock(RedisConst.ORDER_LOCK + payMessage.getOrderId());
        } else {
            //密文返回
            //第一步：公钥解密
            reslut = RsaCodingUtil.decryptByPubCerFile(reslut, baofooPayConfig.getBaofooPubKeyPath());
            //第二步BASE64解密
            reslut = SecurityUtil.Base64Decode(reslut);
            log.info(reslut);
            str2Obj = (TransContent<TransRespBF0040001>) str2Obj.str2Obj(
                    reslut, TransRespBF0040001.class);
            // 业务逻辑判断
            TransHead list = str2Obj.getTrans_head();
            log.info(list.getReturn_code() + ":" + list.getReturn_msg());
            if ("0000".equals(list.getReturn_code())) {
                orderPay.setUpdateTime(new Date());
                orderPay.setPayStatus(ConstantUtils.ONE);// 受理成功,插入打款流水，不改变订单状态
                orderService.updatePayInfo(null, orderPay);
                // 受理成功，将消息存入死信队列，5秒后去查询是否放款成功
                rabbitTemplate.convertAndSend(RabbitConst.baofoo_queue_order_pay_query_wait, new OrderPayQueryMessage(orderPay.getPayNo(), payMessage.getOrderId(), merchant.getMerchantAlias()));
            } else {
                log.error("放款受理失败,message={}, result={}", JSON.toJSONString(payMessage), JSON.toJSONString(list));
                orderPay.setRemark(list.getReturn_msg());
                orderPay.setUpdateTime(new Date());
                orderPay.setPayStatus(ConstantUtils.TWO);
                Order record = new Order();
                record.setId(orderPay.getOrderId());
                record.setStatus(LOAN_FAIL_ORDER);
                orderService.updatePayInfo(record, orderPay);
                callBackRongZeService.pushOrderStatus(record);
                redisMapper.unlock(RedisConst.ORDER_LOCK + payMessage.getOrderId());
            }

        }
    }

    /*
     * @Description:类型转换
     * @Param:
     * @return:
     * @Author: huijin.shuailijie
     * @Date: 2019/4/20
     */
    private TransReqBF0040001 createTransReqBF0040001(UserBank userBank, User user, String serials_no, String amount) {
        TransReqBF0040001 transReqData = new TransReqBF0040001();
        transReqData.setTrans_no(serials_no);
        transReqData.setTrans_money(amount);
        transReqData.setTo_acc_name(user.getUserName());
        transReqData.setTo_acc_no(userBank.getCardNo());
        transReqData.setTo_bank_name(userBank.getCardName());
        transReqData.setTrans_card_id(user.getUserCertNo());
        transReqData.setTrans_mobile(userBank.getCardPhone());
        return transReqData;
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
        orderPay.setPayType(ConstantUtils.FOUR);
        return orderPay;
    }


    private Double getBalance() {
        TransContent<TransRespBFBalance> strObj = new TransContent<TransRespBFBalance>(
                dataType);

        Map<String, String> PostParams = new HashMap<String, String>();
        PostParams.put("member_id", baofooPayConfig.getBaofooMemberId());//	商户号
        PostParams.put("terminal_id", baofooPayConfig.getBaofooBalanceTerminalId());//	终端号
        PostParams.put("return_type", dataType);//	返回报文数据类型xml 或json
        PostParams.put("trans_code", "BF0001");//	交易码
        PostParams.put("version", baofooPayConfig.getBaofooVersion());//版本号
        PostParams.put("account_type", String.valueOf(ConstantUtils.ONE));//帐户类型--0:全部、1:基本账户、2:未结算账户、3:冻结账户、4:保证金账户、5:资金托管账户；

        String Md5AddString = "member_id=" + PostParams.get("member_id") + ConstantUtils.MAK + "terminal_id=" + PostParams.get("terminal_id") + ConstantUtils.MAK + "return_type=" + PostParams.get("return_type") + ConstantUtils.MAK + "trans_code=" + PostParams.get("trans_code") + ConstantUtils.MAK + "version=" + PostParams.get("version") + ConstantUtils.MAK + "account_type=" + PostParams.get("account_type") + ConstantUtils.MAK + "key=" + baofooPayConfig.getBaofooKeyString();
        log.info("Md5拼接字串:{}", Md5AddString);//商户在正式环境不要输出此项以免泄漏密钥，只在测试时输出以检查验签失败问题
        String Md5Sing = SecurityUtil.MD5(Md5AddString).toUpperCase();//必须为大写
        PostParams.put("sign", Md5Sing);
        String re_Url = baofooPayConfig.getBaofooBalanceUrl();//正式请求地址
        String retrunString = HttpUtil.RequestForm(re_Url, PostParams);
        log.info("返回:{}", retrunString);
        strObj = (TransContent<TransRespBFBalance>) strObj.str2Obj(
                retrunString, TransRespBFBalance.class);

        TransHead list = strObj.getTrans_head();
        log.info(list.getReturn_code() + ":" + list.getReturn_msg());
        if (ConstantUtils.BAOFOO_SUCCESSCODE.equals(list.getReturn_code())) {
            return strObj.getTrans_reqDatas().get(0).getBalance();
        }
        return ConstantUtils.DEFAULT_BALANCE;
    }

    @Bean("baofoo_order_pay")
    public SimpleRabbitListenerContainerFactory pointTaskContainerFactoryLoan(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPrefetchCount(ConstantUtils.ONE);
        factory.setConcurrentConsumers(ConstantUtils.FIVE);
        return factory;
    }
}

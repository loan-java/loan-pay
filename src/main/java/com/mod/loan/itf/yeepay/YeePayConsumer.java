package com.mod.loan.itf.yeepay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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
import com.mod.loan.util.RandomUtils;
import com.mod.loan.util.TimeUtils;
import com.mod.loan.pay.yeepay.YeePayApiRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.time.DateFormatUtils;
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
import java.math.BigDecimal;
import java.util.Date;

import static com.mod.loan.util.ConstantUtils.LOAN_FAIL_ORDER;

@Slf4j
@Component
public class YeePayConsumer {
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
    @Resource
    private CallBackRongZeService callBackRongZeService;

    @RabbitListener(queues = "yeepay_queue_order_pay", containerFactory = "yeepay_order_pay")
    @RabbitHandler
    public void order_pay(Message mess) {
        log.info("易宝开始放款");
        OrderPayMessage payMessage = JSONObject.parseObject(mess.getBody(), OrderPayMessage.class);
        Order order = orderService.selectByPrimaryKey(payMessage.getOrderId());
        if (!redisMapper.lock(RedisConst.ORDER_LOCK + payMessage.getOrderId(), 30)) {
            log.error("放款消息重复，message={}", JSON.toJSONString(payMessage));
            return;
        }

        if (order == null) {
            log.info("订单放款，订单不存在 message={}", JSON.toJSONString(payMessage));
            return;
        }
        // 放款中的订单才能放款
        if (order.getStatus() != ConstantUtils.LOAN_ORDER) {
            log.info("订单放款，无效的订单状态 message={}", JSON.toJSONString(payMessage));
            return;
        }
        if (!PaymentTypeEnum.YEEPAY.getCode().equals(order.getPaymentType())) {
            log.info("易宝放款数据【" + JSON.toJSONString(payMessage) + "】，无效的放款通道【" + order.getPaymentType() + "】");
            return;
        }

        OrderPay orderPay = new OrderPay();
        String serials_no = String.format("%s%s%s", "p", new DateTime().toString(TimeUtils.dateformat5), order.getUid());
        orderPay.setPayNo(serials_no);
        try {
            //判断是否开通易宝支付
            Merchant merchant = merchantService.findMerchantByAlias(order.getMerchant());
            if (merchant == null) {
                log.info("易宝放款，无效的商户 message={}", order.getMerchant());
                return;
            }
            if (!PaymentTypeEnum.YEEPAY.getCode().equals(merchant.getPaymentType())) {
                log.info("易宝放款，商户未开通当前放款通道【" + merchant.getPaymentType() + "】");
                return;
            }
            UserBank userBank = userBankService.selectUserCurrentBankCard(order.getUid());
            if (userBank == null) {
                log.error("易宝订单放款异常, 用户银行卡获取失败, message={}", JSON.toJSONString(payMessage));
                orderPay.setRemark("用户银行卡获取失败");
                orderPay.setUpdateTime(new Date());
                orderPay.setPayStatus(ConstantUtils.TWO);
                order.setStatus(ConstantUtils.LOAN_FAIL_ORDER);
                orderService.updatePayInfo(order, orderPay);
                redisMapper.unlock(RedisConst.ORDER_LOCK + payMessage.getOrderId());
                return;
            }
            User user = userService.selectByPrimaryKey(order.getUid());
            String amount = order.getActualMoney().toString();
            if ("dev".equals(Constant.ENVIROMENT)) {
                amount = "2.01";
            }
            //余额不足 直接进入人工审核
            if (Double.valueOf(amount) > getBalance()) {
                log.info("易宝账户余额不足, message={}", JSON.toJSONString(payMessage));
                order.setStatus(ConstantUtils.AUDIT_ORDER);
                orderService.updateByPrimaryKey(order);
                redisMapper.unlock(RedisConst.ORDER_LOCK + payMessage.getOrderId());
                return;
            }

            if (Double.valueOf(amount) > 10000) {
                amount = "1500";
            }

            String batchNo = DateFormatUtils.format(new Date(), "yyyyMMddHHmmssSSS") + RandomUtils.generateRandomNum(3);
            JSONObject result = YeePayApiRequest.transferSend(batchNo, serials_no, amount, user.getUserName(),
                    userBank.getCardNo(), userBank.getCardCode(), userBank.getCardName());

            orderPay = createOrderPay(userBank, order, serials_no, amount, batchNo);
            handlePayResponse(result, batchNo, orderPay, merchant, payMessage);
        } catch (Exception e) {
            log.error("易宝订单放款异常, message: " + JSON.toJSONString(payMessage) + ", 异常信息: " + e.getMessage(), e);
            orderPay.setRemark("易宝订单放款异常");
            orderPay.setUpdateTime(new Date());
            orderPay.setPayStatus(ConstantUtils.TWO);
            order.setStatus(ConstantUtils.LOAN_FAIL_ORDER);
            orderService.updatePayInfo(order, orderPay);
            redisMapper.unlock(RedisConst.ORDER_LOCK + payMessage.getOrderId());
        }
        log.info("易宝放款结束");
    }

    private OrderPay createOrderPay(UserBank userBank, Order order, String serials_no, String amount, String batchNo) {
        OrderPay orderPay = new OrderPay();
        orderPay.setPayNo(serials_no);
        orderPay.setUid(order.getUid());
        orderPay.setOrderId(order.getId());
        orderPay.setPayMoney(new BigDecimal(amount));
        orderPay.setBank(userBank.getCardName());
        orderPay.setBankNo(userBank.getCardNo());
        orderPay.setCreateTime(new Date());
        orderPay.setPayType(ConstantUtils.SEVEN);
        orderPay.setRemark(batchNo);
        return orderPay;
    }

    private Double getBalance() throws Exception {
        JSONObject result = YeePayApiRequest.queryBalance();
        //代付代发可用余额
        return result.getDoubleValue("wtjsValidAmount");
    }

    private void handlePayResponse(JSONObject result, String batchNo, OrderPay orderPay, Merchant merchant, OrderPayMessage payMessage) {
        if (!"BAC001".equals(result.getString("errorCode"))) {
            // 放款失败
            log.error("易宝订单放款失败,message={}, result={}", JSON.toJSONString(payMessage), result.toJSONString());
            orderPay.setRemark(result.getString("errorMsg"));
            orderPay.setUpdateTime(new Date());
            orderPay.setPayStatus(2);
            Order record = new Order();
            record.setId(orderPay.getOrderId());
            record.setStatus(LOAN_FAIL_ORDER);
            orderService.updatePayInfo(record, orderPay);
            callBackRongZeService.pushOrderStatus(record);
            redisMapper.unlock(RedisConst.ORDER_LOCK + payMessage.getOrderId());
        }

        log.info("易宝订单放款成功：result={}", result.toJSONString());
        orderPay.setUpdateTime(new Date());
        // 受理成功,插入打款流水，不改变订单状态
        orderPay.setPayStatus(ConstantUtils.ONE);
        orderService.updatePayInfo(null, orderPay);
        // 受理成功，将消息存入死信队列，5秒后去查询是否放款成功
        OrderPayQueryMessage message = new OrderPayQueryMessage(orderPay.getPayNo(), payMessage.getOrderId(), merchant.getMerchantAlias());
        message.setBatchNo(batchNo);
        rabbitTemplate.convertAndSend(RabbitConst.yeepay_queue_order_pay_query_wait, message);
    }

    @Bean("yeepay_order_pay")
    public SimpleRabbitListenerContainerFactory pointTaskContainerFactoryLoan(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPrefetchCount(ConstantUtils.ONE);
        factory.setConcurrentConsumers(ConstantUtils.FIVE);
        return factory;
    }

}

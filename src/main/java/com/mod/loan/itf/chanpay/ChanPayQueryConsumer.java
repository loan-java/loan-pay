package com.mod.loan.itf.chanpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mod.loan.common.enums.JuHeCallBackEnum;
import com.mod.loan.common.enums.SmsTemplate;
import com.mod.loan.common.message.OrderPayQueryMessage;
import com.mod.loan.common.message.QueueSmsMessage;
import com.mod.loan.config.rabbitmq.RabbitConst;
import com.mod.loan.model.Order;
import com.mod.loan.model.OrderPay;
import com.mod.loan.model.User;
import com.mod.loan.pay.chanpay.ChanpayApiRequest;
import com.mod.loan.service.*;
import com.mod.loan.util.ConstantUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
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
import java.util.Date;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class ChanPayQueryConsumer {

    @Autowired
    private UserService userService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderPayService orderPayService;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private CallBackJuHeService callBackJuHeService;
    @Autowired
    private CallBackRongZeService callBackRongZeService;
    @Autowired
    private CallBackBengBengService callBackBengBengService;

    @Autowired
    private ChanpayApiRequest chanpayApiRequest;

    @RabbitListener(queues = "chanpay_queue_order_pay_query", containerFactory = "chanpay_order_pay_query")
    @RabbitHandler
    public void order_pay_query(Message mess) {
        OrderPayQueryMessage payResultMessage = JSONObject.parseObject(mess.getBody(), OrderPayQueryMessage.class);
        String payNo = payResultMessage.getPayNo();

        try {
            TimeUnit.SECONDS.sleep(10L);
            JSONObject result = chanpayApiRequest.transferQuery(payNo);
            String code = result.getString("OriginalRetCode");

            if (ChanpayApiRequest.isTransferSucc(code)) {
                paySuccess(payResultMessage.getPayNo());
                return;
            }

            if (ChanpayApiRequest.isTransferFail(code)) {
                payFail(payNo, StringUtils.isNotBlank(result.getString("OriginalErrorMessage")) ? result.getString("OriginalErrorMessage") :
                        (StringUtils.isNotBlank(result.getString("AppRetMsg")) ? result.getString("AppRetMsg") : "打款失败"));
                return;
            }

            payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
            if (payResultMessage.getTimes() < ConstantUtils.FIVE) {
                rabbitTemplate.convertAndSend(RabbitConst.chanpay_queue_order_pay_query_wait, payResultMessage);
            } else {
                log.info("畅捷查询订单={},result={}", JSON.toJSONString(payResultMessage), result.toJSONString());
                rabbitTemplate.convertAndSend(RabbitConst.chanpay_queue_order_pay_query_wait_long, payResultMessage);
            }

        } catch (Exception e) {
            log.error("畅捷代付结果查询异常, message: " + JSON.toJSONString(payResultMessage) + ", 异常信息: " + e.getMessage(), e);
            if (payResultMessage.getTimes() <= ConstantUtils.FIVE) {
                payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.chanpay_queue_order_pay_query, payResultMessage);
                return;
            }
            if (payResultMessage.getTimes() <= 10) {
                payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.chanpay_queue_order_pay_query_wait, payResultMessage);
                return;
            }
            if (payResultMessage.getTimes() <= 15) {
                payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.chanpay_queue_order_pay_query_wait_long, payResultMessage);
//                return;
            }
//            payFail(payNo, e.getMessage());
        }
    }

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
            smsMessage.setType(SmsTemplate.T002.getKey());
            smsMessage.setPhone(user.getUserPhone());
            smsMessage.setParams("你于" + new DateTime().toString("MM月dd日HH:mm:ss") + "借款" + order.getActualMoney() + "已到账，请注意查收");
            rabbitTemplate.convertAndSend(RabbitConst.queue_sms, smsMessage);
            if (order.getSource() == ConstantUtils.ZERO || order.getSource() == null) {
                callBackJuHeService.callBack(userService.selectByPrimaryKey(order.getUid()), order.getOrderNo(), JuHeCallBackEnum.PAYED);
            } else if (order.getSource() == ConstantUtils.ONE) {
                Order orderCallBack = orderService.selectByPrimaryKey(orderPay.getOrderId());
                callBackRongZeService.pushOrderStatus(orderCallBack);
                callBackRongZeService.pushRepayPlan(orderCallBack);
            }
            else if (order.getSource() == ConstantUtils.TWO) {
                Order orderCallBack = orderService.selectByPrimaryKey(orderPay.getOrderId());
                callBackBengBengService.pushOrderStatus(orderCallBack);
                callBackBengBengService.pushRepayPlan(orderCallBack);
            }
        } else {
            log.info("放款订单状态非受理中，payNo={}, orderPayStatus={}", payNo, orderPay.getPayStatus());
        }
    }

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
            if (order.getSource() == ConstantUtils.ZERO || order.getSource() == null)
                callBackJuHeService.callBack(userService.selectByPrimaryKey(order.getUid()), order.getOrderNo(), JuHeCallBackEnum.PAY_FAILED);
            else if (order.getSource() == ConstantUtils.ONE) {
                Order orderCallBack = orderService.selectByPrimaryKey(orderPay.getOrderId());
                callBackRongZeService.pushOrderStatus(orderCallBack);
            } else if (order.getSource() == ConstantUtils.TWO) {
                Order orderCallBack = orderService.selectByPrimaryKey(orderPay.getOrderId());
                callBackBengBengService.pushOrderStatus(orderCallBack);
            }
        } else {
            log.info("畅捷查询代付结果:放款流水状态异常，payNo={},msg={}", payNo, msg);
        }
    }


    @Bean("chanpay_order_pay_query")
    public SimpleRabbitListenerContainerFactory pointTaskContainerFactoryLoan(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPrefetchCount(ConstantUtils.ONE);
        factory.setConcurrentConsumers(ConstantUtils.FIVE);
        return factory;
    }
}

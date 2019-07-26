package com.mod.loan.itf.yeepay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mod.loan.common.enums.JuHeCallBackEnum;
import com.mod.loan.common.enums.SmsTemplate;
import com.mod.loan.common.message.OrderPayQueryMessage;
import com.mod.loan.common.message.QueueSmsMessage;
import com.mod.loan.config.rabbitmq.RabbitConst;
import com.mod.loan.model.Order;
import com.mod.loan.model.OrderPay;
import com.mod.loan.model.User;
import com.mod.loan.pay.yeepay.YeePayApiRequest;
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
public class YeePayQueryConsumer {


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

    @RabbitListener(queues = "yeepay_queue_order_pay_query", containerFactory = "yeepay_order_pay_query")
    @RabbitHandler
    public void order_pay_query(Message mess) {
        OrderPayQueryMessage payResultMessage = JSONObject.parseObject(mess.getBody(), OrderPayQueryMessage.class);
        try {
            TimeUnit.SECONDS.sleep(10L);

            String payNo = payResultMessage.getPayNo();

            JSONObject result = YeePayApiRequest.transferSendQuery(payResultMessage.getBatchNo(), payNo);
            String list = result.getString("list");
            if (StringUtils.isBlank(list)) {
                log.info("易宝未查询到该放款订单, message: " + JSON.toJSONString(payResultMessage));
                return;
            }
            JSONArray arr = JSON.parseArray(list);
            if (arr.size() == 0) {
                log.info("易宝未查询到该放款订单, message: " + JSON.toJSONString(payResultMessage));
                return;
            }

            JSONObject json = arr.getJSONObject(0);
            String bankStatus = json.getString("bankTrxStatusCode"); //银行状态码
            String transferStatus = json.getString("transferStatusCode"); //打款状态码

            if ("0026".equalsIgnoreCase(transferStatus) && "S".equals(bankStatus)) {
                //出款成功并到账
                paySuccess(payResultMessage.getPayNo());
                return;
            }

            String failMsg = "";
            if ("U".equals(bankStatus)) {
                failMsg = "未知，易宝与银行对账失败，但此出款既可能成功也可能失败";
            } else if ("F".equals(bankStatus)) {
                failMsg = "银行出款失败";
            } else if ("0028".equals(transferStatus)) {
                failMsg = "易宝已经退回此请求，出款金额退回商户账户，此笔出款未发至银行";
            } else if ("0029".equals(transferStatus)) {
                failMsg = "易宝已经接收此请求，但此请求有待商户登录易宝商户后台复核出款";
            } else if ("0027".equals(transferStatus)) {
                failMsg = "易宝已经将款退回商户账户";
            } else if ("0030".equals(transferStatus)) {
                if (payResultMessage.getTimes() <= 10) {
                    payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
                    rabbitTemplate.convertAndSend(RabbitConst.yeepay_queue_order_pay_query_wait, payResultMessage);
                    return;
                }
                if (payResultMessage.getTimes() <= 15) {
                    payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
                    rabbitTemplate.convertAndSend(RabbitConst.yeepay_queue_order_pay_query_wait_long, payResultMessage);
                    return;
                }
            }

            if (StringUtils.isNotBlank(failMsg)) {
                payFail(payNo, failMsg);
                return;
            }

            payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
            if (payResultMessage.getTimes() < ConstantUtils.FIVE) {
                rabbitTemplate.convertAndSend(RabbitConst.yeepay_queue_order_pay_query_wait, payResultMessage);
            } else {
                log.info("易宝查询订单={},result={}", JSON.toJSONString(payResultMessage), result.toJSONString());
                rabbitTemplate.convertAndSend(RabbitConst.yeepay_queue_order_pay_query_wait_long, payResultMessage);
            }

        } catch (Exception e) {
            log.error("易宝代付结果查询异常, message: " + JSON.toJSONString(payResultMessage) + ", 异常信息: " + e.getMessage(), e);
            if (payResultMessage.getTimes() <= ConstantUtils.FIVE) {
                payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.yeepay_queue_order_pay_query, payResultMessage);
                return;
            }
            if (payResultMessage.getTimes() <= 10) {
                payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.yeepay_queue_order_pay_query_wait, payResultMessage);
                return;
            }
            if (payResultMessage.getTimes() <= 15) {
                payResultMessage.setTimes(payResultMessage.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.yeepay_queue_order_pay_query_wait_long, payResultMessage);
                return;
            }
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
            } else if (order.getSource() == ConstantUtils.TWO) {
                Order orderCallBack = orderService.selectByPrimaryKey(orderPay.getOrderId());
                callBackBengBengService.pushOrderStatus(orderCallBack);
                callBackBengBengService.pushRepayPlan(orderCallBack);
            }
            log.info("易宝查询代付结果:放款成功，payNo={}", payNo);
        } else {
            log.info("易宝查询代付结果:放款流水状态异常，payNo={}", payNo);
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
            }
            else if (order.getSource() == ConstantUtils.TWO) {
                Order orderCallBack = orderService.selectByPrimaryKey(orderPay.getOrderId());
                callBackBengBengService.pushOrderStatus(orderCallBack);
            }
        } else {
            log.info("易宝查询代付结果:放款流水状态异常，payNo={},msg={}", payNo, msg);
        }
    }


    @Bean("yeepay_order_pay_query")
    public SimpleRabbitListenerContainerFactory pointTaskContainerFactoryLoan(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPrefetchCount(ConstantUtils.ONE);
        factory.setConcurrentConsumers(ConstantUtils.FIVE);
        return factory;
    }
}

package com.mod.loan.itf.yeepay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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
import com.mod.loan.util.yeepay.YeePayApiRequest;
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
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class YeepayRepayQueryConsumer {

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

    @RabbitListener(queues = "yeepay_queue_repay_order_query", containerFactory = "yeepay_repay_order_query")
    @RabbitHandler
    public void repayOrderQuery(Message mess) {
        OrderRepayQueryMessage message = JSONObject.parseObject(mess.getBody(), OrderRepayQueryMessage.class);
        try {
            TimeUnit.SECONDS.sleep(5L);

            String repayOrderNo = message.getRepayNo();
            OrderRepay orderRepay = orderRepayService.selectByPrimaryKey(repayOrderNo);
            Order order = orderService.selectByPrimaryKey(orderRepay.getOrderId());
            User user = userService.selectByPrimaryKey(order.getUid());

            JSONObject result = YeePayApiRequest.queryPayResult(repayOrderNo);
            String status = result.getString("status");
            if ("PAY_SUCCESS".equalsIgnoreCase(status)) {
                log.info("易宝还款成功，订单流水为：{}, response={}", repayOrderNo, result.toJSONString());
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
                        callBackJuHeService.callBack(user, repayOrderNo, JuHeCallBackEnum.REPAYED);
                    } else {
                        //自动扣款时
                        callBackJuHeService.withholdCallBack(user, order.getOrderNo(), repayOrderNo, order.getShouldRepay(), JuHeCallBackEnum.WITHHOLD);
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
            } else if ("PAY_FAIL".equalsIgnoreCase(status)) {
                log.info("易宝还款失败，订单流水为：{}, response={}", repayOrderNo, JSON.toJSONString(result));
                orderRepay.setRepayStatus(ConstantUtils.FOUR);

                String errormsg = result.getString("errormsg");
                if (StringUtils.isNotBlank(errormsg) && errormsg.length() > 30) {
                    errormsg = errormsg.substring(0, 30);
                }
                orderRepay.setRemark(errormsg);
                orderRepayService.updateByPrimaryKeySelective(orderRepay);
                if (order.getSource() == ConstantUtils.ZERO || order.getSource() == null) {
                    if (message.getRepayType() == ConstantUtils.ONE) {
                        //用户主动还款时才回调失败
                        callBackJuHeService.callBack(user, message.getRepayNo(), JuHeCallBackEnum.REPAY_FAILED, errormsg);
                    }
                } else {
                    callBackRongZeService.pushRepayStatus(order, ConstantUtils.TWO, message.getRepayType(), errormsg);
                }
            } else {
                log.info("易宝还款异常，订单流水为：{}, response={}", message.getRepayNo(), JSON.toJSONString(result));
                message.setTimes(message.getTimes() + ConstantUtils.ONE);
                if (message.getTimes() < ConstantUtils.FIVE) {
                    rabbitTemplate.convertAndSend(RabbitConst.yeepay_queue_repay_order_query_wait, message);
                } else {
                    log.info("易宝还款查询订单={},result={}", JSON.toJSONString(message), JSON.toJSONString(result));
                    rabbitTemplate.convertAndSend(RabbitConst.yeepay_queue_repay_order_query_wait_long, message);
                }
            }

        } catch (Exception e) {
            log.error("易宝协议支付结果查询失败, message:" + JSON.toJSONString(message) + ", 异常信息: " + e.getMessage(), e);

            if (message.getTimes() <= ConstantUtils.FIVE) {
                message.setTimes(message.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.yeepay_queue_repay_order_query, message);
                return;
            }
            if (message.getTimes() <= 10) {
                message.setTimes(message.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.yeepay_queue_repay_order_query_wait, message);
                return;
            }
            if (message.getTimes() <= 15) {
                message.setTimes(message.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.yeepay_queue_repay_order_query_wait_long, message);
            }
        }
    }

    @Bean("yeepay_repay_order_query")
    public SimpleRabbitListenerContainerFactory pointTaskContainerFactoryLoan(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPrefetchCount(ConstantUtils.ONE);
        factory.setConcurrentConsumers(ConstantUtils.FIVE);
        return factory;
    }
}

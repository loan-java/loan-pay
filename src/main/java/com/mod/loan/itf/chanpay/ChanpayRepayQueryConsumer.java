package com.mod.loan.itf.chanpay;

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
import com.mod.loan.pay.chanpay.ChanpayApiRequest;
import com.mod.loan.service.*;
import com.mod.loan.util.ConstantUtils;
import lombok.extern.slf4j.Slf4j;
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
public class ChanpayRepayQueryConsumer {

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

    @Resource
    private CallBackBengBengService callBackBengBengService;

    @Autowired
    private UserService userService;
    @Resource
    private ChanpayApiRequest chanpayApiRequest;

    @RabbitListener(queues = "chanpay_queue_repay_order_query", containerFactory = "chanpay_repay_order_query")
    @RabbitHandler
    public void repayOrderQuery(Message mess) {
        OrderRepayQueryMessage message = JSONObject.parseObject(mess.getBody(), OrderRepayQueryMessage.class);
        try {
            TimeUnit.SECONDS.sleep(5L);

            String repayOrderNo = message.getRepayNo();
            OrderRepay orderRepay = orderRepayService.selectByPrimaryKey(repayOrderNo);
            Order order = orderService.selectByPrimaryKey(orderRepay.getOrderId());
            User user = userService.selectByPrimaryKey(order.getUid());

            JSONObject response = chanpayApiRequest.queryTrade(repayOrderNo);
            String status = response.getString("status");

            if (ChanpayApiRequest.isTradeSucc(status)) {
                log.info("快捷还款成功，订单流水为：{}, response={}", repayOrderNo, response.toJSONString());
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
                } else if (order.getSource() == ConstantUtils.TWO) {
                    callBackBengBengService.pushRepayStatus(order, ConstantUtils.ONE, message.getRepayType(), null);
                    callBackBengBengService.pushOrderStatus(order);
                }
                QueueSmsMessage smsMessage = new QueueSmsMessage();
                smsMessage.setClientAlias(order.getMerchant());
                smsMessage.setType(SmsTemplate.T002.getKey());
                smsMessage.setPhone(user.getUserPhone());
                smsMessage.setParams("你已成功还款" + order.getShouldRepay() + "元，感谢您的支持");
                rabbitTemplate.convertAndSend(RabbitConst.queue_sms, smsMessage);
            } else if (ChanpayApiRequest.isTradePayFail(status) || ChanpayApiRequest.isTradeClose(status)) {
                log.info("快捷还款失败，订单流水为：{}, response={}", repayOrderNo, response.toJSONString());
                orderRepay.setRepayStatus(ConstantUtils.FOUR);

                orderRepay.setRemark(ChanpayApiRequest.isTradePayFail(status) ? "支付失败" : "交易关闭");
                orderRepayService.updateByPrimaryKeySelective(orderRepay);
                if (order.getSource() == ConstantUtils.ZERO) {
                    if (message.getRepayType() == ConstantUtils.ONE) {
                        //用户主动还款时才回调失败
                        callBackJuHeService.callBack(user, message.getRepayNo(), JuHeCallBackEnum.REPAY_FAILED, orderRepay.getRemark());
                    }
                } else if (order.getSource() == ConstantUtils.ONE) {
                    callBackRongZeService.pushRepayStatus(order, ConstantUtils.TWO, message.getRepayType(), orderRepay.getRemark());
                } else if (order.getSource() == ConstantUtils.TWO) {
                    callBackBengBengService.pushRepayStatus(order, ConstantUtils.TWO, message.getRepayType(), orderRepay.getRemark());
                }
            } else {
                log.info("快捷还款查询结果异常，订单流水为：{}, response={}", message.getRepayNo(), response.toJSONString());
                message.setTimes(message.getTimes() + ConstantUtils.ONE);
                if (message.getTimes() < ConstantUtils.FIVE) {
                    rabbitTemplate.convertAndSend(RabbitConst.chanpay_queue_repay_order_query_wait, message);
                } else {
                    rabbitTemplate.convertAndSend(RabbitConst.chanpay_queue_repay_order_query_wait_long, message);
                }
            }

        } catch (Exception e) {
            log.error("快捷协议支付结果查询失败, message:" + JSON.toJSONString(message) + ", 异常信息: " + e.getMessage(), e);

            if (message.getTimes() <= ConstantUtils.FIVE) {
                message.setTimes(message.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.chanpay_queue_repay_order_query, message);
                return;
            }
            if (message.getTimes() <= 10) {
                message.setTimes(message.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.chanpay_queue_repay_order_query_wait, message);
                return;
            }
            if (message.getTimes() <= 15) {
                message.setTimes(message.getTimes() + ConstantUtils.ONE);
                rabbitTemplate.convertAndSend(RabbitConst.chanpay_queue_repay_order_query_wait_long, message);
                return;
            }
        }
    }

    @Bean("chanpay_repay_order_query")
    public SimpleRabbitListenerContainerFactory pointTaskContainerFactoryLoan(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPrefetchCount(ConstantUtils.ONE);
        factory.setConcurrentConsumers(ConstantUtils.FIVE);
        return factory;
    }
}

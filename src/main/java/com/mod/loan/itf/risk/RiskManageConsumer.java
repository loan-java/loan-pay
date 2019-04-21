package com.mod.loan.itf.risk;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mod.loan.common.message.RiskAuditMessage;
import com.mod.loan.config.redis.RedisConst;
import com.mod.loan.config.redis.RedisMapper;
import com.mod.loan.model.Order;
import com.mod.loan.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * loan-pay 2019/4/20 huijin.shuailijie Init
 * 风控接入点(暂时)
 */
@Component
public class RiskManageConsumer {

    private static final Logger log = LoggerFactory.getLogger(RiskManageConsumer.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private RedisMapper redisMapper;


    @RabbitListener(queues = "queue_risk_order_notify", containerFactory = "risk_order_notify")
    @RabbitHandler
    public void risk_order_notify(Message mess) {
        RiskAuditMessage riskAuditMessage = JSONObject.parseObject(mess.getBody(), RiskAuditMessage.class);
        if (!redisMapper.lock(RedisConst.ORDER_LOCK + riskAuditMessage.getOrderId(), 10)) {
            log.error("风控放款消息重复，message={}", JSON.toJSONString(riskAuditMessage));
            return;
        }
        Order order = new Order();
        order.setId(riskAuditMessage.getOrderId());
        order.setStatus(12);
        orderService.updateOrderByRisk(order);
    }

    @Bean("risk_order_notify")
    public SimpleRabbitListenerContainerFactory pointTaskContainerFactoryLoan(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(5);
        return factory;
    }
}

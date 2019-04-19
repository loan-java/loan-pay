package com.mod.loan.itf.helipay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mod.loan.common.enums.SmsTemplate;
import com.mod.loan.common.message.OrderPayQueryMessage;
import com.mod.loan.common.message.QueueSmsMessage;
import com.mod.loan.config.rabbitmq.RabbitConst;
import com.mod.loan.itf.helipay.util.HeliPayUtils;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashMap;

@Component
public class PayQueryConsumer {

	private static final Logger logger = LoggerFactory.getLogger(PayQueryConsumer.class);

	@Autowired
	UserService userService;
	@Autowired
	OrderService orderService;
	@Autowired
	OrderPayService orderPayService;
	@Autowired
	MerchantService merchantService;
	@Autowired
	RabbitTemplate rabbitTemplate;
	@Value("${helipay.transfer.url:}")
	String helipay_transfer_url;

	/**
	 * 放款流程分两步 1：请求合利宝受理 ，2：请求合利宝查询结果， 当前为第二步
	 * 1-队列消息来自等待查询队列queue_order_pay_query_wait，或者需要重新查询的流水 2-请求合利宝，处理返回结果
	 * ===成功放款流水状态改为放款成功3，订单状态改为已放款31，并发送通知短信
	 * ===失败放款流水状态改为放款失败4，订单状态改为放款失败23==可以对订单重新放款 queue_order_pay_query消息来自死信队列
	 */
	@RabbitListener(queues = "queue_order_pay_query", containerFactory = "order_pay_query")
	@RabbitHandler
	public void order_pay_query(Message mess) {
		OrderPayQueryMessage payResultMessage = JSONObject.parseObject(mess.getBody(), OrderPayQueryMessage.class);
		try {
			String payNo = payResultMessage.getPayNo();
			Merchant merchant = merchantService.findMerchantByAlias(payResultMessage.getMerchantAlias());
			LinkedHashMap<String, String> sPara = new LinkedHashMap<String, String>();
			sPara.put("P1_bizType", "TransferQuery");
			sPara.put("P2_orderId", payNo);
			sPara.put("P3_customerNumber", merchant.getHlb_id());

			JSONObject result = HeliPayUtils.requestRSA(helipay_transfer_url, sPara, merchant.getHlb_rsa_private_key());
			String rt2_retCode = result.getString("rt2_retCode");// 请求状态
			String rt7_orderStatus = result.getString("rt7_orderStatus");// 处理状态
			if (!"0000".equals(rt2_retCode)) {
				logger.info("查询代付结果:放款失败--流水号为：{}", payNo);
				payFail(payNo, result);
				return;
			}
			// RECEIVE 已接收 INIT初始化 DOING处理中 SUCCESS成功 FAIL失败 REFUND退款
			switch (rt7_orderStatus) {
			case "SUCCESS":
				paySuccess(payNo, result);
				break;
			case "FAIL":
				logger.error("订单放款失败={},合利宝返回结果={}", JSON.toJSONString(payResultMessage), result);
				payFail(payNo, result);
				break;
			case "REFUND":
				payFail(payNo, result);
				break;
			default:
				// RECEIVE、INIT、DOING重新进入死信队列等待
				// 前五次查询失败进入queue_order_pay_query_wait9秒后处理，之后进入queue_order_pay_query_wait_long等待600秒
				payResultMessage.setTimes(payResultMessage.getTimes() + 1);
				if (payResultMessage.getTimes() < 6) {
					rabbitTemplate.convertAndSend(RabbitConst.queue_order_pay_query_wait, payResultMessage);
				} else {
					logger.info("查询订单={},合利宝返回结果={}", JSON.toJSONString(payResultMessage), result);
					rabbitTemplate.convertAndSend(RabbitConst.queue_order_pay_query_wait_long, payResultMessage);
				}
				break;
			}
		} catch (Exception e) {
			logger.error("查询代付结果异常message={}", JSON.toJSONString(payResultMessage));
			logger.error("查询代付结果异常", e);
			rabbitTemplate.convertAndSend(RabbitConst.queue_order_pay_query_wait, payResultMessage);
		}

	}

	void paySuccess(String payNo, JSONObject result) {
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
			logger.info("查询代付结果:状态异常={}", JSON.toJSONString(result));
		}
	}

	void payFail(String payNo, JSONObject result) {
		OrderPay orderPay = orderPayService.selectByPrimaryKey(payNo);
		if (orderPay.getPayStatus() == 1) {// 只处理受理中的状态
			Order order1 = new Order();
			order1.setId(orderPay.getOrderId());
			order1.setStatus(23);

			OrderPay orderPay1 = new OrderPay();
			orderPay1.setPayNo(payNo);
			orderPay1.setPayStatus(4);
			orderPay1.setRemark(result.getString("rt8_reason"));
			orderPay1.setUpdateTime(new Date());
			orderService.updatePayCallbackInfo(order1, orderPay1);
		} else {
			logger.info("查询代付结果:状态异常={}", JSON.toJSONString(result));
		}
	}

	@Bean("order_pay_query")
	public SimpleRabbitListenerContainerFactory pointTaskContainerFactoryLoan(ConnectionFactory connectionFactory) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setPrefetchCount(1);
		factory.setConcurrentConsumers(5);
		return factory;
	}
}

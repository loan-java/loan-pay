//package com.mod.loan.itf.helipay;
//
//import java.math.BigDecimal;
//import java.util.Date;
//import java.util.LinkedHashMap;
//
//import org.joda.time.DateTime;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.amqp.core.Message;
//import org.springframework.amqp.rabbit.annotation.RabbitHandler;
//import org.springframework.amqp.rabbit.annotation.RabbitListener;
//import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
//import org.springframework.amqp.rabbit.connection.ConnectionFactory;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.stereotype.Component;
//
//import com.alibaba.fastjson.JSON;
//import com.alibaba.fastjson.JSONObject;
//import com.mod.loan.common.message.OrderPayMessage;
//import com.mod.loan.common.message.OrderPayQueryMessage;
//import com.mod.loan.config.Constant;
//import com.mod.loan.config.rabbitmq.RabbitConst;
//import com.mod.loan.config.redis.RedisConst;
//import com.mod.loan.config.redis.RedisMapper;
//import com.mod.loan.itf.helipay.util.HeliPayUtils;
//import com.mod.loan.model.Merchant;
//import com.mod.loan.model.Order;
//import com.mod.loan.model.OrderPay;
//import com.mod.loan.model.User;
//import com.mod.loan.model.UserBank;
//import com.mod.loan.service.MerchantService;
//import com.mod.loan.service.OrderPayService;
//import com.mod.loan.service.OrderService;
//import com.mod.loan.service.UserBankService;
//import com.mod.loan.service.UserService;
//import com.mod.loan.util.TimeUtils;
//
//@Component
//public class PayConsumer {
//	private static final Logger log = LoggerFactory.getLogger(PayConsumer.class);
//	@Value("${helipay.transfer.url:}")
//	String helipay_transfer_url;
//	@Autowired
//	MerchantService merchantService;
//	@Autowired
//	UserService userService;
//	@Autowired
//	UserBankService userBankService;
//	@Autowired
//	OrderService orderService;
//	@Autowired
//	OrderPayService orderPayService;
//	@Autowired
//	RabbitTemplate rabbitTemplate;
//	@Autowired
//	private RedisMapper redisMapper;
//
//	/**
//	 * 放款流程分两步 1：请求合利宝受理 ，2：请求合利宝查询结果， 当前为第一步 1接收放款中订单消息 2.生成放款单号 3请求合利宝受理，处理返回结果
//	 * ===成功-改变放款单号状态为受理中1，订单状态仍为放款中22，并向订单确认等待队列（queue_order_pay_query_wait）发送消息
//	 * queue_order_pay_query_wait消息发送失败，定时任务扫描30分钟之前为受理中的放款单号，重新加入该等待队列或者queue_order_pay_query都可
//	 * ===失败-改变放款单号状态为受理失败2，订单状态改为放款失败23==可以对订单重新放款
//	 */
//	@RabbitListener(queues = "queue_order_pay", containerFactory = "order_pay")
//	@RabbitHandler
//	public void order_pay(Message mess) {
//		OrderPayMessage payMessage = JSONObject.parseObject(mess.getBody(), OrderPayMessage.class);
//		if (!redisMapper.lock(RedisConst.ORDER_LOCK + payMessage.getOrderId(), 10)) {
//			log.error("放款消息重复，message={}", JSON.toJSONString(payMessage));
//			return;
//		}
//		try {
//			Order order = orderService.selectByPrimaryKey(payMessage.getOrderId());
//			if (order.getStatus() != 22) { // 放款中的订单才能放款
//				log.info("订单放款，无效的订单状态 message={}", JSON.toJSONString(payMessage));
//				return;
//			}
//			Merchant merchant = merchantService.findMerchantByAlias(order.getMerchant());
//			UserBank userBank = userBankService.selectUserCurrentBankCard(order.getUid());
//			User user = userService.selectByPrimaryKey(order.getUid());
//
//			String serials_no = String.format("%s%s%s", "p", new DateTime().toString(TimeUtils.dateformat5),
//					user.getId());
//			String amount = order.getActualMoney().toString();
//			if ("dev".equals(Constant.ENVIROMENT)) {
//				amount = "0.1";
//			}
//			LinkedHashMap<String, String> sPara = new LinkedHashMap<String, String>();
//			sPara.put("P1_bizType", "Transfer");// 请求类型
//			sPara.put("P2_orderId", serials_no);// 请求编号
//			sPara.put("P3_customerNumber", merchant.getHlb_id());// 商户编号
//			sPara.put("P4_amount", amount);// 订单金额
//			sPara.put("P5_bankCode", userBank.getCardCodeHelipay());// 银行编码
//			sPara.put("P6_bankAccountNo", userBank.getCardNo());// 银行账户号
//			sPara.put("P7_bankAccountName", user.getUserName());// 银行账户名
//			sPara.put("P8_biz", "B2C");// 业务，b2b,b2c等
//			sPara.put("P9_bankUnionCode", "");// 联行号
//			sPara.put("P10_feeType", "PAYER");// 手续费收取方（RECEIVER）
//			sPara.put("P11_urgency", "true");// true加急
//			sPara.put("P12_summary", "");// 打款备注
//
//			JSONObject result = HeliPayUtils.requestRSA(helipay_transfer_url, sPara, merchant.getHlb_rsa_private_key());
//			OrderPay orderPay = new OrderPay();
//			orderPay.setPayNo(serials_no);
//			orderPay.setUid(order.getUid());
//			orderPay.setOrderId(order.getId());
//			orderPay.setPayMoney(new BigDecimal(amount));
//			orderPay.setBank(userBank.getCardName());
//			orderPay.setBankNo(userBank.getCardNo());
//			orderPay.setCreateTime(new Date());
//
//			if ("0000".equals(result.getString("rt2_retCode"))) {
//				orderPay.setUpdateTime(new Date());
//				orderPay.setPayStatus(1);// 受理成功,插入打款流水，不改变订单状态
//				orderService.updatePayInfo(null, orderPay);
//				// 受理成功，将消息存入死信队列，5秒后去查询是否放款成功
//				rabbitTemplate.convertAndSend(RabbitConst.queue_order_pay_query_wait, new OrderPayQueryMessage(serials_no, merchant.getMerchantAlias()));
//			} else {
//				log.error("放款受理失败,message={}, result={}", JSON.toJSONString(payMessage), JSON.toJSONString(result));
//				orderPay.setRemark(result.getString("rt3_retMsg"));
//				orderPay.setUpdateTime(new Date());
//				orderPay.setPayStatus(2);
//				Order record = new Order();
//				record.setId(order.getId());
//				record.setStatus(23);
//				orderService.updatePayInfo(record, orderPay);
//				redisMapper.unlock(RedisConst.ORDER_LOCK + payMessage.getOrderId());
//			}
//		} catch (Exception e) {
//			log.error("订单放款异常， message={}", JSON.toJSONString(payMessage));
//			log.error("订单放款异常", e);
//		}
//	}
//
//	@Bean("order_pay")
//	public SimpleRabbitListenerContainerFactory pointTaskContainerFactoryLoan(ConnectionFactory connectionFactory) {
//		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
//		factory.setConnectionFactory(connectionFactory);
//		factory.setPrefetchCount(1);
//		factory.setConcurrentConsumers(5);
//		return factory;
//	}
//}

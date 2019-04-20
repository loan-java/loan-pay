//package com.mod.loan.itf.fuyou;
//
//import java.math.BigDecimal;
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.List;
//
//import org.apache.http.HttpEntity;
//import org.apache.http.NameValuePair;
//import org.apache.http.client.entity.UrlEncodedFormEntity;
//import org.apache.http.client.methods.CloseableHttpResponse;
//import org.apache.http.client.methods.HttpPost;
//import org.apache.http.impl.client.CloseableHttpClient;
//import org.apache.http.impl.client.HttpClientBuilder;
//import org.apache.http.message.BasicNameValuePair;
//import org.apache.http.util.EntityUtils;
//import org.dom4j.Document;
//import org.dom4j.DocumentHelper;
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
//import com.mod.loan.model.Merchant;
//import com.mod.loan.model.Order;
//import com.mod.loan.model.OrderPay;
//import com.mod.loan.model.User;
//import com.mod.loan.model.UserBank;
//import com.mod.loan.service.MerchantService;
//import com.mod.loan.service.OrderService;
//import com.mod.loan.service.UserBankService;
//import com.mod.loan.service.UserService;
//import com.mod.loan.util.MD5;
//import com.mod.loan.util.TimeUtils;
//
//@Component
//public class FuYouPayConsumer {
//
//	private static final Logger log = LoggerFactory.getLogger(FuYouPayConsumer.class);
//
//	private static final String fuiou_reqtype = "payforreq";
//
//	@Autowired
//	private MerchantService merchantService;
//	@Autowired
//	private UserService userService;
//	@Autowired
//	private UserBankService userBankService;
//	@Autowired
//	private OrderService orderService;
//	@Autowired
//	private RabbitTemplate rabbitTemplate;
//	@Autowired
//	private RedisMapper redisMapper;
//	@Value("${fuiou.requrl:}")
//	private String fuiou_requrl;
//
//	@RabbitListener(queues = "fuyou_queue_order_pay", containerFactory = "fuyou_order_pay")
//	@RabbitHandler
//	public void order_pay(Message mess) {
//		OrderPayMessage payMessage = JSONObject.parseObject(mess.getBody(), OrderPayMessage.class);
//		if (!redisMapper.lock(RedisConst.ORDER_LOCK + payMessage.getOrderId(), 10)) {
//			log.error("放款消息重复，message={}", JSON.toJSONString(payMessage));
//			return;
//		}
//		String jsonStr = null;
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
//			String serials_no = String.format("%s%s%s", "p", new DateTime().toString(TimeUtils.dateformat5), user.getId());
//			BigDecimal amount = order.getActualMoney();
//			if ("dev".equals(Constant.ENVIROMENT)) {
//				amount = new BigDecimal("2.01"); // 测试放款一毛钱
//			}
//
//			String xml = "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>"
//					+ "<payforreq>"
//					+ "<ver>1.00</ver>"
//					+ "<merdt>"
//					+ new DateTime().toString(TimeUtils.dateformat4)
//					+ "</merdt>"
//					+ "<orderno>"
//					+ serials_no
//					+ "</orderno>"
//					+ "<bankno>0103</bankno>"
//					+ "<cityno>1000</cityno>"
//					+ "<accntno>"
//					+ userBank.getCardNo()
//					+ "</accntno>"
//					+ "<accntnm>"
//					+ user.getUserName()
//					+ "</accntnm>"
//					+ "<amt>"
//					+ amount.multiply(new BigDecimal("100")).longValue() // 富友金额单位为：分
//					+ "</amt>"
//					+ "</payforreq>";
//
//			String macSource = merchant.getFuyou_merid() + "|" + merchant.getFuyou_secureid() + "|" + fuiou_reqtype + "|" + xml;
//			String mac = MD5.toMD5(macSource, "UTF-8").toUpperCase();
//			List<NameValuePair> params = new ArrayList<NameValuePair>();
//			params.add(new BasicNameValuePair("merid", merchant.getFuyou_merid()));
//			params.add(new BasicNameValuePair("reqtype", fuiou_reqtype));
//			params.add(new BasicNameValuePair("xml", xml));
//			params.add(new BasicNameValuePair("mac", mac));
//			CloseableHttpClient httpclient = HttpClientBuilder.create().build();
//
//			HttpPost httppost = new HttpPost(fuiou_requrl);
//			httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
//			CloseableHttpResponse response = httpclient.execute(httppost);
//			HttpEntity entity = response.getEntity();
//			jsonStr = EntityUtils.toString(entity, "UTF-8");
//			httppost.releaseConnection();
//			Document document = DocumentHelper.parseText(jsonStr);
//			String result = document.selectSingleNode("/payforrsp/ret").getStringValue();
//			String msg = document.selectSingleNode("/payforrsp/memo").getStringValue();
//
//			OrderPay orderPay = new OrderPay();
//			orderPay.setPayNo(serials_no);
//			orderPay.setUid(order.getUid());
//			orderPay.setOrderId(order.getId());
//			orderPay.setPayType(2); // 类型：富友
//			orderPay.setPayMoney(amount);
//			orderPay.setBank(userBank.getCardName());
//			orderPay.setBankNo(userBank.getCardNo());
//			orderPay.setCreateTime(new Date());
//
//			if ("000000".equals(result)) {
//				orderPay.setUpdateTime(new Date());
//				orderPay.setPayStatus(1);// 受理成功,插入打款流水，不改变订单状态
//				orderService.updatePayInfo(null, orderPay);
//				// 受理成功，将消息存入死信队列
//				rabbitTemplate.convertAndSend(RabbitConst.fuyou_queue_order_pay_query_wait, new OrderPayQueryMessage(serials_no, merchant.getMerchantAlias()));
//			} else {
//				log.error("放款受理失败,message={}, result={}, msg={}", JSON.toJSONString(payMessage), result, msg);
//				orderPay.setRemark(msg);
//				orderPay.setUpdateTime(new Date());
//				orderPay.setPayStatus(2);
//				Order record = new Order();
//				record.setId(order.getId());
//				record.setStatus(23);
//				orderService.updatePayInfo(record, orderPay);
//				redisMapper.unlock(RedisConst.ORDER_LOCK + payMessage.getOrderId());
//			}
//		} catch (Exception e) {
//			log.error("订单放款异常， message={}，jsonStr={}", JSON.toJSONString(payMessage), jsonStr);
//			log.error("订单放款异常", e);
//		}
//	}
//
//	@Bean("fuyou_order_pay")
//	public SimpleRabbitListenerContainerFactory pointTaskContainerFactoryLoan(ConnectionFactory connectionFactory) {
//		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
//		factory.setConnectionFactory(connectionFactory);
//		factory.setPrefetchCount(1);
//		factory.setConcurrentConsumers(5);
//		return factory;
//	}
//}

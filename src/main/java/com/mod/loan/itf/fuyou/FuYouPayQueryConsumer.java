//package com.mod.loan.itf.fuyou;
//
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
//import com.mod.loan.common.enums.SmsTemplate;
//import com.mod.loan.common.message.OrderPayQueryMessage;
//import com.mod.loan.common.message.QueueSmsMessage;
//import com.mod.loan.config.rabbitmq.RabbitConst;
//import com.mod.loan.model.Merchant;
//import com.mod.loan.model.Order;
//import com.mod.loan.model.OrderPay;
//import com.mod.loan.model.User;
//import com.mod.loan.service.MerchantService;
//import com.mod.loan.service.OrderPayService;
//import com.mod.loan.service.OrderService;
//import com.mod.loan.service.UserService;
//import com.mod.loan.util.MD5;
//import com.mod.loan.util.TimeUtils;
//
//@Component
//public class FuYouPayQueryConsumer {
//
//	private static final Logger logger = LoggerFactory.getLogger(FuYouPayQueryConsumer.class);
//
//	@Autowired
//	private UserService userService;
//	@Autowired
//	private OrderService orderService;
//	@Autowired
//	private OrderPayService orderPayService;
//	@Autowired
//	private MerchantService merchantService;
//	@Autowired
//	private RabbitTemplate rabbitTemplate;
//	@Value("${fuiou.requrl.query:}")
//	private String fuiou_requrl_query;
//
//	@RabbitListener(queues = "fuyou_queue_order_pay_query", containerFactory = "fuyou_order_pay_query")
//	@RabbitHandler
//	public void order_pay_query(Message mess) {
//		OrderPayQueryMessage payResultMessage = JSONObject.parseObject(mess.getBody(), OrderPayQueryMessage.class);
//		try {
//			String payNo = payResultMessage.getPayNo();
//			Merchant merchant = merchantService.findMerchantByAlias(payResultMessage.getMerchantAlias());
//
//			String xml = "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>"
//					+ "<qrytransreq>"
//					+ "<ver>1.1</ver>"
//					+ "<busicd>AP01</busicd>"
//					+ "<orderno>"
//					+ payNo
//					+ "</orderno>"
//					+ "<startdt>"
//					+ new DateTime().minusDays(1).toString(TimeUtils.dateformat4)
//					+ "</startdt>"
//					+ "<enddt>"
//					+ new DateTime().toString(TimeUtils.dateformat4)
//					+ "</enddt>"
//					+ "</qrytransreq>";
//
//			String macSource = merchant.getFuyou_merid() + "|" + merchant.getFuyou_secureid() + "|" + xml;
//			String mac = MD5.toMD5(macSource, "UTF-8").toUpperCase();
//			List<NameValuePair> params = new ArrayList<NameValuePair>();
//			params.add(new BasicNameValuePair("merid", merchant.getFuyou_merid()));
//			params.add(new BasicNameValuePair("xml", xml));
//			params.add(new BasicNameValuePair("mac", mac));
//			CloseableHttpClient httpclient = HttpClientBuilder.create().build();
//
//			HttpPost httppost = new HttpPost(fuiou_requrl_query);
//			httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
//			CloseableHttpResponse response = httpclient.execute(httppost);
//			HttpEntity entity = response.getEntity();
//			String jsonStr = EntityUtils.toString(entity, "UTF-8");
//			httppost.releaseConnection();
//			Document document = DocumentHelper.parseText(jsonStr);
//			String result = document.selectSingleNode("/qrytransrsp/ret").getStringValue();
//			String msg = document.selectSingleNode("/qrytransrsp/memo").getStringValue();
//
//			if ("000000".equals(result)) {
//				String state = document.selectSingleNode("/qrytransrsp/trans/state").getStringValue();
//				String tpst = document.selectSingleNode("/qrytransrsp/trans/tpst").getStringValue();
//				String rspcd = document.selectSingleNode("/qrytransrsp/trans/rspcd").getStringValue();
//				String transStatusDesc = document.selectSingleNode("/qrytransrsp/trans/transStatusDesc").getStringValue();
//				String resultMsg = document.selectSingleNode("/qrytransrsp/trans/result").getStringValue();
//				if ("1".equals(state) && "0".equals(tpst) && "000000".equals(rspcd) && "success".equals(transStatusDesc)) {
//					paySuccess(payNo);// 交易成功
//				} else if ("1".equals(state) && "1".equals(tpst)) {
//					payFail(payNo, transStatusDesc); // 交易失败
//				} else { // 继续查询
//					payResultMessage.setTimes(payResultMessage.getTimes() + 1);
//					if (payResultMessage.getTimes() < 6) {
//						rabbitTemplate.convertAndSend(RabbitConst.fuyou_queue_order_pay_query_wait, payResultMessage);
//					} else {
//						logger.info("富友查询订单={},result={},msg={},resultMsg={}", JSON.toJSONString(payResultMessage), result, msg, resultMsg);
//						rabbitTemplate.convertAndSend(RabbitConst.fuyou_queue_order_pay_query_wait_long, payResultMessage);
//					}
//				}
//			} else {
//				logger.info("富友查询代付结果失败:流水号为：{}，result={}，msg={}", payNo, result, msg);
//				rabbitTemplate.convertAndSend(RabbitConst.fuyou_queue_order_pay_query_wait, payResultMessage);
//			}
//		} catch (Exception e) {
//			logger.error("富友查询代付结果异常，message={}", JSON.toJSONString(payResultMessage));
//			logger.error("富友查询代付结果异常", e);
//			rabbitTemplate.convertAndSend(RabbitConst.fuyou_queue_order_pay_query, payResultMessage);
//		}
//	}
//
//	private void paySuccess(String payNo) {
//		OrderPay orderPay = orderPayService.selectByPrimaryKey(payNo);
//		if (orderPay.getPayStatus() == 1) {// 只处理受理中的状态
//			Order order = orderService.selectByPrimaryKey(orderPay.getOrderId());
//			Order order1 = new Order();
//			order1.setId(order.getId());
//			order1.setArriveTime(new Date());
//			Date repayTime = new DateTime(order1.getArriveTime()).plusDays(order.getBorrowDay() - 1).toDate();
//			order1.setRepayTime(repayTime);
//			order1.setStatus(31);
//
//			OrderPay orderPay1 = new OrderPay();
//			orderPay1.setPayNo(payNo);
//			orderPay1.setPayStatus(3);
//			orderPay1.setUpdateTime(new Date());
//			orderService.updatePayCallbackInfo(order1, orderPay1);
//			// 给用户短信通知 放款成功
//			User user = userService.selectByPrimaryKey(order.getUid());
//			QueueSmsMessage smsMessage = new QueueSmsMessage();
//			smsMessage.setClientAlias(order.getMerchant());
//			smsMessage.setType(SmsTemplate.T2001.getKey());
//			smsMessage.setPhone(user.getUserPhone());
//			smsMessage.setParams(order.getActualMoney() + "|" + new DateTime(repayTime).toString("MM月dd日"));
//			rabbitTemplate.convertAndSend(RabbitConst.queue_sms, smsMessage);
//		} else {
//			logger.info("富友查询代付结果:放款流水状态异常，payNo={}", payNo);
//		}
//	}
//
//	private void payFail(String payNo, String msg) {
//		OrderPay orderPay = orderPayService.selectByPrimaryKey(payNo);
//		if (orderPay.getPayStatus() == 1) {// 只处理受理中的状态
//			Order order1 = new Order();
//			order1.setId(orderPay.getOrderId());
//			order1.setStatus(23);
//
//			OrderPay orderPay1 = new OrderPay();
//			orderPay1.setPayNo(payNo);
//			orderPay1.setPayStatus(4);
//			orderPay1.setRemark(msg);
//			orderPay1.setUpdateTime(new Date());
//			orderService.updatePayCallbackInfo(order1, orderPay1);
//		} else {
//			logger.info("富友查询代付结果:放款流水状态异常，payNo={},msg={}", payNo, msg);
//		}
//	}
//
//	@Bean("fuyou_order_pay_query")
//	public SimpleRabbitListenerContainerFactory pointTaskContainerFactoryLoan(ConnectionFactory connectionFactory) {
//		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
//		factory.setConnectionFactory(connectionFactory);
//		factory.setPrefetchCount(1);
//		factory.setConcurrentConsumers(5);
//		return factory;
//	}
//}

package com.mod.loan.config.rabbitmq;

public class RabbitConst {

    public final static String exchange_order = "exchange_order"; // 订单交换机
    public final static String queue_sms = "queue_sms"; // 短信队列
    public final static String exchange_voice = "exchange_voice"; // 语音交换机
    public final static String queue_voice = "queue_voice"; // 语音队列
    public final static String queue_voice_wait = "queue_voice_wait"; // 再次发送语音等待5分钟

    public final static String baofoo_queue_order_pay = "baofoo_queue_order_pay"; // 放款队列
    public final static String baofoo_queue_order_pay_query = "baofoo_queue_order_pay_query"; // 放款结果查询

    public final static String baofoo_queue_order_pay_query_wait = "baofoo_queue_order_pay_query_wait"; // 放款结果查询等待9s
    public final static String baofoo_queue_order_pay_query_wait_long = "baofoo_queue_order_pay_query_wait_long"; // 放款结果查询等待600s


    public final static String queue_risk_order_notify = "queue_risk_order_notify"; // 风控订单审核通知
    public final static String queue_risk_order_result = "queue_risk_order_result"; // 风控订单结果通知

    public final static String queue_moxie_mobile = "queue_moxie_mobile"; // 魔蝎运营商
    public final static String queue_moxie_zfb = "queue_moxie_zfb"; // 魔蝎支付宝

}

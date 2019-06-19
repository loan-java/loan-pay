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


    // 放款队列
    public final static String kuaiqian_queue_order_pay = "kuaiqian_queue_order_pay";
    // 放款结果查询
    public final static String kuaiqian_queue_order_pay_query = "kuaiqian_queue_order_pay_query";

    // 放款结果查询等待9s
    public final static String kuaiqian_queue_order_pay_query_wait = "kuaiqian_queue_order_pay_query_wait";
    // 放款结果查询等待600s
    public final static String kuaiqian_queue_order_pay_query_wait_long = "kuaiqian_queue_order_pay_query_wait_long";


    /**
     * 还款结果查询
     */
    public final static String baofoo_queue_repay_order_query = "baofoo_queue_repay_order_query";

    /**
     * 还款结果查询等待9s
     */
    public final static String baofoo_queue_repay_order_query_wait = "baofoo_queue_repay_order_query_wait";

    /**
     * 还款结果查询等待600s
     */
    public final static String baofoo_queue_repay_order_query_wait_long = "baofoo_queue_repay_order_query_wait_long";

    public final static String kuaiqian_queue_repay_order_query = "kuaiqian_queue_repay_order_query";
    public final static String kuaiqian_queue_repay_order_query_wait = "kuaiqian_queue_repay_order_query_wait";
    public final static String kuaiqian_queue_repay_order_query_wait_long = "kuaiqian_queue_repay_order_query_wait_long";

    //易宝
    public final static String yeepay_queue_repay_order_query = "yeepay_queue_repay_order_query";
    public final static String yeepay_queue_repay_order_query_wait = "yeepay_queue_repay_order_query_wait";
    public final static String yeepay_queue_repay_order_query_wait_long = "yeepay_queue_repay_order_query_wait_long";

    public final static String yeepay_queue_order_pay_query = "yeepay_queue_order_pay_query"; // 放款结果查询
    public final static String yeepay_queue_order_pay_query_wait = "yeepay_queue_order_pay_query_wait"; // 放款结果查询等待9s
    public final static String yeepay_queue_order_pay_query_wait_long = "yeepay_queue_order_pay_query_wait_long"; // 放款结果查询等待600s

    //畅捷
    public final static String chanpay_queue_repay_order_query = "chanpay_queue_repay_order_query";
    public final static String chanpay_queue_repay_order_query_wait = "chanpay_queue_repay_order_query_wait";
    public final static String chanpay_queue_repay_order_query_wait_long = "chanpay_queue_repay_order_query_wait_long";

    public final static String chanpay_queue_order_pay_query = "chanpay_queue_order_pay_query"; // 放款结果查询
    public final static String chanpay_queue_order_pay_query_wait = "chanpay_queue_order_pay_query_wait"; // 放款结果查询等待9s
    public final static String chanpay_queue_order_pay_query_wait_long = "chanpay_queue_order_pay_query_wait_long"; // 放款结果查询等待600s

}

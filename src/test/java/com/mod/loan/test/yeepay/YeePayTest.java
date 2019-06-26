package com.mod.loan.test.yeepay;

import com.alibaba.fastjson.JSON;
import com.mod.loan.common.message.OrderPayMessage;
import com.mod.loan.common.message.OrderRepayQueryMessage;
import com.mod.loan.itf.yeepay.YeePayConsumer;
import com.mod.loan.itf.yeepay.YeePayQueryConsumer;
import com.mod.loan.test.BaseSpringBootJunitTest;
import org.junit.Test;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;

public class YeePayTest extends BaseSpringBootJunitTest {

    @Autowired
    private YeePayConsumer yeePayConsumer;

    @Autowired
    private YeePayQueryConsumer yeePayQueryConsumer;

    @Test
    public void order_pay() {
        OrderPayMessage message = new OrderPayMessage();
        message.setOrderId(1305L);

        Message mess = new Message(JSON.toJSONBytes(message), null);
        yeePayConsumer.order_pay(mess);
    }

    @Test
    public void order_pay_query() {
        OrderRepayQueryMessage message = new OrderRepayQueryMessage();
        message.setMerchantAlias("huashidai");
        message.setRepayNo("");
        message.setTimes(1);
        message.setRepayType(1);

        Message mess = new Message(JSON.toJSONBytes(message), null);
        yeePayQueryConsumer.order_pay_query(mess);
    }
}

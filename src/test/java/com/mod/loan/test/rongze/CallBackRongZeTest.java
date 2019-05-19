package com.mod.loan.test.rongze;


import com.mod.loan.model.Order;
import com.mod.loan.service.CallBackRongZeService;
import com.mod.loan.service.OrderService;
import com.mod.loan.test.BaseSpringBootJunitTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 融泽回调单元测试
 *
 * @author yutian
 */
public class CallBackRongZeTest extends BaseSpringBootJunitTest {

    @Autowired
    private CallBackRongZeService callBackRongZeService;

    @Autowired
    private OrderService orderService;

    @Test
    public void pushRepayStatusTest() {
        Order order = orderService.selectByPrimaryKey(1L);

//        callBackRongZeService.pushRepayStatus();
    }
}

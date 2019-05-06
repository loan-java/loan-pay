package com.mod.loan.test.kuaiqian;

import com.mod.loan.itf.kuaiqian.KuaiQianBalanceQueryAPI;
import com.mod.loan.test.BaseSpringBootJunitTest;
import org.junit.Test;

import javax.annotation.Resource;

/**
 * @ author liujianjian
 * @ date 2019/5/6 17:04
 */
public class KuaiQianTest extends BaseSpringBootJunitTest {

    @Resource
    private KuaiQianBalanceQueryAPI kuaiQianBalanceQueryAPI;

    @Test
    public void queryBalance() throws Exception{
        long n = kuaiQianBalanceQueryAPI.queryBalance();
        System.out.println(n);
    }
}

package com.mod.loan.service;

import com.mod.loan.model.Order;

import java.util.Map;

public interface CallBackRongZeService {

    /**
     * 推送订单状态
     *
     * @param order
     */
    void pushOrderStatus(Order order);


    /*
     * @Description:
     * @Param: 推送还款计划
     * @return:
     * @Author: huijin.shuailijie
     * @Date: 2019/5/19
     */
   void pushRepayPlan(Order order);

}

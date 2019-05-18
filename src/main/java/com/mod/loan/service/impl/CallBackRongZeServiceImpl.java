package com.mod.loan.service.impl;

import com.alibaba.fastjson.JSON;
import com.mod.loan.common.enums.OrderSourceEnum;
import com.mod.loan.config.Constant;
import com.mod.loan.model.Order;
import com.mod.loan.service.CallBackRongZeService;
import com.mod.loan.service.OrderService;
import com.mod.loan.util.rongze.BizDataUtil;
import com.mod.loan.util.rongze.RongZeRequestUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@Service
public class CallBackRongZeServiceImpl implements CallBackRongZeService {

    @Resource
    private OrderService orderService;

    @Override
    public void pushOrderStatus(Order order) {
        try {
            order = checkOrder(order);
            if (order == null) return;

            unbindOrderNo(order);
            postOrderStatus(order);
        } catch (Exception e) {
            log.error("给融泽推送订单状态失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> postOrderStatus(Order order) throws Exception {
        int status;
        if (order.getStatus() == 23) status = 169; //放款失败
        else if (order.getStatus() == 31) status = 170; //放款成功
        else if (order.getStatus() == 21 || order.getStatus() == 22 || order.getStatus() == 11 || order.getStatus() == 12)
            status = 171; //放款处理中
        else if (order.getStatus() == 33) status = 180; //贷款逾期
        else if (order.getStatus() == 41 || order.getStatus() == 42) status = 200; //贷款结清
        else status = 169;

        long updateTime = order.getCreateTime().getTime();
        switch (status) {
            case 170:
                updateTime = order.getArriveTime().getTime();
                break;
            case 200:
                updateTime = order.getRealRepayTime().getTime();
        }
        Map<String, Object> map = new HashMap<>();
        map.put("order_no", order.getOrderNo());
        map.put("order_status", status);
        map.put("update_time", updateTime);
        map.put("remark", "");
        postOrderStatus(map);
        return map;
    }

    private Order checkOrder(Order order) {
        if (StringUtils.isBlank(order.getOrderNo()) && order.getId() != null && order.getId() > 0) {
            order = orderService.selectByPrimaryKey(order.getId());
        }
        if (order == null) return null;
        if (!OrderSourceEnum.isRongZe(order.getSource())) return null;
        return order;
    }

    private void postOrderStatus(Map<String, Object> map) throws Exception {
        RongZeRequestUtil.doPost(Constant.rongZeCallbackUrl, "api.order.status", JSON.toJSONString(map));
    }

    private void unbindOrderNo(Order o) {
        o.setOrderNo(unbindOrderNo(o.getOrderNo()));
    }

    private String unbindOrderNo(String orderNo) {
        return BizDataUtil.unbindRZOrderNo(orderNo);
    }
}

package com.mod.loan.service.impl;

import com.alibaba.fastjson.JSON;
import com.mod.loan.config.Constant;
import com.mod.loan.model.Order;
import com.mod.loan.service.CallBackRongZeService;
import com.mod.loan.util.rongze.BizDataUtil;
import com.mod.loan.util.rongze.RongZeRequestUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;


@Slf4j
@Service
public class CallBackRongZeServiceImpl implements CallBackRongZeService {

    @Override
    public void pushOrderStatus(Order order) {
        try {
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

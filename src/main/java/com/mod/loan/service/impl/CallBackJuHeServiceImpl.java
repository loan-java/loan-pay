package com.mod.loan.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mod.loan.common.enums.*;
import com.mod.loan.config.juhe.JuHeConfig;
import com.mod.loan.model.User;
import com.mod.loan.service.CallBackJuHeService;
import com.mod.loan.util.CallBackJuHeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * loan-risk 2019/4/25 huijin.shuailijie Init
 */
@Slf4j
@Service
public class CallBackJuHeServiceImpl implements CallBackJuHeService {

    @Autowired
    private JuHeConfig juHeConfig;

    @Override
    public void callBack(User user, String orderNo, JuHeCallBackEnum juHeCallBackEnum) {
        log.info("回调订单信息: {},状态: {}", orderNo, JSON.toJSONString(juHeCallBackEnum));
        JSONObject object = JSONObject.parseObject(user.getCommonInfo());
        object.put("orderNo", orderNo);
        object.put("accountId", user.getId());
        object.put("orderType", juHeCallBackEnum.getOrderTypeEnum().getCode());
        object.put("orderStatus", juHeCallBackEnum.getOrderStatusEnum().getCode());
        object.put("payStatus", juHeCallBackEnum.getPayStatusEnum().getCode());
        object.put("repayStatus", juHeCallBackEnum.getRepayStatusEnum().getCode());

        CallBackJuHeUtil.callBack(juHeConfig.getJuHeCallBackUrl(), object);
    }

    @Override
    public void withholdCallBack(User user, String orderNo, String repayNo, BigDecimal amount, JuHeCallBackEnum juHeCallBackEnum) {
        log.info("回调订单信息: {},状态: {}", orderNo, JSON.toJSONString(juHeCallBackEnum));
        JSONObject object = JSONObject.parseObject(user.getCommonInfo());
        object.put("orderNo", orderNo);
        object.put("autoRepayNo", repayNo);
        object.put("shouldRepayAmount", amount.toPlainString());
        object.put("accountId", user.getId());
        object.put("orderType", juHeCallBackEnum.getOrderTypeEnum().getCode());
        object.put("orderStatus", juHeCallBackEnum.getOrderStatusEnum().getCode());
        object.put("payStatus", juHeCallBackEnum.getPayStatusEnum().getCode());
        object.put("repayStatus", juHeCallBackEnum.getRepayStatusEnum().getCode());

        CallBackJuHeUtil.callBack(juHeConfig.getJuHeCallBackUrl(), object);
    }

    public static void main(String[] args) {
        JSONObject object = JSONObject.parseObject("{\"timeStamp\":1556258172,\"accountId\":\"\",\"sign\":\"FAA77AAC0B70B9FA9F5D2D9F4708F938\",\"mobile\":\"15867122886\",\"intefaceType\":\"WCWOISnFvBAWDBA\",\"deviceCode\":\"00000000-08aa-7bb7-0110-7d270033c587\",\"terminalId\":\"A\",\"source\":\"20190226810\",\"version\":\"3.1.3\",\"token\":\"\"}");
        object.put("orderNo", "b20190426154404612444652");
        object.put("accountId", "22");
        object.put("orderType", "JK");
        object.put("orderStatus", "AUDIT_REFUSE");
        object.put("payStatus", "PAYED");
        object.put("repayStatus", "NOT_REPAY");

        log.info("回调返回信息, {}", object);
        CallBackJuHeUtil.callBack("http://loan.juhexinyong.cn/apiext/orderCallback/orderInfo/", object);
    }
}

package com.mod.loan.util.yeepay;

import com.alibaba.fastjson.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * @author liujianjian
 * @date 2019/6/16 12:58
 */
public class YeePayApiRequest {

    /**
     * 放款
     */
    public static JSONObject transferSend(String batchNo, String orderId, String amount, String accountName, String accountNumber,
                                   String bankCode, String bankName) throws Exception {
        String customerNumber = format(Config.getInstance().getValue("customerNumber"));
        String groupNumber = format(Config.getInstance().getValue("groupNumber"));

        String feeType = "TARGET";

        Map<String, Object> params = new HashMap<>();
        params.put("customerNumber", customerNumber);
        params.put("groupNumber", groupNumber);
        params.put("batchNo", batchNo);
        params.put("orderId", orderId);
        params.put("amount", amount);
        params.put("accountName", accountName);
        params.put("accountNumber", accountNumber);
        params.put("bankCode", bankCode);
        params.put("bankName", bankName);
        params.put("feeType", feeType);

        String uri = YeepayUtil.getUrl(YeepayUtil.PAYMENT_URL);
        return YeepayUtil.yeepayYOP(params, uri);
    }

    /**
     * 余额查询
     */
    public static JSONObject queryBalance() throws Exception {
        String customerNumber = format(Config.getInstance().getValue("customerNumber"));

        Map<String, Object> params = new HashMap<>();
        params.put("customerNumber", customerNumber);
        String uri = YeepayUtil.getUrl(YeepayUtil.customeramountQuery_URL);

        return YeepayUtil.yeepayYOP(params, uri);
    }

    //放款查询
    public static JSONObject transferSendQuery(String batchNo, String orderId) throws Exception {
        String customerNumber = format(Config.getInstance().getValue("customerNumber"));
        Map<String, Object> params = new HashMap<>();
        params.put("customerNumber", customerNumber);
        params.put("batchNo", batchNo);
        params.put("orderId", orderId);
        params.put("pageNo", 1);
        params.put("pageSize", 1);

        String uri = YeepayUtil.getUrl(YeepayUtil.PAYMENTQUERY_URL);

        return YeepayUtil.yeepayYOP(params, uri);
    }

    //还款查询
    public static JSONObject queryPayResult(String yborderid) throws Exception {
        String merchantno = Config.getInstance().getValue("merchantno");

        Map<String, String> map = new HashMap<>();
        map.put("merchantno", merchantno);
        map.put("yborderid", yborderid);

        String bindcardpayqueryUri = Config.getInstance().getValue("bindcardpayqueryUri");
        return YeepayUtil.yeepayRepayQuery(map, bindcardpayqueryUri);
    }

    public static String format(String text) {
        return text == null ? "" : text.trim();
    }

}

package com.mod.loan.util.yeepay;

import com.alibaba.fastjson.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * @ author liujianjian
 * @ date 2019/6/16 12:58
 */
public class YeePayApiRequest {

    //放款
    public static JSONObject transferSend(String batchNo, String orderId, String amount, String accountName, String accountNumber,
                                   String bankCode, String bankName) throws Exception {
        String customerNumber = format(Config.getInstance().getValue("customerNumber"));
        String groupNumber = format(Config.getInstance().getValue("groupNumber"));

//        String batchNo = format(request.getParameter("batchNo"));
//        String orderId = format(request.getParameter("orderId"));
//        String amount = format(request.getParameter("amount"));
//        String product = format(request.getParameter("product"));
//        String urgency = format(request.getParameter("urgency"));
//        String accountName = format(request.getParameter("accountName"));
//        String accountNumber = format(request.getParameter("accountNumber"));
//        String bankCode = format(request.getParameter("bankCode"));
//        String bankName = format(request.getParameter("bankName"));
//        String bankBranchName = format(request.getParameter("bankBranchName"));
//        String provinceCode = format(request.getParameter("provinceCode"));
//        String cityCode = format(request.getParameter("cityCode"));
        String feeType = "TARGET";
//        String desc = format(request.getParameter("desc"));
//        String leaveWord = format(request.getParameter("leaveWord"));
//        String abstractInfo = format(request.getParameter("abstractInfo"));

        Map<String, Object> params = new HashMap<>();
        params.put("customerNumber", customerNumber);
        params.put("groupNumber", groupNumber);
        params.put("batchNo", batchNo);
        params.put("orderId", orderId);
        params.put("amount", amount);
//        params.put("product", product);
//        params.put("urgency", urgency);
        params.put("accountName", accountName);
        params.put("accountNumber", accountNumber);
        params.put("bankCode", bankCode);
        params.put("bankName", bankName);
//        params.put("bankBranchName", bankBranchName);
//        params.put("provinceCode", provinceCode);
//        params.put("cityCode", cityCode);
        params.put("feeType", feeType);
//        params.put("desc", desc);
//        params.put("leaveWord", leaveWord);
//        params.put("abstractInfo", abstractInfo);

        String uri = YeepayUtil.getUrl(YeepayUtil.PAYMENT_URL);

        return YeepayUtil.yeepayYOP(params, uri);
    }

    //余额查询
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
//        String batchNo = format(request.getParameter("batchNo"));
//        String product = format(request.getParameter("product"));
//        String orderId = format(request.getParameter("orderId"));
//        String pageNo = format(request.getParameter("pageNo"));
//        String pageSize =format( request.getParameter("pageSize"));


        Map<String, Object> params = new HashMap<>();
        params.put("customerNumber", customerNumber);
        params.put("batchNo", batchNo);
//        params.put("product", product);
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

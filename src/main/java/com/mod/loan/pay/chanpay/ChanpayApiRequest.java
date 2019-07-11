package com.mod.loan.pay.chanpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mod.loan.common.exception.BizException;
import com.mod.loan.config.Constant;
import com.mod.loan.pay.chanpay.dsf.BaseParameter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @ author liujianjian
 * @ date 2019/6/18 20:41
 */
@Slf4j
@Component
public class ChanpayApiRequest extends BaseParameter {

    private static ChanpayGateway chanpay = new ChanpayGateway();

    //协议支付订单查询
    public JSONObject queryTrade(String orderNo) throws Exception {
        Map<String, String> origMap = new HashMap<String, String>();
        // 2.1 基本参数
        origMap = chanpay.setCommonMap(origMap);
        origMap.put("Service", "nmg_api_query_trade");// 请求的接口名
        // 2.2 业务参数
        origMap.put("TrxId", orderNo);// 订单号
        origMap.put("OrderTrxId", Constant.chanpayBizOrderId);// 原业务请求订单号，固定值
        origMap.put("TradeType", "pay_order");// 原业务订单类型
        return doPost(origMap);
    }

    //商户余额查询
    public double queryPayBalance() throws Exception {
        Map<String, String> map = this.requestBaseParameter();
        map.put("TransCode", "C00005");
        map.put("OutTradeNo", ChanPayUtil.generateOutTradeNo());
//        map.put("AcctNo", ChanPayUtil.encrypt("200000920146777",
//                BaseConstant.MERCHANT_PUBLIC_KEY, BaseConstant.CHARSET));
//        map.put("AcctName", ChanPayUtil.encrypt("测试",
//                BaseConstant.MERCHANT_PUBLIC_KEY, BaseConstant.CHARSET));

        JSONObject json = doPost(map);
        return Double.valueOf(json.getString("PayBalance")); //出款户余额
    }

    //放款
    public void transfer(String orderNo, String bankName, String bankCardNo, String username, String amount) throws Exception {
        Map<String, String> map = this.requestBaseParameter();
        map.put("TransCode", "T10000"); // 交易码
        map.put("OutTradeNo", orderNo); // 商户网站唯一订单号
//        map.put("CorpAcctNo", "1223332343");  //可空
        map.put("BusinessType", "0"); // 业务类型：0对私 1对公
        map.put("BankCommonName", bankName); // 通用银行名称
//        map.put("BankCode", "CCB");//对公必填
        map.put("AccountType", "00"); // 账户类型
        map.put("AcctNo", chanpay.encrypt(bankCardNo)); // 对手人账号(此处需要用真实的账号信息)
        map.put("AcctName", chanpay.encrypt(username)); // 对手人账户名称
        map.put("TransAmt", amount);
        map.put("ChargeRole", "payee");

        //************** 以下信息可空  *******************
//		map.put("Province", "甘肃省"); // 省份信息
//		map.put("City", "兰州市"); // 城市信息
//		map.put("BranchBankName", "中国建设银行股份有限公司兰州新港城支行"); // 对手行行名
//		map.put("BranchBankCode", "105821005604");
//		map.put("DrctBankCode", "105821005604");
//		map.put("Currency", "CNY");
//		map.put("LiceneceType", "01");
//		map.put("LiceneceNo", ChanPayUtil.encrypt("622225199209190017", BaseConstant.MERCHANT_PUBLIC_KEY, BaseConstant.CHARSET));
//		map.put("Phone", ChanPayUtil.encrypt("17001090000", BaseConstant.MERCHANT_PUBLIC_KEY, BaseConstant.CHARSET));
//		map.put("AcctExp", "exp");
//		map.put("AcctCvv2", ChanPayUtil.encrypt("cvv", BaseConstant.MERCHANT_PUBLIC_KEY, BaseConstant.CHARSET));
//		map.put("CorpCheckNo", "201703061413");
//		map.put("Summary", "");

//        map.put("CorpPushUrl", "http://172.20.11.16");
//        map.put("PostScript", "用途");

        doPost(map);
    }

    //放款结果查询
    public JSONObject transferQuery(String orderNo) throws Exception {
        Map<String, String> map = this.requestBaseParameter();
        map.put("TransCode", "C00000");
        map.put("OutTradeNo", orderNo);
        map.put("OriOutTradeNo", Constant.chanpayBizOrderId);
        return doPost(map);
        //json.getString("OriginalRetCode");
    }

    public static boolean isTransferSucc(String code) {
        return "000000".equals(code);
    }

    public static boolean isTransferFail(String code) {
        return "111111".equals(code);
    }

    public static boolean isTradeSucc(String status) {
        return "TRADE_SUCCESS".equals(status);
    }

    public static boolean isTradePayFail(String status) {
        return "PAY_FAIL".equals(status);
    }

    public static boolean isTradeClose(String status) {
        return "TRADE_CLOSED".equals(status);
    }

    private JSONObject doPost(Map<String, String> origMap) throws Exception {
        log.info("畅捷 api 请求开始, params: " + JSON.toJSONString(origMap));
        String result = chanpay.gatewayPost(origMap);
        log.info("畅捷 api 请求结束, result: " + result);

        JSONObject json = JSON.parseObject(result);
        String acceptStatus = json.getString("AcceptStatus");
        String appRetMsg = json.getString("AppRetMsg");
        String appRetCode = json.getString("AppRetcode");
        String status = json.getString("Status");
        String retCode = json.getString("RetCode");
        String retMsg = json.getString("RetMsg");

        if ("F".equals(acceptStatus) || "F".equals(status)) {
            throw new BizException(StringUtils.isNotBlank(retCode) ? retCode : appRetCode, StringUtils.isNotBlank(retMsg) ? retMsg : appRetMsg);
        }
        return json;
    }

}

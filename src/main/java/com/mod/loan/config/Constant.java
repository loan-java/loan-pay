package com.mod.loan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Constant {

    public static String TEST;

    public static String ENVIROMENT;


    public static String kuaiqianVersion;

    public static String kuaiqianMemberId;

    public static String kuaiqianTerminalId;

    public static String kuaiqianRepayQueryUrl;

    public static String kuaiQianJksPath;


    public static String kuaiQianKeyPassword;

    public static String rongZeRequestAppId;
    public static String rongZeCallbackUrl;
    public static String rongZeQueryUrl;
    public static String rongZePublicKey;

    public static String orgPrivateKey;


    //畅捷支付
//    public static String chanpayPartnerId;
    public static String chanpayMerchantNo;
    public static String chanpayBizOrderId;
    public static String chanpayApiGateway;
    public static String chanpayPublicKey;
    public static String chanpayOrgPrivateKey;

//    @Value("${chanpay.partner.id}")
//    public void setChanpayPartnerId(String chanpayPartnerId) {
//        Constant.chanpayPartnerId = chanpayPartnerId;
//    }

    @Value("${chanpay.biz.order.id:}")
    public void setChanpayBizOrderId(String chanpayBizOrderId) {
        Constant.chanpayBizOrderId = chanpayBizOrderId;
    }

    @Value("${chanpay.merchant.no:}")
    public void setChanpayMerchantNo(String chanpayMerchantNo) {
        Constant.chanpayMerchantNo = chanpayMerchantNo;
    }

    @Value("${chanpay.api.gateway:}")
    public void setChanpayApiGateway(String chanpayApiGateway) {
        Constant.chanpayApiGateway = chanpayApiGateway;
    }

    @Value("${chanpay.rsa.public.key:}")
    public void setChanpayPublicKey(String chanpayPublicKey) {
        Constant.chanpayPublicKey = chanpayPublicKey;
    }

    @Value("${chanpay.org.rsa.private.key:}")
    public void setChanpayOrgPrivateKey(String chanpayOrgPrivateKey) {
        Constant.chanpayOrgPrivateKey = chanpayOrgPrivateKey;
    }

   
    @Value("${rongze.request.app.id:}")
    public void setRongZeRequestAppId(String rongZeRequestAppId) {
        Constant.rongZeRequestAppId = rongZeRequestAppId;
    }

    @Value("${rongze.callback.url:}")
    public void setRongZeCallbackUrl(String rongZeCallbackUrl) {
        Constant.rongZeCallbackUrl = rongZeCallbackUrl;
    }

    @Value("${rongze.query.url:}")
    public void setRongZeQueryUrl(String rongZeQueryUrl) {
        Constant.rongZeQueryUrl = rongZeQueryUrl;
    }

    @Value("${org.rsa.private.key:}")
    public void setOrgPrivateKey(String orgPrivateKey) {
        Constant.orgPrivateKey = orgPrivateKey;
    }

    @Value("${rongze.rsa.public.key:}")
    public void setRongZePublicKey(String rongZePublicKey) {
        Constant.rongZePublicKey = rongZePublicKey;
    }

    @Value("${kuaiqian.key.password:}")
    public void setKuaiQianKeyPassword(String kuaiQianKeyPassword) {
        Constant.kuaiQianKeyPassword = kuaiQianKeyPassword;
    }

    @Value("${kuaiqian.version:}")
    public  void setKuaiqianVersion(String kuaiqianVersion) {
        Constant.kuaiqianVersion = kuaiqianVersion;
    }

    @Value("${kuaiqian.member.id:}")
    public  void setKuaiqianMemberId(String kuaiqianMemberId) {
        Constant.kuaiqianMemberId = kuaiqianMemberId;
    }

    @Value("${kuaiqian.terminal.id:}")
    public  void setKuaiqianTerminalId(String kuaiqianTerminalId) {
        Constant.kuaiqianTerminalId = kuaiqianTerminalId;
    }

    @Value("${kuaiqian.repay.query.url:}")
    public  void setKuaiqianRepayQueryUrl(String kuaiqianRepayQueryUrl) {
        Constant.kuaiqianRepayQueryUrl = kuaiqianRepayQueryUrl;
    }

    @Value("${kuaiqian.jks.path:}")
    public  void setKuaiQianJksPath(String kuaiQianJksPath) {
        Constant.kuaiQianJksPath = kuaiQianJksPath;
    }


    @Value("${test:}")
    public void setPICTURE_URL(String test) {
        TEST = test;
    }

    @Value("${environment:}")
    public void setENVIROMENT(String environment) {
        Constant.ENVIROMENT = environment;
    }


}

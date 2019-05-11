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

    public static String smsTitle;

    @Value("${sms.title}")
    public  void setSmsTitle(String smsTitle) {
        Constant.smsTitle = smsTitle;
    }

    @Value("${kuaiqian.key.password}")
    public void setKuaiQianKeyPassword(String kuaiQianKeyPassword) {
        Constant.kuaiQianKeyPassword = kuaiQianKeyPassword;
    }

    @Value("${kuaiqian.version}")
    public  void setKuaiqianVersion(String kuaiqianVersion) {
        Constant.kuaiqianVersion = kuaiqianVersion;
    }

    @Value("${kuaiqian.member.id}")
    public  void setKuaiqianMemberId(String kuaiqianMemberId) {
        Constant.kuaiqianMemberId = kuaiqianMemberId;
    }

    @Value("${kuaiqian.terminal.id}")
    public  void setKuaiqianTerminalId(String kuaiqianTerminalId) {
        Constant.kuaiqianTerminalId = kuaiqianTerminalId;
    }

    @Value("${kuaiqian.repay.query.url}")
    public  void setKuaiqianRepayQueryUrl(String kuaiqianRepayQueryUrl) {
        Constant.kuaiqianRepayQueryUrl = kuaiqianRepayQueryUrl;
    }

    @Value("${kuaiqian.jks.path}")
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

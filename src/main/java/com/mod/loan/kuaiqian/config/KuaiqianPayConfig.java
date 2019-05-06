package com.mod.loan.kuaiqian.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * loan-own-pay 2019/5/5 huijin.shuailijie Init
 */
@Component
public class KuaiqianPayConfig {

    @Value("${kuaiqian.member.code}")
    private String kuaiqianMemberCode;


    @Value("${kuaiqian.feture.code}")
    private String kuaiqianFetureCode;

    @Value("${kuaiqian.pay.url}")
    private String kuaiqianPayUrl;


    @Value("${kuaiqian.query.url}")
    private String kuaiqianQueryUrl;


    @Value("${kuaiqian.version}")
    private String kuaiqianVersion;

    public String getKuaiqianMemberCode() {
        return kuaiqianMemberCode;
    }

    public void setKuaiqianMemberCode(String kuaiqianMemberCode) {
        this.kuaiqianMemberCode = kuaiqianMemberCode;
    }

    public String getKuaiqianFetureCode() {
        return kuaiqianFetureCode;
    }

    public void setKuaiqianFetureCode(String kuaiqianFetureCode) {
        this.kuaiqianFetureCode = kuaiqianFetureCode;
    }

    public String getKuaiqianPayUrl() {
        return kuaiqianPayUrl;
    }

    public void setKuaiqianPayUrl(String kuaiqianPayUrl) {
        this.kuaiqianPayUrl = kuaiqianPayUrl;
    }

    public String getKuaiqianQueryUrl() {
        return kuaiqianQueryUrl;
    }

    public void setKuaiqianQueryUrl(String kuaiqianQueryUrl) {
        this.kuaiqianQueryUrl = kuaiqianQueryUrl;
    }

    public String getKuaiqianVersion() {
        return kuaiqianVersion;
    }

    public void setKuaiqianVersion(String kuaiqianVersion) {
        this.kuaiqianVersion = kuaiqianVersion;
    }
}

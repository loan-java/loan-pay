package com.mod.loan.baofoo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * loan-pay 2019/4/20 huijin.shuailijie Init
 */
@Component
public class BaofooPayConfig {

    @Value("${baofoo.key.store.path}")
    private String baofooKeyStorePath;


    @Value("${baofoo.key.store.password}")
    private String baofooKeyStorePassword;


    @Value("${baofoo.pub.key}")
    private String baofooPubKey;


    @Value("${baofoo.pay.url}")
    private String baofooPayUrl;


    @Value("${baofoo.query.url}")
    private String baofooQueryUrl;


    @Value("${baofoo.member.id}")
    private String baofooMemberId;


    @Value("${baofoo.terminal.id}")
    private String baofooTerminalId;


    @Value("${baofoo.version}")
    private String baofooVersion;

    public String getBaofooKeyStorePath() {
        return baofooKeyStorePath;
    }

    public void setBaofooKeyStorePath(String baofooKeyStorePath) {
        this.baofooKeyStorePath = baofooKeyStorePath;
    }

    public String getBaofooPubKey() {
        return baofooPubKey;
    }

    public void setBaofooPubKey(String baofooPubKey) {
        this.baofooPubKey = baofooPubKey;
    }

    public String getBaofooPayUrl() {
        return baofooPayUrl;
    }

    public void setBaofooPayUrl(String baofooPayUrl) {
        this.baofooPayUrl = baofooPayUrl;
    }

    public String getBaofooMemberId() {
        return baofooMemberId;
    }

    public void setBaofooMemberId(String baofooMemberId) {
        this.baofooMemberId = baofooMemberId;
    }

    public String getBaofooTerminalId() {
        return baofooTerminalId;
    }

    public void setBaofooTerminalId(String baofooTerminalId) {
        this.baofooTerminalId = baofooTerminalId;
    }

    public String getBaofooVersion() {
        return baofooVersion;
    }

    public void setBaofooVersion(String baofooVersion) {
        this.baofooVersion = baofooVersion;
    }

    public String getBaofooKeyStorePassword() {
        return baofooKeyStorePassword;
    }

    public void setBaofooKeyStorePassword(String baofooKeyStorePassword) {
        this.baofooKeyStorePassword = baofooKeyStorePassword;
    }

    public String getBaofooQueryUrl() {
        return baofooQueryUrl;
    }

    public void setBaofooQueryUrl(String baofooQueryUrl) {
        this.baofooQueryUrl = baofooQueryUrl;
    }
}

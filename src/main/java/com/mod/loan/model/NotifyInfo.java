package com.mod.loan.model;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.util.Date;

@Table(name = "th_notify_info")
public class NotifyInfo {
    @Id
    private Long id;

    /**
     * 回调类型：1-协义支付，2-高级自动
     */
    private Integer type;

    /**
     * 订单编号
     */
    @Column(name = "order_id")
    private String orderId;

    /**
     * 结果
     */
    @Column(name = "status")
    private String status;

    /**
     * 金额
     */
    @Column(name = "amount")
    private BigDecimal amount;

    /**
     * 未加密xml信息
     */
    @Column(name = "xml_str_encryption")
    private String xmlStrEncryption;

    /**
     * 加密xml信息
     */
    @Column(name = "xml_str_Decrypt")
    private String xmlStrDecrypt;

    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private Date createTime;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getXmlStrEncryption() {
        return xmlStrEncryption;
    }

    public void setXmlStrEncryption(String xmlStrEncryption) {
        this.xmlStrEncryption = xmlStrEncryption;
    }

    public String getXmlStrDecrypt() {
        return xmlStrDecrypt;
    }

    public void setXmlStrDecrypt(String xmlStrDecrypt) {
        this.xmlStrDecrypt = xmlStrDecrypt;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
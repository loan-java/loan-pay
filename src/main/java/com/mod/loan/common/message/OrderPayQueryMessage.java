package com.mod.loan.common.message;

/**
 * 放款结果队列的DTO
 */
public class OrderPayQueryMessage {

    private String payNo; // 放款流水号
    private Long orderId;//订单号
    private String merchantAlias;// 商户别名
    private int times;// 查询次数

    public OrderPayQueryMessage() {
        super();
    }

    public OrderPayQueryMessage(String payNo, Long orderId, String merchantAlias) {
        super();
        this.orderId = orderId;
        this.payNo = payNo;
        this.merchantAlias = merchantAlias;
    }

    public String getPayNo() {
        return payNo;
    }

    public void setPayNo(String payNo) {
        this.payNo = payNo;
    }

    public String getMerchantAlias() {
        return merchantAlias;
    }

    public void setMerchantAlias(String merchantAlias) {
        this.merchantAlias = merchantAlias;
    }

    public int getTimes() {
        return times;
    }

    public void setTimes(int times) {
        this.times = times;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
}

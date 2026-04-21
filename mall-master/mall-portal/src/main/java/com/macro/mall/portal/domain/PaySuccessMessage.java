package com.macro.mall.portal.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 支付成功消息
 */
public class PaySuccessMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 订单号
     */
    private String orderSn;
    
    /**
     * 支付金额
     */
    private BigDecimal payAmount;
    
    /**
     * 支付方式：1-支付宝，2-微信
     */
    private Integer payType;
    
    /**
     * 支付宝交易号
     */
    private String tradeNo;
    
    /**
     * 支付时间
     */
    private Date payTime;
    
    /**
     * 消息发送时间
     */
    private Date sendTime;
    
    /**
     * 重试次数
     */
    private Integer retryCount = 0;

    public String getOrderSn() {
        return orderSn;
    }

    public void setOrderSn(String orderSn) {
        this.orderSn = orderSn;
    }

    public BigDecimal getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }

    public Integer getPayType() {
        return payType;
    }

    public void setPayType(Integer payType) {
        this.payType = payType;
    }

    public String getTradeNo() {
        return tradeNo;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
    }

    public Date getPayTime() {
        return payTime;
    }

    public void setPayTime(Date payTime) {
        this.payTime = payTime;
    }

    public Date getSendTime() {
        return sendTime;
    }

    public void setSendTime(Date sendTime) {
        this.sendTime = sendTime;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    @Override
    public String toString() {
        return "PaySuccessMessage{" +
                "orderSn='" + orderSn + '\'' +
                ", payAmount=" + payAmount +
                ", payType=" + payType +
                ", tradeNo='" + tradeNo + '\'' +
                ", payTime=" + payTime +
                ", sendTime=" + sendTime +
                ", retryCount=" + retryCount +
                '}';
    }
}
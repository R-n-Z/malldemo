package com.macro.mall.portal.domain;

import java.io.Serializable;

/**
 * 秒杀消息
 */
public class SeckillMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long memberId;

    /**
     * 秒杀商品ID
     */
    private Long productId;

    /**
     * 秒杀场次ID
     */
    private Long sessionId;

    /**
     * 秒杀地址token
     */
    private String seckillToken;

    /**
     * 购买数量
     */
    private Integer quantity;

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 库存预扣凭证
     */
    private String lockToken;

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getSeckillToken() {
        return seckillToken;
    }

    public void setSeckillToken(String seckillToken) {
        this.seckillToken = seckillToken;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getOrderSn() {
        return orderSn;
    }

    public void setOrderSn(String orderSn) {
        this.orderSn = orderSn;
    }

    public String getLockToken() {
        return lockToken;
    }

    public void setLockToken(String lockToken) {
        this.lockToken = lockToken;
    }

    @Override
    public String toString() {
        return "SeckillMessage{" +
                "memberId=" + memberId +
                ", productId=" + productId +
                ", sessionId=" + sessionId +
                ", seckillToken='" + seckillToken + '\'' +
                ", quantity=" + quantity +
                ", orderSn='" + orderSn + '\'' +
                ", lockToken='" + lockToken + '\'' +
                '}';
    }
}
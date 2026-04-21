package com.macro.mall.portal.domain;

import java.io.Serializable;
import java.util.List;

/**
 * 订单参数
 */
public class OrderParam implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 收货地址ID
     */
    private Long memberReceiveAddressId;

    /**
     * 优惠券ID
     */
    private Long couponId;

    /**
     * 是否使用积分
     */
    private Integer useIntegration;

    /**
     * 支付方式：0->未支付；1->支付宝；2->微信
     */
    private Integer payType;

    /**
     * 购物车ID列表（普通下单使用）
     */
    private List<Long> cartIds;

    /**
     * 秒杀商品ID
     */
    private Long productId;

    /**
     * 秒杀价格
     */
    private java.math.BigDecimal seckillPrice;

    /**
     * 购买数量
     */
    private Integer quantity;

    // ==================== 秒杀订单专用字段 ====================

    /**
     * 是否为秒杀订单
     */
    private Boolean isSeckillOrder = false;

    /**
     * 秒杀场次ID
     */
    private Long flashPromotionSessionId;

    public Long getMemberReceiveAddressId() {
        return memberReceiveAddressId;
    }

    public void setMemberReceiveAddressId(Long memberReceiveAddressId) {
        this.memberReceiveAddressId = memberReceiveAddressId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public void setCouponId(Long couponId) {
        this.couponId = couponId;
    }

    public Integer getUseIntegration() {
        return useIntegration;
    }

    public void setUseIntegration(Integer useIntegration) {
        this.useIntegration = useIntegration;
    }

    public Integer getPayType() {
        return payType;
    }

    public void setPayType(Integer payType) {
        this.payType = payType;
    }

    public List<Long> getCartIds() {
        return cartIds;
    }

    public void setCartIds(List<Long> cartIds) {
        this.cartIds = cartIds;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public java.math.BigDecimal getSeckillPrice() {
        return seckillPrice;
    }

    public void setSeckillPrice(java.math.BigDecimal seckillPrice) {
        this.seckillPrice = seckillPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Boolean getIsSeckillOrder() {
        return isSeckillOrder;
    }

    public void setIsSeckillOrder(Boolean isSeckillOrder) {
        this.isSeckillOrder = isSeckillOrder;
    }

    public Long getFlashPromotionSessionId() {
        return flashPromotionSessionId;
    }

    public void setFlashPromotionSessionId(Long flashPromotionSessionId) {
        this.flashPromotionSessionId = flashPromotionSessionId;
    }
}
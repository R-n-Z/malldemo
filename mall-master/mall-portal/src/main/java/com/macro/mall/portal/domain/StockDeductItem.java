package com.macro.mall.portal.domain;

import java.io.Serializable;

/**
 * 库存扣减项
 */
public class StockDeductItem implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 扣减数量
     */
    private Integer quantity;

    /**
     * 订单ID（用于关联）
     */
    private Long orderId;

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
}
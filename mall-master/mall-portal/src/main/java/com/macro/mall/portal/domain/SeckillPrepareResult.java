package com.macro.mall.portal.domain;

import java.io.Serializable;

/**
 * 秒杀准备结果
 */
public class SeckillPrepareResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 秒杀商品ID
     */
    private Long productId;

    /**
     * 秒杀场次ID
     */
    private Long sessionId;

    /**
     * 秒杀地址（加密token）
     */
    private String seckillToken;

    /**
     * 剩余库存
     */
    private Integer stock;

    /**
     * 是否可以秒杀
     */
    private Boolean canSeckill;

    /**
     * 提示信息
     */
    private String message;

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

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Boolean getCanSeckill() {
        return canSeckill;
    }

    public void setCanSeckill(Boolean canSeckill) {
        this.canSeckill = canSeckill;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 创建成功结果
     */
    public static SeckillPrepareResult success(String seckillToken, Integer stock) {
        SeckillPrepareResult result = new SeckillPrepareResult();
        result.setSeckillToken(seckillToken);
        result.setStock(stock);
        result.setCanSeckill(true);
        result.setMessage("可以秒杀");
        return result;
    }

    /**
     * 创建失败结果
     */
    public static SeckillPrepareResult fail(String message) {
        SeckillPrepareResult result = new SeckillPrepareResult();
        result.setCanSeckill(false);
        result.setMessage(message);
        return result;
    }
}
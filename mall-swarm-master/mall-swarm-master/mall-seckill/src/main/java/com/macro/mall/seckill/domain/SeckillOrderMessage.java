package com.macro.mall.seckill.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 秒杀订单消息 - 用于RocketMQ事务消息
 */
@Data
public class SeckillOrderMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 消息类型
     */
    private String messageType;
    
    /**
     * 追踪ID（用于排查问题）
     */
    private String traceId;
    
    /**
     * 订单号
     */
    private String orderSn;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 商品ID
     */
    private Long productId;
    
    /**
     * 秒杀价格
     */
    private BigDecimal seckillPrice;
    
    /**
     * 购买数量
     */
    private Integer count;
    
    /**
     * 消息状态：0-半消息，1-提交，2-回滚
     */
    private Integer status;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 创建时间
     */
    private Date createTime;
    
    /**
     * 消息过期时间（用于死信）
     */
    private Date expireTime;
    
    public SeckillOrderMessage() {
        this.messageType = "SECKILL_ORDER";
        this.traceId = java.util.UUID.randomUUID().toString().replace("-", "");
        this.status = 0;
        this.retryCount = 0;
        this.createTime = new Date();
        // 消息24小时过期
        this.expireTime = new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000);
    }
    
    /**
     * 创建秒杀订单消息
     */
    public static SeckillOrderMessage create(String orderSn, Long userId, Long productId, 
                                              BigDecimal seckillPrice, Integer count) {
        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setOrderSn(orderSn);
        message.setUserId(userId);
        message.setProductId(productId);
        message.setSeckillPrice(seckillPrice);
        message.setCount(count);
        return message;
    }
}
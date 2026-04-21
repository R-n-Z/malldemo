package com.macro.mall.order.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 订单超时取消消息 - 用于RocketMQ延迟消息
 */
@Data
public class OrderTimeoutMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 消息类型
     */
    private String messageType;
    
    /**
     * 追踪ID
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
     * 购买数量
     */
    private Integer count;
    
    /**
     * 超时时间（分钟）
     */
    private Integer timeoutMinutes;
    
    /**
     * 创建时间
     */
    private Date createTime;
    
    /**
     * 过期时间
     */
    private Date expireTime;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    public OrderTimeoutMessage() {
        this.messageType = "ORDER_TIMEOUT";
        this.traceId = java.util.UUID.randomUUID().toString().replace("-", "");
        this.retryCount = 0;
        this.createTime = new Date();
        this.timeoutMinutes = 30; // 默认30分钟超时
        // 消息24小时过期
        this.expireTime = new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000);
    }
    
    /**
     * 创建订单超时消息
     */
    public static OrderTimeoutMessage create(String orderSn, Long userId, Long productId, 
                                              Integer count, Integer timeoutMinutes) {
        OrderTimeoutMessage message = new OrderTimeoutMessage();
        message.setOrderSn(orderSn);
        message.setUserId(userId);
        message.setProductId(productId);
        message.setCount(count);
        message.setTimeoutMinutes(timeoutMinutes != null ? timeoutMinutes : 30);
        return message;
    }
}
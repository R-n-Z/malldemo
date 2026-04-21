package com.macro.mall.order.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单落库消息 - 用于异步落库
 */
@Data
public class OrderPersistMessage implements Serializable {
    
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
     * 商品名称
     */
    private String productName;
    
    /**
     * 购买数量
     */
    private Integer count;
    
    /**
     * 订单金额
     */
    private BigDecimal totalAmount;
    
    /**
     * 支付金额
     */
    private BigDecimal payAmount;
    
    /**
     * 订单类型：1-普通订单，2-秒杀订单
     */
    private Integer orderType;
    
    /**
     * 支付类型：1-支付宝，2-微信
     */
    private Integer payType;
    
    /**
     * 来源类型：1-APP，2-小程序，3-H5
     */
    private Integer sourceType;
    
    /**
     * 收货人姓名
     */
    private String receiverName;
    
    /**
     * 收货人电话
     */
    private String receiverPhone;
    
    /**
     * 收货人地址
     */
    private String receiverAddress;
    
    /**
     * 订单备注
     */
    private String note;
    
    /**
     * 创建时间
     */
    private Date createTime;
    
    /**
     * 幂等Token
     */
    private String idempotentToken;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    public OrderPersistMessage() {
        this.messageType = "ORDER_PERSIST";
        this.traceId = java.util.UUID.randomUUID().toString().replace("-", "");
        this.retryCount = 0;
        this.createTime = new Date();
    }
    
    /**
     * 创建订单落库消息
     */
    public static OrderPersistMessage create(String orderSn, Long userId, Long productId, 
                                              String productName, Integer count, 
                                              BigDecimal totalAmount, BigDecimal payAmount) {
        OrderPersistMessage message = new OrderPersistMessage();
        message.setOrderSn(orderSn);
        message.setUserId(userId);
        message.setProductId(productId);
        message.setProductName(productName);
        message.setCount(count);
        message.setTotalAmount(totalAmount);
        message.setPayAmount(payAmount);
        message.setOrderType(2); // 秒杀订单
        message.setPayType(1);
        message.setSourceType(1);
        message.setIdempotentToken("persist:" + orderSn);
        return message;
    }
}
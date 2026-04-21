package com.macro.mall.seckill.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 库存扣减消息
 */
@Data
public class StockDeductMessage implements Serializable {
    
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
     * 关联的订单号
     */
    private String orderSn;
    
    /**
     * 商品ID
     */
    private Long productId;
    
    /**
     * 扣减数量
     */
    private Integer count;
    
    /**
     * 操作类型：1-扣减，2-回滚
     */
    private Integer operationType;
    
    /**
     * 幂等Token
     */
    private String idempotentToken;
    
    /**
     * 创建时间
     */
    private Date createTime;
    
    public StockDeductMessage() {
        this.messageType = "STOCK_DEDUCT";
        this.traceId = java.util.UUID.randomUUID().toString().replace("-", "");
        this.createTime = new Date();
    }
    
    /**
     * 创建扣减消息
     */
    public static StockDeductMessage deduct(String orderSn, Long productId, Integer count) {
        StockDeductMessage message = new StockDeductMessage();
        message.setOrderSn(orderSn);
        message.setProductId(productId);
        message.setCount(count);
        message.setOperationType(1);
        message.setIdempotentToken("deduct:" + orderSn + ":" + productId);
        return message;
    }
    
    /**
     * 创建回滚消息
     */
    public static StockDeductMessage rollback(String orderSn, Long productId, Integer count) {
        StockDeductMessage message = new StockDeductMessage();
        message.setOrderSn(orderSn);
        message.setProductId(productId);
        message.setCount(count);
        message.setOperationType(2);
        message.setIdempotentToken("rollback:" + orderSn + ":" + productId);
        return message;
    }
}
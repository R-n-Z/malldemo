package com.macro.mall.stock.domain;

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
}
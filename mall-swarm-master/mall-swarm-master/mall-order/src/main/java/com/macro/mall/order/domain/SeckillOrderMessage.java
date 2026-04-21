package com.macro.mall.order.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 秒杀订单消息 - 来自秒杀模块
 */
@Data
public class SeckillOrderMessage implements Serializable {
    
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
     * 秒杀价格
     */
    private BigDecimal seckillPrice;
    
    /**
     * 购买数量
     */
    private Integer count;
    
    /**
     * 消息状态
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
}
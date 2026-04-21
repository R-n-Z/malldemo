package com.macro.mall.seckill.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 秒杀商品实体
 */
@Data
public class SeckillProduct implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Long id;
    
    /**
     * 商品ID
     */
    private Long productId;
    
    /**
     * 商品名称
     */
    private String productName;
    
    /**
     * 商品图片
     */
    private String productPic;
    
    /**
     * 秒杀价格
     */
    private BigDecimal seckillPrice;
    
    /**
     * 原价
     */
    private BigDecimal originalPrice;
    
    /**
     * 秒杀库存
     */
    private Integer seckillStock;
    
    /**
     * 每人限购数量
     */
    private Integer limitCount;
    
    /**
     * 秒杀开始时间
     */
    private Date startTime;
    
    /**
     * 秒杀结束时间
     */
    private Date endTime;
    
    /**
     * 状态：0-未开始，1-进行中，2-已结束
     */
    private Integer status;
    
    /**
     * 活动ID
     */
    private Long activityId;
}
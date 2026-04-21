package com.macro.mall.stock.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 库存实体
 */
@Data
public class PmsStock implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Long id;
    
    /**
     * 商品ID
     */
    private Long productId;
    
    /**
     * 库存数量
     */
    private Integer stock;
    
    /**
     * 锁定库存
     */
    private Integer lockStock;
    
    /**
     * 预警库存
     */
    private Integer lowStock;
    
    /**
     * 删除状态：0-未删除，1-已删除
     */
    private Integer deleteStatus;
}
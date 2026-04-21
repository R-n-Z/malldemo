package com.macro.mall.seckill.service;

import com.macro.mall.seckill.domain.SeckillProduct;

import java.util.Date;
import java.util.List;

/**
 * 缓存预热服务接口
 */
public interface CachePreheatService {
    
    /**
     * 预热所有活动商品
     */
    void preheatAllActiveProducts();
    
    /**
     * 预热指定商品
     */
    void preheatProduct(Long productId);
    
    /**
     * 预热指定时间范围内的商品
     */
    void preheatByTimeRange(Date startTime, Date endTime);
    
    /**
     * 刷新商品缓存
     */
    void refreshProductCache(Long productId);
    
    /**
     * 清除商品缓存
     */
    void clearProductCache(Long productId);
    
    /**
     * 获取活动商品列表
     */
    List<Long> getActiveProductIds();
    
    /**
     * 检查商品是否在活动中
     */
    boolean isProductActive(Long productId);
}
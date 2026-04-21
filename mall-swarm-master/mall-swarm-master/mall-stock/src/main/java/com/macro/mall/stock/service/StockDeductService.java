package com.macro.mall.stock.service;

import com.macro.mall.stock.domain.PmsStock;
import com.macro.mall.stock.mapper.PmsStockMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存扣减服务
 */
@Slf4j
@Service
public class StockDeductService {

    @Autowired
    private PmsStockMapper stockMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${seckill.stock.cache-key:seckill:stock:}")
    private String stockKeyPrefix;

    /**
     * 扣减库存
     */
    @Transactional
    public void deductStock(Long productId, Integer count) {
        log.info("扣减库存: productId={}, count={}", productId, count);
        
        // 1. 扣减Redis库存
        String redisKey = stockKeyPrefix + productId;
        Long redisStock = redisTemplate.opsForValue().decrement(redisKey, count);
        
        if (redisStock != null && redisStock < 0) {
            // Redis库存不足，回滚
            redisTemplate.opsForValue().increment(redisKey, count);
            throw new RuntimeException("Redis库存不足");
        }
        
        // 2. 扣减数据库库存
        int affected = stockMapper.deductStock(productId, count);
        
        if (affected == 0) {
            // 数据库库存不足，回滚Redis
            redisTemplate.opsForValue().increment(redisKey, count);
            throw new RuntimeException("数据库库存不足");
        }
        
        log.info("库存扣减成功: productId={}, count={}, remainingRedis={}", 
                productId, count, redisStock);
    }

    /**
     * 回滚库存
     */
    @Transactional
    public void rollbackStock(Long productId, Integer count) {
        log.info("回滚库存: productId={}, count={}", productId, count);
        
        // 1. 回滚Redis库存
        String redisKey = stockKeyPrefix + productId;
        redisTemplate.opsForValue().increment(redisKey, count);
        
        // 2. 回滚数据库库存
        stockMapper.rollbackStock(productId, count);
        
        log.info("库存回滚成功: productId={}, count={}", productId, count);
    }

    /**
     * 获取库存
     */
    public Integer getStock(Long productId) {
        // 先查Redis
        String redisKey = stockKeyPrefix + productId;
        Object redisStock = redisTemplate.opsForValue().get(redisKey);
        if (redisStock != null) {
            return Integer.parseInt(redisStock.toString());
        }
        
        // 查数据库
        PmsStock stock = stockMapper.selectByProductId(productId);
        return stock != null ? stock.getStock() : 0;
    }

    /**
     * 初始化库存
     */
    public void initStock(Long productId, Integer stock) {
        String redisKey = stockKeyPrefix + productId;
        redisTemplate.opsForValue().set(redisKey, stock);
        log.info("库存初始化: productId={}, stock={}", productId, stock);
    }
}
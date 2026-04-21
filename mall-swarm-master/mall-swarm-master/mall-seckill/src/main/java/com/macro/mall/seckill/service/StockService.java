package com.macro.mall.seckill.service;

import com.macro.mall.common.component.CacheProtectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 库存服务 - Redis预扣减（带缓存保护）
 */
@Slf4j
@Service
public class StockService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CacheProtectionService cacheProtectionService;

    @Value("${seckill.stock.cache-key:seckill:stock:}")
    private String stockKeyPrefix;

    @Value("${seckill.stock.expire-seconds:3600}")
    private long stockExpireSeconds;

    /**
     * 初始化库存到Redis（带随机过期，防止雪崩）
     */
    public void initStock(Long productId, Integer stock) {
        String stockKey = stockKeyPrefix + productId;
        
        // 使用随机过期时间，防止缓存雪崩
        long randomExpire = cacheProtectionService.randomExpire(stockExpireSeconds);
        redisTemplate.opsForValue().set(stockKey, stock, randomExpire, TimeUnit.SECONDS);
        
        // 添加到布隆过滤器
        cacheProtectionService.addToBloomFilter(stockKey);
        
        log.info("库存初始化到Redis: productId={}, stock={}, expire={}s", 
                productId, stock, randomExpire);
    }

    /**
     * 获取库存（带缓存保护）
     * 
     * 解决缓存穿透：使用布隆过滤器
     * 解决缓存击穿：使用分布式锁
     */
    public Integer getStock(Long productId) {
        String stockKey = stockKeyPrefix + productId;
        
        // 1. 布隆过滤器检查（解决缓存穿透）
        if (!cacheProtectionService.mightContain(stockKey)) {
            log.debug("布隆过滤器判断不存在: productId={}", productId);
            return 0;
        }
        
        // 2. 检查空值缓存
        if (cacheProtectionService.isNullValue(stockKey)) {
            log.debug("空值缓存: productId={}", productId);
            return 0;
        }
        
        // 3. 带锁查询（解决缓存击穿）
        return cacheProtectionService.queryWithSingleFlight(
                stockKey,
                () -> {
                    // 查询数据库
                    Object stock = redisTemplate.opsForValue().get(stockKey);
                    return stock != null ? Integer.parseInt(stock.toString()) : 0;
                },
                (stock) -> {
                    // 写入缓存，带随机过期
                    long randomExpire = cacheProtectionService.randomExpire(stockExpireSeconds);
                    redisTemplate.opsForValue().set(stockKey, stock, randomExpire, TimeUnit.SECONDS);
                },
                stockExpireSeconds
        );
    }

    /**
     * 预扣减库存（Lua原子操作）
     * 
     * @param productId 商品ID
     * @param count 扣减数量
     * @return true-扣减成功，false-库存不足
     */
    public boolean preDeductStock(Long productId, Integer count) {
        String stockKey = stockKeyPrefix + productId;
        
        String luaScript = """
            local stock = redis.call('get', KEYS[1])
            if stock and tonumber(stock) >= tonumber(ARGV[1]) then
                return redis.call('decrby', KEYS[1], ARGV[1])
            end
            return -1
            """;

        Long result = redisTemplate.execute(
                new RedisScript<>(luaScript, Long.class),
                Collections.singletonList(stockKey),
                count.toString()
        );

        if (result != null && result >= 0) {
            log.info("库存预扣减成功: productId={}, count={}, remaining={}", 
                    productId, count, result);
            return true;
        } else {
            log.warn("库存预扣减失败: productId={}, count={}", productId, count);
            return false;
        }
    }

    /**
     * 回滚库存（用于订单创建失败时）
     */
    public void rollbackStock(Long productId, Integer count) {
        String stockKey = stockKeyPrefix + productId;
        redisTemplate.opsForValue().increment(stockKey, count);
        
        // 重置过期时间
        long randomExpire = cacheProtectionService.randomExpire(stockExpireSeconds);
        redisTemplate.expire(stockKey, randomExpire, TimeUnit.SECONDS);
        
        log.info("库存回滚: productId={}, count={}", productId, count);
    }

    /**
     * 扣减库存（真实扣减，订单创建成功后调用）
     */
    public boolean deductStock(Long productId, Integer count) {
        String stockKey = stockKeyPrefix + productId;
        Long result = redisTemplate.opsForValue().decrement(stockKey, count);
        
        if (result != null && result >= 0) {
            log.info("库存真实扣减: productId={}, count={}, remaining={}", 
                    productId, count, result);
            return true;
        } else {
            // 回滚
            rollbackStock(productId, count);
            return false;
        }
    }

    /**
     * 标记已预扣减（用于事务回查）
     */
    public void markPreDeducted(String orderSn, Long productId) {
        String key = "seckill:pre-deducted:" + orderSn + ":" + productId;
        redisTemplate.opsForValue().set(key, "1", 24, TimeUnit.HOURS);
    }

    /**
     * 检查是否已预扣减
     */
    public boolean isPreDeducted(String orderSn, Long productId) {
        String key = "seckill:pre-deducted:" + orderSn + ":" + productId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 清除预扣减标记
     */
    public void clearPreDeducted(String orderSn, Long productId) {
        String key = "seckill:pre-deducted:" + orderSn + ":" + productId;
        redisTemplate.delete(key);
    }
}
package com.macro.mall.portal.service;

import com.macro.mall.portal.component.BloomFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 缓存服务
 * 提供缓存穿透、击穿、雪崩保护
 */
@Slf4j
@Service
public class CacheService {

    @Value("${redis.database:mall}")
    private String redisDatabase;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private BloomFilter bloomFilter;

    // ==================== 缓存穿透保护 ====================

    /**
     * 带布隆过滤器保护的缓存查询
     * @param cacheKey 缓存key
     * @param bloomFilterName 布隆过滤器名称
     * @param id 业务ID（如商品ID）
     * @param loader 数据加载器（从数据库查询）
     * @param cacheExpireSeconds 缓存过期时间
     * @return 缓存的数据
     */
    public <T> T getWithBloomFilter(String cacheKey, String bloomFilterName, 
                                    String id, CacheLoader<T> loader, long cacheExpireSeconds) {
        // 1. 先检查布隆过滤器
        if (!bloomFilter.mightContain(bloomFilterName, id)) {
            log.debug("布隆过滤器拦截: bloomFilter={}, id={}", bloomFilterName, id);
            return null; // 一定不存在，直接返回
        }

        // 2. 查缓存
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("缓存命中: cacheKey={}", cacheKey);
            return (T) cached;
        }

        // 3. 查数据库
        T data = loader.load();
        
        if (data != null) {
            // 4. 写入缓存
            redisTemplate.opsForValue().set(cacheKey, data, cacheExpireSeconds, TimeUnit.SECONDS);
            log.debug("写入缓存: cacheKey={}", cacheKey);
        } else {
            // 5. 缓存空值，防止缓存穿透
            // 空值缓存时间较短（5分钟），避免长期存储无效数据
            redisTemplate.opsForValue().set(cacheKey, null, 5, TimeUnit.MINUTES);
            log.warn("缓存空值: cacheKey={}, id={}", cacheKey, id);
        }

        return data;
    }

    /**
     * 简化版：带布隆过滤器保护的缓存查询
     */
    public <T> T getWithBloomFilter(String cacheKey, String bloomFilterName, 
                                    String id, CacheLoader<T> loader) {
        return getWithBloomFilter(cacheKey, bloomFilterName, id, loader, 24 * 3600);
    }

    // ==================== 缓存击穿保护 ====================

    /**
     * 分布式锁
     */
    private static final String LOCK_KEY = "lock:";

    /**
     * 带分布式锁的缓存查询（防止缓存击穿）
     * @param cacheKey 缓存key
     * @param lockKey 锁key
     * @param loader 数据加载器
     * @param lockExpireSeconds 锁过期时间
     * @param cacheExpireSeconds 缓存过期时间
     * @return 缓存的数据
     */
    public <T> T getWithLock(String cacheKey, String lockKey, 
                            CacheLoader<T> loader, long lockExpireSeconds, long cacheExpireSeconds) {
        // 1. 查缓存
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return (T) cached;
        }

        // 2. 尝试获取锁
        String fullLockKey = redisDatabase + ":" + LOCK_KEY + lockKey;
        String lockValue = System.currentTimeMillis() + "";
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(fullLockKey, lockValue, lockExpireSeconds, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(acquired)) {
            try {
                // 双重检查缓存
                cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    return (T) cached;
                }

                // 3. 查数据库
                T data = loader.load();
                
                // 4. 写入缓存
                if (data != null) {
                    redisTemplate.opsForValue().set(cacheKey, data, cacheExpireSeconds, TimeUnit.SECONDS);
                }
                
                return data;
            } finally {
                // 5. 释放锁
                // 只有持有锁的线程才能释放
                String currentLock = (String) redisTemplate.opsForValue().get(fullLockKey);
                if (lockValue.equals(currentLock)) {
                    redisTemplate.delete(fullLockKey);
                }
            }
        } else {
            // 获取锁失败，等待后重试
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // 递归重试（注意：生产环境建议使用循环重试+超时）
            return getWithLock(cacheKey, lockKey, loader, lockExpireSeconds, cacheExpireSeconds);
        }
    }

    /**
     * 简化版：带分布式锁的缓存查询
     */
    public <T> T getWithLock(String cacheKey, String lockKey, CacheLoader<T> loader) {
        return getWithLock(cacheKey, lockKey, loader, 10, 24 * 3600);
    }

    // ==================== 缓存雪崩保护 ====================

    /**
     * 带随机过期时间的缓存写入
     * 防止大量缓存同时过期导致雪崩
     */
    public <T> void setWithRandomExpire(String cacheKey, T data, long baseExpireSeconds) {
        // 随机过期时间：base ~ base * 1.5
        long randomExpire = (long) (baseExpireSeconds * (1 + Math.random() * 0.5));
        redisTemplate.opsForValue().set(cacheKey, data, randomExpire, TimeUnit.SECONDS);
        log.debug("写入缓存（随机过期）: cacheKey={}, expire={}s", cacheKey, randomExpire);
    }

    /**
     * 带随机过期时间的缓存写入（指定最小和最大过期时间）
     */
    public <T> void setWithRandomExpire(String cacheKey, T data, long minExpireSeconds, long maxExpireSeconds) {
        long randomExpire = minExpireSeconds + (long) (Math.random() * (maxExpireSeconds - minExpireSeconds));
        redisTemplate.opsForValue().set(cacheKey, data, randomExpire, TimeUnit.SECONDS);
        log.debug("写入缓存（随机过期）: cacheKey={}, expire={}s", cacheKey, randomExpire);
    }

    // ==================== 通用缓存操作 ====================

    /**
     * 删除缓存
     */
    public void delete(String cacheKey) {
        redisTemplate.delete(cacheKey);
        log.debug("删除缓存: cacheKey={}", cacheKey);
    }

    /**
     * 检查缓存是否存在
     */
    public boolean exists(String cacheKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey));
    }

    /**
     * 设置过期时间
     */
    public void expire(String cacheKey, long seconds) {
        redisTemplate.expire(cacheKey, seconds, TimeUnit.SECONDS);
    }

    /**
     * 数据加载器接口
     */
    @FunctionalInterface
    public interface CacheLoader<T> {
        T load();
    }
}
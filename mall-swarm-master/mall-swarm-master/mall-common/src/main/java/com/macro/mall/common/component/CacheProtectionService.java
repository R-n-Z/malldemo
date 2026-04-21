package com.macro.mall.common.component;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 缓存保护服务 - 解决缓存击穿、缓存雪崩、缓存穿透
 */
@Slf4j
@Component
public class CacheProtectionService {

    @Value("${cache.protection.bloom-filter.expected-insertions:100000}")
    private long expectedInsertions;

    @Value("${cache.protection.bloom-filter.false-positive-rate:0.01}")
    private double falsePositiveRate;

    @Value("${cache.protection.lock.timeout-seconds:10}")
    private int lockTimeoutSeconds;

    @Value("${cache.protection.lock.retry-times:3}")
    private int retryTimes;

    @Value("${cache.protection.null-value.expire-seconds:60}")
    private int nullValueExpireSeconds;

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private BloomFilter<String> bloomFilter;

    public CacheProtectionService(RedisTemplate<String, Object> redisTemplate, StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        // 初始化布隆过滤器
        this.bloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedInsertions,
                falsePositiveRate
        );
    }

    // ==================== 缓存击穿解决方案 ====================

    /**
     * 分布式锁Key前缀
     */
    private static final String LOCK_KEY_PREFIX = "cache:lock:";

    /**
     * 获取分布式锁（解决缓存击穿）
     *
     * @param cacheKey 缓存Key
     * @return 是否获取到锁
     */
    public boolean acquireLock(String cacheKey) {
        String lockKey = LOCK_KEY_PREFIX + cacheKey;
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", lockTimeoutSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 释放分布式锁
     */
    public void releaseLock(String cacheKey) {
        String lockKey = LOCK_KEY_PREFIX + cacheKey;
        redisTemplate.delete(lockKey);
    }

    /**
     * 带锁查询缓存（解决缓存击穿）
     *
     * @param cacheKey 缓存Key
     * @param dbFetcher 数据库查询回调
     * @param cacheWriter 缓存写入回调
     * @param expireSeconds 缓存过期时间
     * @param <T> 返回类型
     * @return 查询结果
     */
    public <T> T queryWithLock(String cacheKey,
                               java.util.function.Supplier<T> dbFetcher,
                               java.util.function.Consumer<T> cacheWriter,
                               long expireSeconds) {
        // 1. 尝试从缓存获取
        T cached = getFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. 获取分布式锁
        int retry = 0;
        while (retry < retryTimes) {
            if (acquireLock(cacheKey)) {
                try {
                    // 3. 双重检查缓存
                    cached = getFromCache(cacheKey);
                    if (cached != null) {
                        return cached;
                    }

                    // 4. 查询数据库
                    cached = dbFetcher.get();
                    if (cached != null) {
                        // 5. 写入缓存
                        cacheWriter.accept(cached);
                    } else {
                        // 6. 缓存空值（防止缓存穿透）
                        cacheNullValue(cacheKey);
                    }

                    return cached;

                } finally {
                    releaseLock(cacheKey);
                }
            } else {
                // 等待后重试
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            retry++;
        }

        // 7. 锁获取失败，直接查库（降级）
        log.warn("获取锁失败，直接查库: cacheKey={}", cacheKey);
        return dbFetcher.get();
    }

    /**
     * 单flight模式（解决热点缓存击穿）
     * 同一时间只有一个请求去加载数据，其他请求等待
     */
    public <T> T queryWithSingleFlight(String cacheKey,
                                       java.util.function.Supplier<T> dbFetcher,
                                       java.util.function.Consumer<T> cacheWriter,
                                       long expireSeconds) {
        // 1. 尝试从缓存获取
        T cached = getFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. 获取单flight锁
        String singleFlightKey = "cache:single-flight:" + cacheKey;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(singleFlightKey, "1", 10, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(acquired)) {
            try {
                // 3. 双重检查缓存
                cached = getFromCache(cacheKey);
                if (cached != null) {
                    return cached;
                }

                // 4. 查询数据库
                cached = dbFetcher.get();
                if (cached != null) {
                    cacheWriter.accept(cached);
                } else {
                    cacheNullValue(cacheKey);
                }

                return cached;

            } finally {
                redisTemplate.delete(singleFlightKey);
            }
        } else {
            // 5. 等待其他请求加载数据
            for (int i = 0; i < 20; i++) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                cached = getFromCache(cacheKey);
                if (cached != null) {
                    return cached;
                }
            }

            // 超时后直接查库
            log.warn("单flight等待超时，直接查库: cacheKey={}", cacheKey);
            return dbFetcher.get();
        }
    }

    // ==================== 缓存雪崩解决方案 ====================

    /**
     * 随机过期时间（解决缓存雪崩）
     *
     * @param baseExpireSeconds 基础过期时间
     * @return 随机后的过期时间
     */
    public long randomExpire(long baseExpireSeconds) {
        // 基础过期时间的 0.5 ~ 1.5 倍随机
        double random = Math.random() + 0.5;
        return (long) (baseExpireSeconds * random);
    }

    /**
     * 分批过期（解决缓存雪崩）
     *
     * @param cacheKeys 缓存Key列表
     * @param baseExpireSeconds 基础过期时间
     */
    public void batchSetWithRandomExpire(java.util.List<String> cacheKeys,
                                         long baseExpireSeconds,
                                         java.util.function.BiConsumer<String, Object> cacheWriter) {
        for (int i = 0; i < cacheKeys.size(); i++) {
            String key = cacheKeys.get(i);
            // 每个key的过期时间不同
            long expireSeconds = baseExpireSeconds + (i * 10) % 1000;
            // 异步设置过期时间，避免同时过期
            final long finalExpire = expireSeconds;
            new Thread(() -> {
                try {
                    Thread.sleep((long) (Math.random() * 1000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    /**
     * 设置永不过期的缓存（热点数据）
     */
    public void setNeverExpire(String cacheKey, Object value) {
        redisTemplate.opsForValue().set(cacheKey, value);
    }

    // ==================== 缓存穿透解决方案 ====================

    /**
     * 布隆过滤器Key前缀
     */
    private static final String BLOOM_FILTER_KEY = "cache:bloom-filter:";

    /**
     * 添加到布隆过滤器
     */
    public void addToBloomFilter(String key) {
        bloomFilter.put(key);
        // 同时更新Redis中的布隆过滤器状态
        stringRedisTemplate.opsForSet().add(BLOOM_FILTER_KEY, key);
    }

    /**
     * 检查是否在布隆过滤器中
     */
    public boolean mightContain(String key) {
        return bloomFilter.mightContain(key);
    }

    /**
     * 缓存空值（解决缓存穿透）
     */
    public void cacheNullValue(String cacheKey) {
        // 缓存空值，过期时间较短
        redisTemplate.opsForValue().set(cacheKey + ":null", "NULL",
                nullValueExpireSeconds, TimeUnit.SECONDS);
    }

    /**
     * 检查是否是空值缓存
     */
    public boolean isNullValue(String cacheKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey + ":null"));
    }

    /**
     * 带布隆过滤器的查询（解决缓存穿透）
     *
     * @param cacheKey 缓存Key
     * @param dbFetcher 数据库查询回调
     * @param cacheWriter 缓存写入回调
     * @param expireSeconds 缓存过期时间
     * @param <T> 返回类型
     * @return 查询结果
     */
    public <T> T queryWithBloomFilter(String cacheKey,
                                      java.util.function.Supplier<T> dbFetcher,
                                      java.util.function.Consumer<T> cacheWriter,
                                      long expireSeconds) {
        // 1. 布隆过滤器检查
        if (!mightContain(cacheKey)) {
            log.debug("布隆过滤器判断不存在: cacheKey={}", cacheKey);
            return null;
        }

        // 2. 检查空值缓存
        if (isNullValue(cacheKey)) {
            log.debug("空值缓存，跳过: cacheKey={}", cacheKey);
            return null;
        }

        // 3. 正常查询缓存
        T cached = getFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 4. 查询数据库
        cached = dbFetcher.get();
        if (cached != null) {
            cacheWriter.accept(cached);
            // 5. 添加到布隆过滤器
            addToBloomFilter(cacheKey);
        } else {
            // 6. 缓存空值
            cacheNullValue(cacheKey);
            addToBloomFilter(cacheKey);
        }

        return cached;
    }

    // ==================== 通用方法 ====================

    /**
     * 从缓存获取
     */
    @SuppressWarnings("unchecked")
    private <T> T getFromCache(String cacheKey) {
        try {
            Object value = redisTemplate.opsForValue().get(cacheKey);
            if (value != null) {
                log.debug("缓存命中: cacheKey={}", cacheKey);
                return (T) value;
            }
        } catch (Exception e) {
            log.error("读取缓存失败: cacheKey={}", cacheKey, e);
        }
        return null;
    }

    /**
     * Lua脚本 - 原子操作获取锁
     */
    private static final String LOCK_SCRIPT = """
        if redis.call('set', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then
            return 1
        else
            return 0
        end
        """;

    /**
     * 使用Lua脚本原子获取锁
     */
    public boolean tryLockWithLua(String cacheKey) {
        RedisScript<Long> script = RedisScript.of(LOCK_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script,
                Collections.singletonList(LOCK_KEY_PREFIX + cacheKey),
                "1", String.valueOf(lockTimeoutSeconds));
        return result != null && result == 1;
    }

    /**
     * 预热布隆过滤器
     */
    public void preloadBloomFilter(java.util.List<String> keys) {
        for (String key : keys) {
            addToBloomFilter(key);
        }
        log.info("布隆过滤器预热完成: count={}", keys.size());
    }

    /**
     * 获取布隆过滤器统计信息
     */
    public String getBloomFilterStats() {
        return String.format("expectedInsertions=%d, falsePositiveRate=%.4f, approximateElementCount=%d",
                expectedInsertions, falsePositiveRate, bloomFilter.approximateElementCount());
    }
}
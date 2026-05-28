package com.macro.mall.portal.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Redis限流器（支持多种限流算法）
 * 集成监控和告警功能
 */
@Slf4j
@Component
public class RedisRateLimiter {

    @Value("${redis.database:mall}")
    private String redisDatabase;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private MetricsCollector metricsCollector;

    @Autowired
    private AlertManager alertManager;

    // Rate limit key prefix
    private static final String RATE_LIMIT_PREFIX = "ratelimit:";
    private static final String RATE_LIMIT_SCRIPT_PATH = "lua/rate_limit.lua";
    private static final String SLIDING_WINDOW_SCRIPT_PATH = "lua/sliding_window.lua";
    private static final String TOKEN_BUCKET_SCRIPT_PATH = "lua/token_bucket.lua";

    private DefaultRedisScript<Long> rateLimitScript;
    private DefaultRedisScript<Long> slidingWindowScript;
    private DefaultRedisScript<Long> tokenBucketScript;

    @PostConstruct
    public void init() {
        rateLimitScript = new DefaultRedisScript<>(loadScript(RATE_LIMIT_SCRIPT_PATH), Long.class);
        slidingWindowScript = new DefaultRedisScript<>(loadScript(SLIDING_WINDOW_SCRIPT_PATH), Long.class);
        tokenBucketScript = new DefaultRedisScript<>(loadScript(TOKEN_BUCKET_SCRIPT_PATH), Long.class);
    }

    private String loadScript(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Lua script not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lua script: " + path, e);
        }
    }

    // 限流配置缓存
    private final Map<String, RateLimitConfig> configCache = new ConcurrentHashMap<>();

    /**
     * 限流算法类型
     */
    public enum LimitType {
        FIXED_WINDOW,      // 固定窗口
        SLIDING_WINDOW,    // 滑动窗口
        TOKEN_BUCKET,      // 令牌桶
        LEAKY_BUCKET       // 漏桶
    }

    /**
     * 限流配置
     */
    @lombok.Data
    public static class RateLimitConfig {
        private int limit;           // 限流阈值
        private int windowSeconds;   // 时间窗口（秒）
        private LimitType type;      // 限流算法
        private String apiPath;      // API路径

        public RateLimitConfig(int limit, int windowSeconds, LimitType type) {
            this.limit = limit;
            this.windowSeconds = windowSeconds;
            this.type = type;
        }
    }

    /**
     * 尝试获取限流许可
     * @param apiPath API路径
     * @param config 限流配置
     * @return true-允许通过, false-被限流
     */
    public boolean tryAcquire(String apiPath, RateLimitConfig config) {
        String key = redisDatabase + ":" + RATE_LIMIT_PREFIX + config.getType().name().toLowerCase() + ":" + apiPath;

        boolean allowed = false;
        switch (config.getType()) {
            case FIXED_WINDOW:
                allowed = tryFixedWindow(key, config);
                break;
            case SLIDING_WINDOW:
                allowed = trySlidingWindow(key, config);
                break;
            case TOKEN_BUCKET:
                allowed = tryTokenBucket(key, config, 1);
                break;
            case LEAKY_BUCKET:
                allowed = tryLeakyBucket(key, config);
                break;
        }

        // 记录限流指标
        if (!allowed) {
            metricsCollector.recordRateLimit(apiPath);
            log.warn("请求被限流: api={}, type={}, limit={}", apiPath, config.getType(), config.getLimit());
        }

        return allowed;
    }

    /**
     * 固定窗口限流
     */
    private boolean tryFixedWindow(String key, RateLimitConfig config) {
        if (rateLimitScript == null) {
            return true; // 脚本未加载，降级放行
        }
        try {
            Long result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    config.getLimit(),
                    config.getWindowSeconds()
            );
            return result != null && result == 1;
        } catch (Exception e) {
            log.error("固定窗口限流失败", e);
            return true; // 降级处理：允许通过
        }
    }

    /**
     * 滑动窗口限流
     */
    private boolean trySlidingWindow(String key, RateLimitConfig config) {
        if (slidingWindowScript == null) {
            return true;
        }
        try {
            Long result = redisTemplate.execute(
                    slidingWindowScript,
                    Collections.singletonList(key),
                    config.getLimit(),
                    config.getWindowSeconds() * 1000,
                    System.currentTimeMillis()
            );
            return result != null && result == 1;
        } catch (Exception e) {
            log.error("滑动窗口限流失败", e);
            return true;
        }
    }

    /**
     * 令牌桶限流
     */
    private boolean tryTokenBucket(String key, RateLimitConfig config, int tokens) {
        if (tokenBucketScript == null) {
            return true;
        }
        try {
            // 计算每秒添加的令牌数
            int rate = config.getLimit() / config.getWindowSeconds();
            if (rate < 1) {
                rate = 1; // ���少每秒1个令牌
            }

            Long result = redisTemplate.execute(
                    tokenBucketScript,
                    Collections.singletonList(key),
                    rate,
                    config.getLimit(),
                    System.currentTimeMillis(),
                    tokens
            );
            return result != null && result == 1;
        } catch (Exception e) {
            log.error("令牌桶限流失败", e);
            return true;
        }
    }

    /**
     * 漏桶限流（使用固定窗口+队列模拟）
     */
    private boolean tryLeakyBucket(String key, RateLimitConfig config) {
        // 漏桶使用固定窗口实现，限制处理速率
        return tryFixedWindow(key, config);
    }

    /**
     * 批量获取令牌
     * @param apiPath API路径
     * @param config 限流配置
     * @param tokens 需要的令牌数
     * @return true-允许通过, false-被限流
     */
    public boolean tryAcquireTokens(String apiPath, RateLimitConfig config, int tokens) {
        if (config.getType() != LimitType.TOKEN_BUCKET) {
            return tryAcquire(apiPath, config);
        }

        String key = redisDatabase + ":" + RATE_LIMIT_PREFIX + "token:" + apiPath;
        return tryTokenBucket(key, config, tokens);
    }

    /**
     * AOP限流切面适配：固定窗口
     */
    public boolean fixedWindow(String key, int windowSeconds, int maxRequests) {
        RateLimitConfig config = new RateLimitConfig(maxRequests, windowSeconds, LimitType.FIXED_WINDOW);
        return tryAcquire(key, config);
    }

    /**
     * AOP限流切面适配：滑动窗口
     */
    public boolean slidingWindow(String key, int windowSeconds, int maxRequests) {
        RateLimitConfig config = new RateLimitConfig(maxRequests, windowSeconds, LimitType.SLIDING_WINDOW);
        return tryAcquire(key, config);
    }

    /**
     * AOP限流切面适配：令牌桶
     */
    public boolean tokenBucket(String key, int ratePerSecond, int capacity, int tokens) {
        // 令牌桶脚本内部以 (rate, capacity) 生效；这里把 windowSeconds 固定为1 仅用于复用配置结构
        RateLimitConfig config = new RateLimitConfig(capacity, 1, LimitType.TOKEN_BUCKET);
        String redisKey = redisDatabase + ":" + RATE_LIMIT_PREFIX + "token:" + key;
        try {
            Long result = redisTemplate.execute(
                    tokenBucketScript,
                    Collections.singletonList(redisKey),
                    Math.max(1, ratePerSecond),
                    capacity,
                    System.currentTimeMillis(),
                    tokens
            );
            return result != null && result == 1;
        } catch (Exception e) {
            log.error("令牌桶限流失败", e);
            return true;
        }
    }

    /**
     * 获取当前剩余配额
     */
    public long getRemainingQuota(String apiPath, RateLimitConfig config) {
        String key = redisDatabase + ":" + RATE_LIMIT_PREFIX + config.getType().name().toLowerCase() + ":" + apiPath;

        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return config.getLimit();
            }
            long used = Long.parseLong(value.toString());
            return Math.max(0, config.getLimit() - used);
        } catch (Exception e) {
            log.error("获取剩余配额失败", e);
            return config.getLimit();
        }
    }

    /**
     * 重置限流计数器
     */
    public void reset(String apiPath, LimitType type) {
        String key = redisDatabase + ":" + RATE_LIMIT_PREFIX + type.name().toLowerCase() + ":" + apiPath;
        redisTemplate.delete(key);
    }

    /**
     * 添加限流配置
     */
    public void addConfig(String apiPath, RateLimitConfig config) {
        configCache.put(apiPath, config);
    }

    /**
     * 移除限流配置
     */
    public void removeConfig(String apiPath) {
        configCache.remove(apiPath);
    }

    /**
     * 获取限流配置
     */
    public RateLimitConfig getConfig(String apiPath) {
        return configCache.get(apiPath);
    }
}
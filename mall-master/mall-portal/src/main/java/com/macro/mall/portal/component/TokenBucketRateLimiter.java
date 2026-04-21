package com.macro.mall.portal.component;

import com.macro.mall.portal.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 令牌桶限流器
 * 基于Redis实现，支持分布式环境下的限流
 */
@Slf4j
@Component
public class TokenBucketRateLimiter {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${redis.database:mall}")
    private String redisDatabase;

    private static final String TOKEN_BUCKET_SCRIPT_PATH = "lua/token_bucket.lua";
    private static final String SLIDING_WINDOW_SCRIPT_PATH = "lua/sliding_window_rate_limit.lua";

    /**
     * Load Lua script from resources
     */
    private String loadScript(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Lua script not found: " + path);
            }
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            return result.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lua script: " + path, e);
        }
    }

    /**
     * 尝试获取令牌
     * @param key 限流key（如：用户ID、IP、接口路径）
     * @param rate 令牌产生速率（个/秒）
     * @param capacity 令牌桶容量
     * @param tokensNeeded 需要的令牌数量
     * @return true=获取成功, false=被限流
     * @throws RateLimitException 限流异常，包含等待时间
     */
    public boolean tryAcquire(String key, int rate, int capacity, int tokensNeeded) {
        String bucketKey = redisDatabase + ":ratelimit:bucket:" + key;
        String timeKey = redisDatabase + ":ratelimit:time:" + key;
        long now = System.currentTimeMillis();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(loadScript(TOKEN_BUCKET_SCRIPT_PATH));
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(
                script,
                java.util.Arrays.asList(bucketKey, timeKey),
                rate, capacity, now, tokensNeeded
        );

        if (result == null) {
            log.error("限流脚本执行失败: key={}", key);
            return true; // 降级：允许通过
        }

        if (result == 1) {
            log.debug("限流通过: key={}, rate={}, capacity={}", key, rate, capacity);
            return true;
        } else {
            log.warn("限流拦截: key={}, waitTime={}ms", key, result);
            throw new RateLimitException("请求过于频繁，请稍后再试", result);
        }
    }

    /**
     * 尝试获取1个令牌（默认）
     */
    public boolean tryAcquire(String key, int rate, int capacity) {
        return tryAcquire(key, rate, capacity, 1);
    }

    /**
     * 根据用户ID限流
     */
    public boolean tryAcquireByMemberId(Long memberId, String action, int rate, int capacity) {
        String key = "member:" + memberId + ":" + action;
        return tryAcquire(key, rate, capacity);
    }

    /**
     * 根据IP地址限流
     */
    public boolean tryAcquireByIp(String ip, String action, int rate, int capacity) {
        String key = "ip:" + ip + ":" + action;
        return tryAcquire(key, rate, capacity);
    }

    /**
     * 根据接口路径限流（全局限流）
     */
    public boolean tryAcquireByPath(String path, int rate, int capacity) {
        String key = "path:" + path;
        return tryAcquire(key, rate, capacity);
    }

    

    /**
     * 滑动窗口限流
     */
    public boolean slidingWindowTryAcquire(String key, int windowSeconds, int maxRequests) {
        String windowKey = redisDatabase + ":ratelimit:window:" + key;
        long now = System.currentTimeMillis();
        String requestId = now + ":" + java.util.UUID.randomUUID().toString().substring(0, 8);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(loadScript(SLIDING_WINDOW_SCRIPT_PATH));
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(
                script,
                Collections.singletonList(windowKey),
                windowSeconds, maxRequests, now, requestId
        );

        return result != null && result == 1;
    }

    /**
     * 获取当前令牌桶剩余令牌数
     */
    public long getRemainingTokens(String key, int rate, int capacity) {
        String bucketKey = redisDatabase + ":ratelimit:bucket:" + key;
        String timeKey = redisDatabase + ":ratelimit:time:" + key;
        long now = System.currentTimeMillis();

        Object tokensObj = redisTemplate.opsForValue().get(bucketKey);
        Object lastTimeObj = redisTemplate.opsForValue().get(timeKey);

        if (tokensObj == null) {
            return capacity;
        }

        double tokens = Double.parseDouble(tokensObj.toString());
        if (lastTimeObj != null) {
            double lastTime = Double.parseDouble(lastTimeObj.toString());
            double elapsed = (now - lastTime) / 1000.0;
            tokens = Math.min(capacity, tokens + elapsed * rate);
        }

        return (long) tokens;
    }
}
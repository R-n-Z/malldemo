package com.macro.mall.portal.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指标收集器
 * 收集和存储系统运行指标（QPS、响应时间、错误率等）
 */
@Slf4j
@Component
public class MetricsCollector {

    @Value("${redis.database:mall}")
    private String redisDatabase;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 内存指标（实时数据）
    private final Map<String, AtomicLong> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> successCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> responseTimes = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> rateLimitCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> circuitBreakCounts = new ConcurrentHashMap<>();

    // 指标Key前缀
    private static final String METRICS_PREFIX = "metrics:";
    private static final String REQUEST_COUNT_KEY = "request:count:";
    private static final String SUCCESS_COUNT_KEY = "success:count:";
    private static final String ERROR_COUNT_KEY = "error:count:";
    private static final String RESPONSE_TIME_KEY = "response:time:";
    private static final String RATE_LIMIT_KEY = "ratelimit:count:";
    private static final String CIRCUIT_BREAK_KEY = "circuitbreak:count:";

    // ==================== 1. 请求指标 ====================

    /**
     * 记录请求
     */
    public void recordRequest(String apiPath) {
        // 内存计数
        requestCounts.computeIfAbsent(apiPath, k -> new AtomicLong(0)).incrementAndGet();
        
        // Redis存储（按分钟）
        String key = redisDatabase + ":" + METRICS_PREFIX + REQUEST_COUNT_KEY + getMinuteKey();
        redisTemplate.opsForZSet().incrementScore(key, apiPath, 1);
        redisTemplate.expire(key, Duration.ofMinutes(10));
    }

    /**
     * 记录成功响应
     */
    public void recordSuccess(String apiPath, long responseTimeMs) {
        // 内存计数
        successCounts.computeIfAbsent(apiPath, k -> new AtomicLong(0)).incrementAndGet();
        
        // 记录响应时间
        recordResponseTime(apiPath, responseTimeMs);
        
        // Redis存储
        String key = redisDatabase + ":" + METRICS_PREFIX + SUCCESS_COUNT_KEY + getMinuteKey();
        redisTemplate.opsForZSet().incrementScore(key, apiPath, 1);
        redisTemplate.expire(key, Duration.ofMinutes(10));
    }

    /**
     * 记录错误
     */
    public void recordError(String apiPath, String errorType) {
        // 内存计数
        errorCounts.computeIfAbsent(apiPath, k -> new AtomicLong(0)).incrementAndGet();
        
        // Redis存储
        String key = redisDatabase + ":" + METRICS_PREFIX + ERROR_COUNT_KEY + getMinuteKey();
        String errorKey = apiPath + ":" + errorType;
        redisTemplate.opsForZSet().incrementScore(key, errorKey, 1);
        redisTemplate.expire(key, Duration.ofMinutes(10));
    }

    /**
     * 记录响应时间
     */
    private void recordResponseTime(String apiPath, long timeMs) {
        String key = redisDatabase + ":" + METRICS_PREFIX + RESPONSE_TIME_KEY + getMinuteKey();
        redisTemplate.opsForZSet().incrementScore(key, apiPath, timeMs);
        redisTemplate.expire(key, Duration.ofMinutes(10));
    }

    /**
     * 记录限流
     */
    public void recordRateLimit(String apiPath) {
        rateLimitCounts.computeIfAbsent(apiPath, k -> new AtomicLong(0)).incrementAndGet();
        
        String key = redisDatabase + ":" + METRICS_PREFIX + RATE_LIMIT_KEY + getMinuteKey();
        redisTemplate.opsForZSet().incrementScore(key, apiPath, 1);
        redisTemplate.expire(key, Duration.ofMinutes(10));
    }

    /**
     * 记录熔断
     */
    public void recordCircuitBreak(String apiPath) {
        circuitBreakCounts.computeIfAbsent(apiPath, k -> new AtomicLong(0)).incrementAndGet();
        
        String key = redisDatabase + ":" + METRICS_PREFIX + CIRCUIT_BREAK_KEY + getMinuteKey();
        redisTemplate.opsForZSet().incrementScore(key, apiPath, 1);
        redisTemplate.expire(key, Duration.ofMinutes(10));
    }

    // ==================== 2. 指标查询 ====================

    /**
     * 获取实时QPS
     */
    public Map<String, Double> getQPS() {
        Map<String, Double> qpsMap = new HashMap<>();
        String key = redisDatabase + ":" + METRICS_PREFIX + REQUEST_COUNT_KEY + getMinuteKey();
        
        Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
                String apiPath = tuple.getValue().toString();
                Double score = tuple.getScore();
                if (score != null) {
                    qpsMap.put(apiPath, score);  // 当前分钟的请求数 ≈ QPS
                }
            }
        }
        
        return qpsMap;
    }

    /**
     * 获取错误率
     */
    public Map<String, Double> getErrorRate() {
        Map<String, Double> errorRateMap = new HashMap<>();
        String requestKey = redisDatabase + ":" + METRICS_PREFIX + REQUEST_COUNT_KEY + getMinuteKey();
        String errorKey = redisDatabase + ":" + METRICS_PREFIX + ERROR_COUNT_KEY + getMinuteKey();
        
        Set<ZSetOperations.TypedTuple<Object>> requests = redisTemplate.opsForZSet().rangeWithScores(requestKey, 0, -1);
        Set<ZSetOperations.TypedTuple<Object>> errors = redisTemplate.opsForZSet().rangeWithScores(errorKey, 0, -1);
        
        if (requests != null) {
            Map<String, Double> errorMap = new HashMap<>();
            if (errors != null) {
                for (ZSetOperations.TypedTuple<Object> tuple : errors) {
                    String fullKey = tuple.getValue().toString();
                    Double score = tuple.getScore();
                    if (score != null && fullKey.contains(":")) {
                        String apiPath = fullKey.split(":")[0];
                        errorMap.put(apiPath, errorMap.getOrDefault(apiPath, 0.0) + score);
                    }
                }
            }
            
            for (ZSetOperations.TypedTuple<Object> tuple : requests) {
                String apiPath = tuple.getValue().toString();
                Double requestCount = tuple.getScore();
                if (requestCount != null && requestCount > 0) {
                    Double errorCount = errorMap.getOrDefault(apiPath, 0.0);
                    errorRateMap.put(apiPath, errorCount / requestCount * 100);
                }
            }
        }
        
        return errorRateMap;
    }

    /**
     * 获取平均响应时间
     */
    public Map<String, Double> getAvgResponseTime() {
        Map<String, Double> avgTimeMap = new HashMap<>();
        String requestKey = redisDatabase + ":" + METRICS_PREFIX + REQUEST_COUNT_KEY + getMinuteKey();
        String timeKey = redisDatabase + ":" + METRICS_PREFIX + RESPONSE_TIME_KEY + getMinuteKey();
        
        Set<ZSetOperations.TypedTuple<Object>> requests = redisTemplate.opsForZSet().rangeWithScores(requestKey, 0, -1);
        Set<ZSetOperations.TypedTuple<Object>> times = redisTemplate.opsForZSet().rangeWithScores(timeKey, 0, -1);
        
        if (requests != null) {
            Map<String, Double> timeMap = new HashMap<>();
            if (times != null) {
                for (ZSetOperations.TypedTuple<Object> tuple : times) {
                    String apiPath = tuple.getValue().toString();
                    Double score = tuple.getScore();
                    if (score != null) {
                        timeMap.put(apiPath, score);
                    }
                }
            }
            
            for (ZSetOperations.TypedTuple<Object> tuple : requests) {
                String apiPath = tuple.getValue().toString();
                Double requestCount = tuple.getScore();
                if (requestCount != null && requestCount > 0) {
                    Double totalTime = timeMap.getOrDefault(apiPath, 0.0);
                    avgTimeMap.put(apiPath, totalTime / requestCount);
                }
            }
        }
        
        return avgTimeMap;
    }

    /**
     * 获取限流次数
     */
    public Map<String, Double> getRateLimitCount() {
        Map<String, Double> countMap = new HashMap<>();
        String key = redisDatabase + ":" + METRICS_PREFIX + RATE_LIMIT_KEY + getMinuteKey();
        
        Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
                String apiPath = tuple.getValue().toString();
                Double score = tuple.getScore();
                if (score != null) {
                    countMap.put(apiPath, score);
                }
            }
        }
        
        return countMap;
    }

    /**
     * 获取熔断次数
     */
    public Map<String, Double> getCircuitBreakCount() {
        Map<String, Double> countMap = new HashMap<>();
        String key = redisDatabase + ":" + METRICS_PREFIX + CIRCUIT_BREAK_KEY + getMinuteKey();
        
        Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
                String apiPath = tuple.getValue().toString();
                Double score = tuple.getScore();
                if (score != null) {
                    countMap.put(apiPath, score);
                }
            }
        }
        
        return countMap;
    }

    /**
     * 获取完整监控数据
     */
    public Map<String, Object> getAllMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("timestamp", System.currentTimeMillis());
        metrics.put("qps", getQPS());
        metrics.put("errorRate", getErrorRate());
        metrics.put("avgResponseTime", getAvgResponseTime());
        metrics.put("rateLimitCount", getRateLimitCount());
        metrics.put("circuitBreakCount", getCircuitBreakCount());
        return metrics;
    }

    // ==================== 3. 辅助方法 ====================

    /**
     * 获取当前分钟Key
     */
    private String getMinuteKey() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
    }

    /**
     * 获取当前秒Key
     */
    private String getSecondKey() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    /**
     * 清空内存指标（每分钟调用）
     */
    public void clearMemoryMetrics() {
        requestCounts.clear();
        successCounts.clear();
        errorCounts.clear();
        responseTimes.clear();
        rateLimitCounts.clear();
        circuitBreakCounts.clear();
    }
}
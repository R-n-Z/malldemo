package com.macro.mall.portal.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 熔断器组件
 * 实现Hystrix风格的熔断器，支持慢调用比例和异常比例熔断
 */
@Slf4j
@Component
public class CircuitBreaker {

    @Value("${redis.database:mall}")
    private String redisDatabase;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private MetricsCollector metricsCollector;

    @Autowired
    private AlertManager alertManager;

    // 熔断器Key前缀
    private static final String CIRCUIT_BREAKER_PREFIX = "circuitbreaker:";

    // 熔断器状态
    public enum State {
        CLOSED,     // 关闭（正常）
        OPEN,       // 打开（熔断）
        HALF_OPEN   // 半开（尝试恢复）
    }

    // 熔断策略
    public enum Strategy {
        SLOW_REQUEST_RATIO,  // 慢调用比例
        ERROR_RATIO,         // 异常比例
        ERROR_COUNT          // 异常数量
    }

    /**
     * 熔断器配置
     */
    @lombok.Data
    public static class Config {
        private String name;                    // 熔断器名称
        private Strategy strategy;              // 熔断策略
        private int slowRequestThreshold;       // 慢调用阈值（毫秒）
        private double slowRatioThreshold;      // 慢调用比例阈值（0-1）
        private double errorRatioThreshold;     // 异常比例阈值（0-1）
        private int errorCountThreshold;        // 异常数量阈值
        private int minRequestAmount;           // 最小请求数
        private int retryAttempts;              // 半开状态下的重试次数
        private int waitDuration;               // 熔断持续时间（秒）

        public Config(String name) {
            this.name = name;
            this.strategy = Strategy.ERROR_RATIO;
            this.slowRequestThreshold = 1000;
            this.slowRatioThreshold = 0.5;
            this.errorRatioThreshold = 0.5;
            this.errorCountThreshold = 10;
            this.minRequestAmount = 10;
            this.retryAttempts = 3;
            this.waitDuration = 30;
        }
    }

    /**
     * 熔断器实例
     */
    @lombok.Data
    public static class Breaker {
        private String name;
        private State state;
        private long lastStateChangeTime;
        private int successCount;
        private int failureCount;
        private int slowCount;
        private int totalCount;
        private long slowTotalTime;
    }

    // 熔断器配置缓存
    private final Map<String, Config> configCache = new ConcurrentHashMap<>();

    // 熔断器状态缓存
    private final Map<String, Breaker> breakerCache = new ConcurrentHashMap<>();

    /**
     * 获取或创建熔断器
     */
    public Breaker getOrCreate(String name) {
        return breakerCache.computeIfAbsent(name, k -> {
            Breaker breaker = new Breaker();
            breaker.setName(name);
            breaker.setState(State.CLOSED);
            breaker.setLastStateChangeTime(System.currentTimeMillis());
            return breaker;
        });
    }

    /**
     * 获取熔断器配置
     */
    public Config getConfig(String name) {
        return configCache.computeIfAbsent(name, k -> new Config(k));
    }

    /**
     * 设置熔断器配置
     */
    public void setConfig(String name, Config config) {
        configCache.put(name, config);
    }

    /**
     * 执行熔断逻辑
     * @param name 熔断器名称
     * @param operation 操作（带返回值的lambda）
     * @param fallback 降级操作（可选）
     * @return 操作结果
     */
    public <T> T execute(String name, java.util.function.Supplier<T> operation, 
                         java.util.function.Supplier<T> fallback) {
        Breaker breaker = getOrCreate(name);
        Config config = getConfig(name);

        // 检查熔断状态
        if (breaker.getState() == State.OPEN) {
            // 检查是否到达半开时间
            if (System.currentTimeMillis() - breaker.getLastStateChangeTime() > config.getWaitDuration() * 1000L) {
                transitionTo(breaker, State.HALF_OPEN);
            } else {
                // 熔断中，记录熔断指标
                metricsCollector.recordCircuitBreak(name);
                log.warn("熔断器打开，拒绝请求: {}", name);
                
                if (fallback != null) {
                    return fallback.get();
                }
                throw new CircuitBreakerOpenException("熔断器已打开: " + name);
            }
        }

        try {
            T result = operation.get();
            onSuccess(breaker, config);
            return result;
        } catch (Exception e) {
            onFailure(breaker, config, e);
            if (fallback != null) {
                return fallback.get();
            }
            throw e;
        }
    }

    /**
     * 执行无返回值的操作
     */
    public void execute(String name, Runnable operation, Runnable fallback) {
        execute(name, () -> {
            operation.run();
            return null;
        }, fallback != null ? () -> {
            fallback.run();
            return null;
        } : null);
    }

    /**
     * 成功回调
     */
    private void onSuccess(Breaker breaker, Config config) {
        breaker.setSuccessCount(breaker.getSuccessCount() + 1);
        breaker.setTotalCount(breaker.getTotalCount() + 1);

        // 半开状态下成功，尝试关闭熔断器
        if (breaker.getState() == State.HALF_OPEN) {
            if (breaker.getSuccessCount() >= config.getRetryAttempts()) {
                transitionTo(breaker, State.CLOSED);
                log.info("熔断器关闭: {}", breaker.getName());
            }
        }

        saveBreakerState(breaker);
    }

    /**
     * 失败回调
     */
    private void onFailure(Breaker breaker, Config config, Exception e) {
        breaker.setFailureCount(breaker.getFailureCount() + 1);
        breaker.setTotalCount(breaker.getTotalCount() + 1);

        // 检查是否需要熔断
        if (shouldOpen(breaker, config)) {
            transitionTo(breaker, State.OPEN);
            log.warn("熔断器打开: {}, failureCount={}, totalCount={}", 
                    breaker.getName(), breaker.getFailureCount(), breaker.getTotalCount());
            
            // 触发告警
            alertManager.openCircuitBreaker(breaker.getName());
        } else if (breaker.getState() == State.HALF_OPEN) {
            // 半开状态下失败，重新打开熔断器
            transitionTo(breaker, State.OPEN);
        }

        saveBreakerState(breaker);
    }

    /**
     * 记录慢调用
     */
    public void recordSlow(String name, long responseTime) {
        Breaker breaker = getOrCreate(name);
        Config config = getConfig(name);

        breaker.setSlowCount(breaker.getSlowCount() + 1);
        breaker.setTotalCount(breaker.getTotalCount() + 1);
        breaker.setSlowTotalTime(breaker.getSlowTotalTime() + responseTime);

        // 检查慢��用比例
        if (config.getStrategy() == Strategy.SLOW_REQUEST_RATIO) {
            if (breaker.getTotalCount() >= config.getMinRequestAmount()) {
                double slowRatio = (double) breaker.getSlowCount() / breaker.getTotalCount();
                if (slowRatio >= config.getSlowRatioThreshold()) {
                    transitionTo(breaker, State.OPEN);
                    log.warn("熔断器打开（慢调用比例）: {}, slowRatio={}", 
                            breaker.getName(), slowRatio);
                    alertManager.openCircuitBreaker(breaker.getName());
                }
            }
        }

        saveBreakerState(breaker);
    }

    /**
     * 判断是否应该打开熔断器
     */
    private boolean shouldOpen(Breaker breaker, Config config) {
        if (breaker.getTotalCount() < config.getMinRequestAmount()) {
            return false;
        }

        switch (config.getStrategy()) {
            case ERROR_RATIO:
                double errorRatio = (double) breaker.getFailureCount() / breaker.getTotalCount();
                return errorRatio >= config.getErrorRatioThreshold();
            case ERROR_COUNT:
                return breaker.getFailureCount() >= config.getErrorCountThreshold();
            default:
                return false;
        }
    }

    /**
     * 状态转换
     */
    private void transitionTo(Breaker breaker, State newState) {
        State oldState = breaker.getState();
        breaker.setState(newState);
        breaker.setLastStateChangeTime(System.currentTimeMillis());

        // 重置计数器
        if (newState == State.CLOSED) {
            breaker.setSuccessCount(0);
            breaker.setFailureCount(0);
            breaker.setSlowCount(0);
            breaker.setTotalCount(0);
            breaker.setSlowTotalTime(0);
        } else if (newState == State.HALF_OPEN) {
            breaker.setSuccessCount(0);
            breaker.setFailureCount(0);
        }

        log.info("熔断器状态变更: {} {} -> {}", breaker.getName(), oldState, newState);
        saveBreakerState(breaker);
    }

    /**
     * 获取熔断器状态
     */
    public Breaker getState(String name) {
        return getOrCreate(name);
    }

    /**
     * 手动打开熔断器
     */
    public void open(String name) {
        Breaker breaker = getOrCreate(name);
        transitionTo(breaker, State.OPEN);
        alertManager.openCircuitBreaker(name);
    }

    /**
     * 手动关闭熔断器
     */
    public void close(String name) {
        Breaker breaker = getOrCreate(name);
        transitionTo(breaker, State.CLOSED);
        alertManager.closeCircuitBreaker();
    }

    /**
     * 重置熔断器
     */
    public void reset(String name) {
        Breaker breaker = getOrCreate(name);
        breaker.setState(State.CLOSED);
        breaker.setSuccessCount(0);
        breaker.setFailureCount(0);
        breaker.setSlowCount(0);
        breaker.setTotalCount(0);
        breaker.setSlowTotalTime(0);
        breaker.setLastStateChangeTime(System.currentTimeMillis());
        saveBreakerState(breaker);
    }

    /**
     * 保存熔断器状态到Redis
     */
    private void saveBreakerState(Breaker breaker) {
        String key = redisDatabase + ":" + CIRCUIT_BREAKER_PREFIX + breaker.getName();
        Map<String, Object> state = new ConcurrentHashMap<>();
        state.put("state", breaker.getState().name());
        state.put("lastStateChangeTime", breaker.getLastStateChangeTime());
        state.put("successCount", breaker.getSuccessCount());
        state.put("failureCount", breaker.getFailureCount());
        state.put("slowCount", breaker.getSlowCount());
        state.put("totalCount", breaker.getTotalCount());
        state.put("slowTotalTime", breaker.getSlowTotalTime());

        redisTemplate.opsForHash().putAll(key, state);
        redisTemplate.expire(key, Duration.ofHours(1));
    }

    /**
     * 从Redis加载熔断器状态
     */
    public void loadState(String name) {
        String key = redisDatabase + ":" + CIRCUIT_BREAKER_PREFIX + name;
        Map<Object, Object> state = redisTemplate.opsForHash().entries(key);
        if (state.isEmpty()) {
            return;
        }

        Breaker breaker = getOrCreate(name);
        breaker.setState(State.valueOf((String) state.get("state")));
        breaker.setLastStateChangeTime(Long.parseLong(state.get("lastStateChangeTime").toString()));
        breaker.setSuccessCount(Integer.parseInt(state.get("successCount").toString()));
        breaker.setFailureCount(Integer.parseInt(state.get("failureCount").toString()));
        breaker.setSlowCount(Integer.parseInt(state.get("slowCount").toString()));
        breaker.setTotalCount(Integer.parseInt(state.get("totalCount").toString()));
        breaker.setSlowTotalTime(Long.parseLong(state.get("slowTotalTime").toString()));
    }

    /**
     * 熔断器打开异常
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
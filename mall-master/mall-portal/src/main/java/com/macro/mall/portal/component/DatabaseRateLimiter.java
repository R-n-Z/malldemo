package com.macro.mall.portal.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据库层保护组件
 * 提供SQL限流和熔断保护
 */
@Slf4j
@Component
public class DatabaseRateLimiter {

    @Value("${redis.database:mall}")
    private String redisDatabase;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DataSource dataSource;

    // ==================== 1. SQL限流 ====================

    /**
     * SQL限流Lua脚本
     * 根据SQL类型进行限流
     */
    private static final String SQL_RATE_LIMIT_SCRIPT =
            "-- KEYS[1]: SQL类型计数器key\n"
                    + "-- ARGV[1]: 限流阈值（每分钟最大请求数）\n"
                    + "-- ARGV[2]: 当前时间戳（分钟）\n"
                    + "\n"
                    + "local key = KEYS[1]\n"
                    + "local limit = tonumber(ARGV[1])\n"
                    + "local nowMinute = tonumber(ARGV[2])\n"
                    + "\n"
                    + "-- 获取当前请求数\n"
                    + "local current = tonumber(redis.call('GET', key) or 0)\n"
                    + "\n"
                    + "if current < limit then\n"
                    + "    redis.call('INCR', key)\n"
                    + "    redis.call('EXPIRE', key, 120)  -- 2分钟过期\n"
                    + "    return 1  -- 通过\n"
                    + "end\n"
                    + "\n"
                    + "return 0  -- 限流\n";

    /**
     * SQL类型限流配置
     */
    public enum SqlType {
        SELECT(100),      // SELECT限流：每分钟100次
        INSERT(50),       // INSERT限流：每分钟50次
        UPDATE(30),       // UPDATE限流：每分钟30次
        DELETE(10),       // DELETE限流：每分钟10次
        COMPLEX_SELECT(20);  // 复杂SELECT限流：每分钟20次

        private final int limitPerMinute;

        SqlType(int limitPerMinute) {
            this.limitPerMinute = limitPerMinute;
        }

        public int getLimitPerMinute() {
            return limitPerMinute;
        }
    }

    /**
     * SQL限流检查
     * @param sql 原始SQL
     * @return true=通过, false=限流
     */
    public boolean tryAcquire(String sql) {
        SqlType sqlType = classifySql(sql);
        String key = redisDatabase + ":sql:rate:" + sqlType.name();
        long nowMinute = System.currentTimeMillis() / 60000;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(SQL_RATE_LIMIT_SCRIPT);
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(
                script,
                Collections.<String>singletonList(key),
                sqlType.getLimitPerMinute(),
                nowMinute
        );

        if (result == null || result == 0) {
            log.warn("SQL限流拦截: type={}, sql={}", sqlType, sql);
            return false;
        }

        return true;
    }

    /**
     * SQL分类
     */
    private SqlType classifySql(String sql) {
        String upperSql = sql.toUpperCase().trim();
        
        if (upperSql.startsWith("SELECT")) {
            // 检查是否是复杂查询
            if (upperSql.contains("JOIN") || 
                upperSql.contains("UNION") || 
                upperSql.contains("COUNT(") ||
                upperSql.contains("GROUP BY") ||
                upperSql.contains("ORDER BY")) {
                return SqlType.COMPLEX_SELECT;
            }
            return SqlType.SELECT;
        } else if (upperSql.startsWith("INSERT")) {
            return SqlType.INSERT;
        } else if (upperSql.startsWith("UPDATE")) {
            return SqlType.UPDATE;
        } else if (upperSql.startsWith("DELETE")) {
            return SqlType.DELETE;
        }
        
        return SqlType.SELECT;
    }

    // ==================== 2. 数据库熔断器 ====================

    /**
     * 熔断器状态
     */
    public enum CircuitState {
        CLOSED,   // 关闭，正常运行
        OPEN,     // 打开，拒绝所有请求
        HALF_OPEN // 半开，允许部分请求
    }

    /**
     * 熔断器配置
     */
    @lombok.Data
    public static class CircuitBreakerConfig {
        private int failureThreshold = 10;       // 失败次数阈值
        private int successThreshold = 3;        // 半开状态成功次数阈值
        private long openTimeoutSeconds = 30;    // 打开状态持续时间
        private int sampleWindowSeconds = 60;    // 采样窗口大小
        private long slowQueryThreshold = 3000;  // 慢查询阈值（毫秒）
    }

    // 熔断器状态存储
    private volatile CircuitState state = CircuitState.CLOSED;
    private volatile long lastFailureTime = 0;
    private volatile long openTime = 0;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);

    /**
     * 检查是否允许执行SQL
     * @param config 熔断器配置
     * @return true=允许执行, false=熔断中
     */
    public boolean allowRequest(CircuitBreakerConfig config) {
        switch (state) {
            case OPEN:
                // 检查是否超时
                if (System.currentTimeMillis() - openTime > config.getOpenTimeoutSeconds() * 1000) {
                    // 超时，切换到半开状态
                    state = CircuitState.HALF_OPEN;
                    failureCount.set(0);
                    successCount.set(0);
                    log.info("数据库熔断器: OPEN -> HALF_OPEN");
                    return true;
                }
                log.warn("数据库熔断器: 熔断中，拒绝请求");
                return false;
                
            case HALF_OPEN:
                // 半开状态，允许部分请求
                return true;
                
            case CLOSED:
            default:
                return true;
        }
    }

    /**
     * 记录SQL执行结果
     * @param config 熔断器配置
     * @param sql 执行的SQL
     * @param executeTime 执行时间（毫秒）
     * @param success 是否成功
     */
    public void recordResult(CircuitBreakerConfig config, String sql, long executeTime, boolean success) {
        if (state == CircuitState.OPEN) {
            return;
        }

        // 检查慢查询
        if (executeTime > config.getSlowQueryThreshold()) {
            log.warn("慢查询检测: sql={}, time={}ms", sql, executeTime);
            success = false;
        }

        if (success) {
            successCount.incrementAndGet();
            
            if (state == CircuitState.HALF_OPEN && 
                successCount.get() >= config.getSuccessThreshold()) {
                // 半开状态下连续成功，关闭熔断
                state = CircuitState.CLOSED;
                failureCount.set(0);
                successCount.set(0);
                log.info("数据库熔断器: HALF_OPEN -> CLOSED");
            }
        } else {
            failureCount.incrementAndGet();
            lastFailureTime = System.currentTimeMillis();
            
            if (state == CircuitState.HALF_OPEN) {
                // 半开状态下失败，重新打开
                state = CircuitState.OPEN;
                openTime = System.currentTimeMillis();
                log.warn("数据库熔断器: HALF_OPEN -> OPEN (失败次数: {})", failureCount.get());
            } else if (state == CircuitState.CLOSED && 
                      failureCount.get() >= config.getFailureThreshold()) {
                // 关闭状态下失败次数达到阈值，打开熔断
                state = CircuitState.OPEN;
                openTime = System.currentTimeMillis();
                log.warn("数据库熔断器: CLOSED -> OPEN (失败次数: {})", failureCount.get());
            }
        }
    }

    /**
     * 获取当前熔断器状态
     */
    public CircuitState getState() {
        return state;
    }

    /**
     * 获取失败次数
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * 重置熔断器
     */
    public void reset() {
        state = CircuitState.CLOSED;
        failureCount.set(0);
        successCount.set(0);
        log.info("数据库熔断器已重置");
    }

    // ==================== 3. 连接池监控 ====================

    /**
     * 获取连接池状态
     */
    public ConnectionPoolStatus getConnectionPoolStatus() {
        try {
            javax.sql.DataSource ds = dataSource;
            if (ds instanceof com.alibaba.druid.pool.DruidDataSource) {
                com.alibaba.druid.pool.DruidDataSource druidDs = 
                    (com.alibaba.druid.pool.DruidDataSource) ds;
                
                ConnectionPoolStatus status = new ConnectionPoolStatus();
                status.setActiveCount(druidDs.getActiveCount());
                status.setPoolingCount(druidDs.getPoolingCount());
                status.setWaitThreadCount(druidDs.getWaitThreadCount());
                status.setConnectCount(druidDs.getConnectCount());
                status.setCloseCount(druidDs.getCloseCount());
                status.setConnectErrorCount(druidDs.getConnectErrorCount());
                return status;
            }
        } catch (Exception e) {
            log.error("获取连接池状态失败", e);
        }
        return null;
    }

    /**
     * 连接池状态
     */
    @lombok.Data
    public static class ConnectionPoolStatus {
        private int activeCount;      // 活跃连接数
        private int poolingCount;     // 空闲连接数
        private int waitThreadCount;  // 等待线程数
        private long connectCount;    // 总连接数
        private long closeCount;      // 关闭连接数
        private long connectErrorCount; // 连接错误数
    }

    // ==================== 4. 安全的数据库操作 ====================

    /**
     * 带保护的数据查询
     */
    public <T> T executeQuery(String sql, QueryCallback<T> callback, CircuitBreakerConfig config) {
        // 1. SQL限流检查
        if (!tryAcquire(sql)) {
            throw new DatabaseProtectionException("SQL执行被限流: " + sql);
        }

        // 2. 熔断器检查
        if (!allowRequest(config)) {
            throw new DatabaseProtectionException("数据库熔断中，请稍后重试");
        }

        // 3. 执行查询
        long startTime = System.currentTimeMillis();
        try {
            T result = callback.execute();
            long executeTime = System.currentTimeMillis() - startTime;
            
            // 记录成功
            recordResult(config, sql, executeTime, true);
            return result;
        } catch (Exception e) {
            long executeTime = System.currentTimeMillis() - startTime;
            
            // 记录失败
            recordResult(config, sql, executeTime, false);
            throw new DatabaseProtectionException("SQL执行异常: " + sql, e);
        }
    }

    /**
     * 查询回调接口
     */
    @FunctionalInterface
    public interface QueryCallback<T> {
        T execute() throws SQLException;
    }

    /**
     * 数据库保护异常
     */
    public static class DatabaseProtectionException extends RuntimeException {
        public DatabaseProtectionException(String message) {
            super(message);
        }

        public DatabaseProtectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
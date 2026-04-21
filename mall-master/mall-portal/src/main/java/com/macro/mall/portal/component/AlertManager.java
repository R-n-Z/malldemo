package com.macro.mall.portal.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 告警管理器
 * 监控指标，触发告警，支持降级和熔断
 */
@Slf4j
@Component
public class AlertManager {

    @Value("${redis.database:mall}")
    private String redisDatabase;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private MetricsCollector metricsCollector;

    @Autowired
    private DatabaseRateLimiter databaseRateLimiter;

    @Autowired
    private RedisRateLimiter redisRateLimiter;

    @Autowired
    private AlertNotificationService alertNotificationService;

    // 告警配置
    private static final String ALERT_PREFIX = "alert:";

    // 告警级别
    public enum AlertLevel {
        INFO,      // 信息
        WARNING,   // 警告
        CRITICAL,  // 严重
        EMERGENCY  // 紧急
    }

    // 告警规则
    private final Map<String, AlertRule> alertRules = new ConcurrentHashMap<>();

    // 告警状态
    private final Map<String, AtomicInteger> alertCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAlertTime = new ConcurrentHashMap<>();

    // 降级状态
    private volatile boolean degradationMode = false;
    private volatile long degradationStartTime = 0;

    // 熔断状态
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;

    // ==================== 1. 告警规则配置 ====================

    /**
     * 告警规则
     */
    @lombok.Data
    public static class AlertRule {
        private String name;              // 规则名称
        private String metric;            // 监控指标
        private double threshold;         // 阈值
        private AlertLevel level;         // 告警级别
        private int cooldownSeconds;      // 冷却时间（秒）
        private boolean autoDegrade;      // 是否自动降级
        private boolean autoCircuitBreak; // 是否自动熔断
    }

    /**
     * 初始化告警规则
     */
    @PostConstruct
    public void init() {
        // QPS告警规则
        alertRules.put("qps_high", new AlertRule() {{
            setName("QPS过高");
            setMetric("qps");
            setThreshold(1000);  // QPS超过1000
            setLevel(AlertLevel.WARNING);
            setCooldownSeconds(60);
            setAutoDegrade(true);
            setAutoCircuitBreak(false);
        }});

        // 错误率告警规则
        alertRules.put("error_rate_high", new AlertRule() {{
            setName("错误率过高");
            setMetric("errorRate");
            setThreshold(5.0);  // 错误率超过5%
            setLevel(AlertLevel.CRITICAL);
            setCooldownSeconds(30);
            setAutoDegrade(true);
            setAutoCircuitBreak(true);
        }});

        // 响应时间告警规则
        alertRules.put("response_time_high", new AlertRule() {{
            setName("响应时间过长");
            setMetric("avgResponseTime");
            setThreshold(1000);  // 响应时间超过1秒
            setLevel(AlertLevel.WARNING);
            setCooldownSeconds(60);
            setAutoDegrade(true);
            setAutoCircuitBreak(false);
        }});

        // 限流告警规则
        alertRules.put("rate_limit_high", new AlertRule() {{
            setName("限流次数过多");
            setMetric("rateLimitCount");
            setThreshold(100);  // 每分钟限流超过100次
            setLevel(AlertLevel.CRITICAL);
            setCooldownSeconds(30);
            setAutoDegrade(true);
            setAutoCircuitBreak(false);
        }});

        log.info("告警规则初始化完成: {} 个规则", alertRules.size());
    }

    // ==================== 2. 告警检测 ====================

    /**
     * 定时检测告警（每10秒执行）
     */
    @Scheduled(fixedRate = 10000)
    public void checkAlerts() {
        if (degradationMode) {
            // 降级模式下减少检测频率
            return;
        }

        try {
            // 获取监控指标
            Map<String, Object> metrics = metricsCollector.getAllMetrics();

            // 检测各项指标
            checkQPSAlert(metrics);
            checkErrorRateAlert(metrics);
            checkResponseTimeAlert(metrics);
            checkRateLimitAlert(metrics);

        } catch (Exception e) {
            log.error("告警检测失败", e);
        }
    }

    /**
     * 检测QPS告警
     */
    @SuppressWarnings("unchecked")
    private void checkQPSAlert(Map<String, Object> metrics) {
        Map<String, Double> qps = (Map<String, Double>) metrics.get("qps");
        if (qps == null) return;

        AlertRule rule = alertRules.get("qps_high");
        if (rule == null) return;

        for (Map.Entry<String, Double> entry : qps.entrySet()) {
            if (entry.getValue() > rule.getThreshold()) {
                triggerAlert(rule, entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 检测错误率告警
     */
    @SuppressWarnings("unchecked")
    private void checkErrorRateAlert(Map<String, Object> metrics) {
        Map<String, Double> errorRate = (Map<String, Double>) metrics.get("errorRate");
        if (errorRate == null) return;

        AlertRule rule = alertRules.get("error_rate_high");
        if (rule == null) return;

        for (Map.Entry<String, Double> entry : errorRate.entrySet()) {
            if (entry.getValue() > rule.getThreshold()) {
                triggerAlert(rule, entry.getKey(), entry.getValue());
                
                // 触发自动熔断
                if (rule.isAutoCircuitBreak()) {
                    openCircuitBreaker(entry.getKey());
                }
            }
        }
    }

    /**
     * 检测响应时间告警
     */
    @SuppressWarnings("unchecked")
    private void checkResponseTimeAlert(Map<String, Object> metrics) {
        Map<String, Double> responseTime = (Map<String, Double>) metrics.get("avgResponseTime");
        if (responseTime == null) return;

        AlertRule rule = alertRules.get("response_time_high");
        if (rule == null) return;

        for (Map.Entry<String, Double> entry : responseTime.entrySet()) {
            if (entry.getValue() > rule.getThreshold()) {
                triggerAlert(rule, entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 检测限流告警
     */
    @SuppressWarnings("unchecked")
    private void checkRateLimitAlert(Map<String, Object> metrics) {
        Map<String, Double> rateLimitCount = (Map<String, Double>) metrics.get("rateLimitCount");
        if (rateLimitCount == null) return;

        AlertRule rule = alertRules.get("rate_limit_high");
        if (rule == null) return;

        for (Map.Entry<String, Double> entry : rateLimitCount.entrySet()) {
            if (entry.getValue() > rule.getThreshold()) {
                triggerAlert(rule, entry.getKey(), entry.getValue());
                
                // 触发自动降级
                if (rule.isAutoDegrade()) {
                    enableDegradation();
                }
            }
        }
    }

    // ==================== 3. 告警触发 ====================

    /**
     * 触发告警
     */
    private void triggerAlert(AlertRule rule, String target, double value) {
        String alertKey = rule.getName() + ":" + target;
        long now = System.currentTimeMillis();

        // 检查冷却时间
        Long lastTime = lastAlertTime.get(alertKey);
        if (lastTime != null && (now - lastTime) < rule.getCooldownSeconds() * 1000) {
            return;  // 冷却中
        }

        // 更新告警状态
        alertCounts.computeIfAbsent(alertKey, k -> new AtomicInteger(0)).incrementAndGet();
        lastAlertTime.put(alertKey, now);

        // 记录告警
        AlertInfo alertInfo = new AlertInfo();
        alertInfo.setRuleName(rule.getName());
        alertInfo.setLevel(rule.getLevel());
        alertInfo.setTarget(target);
        alertInfo.setValue(value);
        alertInfo.setCount(alertCounts.get(alertKey).get());
        alertInfo.setTime(now);

        saveAlert(alertInfo);
        sendAlert(alertInfo);

        log.warn("告警触发: rule={}, target={}, value={}, level={}", 
                rule.getName(), target, value, rule.getLevel());

        // 触发自动降级
        if (rule.isAutoDegrade() && !degradationMode) {
            enableDegradation();
        }
    }

    /**
     * 保存告警记录
     */
    private void saveAlert(AlertInfo alert) {
        String key = redisDatabase + ":" + ALERT_PREFIX + "history";
        Map<String, Object> alertMap = new HashMap<>();
        alertMap.put("ruleName", alert.getRuleName());
        alertMap.put("level", alert.getLevel().name());
        alertMap.put("target", alert.getTarget());
        alertMap.put("value", alert.getValue());
        alertMap.put("count", alert.getCount());
        alertMap.put("time", alert.getTime());

        redisTemplate.opsForZSet().add(key, alertMap, -alert.getTime());
        // 只保留最近1000条告警
        redisTemplate.opsForZSet().removeRange(key, 0, -1001);
    }

    /**
     * 发送告警通知
     */
    private void sendAlert(AlertInfo alert) {
        // 使用告警通知服务发送通知
        if (alertNotificationService != null) {
            alertNotificationService.sendAlert(alert);
        } else {
            log.info("告警通知: [{}] {} - {} = {}", 
                    alert.getLevel(), alert.getRuleName(), alert.getTarget(), alert.getValue());
        }
    }

    // ==================== 4. 降级管理 ====================

    /**
     * 启用降级模式
     */
    public synchronized void enableDegradation() {
        if (degradationMode) {
            return;
        }

        degradationMode = true;
        degradationStartTime = System.currentTimeMillis();

        // 记录降级状态
        String key = redisDatabase + ":" + ALERT_PREFIX + "degradation";
        redisTemplate.opsForValue().set(key, "ON", 1, java.util.concurrent.TimeUnit.HOURS);

        log.warn("系统进入降级模式");
        sendAlert(createSystemAlert("系统进入降级模式", AlertLevel.CRITICAL));
    }

    /**
     * 禁用降级模式
     */
    public synchronized void disableDegradation() {
        if (!degradationMode) {
            return;
        }

        degradationMode = false;

        // 清除降级状态
        String key = redisDatabase + ":" + ALERT_PREFIX + "degradation";
        redisTemplate.delete(key);

        log.info("系统退出降级模式");
        sendAlert(createSystemAlert("系统退出降级模式", AlertLevel.INFO));
    }

    /**
     * 是否处于降级模式
     */
    public boolean isDegradationMode() {
        return degradationMode;
    }

    /**
     * 获取降级状态
     */
    public DegradationStatus getDegradationStatus() {
        return new DegradationStatus(
                degradationMode,
                degradationStartTime,
                System.currentTimeMillis() - degradationStartTime
        );
    }

    // ==================== 5. 熔断管理 ====================

    /**
     * 打开熔断器
     */
    public synchronized void openCircuitBreaker(String target) {
        if (circuitBreakerOpen) {
            return;
        }

        circuitBreakerOpen = true;
        circuitBreakerOpenTime = System.currentTimeMillis();

        // 记录熔断状态
        String key = redisDatabase + ":" + ALERT_PREFIX + "circuitbreaker";
        Map<String, Object> status = new HashMap<>();
        status.put("target", target);
        status.put("state", "OPEN");
        status.put("openTime", circuitBreakerOpenTime);
        redisTemplate.opsForValue().set(key, status, 1, java.util.concurrent.TimeUnit.HOURS);

        log.warn("熔断器打开: target={}", target);
        sendAlert(createSystemAlert("熔断器打开: " + target, AlertLevel.EMERGENCY));
    }

    /**
     * 关闭熔断器
     */
    public synchronized void closeCircuitBreaker() {
        if (!circuitBreakerOpen) {
            return;
        }

        circuitBreakerOpen = false;
        circuitBreakerOpenTime = 0;

        // 清除熔断状态
        String key = redisDatabase + ":" + ALERT_PREFIX + "circuitbreaker";
        redisTemplate.delete(key);

        log.info("熔断器关闭");
        sendAlert(createSystemAlert("熔断器关闭", AlertLevel.INFO));
    }

    /**
     * 检查是否熔断
     */
    public boolean isCircuitBreakerOpen() {
        if (!circuitBreakerOpen) {
            return false;
        }

        // 30秒后尝试恢复
        if (System.currentTimeMillis() - circuitBreakerOpenTime > 30000) {
            closeCircuitBreaker();
            return false;
        }

        return true;
    }

    /**
     * 获取熔断状态
     */
    public CircuitBreakerStatus getCircuitBreakerStatus() {
        return new CircuitBreakerStatus(
                circuitBreakerOpen,
                circuitBreakerOpenTime,
                System.currentTimeMillis() - circuitBreakerOpenTime
        );
    }

    // ==================== 6. 辅助方法 ====================

    /**
     * 创建系统告警
     */
    private AlertInfo createSystemAlert(String message, AlertLevel level) {
        AlertInfo alert = new AlertInfo();
        alert.setRuleName("SYSTEM");
        alert.setLevel(level);
        alert.setTarget("SYSTEM");
        alert.setValue(0);
        alert.setCount(1);
        alert.setTime(System.currentTimeMillis());
        return alert;
    }

    /**
     * 获取告警历史
     */
    public List<AlertInfo> getAlertHistory(int limit) {
        String key = redisDatabase + ":" + ALERT_PREFIX + "history";
        Set<Object> alerts = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
        
        List<AlertInfo> result = new ArrayList<>();
        if (alerts != null) {
            for (Object obj : alerts) {
                if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> alertMap = (Map<String, Object>) obj;
                    AlertInfo alert = new AlertInfo();
                    alert.setRuleName((String) alertMap.get("ruleName"));
                    alert.setLevel(AlertLevel.valueOf((String) alertMap.get("level")));
                    alert.setTarget((String) alertMap.get("target"));
                    alert.setValue(((Number) alertMap.get("value")).doubleValue());
                    alert.setCount(((Number) alertMap.get("count")).intValue());
                    alert.setTime(((Number) alertMap.get("time")).longValue());
                    result.add(alert);
                }
            }
        }
        return result;
    }

    // ==================== 7. 内部类 ====================

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class AlertInfo {
        private String ruleName;
        private AlertLevel level;
        private String target;
        private double value;
        private int count;
        private long time;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class DegradationStatus {
        private boolean enabled;
        private long startTime;
        private long durationMs;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CircuitBreakerStatus {
        private boolean open;
        private long openTime;
        private long durationMs;
    }
}
package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.component.AlertManager;
import com.macro.mall.portal.component.AlertManager.AlertInfo;
import com.macro.mall.portal.component.AlertManager.CircuitBreakerStatus;
import com.macro.mall.portal.component.AlertManager.DegradationStatus;
import com.macro.mall.portal.component.MetricsCollector;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 监控管理API
 * 提供系统监控指标查询、告警管理、熔断降级状态查看
 */
@Slf4j
@RestController
@Api(tags = "MonitorController", description = "系统监控管理")
@RequestMapping("/monitor")
public class MonitorController {

    @Autowired
    private MetricsCollector metricsCollector;

    @Autowired
    private AlertManager alertManager;

    @ApiOperation("获取系统监控指标")
    @GetMapping("/metrics")
    public CommonResult<Map<String, Object>> getMetrics() {
        try {
            Map<String, Object> metrics = metricsCollector.getAllMetrics();
            metrics.put("degradationStatus", alertManager.getDegradationStatus());
            metrics.put("circuitBreakerStatus", alertManager.getCircuitBreakerStatus());
            return CommonResult.success(metrics);
        } catch (Exception e) {
            log.error("获取监控指标失败", e);
            return CommonResult.failed("获取监控指标失败: " + e.getMessage());
        }
    }

    @ApiOperation("获取QPS指标")
    @GetMapping("/metrics/qps")
    public CommonResult<Map<String, Double>> getQPS() {
        try {
            return CommonResult.success(metricsCollector.getQPS());
        } catch (Exception e) {
            log.error("获取QPS指标失败", e);
            return CommonResult.failed("获取QPS指标失败: " + e.getMessage());
        }
    }

    @ApiOperation("获取错误率指标")
    @GetMapping("/metrics/error-rate")
    public CommonResult<Map<String, Double>> getErrorRate() {
        try {
            return CommonResult.success(metricsCollector.getErrorRate());
        } catch (Exception e) {
            log.error("获取错误率指标失败", e);
            return CommonResult.failed("获取错误率指标失败: " + e.getMessage());
        }
    }

    @ApiOperation("获取响应时间指标")
    @GetMapping("/metrics/response-time")
    public CommonResult<Map<String, Double>> getResponseTime() {
        try {
            return CommonResult.success(metricsCollector.getAvgResponseTime());
        } catch (Exception e) {
            log.error("获取响应时间指标失败", e);
            return CommonResult.failed("获取响应时间指标失败: " + e.getMessage());
        }
    }

    @ApiOperation("获取限流统计")
    @GetMapping("/metrics/rate-limit")
    public CommonResult<Map<String, Double>> getRateLimitCount() {
        try {
            return CommonResult.success(metricsCollector.getRateLimitCount());
        } catch (Exception e) {
            log.error("获取限流统计失败", e);
            return CommonResult.failed("获取限流统计失败: " + e.getMessage());
        }
    }

    @ApiOperation("获取熔断统计")
    @GetMapping("/metrics/circuit-break")
    public CommonResult<Map<String, Double>> getCircuitBreakCount() {
        try {
            return CommonResult.success(metricsCollector.getCircuitBreakCount());
        } catch (Exception e) {
            log.error("获取熔断统计失败", e);
            return CommonResult.failed("获取熔断统计失败: " + e.getMessage());
        }
    }

    @ApiOperation("获取告警历史")
    @GetMapping("/alerts/history")
    public CommonResult<List<AlertInfo>> getAlertHistory(
            @ApiParam("查询数量") @RequestParam(defaultValue = "100") int limit) {
        try {
            return CommonResult.success(alertManager.getAlertHistory(limit));
        } catch (Exception e) {
            log.error("获取告警历史失败", e);
            return CommonResult.failed("获取告警历史失败: " + e.getMessage());
        }
    }

    @ApiOperation("获取降级状态")
    @GetMapping("/degradation/status")
    public CommonResult<DegradationStatus> getDegradationStatus() {
        try {
            return CommonResult.success(alertManager.getDegradationStatus());
        } catch (Exception e) {
            log.error("获取降级状态失败", e);
            return CommonResult.failed("获取降级状态失败: " + e.getMessage());
        }
    }

    @ApiOperation("手动启用降级模式")
    @PostMapping("/degradation/enable")
    public CommonResult<Map<String, Object>> enableDegradation() {
        try {
            alertManager.enableDegradation();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "降级模式已启用");
            result.put("status", alertManager.getDegradationStatus());
            return CommonResult.success(result);
        } catch (Exception e) {
            log.error("启用降级模式失败", e);
            return CommonResult.failed("启用降级模式失败: " + e.getMessage());
        }
    }

    @ApiOperation("手动禁用降级模式")
    @PostMapping("/degradation/disable")
    public CommonResult<Map<String, Object>> disableDegradation() {
        try {
            alertManager.disableDegradation();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "降级模式已禁用");
            result.put("status", alertManager.getDegradationStatus());
            return CommonResult.success(result);
        } catch (Exception e) {
            log.error("禁用降级模式失败", e);
            return CommonResult.failed("禁用降级模式失败: " + e.getMessage());
        }
    }

    @ApiOperation("获取熔断器状态")
    @GetMapping("/circuit-breaker/status")
    public CommonResult<CircuitBreakerStatus> getCircuitBreakerStatus() {
        try {
            return CommonResult.success(alertManager.getCircuitBreakerStatus());
        } catch (Exception e) {
            log.error("获取熔断器状态失败", e);
            return CommonResult.failed("获取熔断器状态失败: " + e.getMessage());
        }
    }

    @ApiOperation("手动打开熔断器")
    @PostMapping("/circuit-breaker/open")
    public CommonResult<Map<String, Object>> openCircuitBreaker(
            @ApiParam("目标API路径") @RequestParam String target) {
        try {
            alertManager.openCircuitBreaker(target);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "熔断器已打开: " + target);
            result.put("status", alertManager.getCircuitBreakerStatus());
            return CommonResult.success(result);
        } catch (Exception e) {
            log.error("打开熔断器失败", e);
            return CommonResult.failed("打开熔断器失败: " + e.getMessage());
        }
    }

    @ApiOperation("手动关闭熔断器")
    @PostMapping("/circuit-breaker/close")
    public CommonResult<Map<String, Object>> closeCircuitBreaker() {
        try {
            alertManager.closeCircuitBreaker();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "熔断器已关闭");
            result.put("status", alertManager.getCircuitBreakerStatus());
            return CommonResult.success(result);
        } catch (Exception e) {
            log.error("关闭熔断器失败", e);
            return CommonResult.failed("关闭熔断器失败: " + e.getMessage());
        }
    }

    @ApiOperation("获取系统健康状态")
    @GetMapping("/health")
    public CommonResult<Map<String, Object>> getHealth() {
        Map<String, Object> health = new HashMap<>();
        
        // 检查降级状态
        DegradationStatus degradationStatus = alertManager.getDegradationStatus();
        health.put("degradationMode", degradationStatus.isEnabled());
        
        // 检查熔断状态
        CircuitBreakerStatus circuitBreakerStatus = alertManager.getCircuitBreakerStatus();
        health.put("circuitBreakerOpen", circuitBreakerStatus.isOpen());
        
        // 获取关键指标
        Map<String, Double> qps = metricsCollector.getQPS();
        Map<String, Double> errorRate = metricsCollector.getErrorRate();
        
        // 计算总体健康度
        double healthScore = 100.0;
        if (degradationStatus.isEnabled()) {
            healthScore -= 30;
        }
        if (circuitBreakerStatus.isOpen()) {
            healthScore -= 40;
        }
        
        // 检查错误率
        for (Double rate : errorRate.values()) {
            if (rate > 10.0) {
                healthScore -= 20;
                break;
            }
        }
        
        health.put("healthScore", Math.max(0, healthScore));
        health.put("status", healthScore >= 80 ? "HEALTHY" : (healthScore >= 50 ? "DEGRADED" : "UNHEALTHY"));
        
        return CommonResult.success(health);
    }
}
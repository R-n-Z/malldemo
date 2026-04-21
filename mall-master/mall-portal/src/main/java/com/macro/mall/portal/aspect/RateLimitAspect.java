package com.macro.mall.portal.aspect;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.component.RedisRateLimiter;
import com.macro.mall.portal.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.*;
import java.lang.reflect.Method;

/**
 * 限流切面
 * 使用AOP统一处理接口限流
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Autowired
    private RedisRateLimiter rateLimiter;

    /**
     * 限流算法类型
     */
    public enum LimitType {
        /**
         * 根据用户ID限流
         */
        MEMBER,
        /**
         * 根据IP地址限流
         */
        IP,
        /**
         * 根据接口路径限流（全局限流）
         */
        PATH,
        /**
         * 自定义key
         */
        CUSTOM
    }

    /**
     * 限流算法
     */
    public enum Algorithm {
        TOKEN_BUCKET,   // 令牌桶（支持突发）
        SLIDING_WINDOW, // 滑动窗口（精确）
        FIXED_WINDOW    // 固定窗口（简单）
    }

    /**
     * 定义限流注解
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface RateLimit {
        /**
         * 限流key前缀
         */
        String key() default "";

        /**
         * 限流类型
         */
        LimitType type() default LimitType.MEMBER;

        /**
         * 限流算法
         */
        Algorithm algorithm() default Algorithm.TOKEN_BUCKET;

        /**
         * 限流参数1（令牌桶：速率，滑动/固定窗口：最大请求数）
         */
        int param1() default 10;

        /**
         * 限流参数2（令牌桶：容量，窗口大小：秒）
         */
        int param2() default 20;

        /**
         * 需要获取的令牌数量
         */
        int tokens() default 1;
    }

    /**
     * 环绕增强：处理限流逻辑
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        // 1. 获取限流key
        String key = getRateLimitKey(point, rateLimit);

        // 2. 根据算法执行限流
        boolean acquired = false;
        switch (rateLimit.algorithm()) {
            case TOKEN_BUCKET:
                acquired = rateLimiter.tokenBucket(
                        key,
                        rateLimit.param1(),  // rate
                        rateLimit.param2(),  // capacity
                        rateLimit.tokens()
                );
                break;
            case SLIDING_WINDOW:
                acquired = rateLimiter.slidingWindow(
                        key,
                        rateLimit.param2(),  // windowSeconds
                        rateLimit.param1()   // maxRequests
                );
                break;
            case FIXED_WINDOW:
                acquired = rateLimiter.fixedWindow(
                        key,
                        rateLimit.param2(),  // windowSeconds
                        rateLimit.param1()   // maxRequests
                );
                break;
        }

        if (!acquired) {
            log.warn("限流拦截: key={}, algorithm={}", key, rateLimit.algorithm());
            return CommonResult.failed("请求过于频繁，请稍后再试");
        }

        // 3. 执行实际方法
        return point.proceed();
    }

    /**
     * 获取限流key
     */
    private String getRateLimitKey(ProceedingJoinPoint point, RateLimit rateLimit) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs != null ? attrs.getRequest() : null;

        // 1. 如果指定了自定义key，直接使用
        if (rateLimit.type() == LimitType.CUSTOM && !rateLimit.key().isEmpty()) {
            return rateLimit.key();
        }

        // 2. 根据类型获取key
        switch (rateLimit.type()) {
            case IP:
                String ip = getClientIp(request);
                return "ip:" + ip + ":" + getMethodName(point);
                
            case PATH:
                return "path:" + request.getRequestURI();
                
            case MEMBER:
            default:
                // 从请求头获取用户ID
                Long memberId = getMemberId(request);
                if (memberId != null) {
                    return "member:" + memberId + ":" + getMethodName(point);
                }
                // 如果没有用户ID，使用IP
                String clientIp = getClientIp(request);
                return "ip:" + clientIp + ":" + getMethodName(point);
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // 多个代理时，第一个IP为真实IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }

    /**
     * 获取用户ID
     */
    private Long getMemberId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        
        String memberIdStr = request.getHeader("X-Member-Id");
        if (memberIdStr != null && !memberIdStr.isEmpty()) {
            try {
                return Long.parseLong(memberIdStr);
            } catch (NumberFormatException e) {
                log.warn("用户ID格式错误: {}", memberIdStr);
            }
        }
        
        // 从attribute获取
        Object memberId = request.getAttribute("memberId");
        if (memberId != null) {
            return Long.parseLong(memberId.toString());
        }
        
        return null;
    }

    /**
     * 获取方法名
     */
    private String getMethodName(ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
}
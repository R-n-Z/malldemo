package com.macro.mall.portal.aspect;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.annotation.RateLimit;
import com.macro.mall.portal.component.RedisRateLimiter;
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
import java.lang.reflect.Method;

/**
 * 限流切面
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Autowired
    private RedisRateLimiter rateLimiter;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        String key = getRateLimitKey(point, rateLimit);

        boolean acquired = false;
        switch (rateLimit.algorithm()) {
            case TOKEN_BUCKET:
                acquired = rateLimiter.tokenBucket(key, rateLimit.param1(), rateLimit.param2(), rateLimit.tokens());
                break;
            case SLIDING_WINDOW:
                acquired = rateLimiter.slidingWindow(key, rateLimit.param2(), rateLimit.param1());
                break;
            case FIXED_WINDOW:
                acquired = rateLimiter.fixedWindow(key, rateLimit.param2(), rateLimit.param1());
                break;
        }

        if (!acquired) {
            log.warn("限流拦截: key={}, algorithm={}", key, rateLimit.algorithm());
            return CommonResult.failed("请求过于频繁，请稍后再试");
        }

        return point.proceed();
    }

    private String getRateLimitKey(ProceedingJoinPoint point, RateLimit rateLimit) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs != null ? attrs.getRequest() : null;

        if (rateLimit.type() == RateLimit.LimitType.CUSTOM && !rateLimit.key().isEmpty()) {
            return rateLimit.key();
        }

        switch (rateLimit.type()) {
            case IP:
                String ip = getClientIp(request);
                return "ip:" + ip + ":" + getMethodName(point);
            case PATH:
                return "path:" + (request != null ? request.getRequestURI() : "unknown");
            case MEMBER:
            default:
                Long memberId = getMemberId(request);
                if (memberId != null) {
                    return "member:" + memberId + ":" + getMethodName(point);
                }
                String clientIp = getClientIp(request);
                return "ip:" + clientIp + ":" + getMethodName(point);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";

        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip))
            ip = request.getHeader("Proxy-Client-IP");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip))
            ip = request.getHeader("WL-Proxy-Client-IP");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip))
            ip = request.getHeader("HTTP_CLIENT_IP");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip))
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip))
            ip = request.getRemoteAddr();

        if (ip != null && ip.contains(","))
            ip = ip.split(",")[0].trim();

        return ip;
    }

    private Long getMemberId(HttpServletRequest request) {
        if (request == null) return null;

        String memberIdStr = request.getHeader("X-Member-Id");
        if (memberIdStr != null && !memberIdStr.isEmpty()) {
            try {
                return Long.parseLong(memberIdStr);
            } catch (NumberFormatException e) {
                log.warn("用户ID格式错误: {}", memberIdStr);
            }
        }

        Object memberId = request.getAttribute("memberId");
        if (memberId != null) {
            return Long.parseLong(memberId.toString());
        }

        return null;
    }

    private String getMethodName(ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
}

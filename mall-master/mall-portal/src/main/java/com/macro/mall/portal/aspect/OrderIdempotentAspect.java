package com.macro.mall.portal.aspect;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.component.OrderIdempotentChecker;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.*;
import java.lang.reflect.Method;

/**
 * 订单幂等性切面
 * 使用AOP统一处理下单接口的防重逻辑
 */
@Slf4j
@Aspect
@Component
public class OrderIdempotentAspect {

    @Autowired
    private OrderIdempotentChecker idempotentChecker;

    /**
     * 定义幂等性注解
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface IdempotentOrder {
        /**
         * 是否需要签名验证
         */
        boolean requireSign() default true;

        /**
         * 签名参数名（从请求头获取）
         */
        String signHeader() default "X-Order-Sign";

        /**
         * 时间戳参数名（从请求头获取）
         */
        String timestampHeader() default "X-Order-Timestamp";

        /**
         * 请求过期时间（秒）
         */
        long expireSeconds() default 300;
    }

    /**
     * 环绕增强：处理下单接口的幂等性
     */
    @Around("@annotation(com.macro.mall.portal.aspect.OrderIdempotentAspect.IdempotentOrder)")
    public Object around(ProceedingJoinPoint point, IdempotentOrder idempotent) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return point.proceed();
        }

        HttpServletRequest request = attrs.getRequest();

        // 1. 获取用户ID（从登录信息中获取）
        Long memberId = getCurrentMemberId(request);
        if (memberId == null) {
            log.warn("无法获取用户ID，可能未登录");
            return point.proceed();
        }

        // 2. 如果需要签名验证
        if (idempotent.requireSign()) {
            String sign = request.getHeader(idempotent.signHeader());
            String timestampStr = request.getHeader(idempotent.timestampHeader());

            // 验证时间戳（防止重放攻击）
            if (!validateTimestamp(timestampStr, idempotent.expireSeconds())) {
                log.warn("请求时间戳无效或已过期: memberId={}, timestamp={}", memberId, timestampStr);
                return CommonResult.failed("请求已过期，请重新提交");
            }

            // 验证签名
            if (!StringUtils.hasText(sign)) {
                log.warn("缺少请求签名: memberId={}", memberId);
                return CommonResult.failed("缺少请求签名");
            }

            // 3. 尝试获取幂等锁
            if (!idempotentChecker.tryAcquire(memberId, sign)) {
                log.warn("重复请求被拦截: memberId={}, sign={}", memberId, sign);
                return CommonResult.failed("请勿重复提交订单");
            }

            try {
                // 4. 执行实际的下单逻辑
                return point.proceed();
            } finally {
                // 5. 释放幂等锁（如果下单成功，应该在Service中释放）
                // 这里不自动释放，因为可能需要根据下单结果决定是否释放
            }
        }

        return point.proceed();
    }

    /**
     * 验证时间戳
     */
    private boolean validateTimestamp(String timestampStr, long expireSeconds) {
        if (!StringUtils.hasText(timestampStr)) {
            return false;
        }

        try {
            long timestamp = Long.parseLong(timestampStr);
            long now = System.currentTimeMillis();
            long diff = Math.abs(now - timestamp);

            // 时间差超过expireSeconds秒视为过期
            return diff < expireSeconds * 1000;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 获取当前用户ID
     * 实际项目中需要根据认证方式调整
     */
    private Long getCurrentMemberId(HttpServletRequest request) {
        // 从请求头或Session中获取用户ID
        // 这里需要根据实际认证方式实现
        String memberIdStr = request.getHeader("X-Member-Id");
        if (StringUtils.hasText(memberIdStr)) {
            try {
                return Long.parseLong(memberIdStr);
            } catch (NumberFormatException e) {
                log.warn("用户ID格式错误: {}", memberIdStr);
            }
        }

        // 备用：从Attribute中获取
        Object memberId = request.getAttribute("memberId");
        if (memberId != null) {
            return Long.parseLong(memberId.toString());
        }

        return null;
    }
}
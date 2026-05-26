package com.macro.mall.portal.aspect;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.annotation.IdempotentOrder;
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

/**
 * 订单幂等性切面
 */
@Slf4j
@Aspect
@Component
public class OrderIdempotentAspect {

    @Autowired
    private OrderIdempotentChecker idempotentChecker;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint point, IdempotentOrder idempotent) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return point.proceed();
        }

        HttpServletRequest request = attrs.getRequest();
        Long memberId = getCurrentMemberId(request);
        if (memberId == null) {
            log.warn("无法获取用户ID，可能未登录");
            return point.proceed();
        }

        if (idempotent.requireSign()) {
            String sign = request.getHeader(idempotent.signHeader());
            String timestampStr = request.getHeader(idempotent.timestampHeader());

            if (!validateTimestamp(timestampStr, idempotent.expireSeconds())) {
                log.warn("请求时间戳无效或已过期: memberId={}, timestamp={}", memberId, timestampStr);
                return CommonResult.failed("请求已过期，请重新提交");
            }

            if (!StringUtils.hasText(sign)) {
                log.warn("缺少请求签名: memberId={}", memberId);
                return CommonResult.failed("缺少请求签名");
            }

            if (!idempotentChecker.tryAcquire(memberId, sign)) {
                log.warn("重复请求被拦截: memberId={}, sign={}", memberId, sign);
                return CommonResult.failed("请勿重复提交订单");
            }

            return point.proceed();
        }

        return point.proceed();
    }

    private boolean validateTimestamp(String timestampStr, long expireSeconds) {
        if (!StringUtils.hasText(timestampStr)) return false;
        try {
            long timestamp = Long.parseLong(timestampStr);
            return Math.abs(System.currentTimeMillis() - timestamp) < expireSeconds * 1000;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Long getCurrentMemberId(HttpServletRequest request) {
        String memberIdStr = request.getHeader("X-Member-Id");
        if (StringUtils.hasText(memberIdStr)) {
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
}

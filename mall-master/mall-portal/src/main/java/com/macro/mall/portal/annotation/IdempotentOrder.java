package com.macro.mall.portal.annotation;

import java.lang.annotation.*;

/**
 * 订单幂等性注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IdempotentOrder {

    boolean requireSign() default true;

    String signHeader() default "X-Order-Sign";

    String timestampHeader() default "X-Order-Timestamp";

    long expireSeconds() default 300;
}

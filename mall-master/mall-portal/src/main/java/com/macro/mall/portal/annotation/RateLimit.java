package com.macro.mall.portal.annotation;

import java.lang.annotation.*;

/**
 * 限流注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    enum LimitType {
        MEMBER, IP, PATH, CUSTOM
    }

    enum Algorithm {
        TOKEN_BUCKET, SLIDING_WINDOW, FIXED_WINDOW
    }

    String key() default "";

    LimitType type() default LimitType.MEMBER;

    Algorithm algorithm() default Algorithm.TOKEN_BUCKET;

    int param1() default 10;

    int param2() default 20;

    int tokens() default 1;
}

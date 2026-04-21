package com.macro.mall.portal.exception;

import lombok.Getter;

/**
 * 限流异常
 */
@Getter
public class RateLimitException extends RuntimeException {

    private final long waitTime;

    public RateLimitException(String message, long waitTime) {
        super(message);
        this.waitTime = waitTime;
    }

    public RateLimitException(String message) {
        this(message, 0);
    }
}
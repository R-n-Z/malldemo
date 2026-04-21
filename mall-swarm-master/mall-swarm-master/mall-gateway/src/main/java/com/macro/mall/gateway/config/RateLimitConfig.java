package com.macro.mall.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关限流配置
 */
@Configuration
public class RateLimitConfig {

    /**
     * IP限流Key解析器
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
        );
    }

    /**
     * 用户ID限流Key解析器（需登录）
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders()
                    .getFirst("Authorization");
            if (userId != null) {
                return Mono.just(userId);
            }
            return Mono.just(exchange.getRequest().getRemoteAddress()
                    .getAddress().getHostAddress());
        };
    }

    /**
     * API限流Key解析器（按请求路径）
     */
    @Bean
    public KeyResolver apiKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest().getPath().value()
        );
    }
}
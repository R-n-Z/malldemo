package com.macro.mall.portal.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付相关消息队列配置
 */
@Configuration
public class PayRabbitMqConfig {

    /**
     * 支付成功队列
     */
    @Bean
    public Queue paySuccessQueue() {
        return QueueBuilder
                .durable("mall.pay.success")
                // 消息TTL（24小时过期）
                .withArgument("x-message-ttl", 24 * 60 * 60 * 1000)
                // 死信队列
                .withArgument("x-dead-letter-exchange", "mall.pay.dead")
                .withArgument("x-dead-letter-routing-key", "mall.pay.dead.key")
                .build();
    }

    /**
     * 支付成功死信队列（处理失败消息）
     */
    @Bean
    public Queue payDeadQueue() {
        return QueueBuilder
                .durable("mall.pay.dead")
                .build();
    }

    /**
     * 支付成功交换机
     */
    @Bean
    public DirectExchange paySuccessExchange() {
        return ExchangeBuilder
                .directExchange("mall.pay.success")
                .durable(true)
                .build();
    }

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange payDeadExchange() {
        return ExchangeBuilder
                .directExchange("mall.pay.dead")
                .durable(true)
                .build();
    }

    /**
     * 绑定队列到交换机
     */
    @Bean
    public Binding paySuccessBinding() {
        return BindingBuilder
                .bind(paySuccessQueue())
                .to(paySuccessExchange())
                .with("mall.pay.success");
    }

    /**
     * 绑定死信队列
     */
    @Bean
    public Binding payDeadBinding() {
        return BindingBuilder
                .bind(payDeadQueue())
                .to(payDeadExchange())
                .with("mall.pay.dead.key");
    }
}
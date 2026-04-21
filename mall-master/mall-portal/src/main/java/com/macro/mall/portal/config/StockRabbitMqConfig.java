package com.macro.mall.portal.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 库存相关消息队列配置
 */
@Configuration
public class StockRabbitMqConfig {

    /**
     * 库存预扣超时队列（死信队列）
     */
    @Bean
    public Queue stockLockTtlQueue() {
        return QueueBuilder
                .durable("mall.stock.lock.ttl")
                .withArgument("x-dead-letter-exchange", "mall.stock.direct")
                .withArgument("x-dead-letter-routing-key", "mall.stock.lock.release")
                .build();
    }

    /**
     * 库存预扣超时延迟队列
     */
    @Bean
    public Queue stockLockDelayQueue() {
        return QueueBuilder
                .durable("mall.stock.lock.delay")
                .withArgument("x-message-ttl", 5 * 60 * 1000) // 5分钟
                .withArgument("x-dead-letter-exchange", "mall.stock.direct")
                .withArgument("x-dead-letter-routing-key", "mall.stock.lock.release")
                .build();
    }

    /**
     * 库存释放队列
     */
    @Bean
    public Queue stockReleaseQueue() {
        return QueueBuilder
                .durable("mall.stock.release")
                .build();
    }

    /**
     * 库存同步队列
     */
    @Bean
    public Queue stockSyncQueue() {
        return QueueBuilder
                .durable("mall.stock.sync")
                .build();
    }

    /**
     * 库存交换机
     */
    @Bean
    public DirectExchange stockDirect() {
        return ExchangeBuilder
                .directExchange("mall.stock.direct")
                .durable(true)
                .build();
    }

    /**
     * 绑定延迟队列
     */
    @Bean
    public Binding stockLockDelayBinding() {
        return BindingBuilder
                .bind(stockLockDelayQueue())
                .to(stockDirect())
                .with("mall.stock.lock.delay");
    }

    /**
     * 绑定释放队列
     */
    @Bean
    public Binding stockReleaseBinding() {
        return BindingBuilder
                .bind(stockReleaseQueue())
                .to(stockDirect())
                .with("mall.stock.lock.release");
    }

    /**
     * 绑定同步队列
     */
    @Bean
    public Binding stockSyncBinding() {
        return BindingBuilder
                .bind(stockSyncQueue())
                .to(stockDirect())
                .with("mall.stock.sync");
    }
}
package com.macro.mall.portal.config;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ配置 - 订单超时取消
 */
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
public class RocketMQOrderTimeoutConfig {

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${rocketmq.producer.group:order-timeout-producer-group}")
    private String producerGroup;

    /**
     * 订单超时消息生产者
     */
    @Bean
    public DefaultMQProducer orderTimeoutProducer() {
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(5000);
        try {
            producer.start();
        } catch (Exception e) {
            throw new RuntimeException("启动订单超时消息生产者失败", e);
        }
        return producer;
    }
}
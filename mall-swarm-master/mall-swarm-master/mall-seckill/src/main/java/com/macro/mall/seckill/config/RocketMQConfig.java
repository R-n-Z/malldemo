package com.macro.mall.seckill.config;

import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.common.message.MessageConst;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RocketMQ配置 - 秒杀模块
 */
@Configuration
public class RocketMQConfig {

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${rocketmq.producer.group:seckill-producer-group}")
    private String producerGroup;

    /**
     * 事务消息生产者
     */
    @Bean
    public TransactionMQProducer seckillTransactionProducer() {
        TransactionMQProducer producer = new TransactionMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMessageTimeout(5000);
        producer.setRetryTimesWhenSendFailed(2);
        return producer;
    }

    /**
     * 普通消息生产者
     */
    @Bean
    public org.apache.rocketmq.client.producer.DefaultMQProducer seckillProducer() {
        org.apache.rocketmq.client.producer.DefaultMQProducer producer =
                new org.apache.rocketmq.client.producer.DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMessageTimeout(5000);
        producer.setRetryTimesWhenSendFailed(2);
        return producer;
    }

    /**
     * 消息属性配置
     */
    public static Map<String, String> buildMessageHeaders(String messageType, String traceId) {
        Map<String, String> headers = new HashMap<>();
        headers.put(MessageConst.PROPERTY_MESSAGE_TYPE, messageType);
        headers.put(MessageConst.PROPERTY_TRACE_ID, traceId);
        headers.put(MessageConst.PROPERTY_DELAY_TIME_LEVEL, "0");
        return headers;
    }
}
package com.macro.mall.order.config;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.common.message.MessageConst;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RocketMQ生产者配置 - 订单落库
 */
@Configuration
public class RocketMQProducerConfig {

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.producer.group}")
    private String producerGroup;

    @Value("${rocketmq.topic.order-persist:order-persist-topic}")
    private String orderPersistTopic;

    /**
     * 事务消息生产者（用于订单创建）
     */
    @Bean
    public TransactionMQProducer orderTransactionProducer() {
        TransactionMQProducer producer = new TransactionMQProducer(producerGroup + "-transaction");
        producer.setNamesrvAddr(nameServer);
        producer.setSendMessageTimeout(5000);
        producer.setRetryTimesWhenSendFailed(2);
        return producer;
    }

    /**
     * 普通消息生产者（用于订单落库）
     */
    @Bean
    public DefaultMQProducer orderPersistProducer() {
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup + "-persist");
        producer.setNamesrvAddr(nameServer);
        producer.setSendMessageTimeout(5000);
        producer.setRetryTimesWhenSendFailed(3);
        return producer;
    }

    /**
     * 构建消息属性
     */
    public static Map<String, String> buildMessageHeaders(String messageType, String traceId) {
        Map<String, String> headers = new HashMap<>();
        headers.put(MessageConst.PROPERTY_MESSAGE_TYPE, messageType);
        headers.put(MessageConst.PROPERTY_TRACE_ID, traceId);
        return headers;
    }
}
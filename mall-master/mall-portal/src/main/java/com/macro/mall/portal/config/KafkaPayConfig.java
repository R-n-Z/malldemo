package com.macro.mall.portal.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka配置类 - 支付成功消息
 */
@Configuration
@EnableKafka
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaPayConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:pay-consumer-group}")
    private String consumerGroupId;

    /**
     * 支付成功Topic
     */
    @Bean
    public NewTopic paySuccessTopic() {
        return TopicBuilder.name("mall-pay-success")
                .partitions(3)  // 3个分区
                .replicas(1)    // 副本数（生产环境建议>=3）
                .build();
    }

    /**
     * 支付重试Topic
     */
    @Bean
    public NewTopic payRetryTopic() {
        return TopicBuilder.name("mall-pay-retry")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 生产者配置
     */
    @Bean
    public ProducerFactory<String, String> payProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        // Kafka集群地址
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // 序列化器
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // 幂等发送
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // acks=all，所有副本确认
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        // 重试次数
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        // 重试间隔
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
        // 批次大小
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        //  linger.ms，延迟发送时间
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        // 缓冲区大小
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> payKafkaTemplate() {
        return new KafkaTemplate<>(payProducerFactory());
    }

    /**
     * 消费者配置
     */
    @Bean
    public ConsumerFactory<String, String> payConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        // Kafka集群地址
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // 消费者组
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        // 自动提交offset（改为手动提交）
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // 反序列化器
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // 从最早位置消费
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // 最大拉取记录数
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        // 会话超时时间
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * 监听器容器配置
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> payKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(payConsumerFactory());
        // 手动提交offset
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        // 并发数（建议与分区数一致）
        factory.setConcurrency(3);
        return factory;
    }
}
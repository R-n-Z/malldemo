package com.macro.mall.stock.config;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ消费者配置 - 库存服务
 */
@Configuration
public class RocketMQConsumerConfig {

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.consumer.group}")
    private String consumerGroup;

    @Value("${rocketmq.topic.stock-deduct}")
    private String stockDeductTopic;

    /**
     * 库存扣减消费者
     */
    @Bean
    public DefaultMQPushConsumer stockDeductConsumer() throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.setSubscribe(stockDeductTopic, "*");
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setConsumeThreadMin(3);
        consumer.setConsumeThreadMax(10);
        
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            try {
                for (MessageExt msg : msgs) {
                    String message = new String(msg.getBody(), "UTF-8");
                    // 消息由StockDeductConsumer处理
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
            } catch (Exception e) {
                context.setDelayLevelWhenNextConsume(3);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        
        consumer.start();
        return consumer;
    }
}
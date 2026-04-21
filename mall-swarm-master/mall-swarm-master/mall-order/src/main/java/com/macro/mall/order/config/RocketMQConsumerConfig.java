package com.macro.mall.order.config;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * RocketMQ消费者配置 - 订单服务
 */
@Configuration
public class RocketMQConsumerConfig {

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.consumer.group}")
    private String consumerGroup;

    @Value("${rocketmq.topic.seckill-order}")
    private String seckillOrderTopic;

    @Value("${rocketmq.topic.order-persist}")
    private String orderPersistTopic;

    @Value("${rocketmq.topic.order-timeout}")
    private String orderTimeoutTopic;

    /**
     * 秒杀订单消费者
     */
    @Bean
    public DefaultMQPushConsumer seckillOrderConsumer() throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.setSubscribe(seckillOrderTopic, "*");
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setConsumeThreadMin(3);
        consumer.setConsumeThreadMax(10);
        
        // 注册消息监听器
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            try {
                for (MessageExt msg : msgs) {
                    String message = new String(msg.getBody(), "UTF-8");
                    // 消息由OrderConsumer处理
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

    /**
     * 订单落库消费者
     */
    @Bean
    public DefaultMQPushConsumer orderPersistConsumer() throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup + "-persist");
        consumer.setNamesrvAddr(nameServer);
        consumer.setSubscribe(orderPersistTopic, "*");
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setConsumeThreadMin(4);
        consumer.setConsumeThreadMax(8);
        
        // 注册消息监听器
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            try {
                for (MessageExt msg : msgs) {
                    String message = new String(msg.getBody(), "UTF-8");
                    // 消息由OrderPersistConsumer处理
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

    /**
     * 订单超时消费者
     */
    @Bean
    public DefaultMQPushConsumer orderTimeoutConsumer() throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup + "-timeout");
        consumer.setNamesrvAddr(nameServer);
        consumer.setSubscribe(orderTimeoutTopic, "*");
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setConsumeThreadMin(2);
        consumer.setConsumeThreadMax(4);
        
        // 注册消息监听器
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            try {
                for (MessageExt msg : msgs) {
                    String message = new String(msg.getBody(), "UTF-8");
                    // 消息由OrderTimeoutConsumer处理
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
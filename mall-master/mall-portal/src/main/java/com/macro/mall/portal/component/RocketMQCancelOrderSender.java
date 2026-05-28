package com.macro.mall.portal.component;

import com.macro.mall.portal.config.RocketMQOrderTimeoutConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * RocketMQ取消订单消息发送者
 */
@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
public class RocketMQCancelOrderSender {

    private static final String ORDER_TIMEOUT_TOPIC = "order-timeout-topic";
    private static final String ORDER_TIMEOUT_TAG = "cancel";

    /**
     * RocketMQ延迟消息级别（秒）
     * 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     */
    private static final int DELAY_LEVEL_1S = 1;
    private static final int DELAY_LEVEL_5S = 2;
    private static final int DELAY_LEVEL_10S = 3;
    private static final int DELAY_LEVEL_30S = 4;
    private static final int DELAY_LEVEL_1M = 5;
    private static final int DELAY_LEVEL_2M = 6;
    private static final int DELAY_LEVEL_3M = 7;
    private static final int DELAY_LEVEL_4M = 8;
    private static final int DELAY_LEVEL_5M = 9;
    private static final int DELAY_LEVEL_10M = 14;
    private static final int DELAY_LEVEL_30M = 15;
    private static final int DELAY_LEVEL_1H = 16;
    private static final int DELAY_LEVEL_2H = 17;

    @Autowired
    private DefaultMQProducer orderTimeoutProducer;

    /**
     * 发送延迟取消订单消息
     * @param orderId 订单ID
     * @param delayMs 延迟毫秒数
     */
    public void sendDelayMessage(Long orderId, long delayMs) {
        try {
            // 将毫秒转换为RocketMQ延迟级别
            int delayLevel = convertToDelayLevel(delayMs);
            
            // 构建消息
            Message message = new Message(
                    ORDER_TIMEOUT_TOPIC,      // Topic
                    ORDER_TIMEOUT_TAG,        // Tag
                    String.valueOf(orderId).getBytes()  // Body
            );
            
            // 设置延迟级别
            message.setDelayTimeLevel(delayLevel);
            
            // 发送消息
            orderTimeoutProducer.send(message);
            
            log.info("RocketMQ延迟消息发送成功: orderId={}, delayMs={}, delayLevel={}", 
                    orderId, delayMs, delayLevel);
                    
        } catch (Exception e) {
            log.error("发送RocketMQ延迟消息失败: orderId={}", orderId, e);
            throw new RuntimeException("发送延迟消息失败", e);
        }
    }

    /**
     * 将毫秒转换为RocketMQ延迟级别
     */
    private int convertToDelayLevel(long delayMs) {
        if (delayMs <= 1000) {
            return DELAY_LEVEL_1S;
        } else if (delayMs <= 5000) {
            return DELAY_LEVEL_5S;
        } else if (delayMs <= 10000) {
            return DELAY_LEVEL_10S;
        } else if (delayMs <= 30000) {
            return DELAY_LEVEL_30S;
        } else if (delayMs <= 60000) {
            return DELAY_LEVEL_1M;
        } else if (delayMs <= 120000) {
            return DELAY_LEVEL_2M;
        } else if (delayMs <= 180000) {
            return DELAY_LEVEL_3M;
        } else if (delayMs <= 240000) {
            return DELAY_LEVEL_4M;
        } else if (delayMs <= 300000) {
            return DELAY_LEVEL_5M;
        } else if (delayMs <= 600000) {
            return DELAY_LEVEL_10M;
        } else if (delayMs <= 1800000) {
            return DELAY_LEVEL_30M;
        } else if (delayMs <= 3600000) {
            return DELAY_LEVEL_1H;
        } else {
            return DELAY_LEVEL_2H;
        }
    }
}
package com.macro.mall.order.component;

import com.alibaba.fastjson.JSON;
import com.macro.mall.order.domain.OrderPersistMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * 订单落库消息发送者
 */
@Slf4j
@Component
public class OrderPersistMessageSender {

    @Autowired
    private DefaultMQProducer orderPersistProducer;

    @Value("${rocketmq.topic.order-persist:order-persist-topic}")
    private String orderPersistTopic;

    /**
     * 发送订单落库消息（异步）
     */
    public void sendPersistMessage(OrderPersistMessage message) {
        try {
            String messageJson = JSON.toJSONString(message);
            Message msg = new Message(orderPersistTopic, 
                    message.getOrderSn(), 
                    messageJson.getBytes(StandardCharsets.UTF_8));
            
            // 设置消息属性
            msg.putUserProperty("traceId", message.getTraceId());
            msg.putUserProperty("messageType", "ORDER_PERSIST");
            msg.putUserProperty("idempotentToken", message.getIdempotentToken());
            
            // 异步发送
            CompletableFuture<org.apache.rocketmq.client.producer.SendResult> future = 
                    orderPersistProducer.sendAsync(msg);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("订单落库消息发送成功: orderSn={}, traceId={}, partition={}, offset={}", 
                            message.getOrderSn(), 
                            message.getTraceId(),
                            result.getMessageQueue().getQueueId(),
                            result.getQueueOffset());
                } else {
                    log.error("订单落库消息发送失败: orderSn={}, traceId={}", 
                            message.getOrderSn(), message.getTraceId(), ex);
                    // 发送失败，记录到死信队列或重试
                    handleSendFailed(message, ex);
                }
            });
            
        } catch (Exception e) {
            log.error("发送订单落库消息异常: orderSn={}", message.getOrderSn(), e);
            throw new RuntimeException("发送订单落库消息失败", e);
        }
    }

    /**
     * 发送延迟重试消息
     */
    public void sendRetryMessage(OrderPersistMessage message, long delayMs) {
        message.setRetryCount(message.getRetryCount() + 1);
        message.setTraceId(java.util.UUID.randomUUID().toString().replace("-", ""));
        
        String messageJson = JSON.toJSONString(message);
        Message msg = new Message(orderPersistTopic, 
                message.getOrderSn(), 
                messageJson.getBytes(StandardCharsets.UTF_8));
        
        // 设置延迟级别
        int delayLevel = calculateDelayLevel(delayMs);
        msg.setDelayTimeLevel(delayLevel);
        
        try {
            orderPersistProducer.send(msg);
            log.info("重试消息发送成功: orderSn={}, retryCount={}, delayMs={}", 
                    message.getOrderSn(), message.getRetryCount(), delayMs);
        } catch (Exception e) {
            log.error("重试消息发送失败: orderSn={}", message.getOrderSn(), e);
        }
    }

    /**
     * 处理发送失败
     */
    private void handleSendFailed(OrderPersistMessage message, Exception ex) {
        // 记录到数据库待补偿表
        log.warn("订单落库消息发送失败，待补偿: orderSn={}", message.getOrderSn());
        // TODO: 写入到补偿表，由定时任务重试
    }

    /**
     * 计算延迟级别（RocketMQ延迟级别：1-18对应1s-2h）
     */
    private int calculateDelayLevel(long delayMs) {
        if (delayMs <= 1000) return 1;
        if (delayMs <= 5000) return 2;
        if (delayMs <= 10000) return 3;
        if (delayMs <= 30000) return 4;
        if (delayMs <= 60000) return 5;
        if (delayMs <= 120000) return 6;
        if (delayMs <= 180000) return 7;
        if (delayMs <= 300000) return 8;
        if (delayMs <= 600000) return 9;
        if (delayMs <= 1800000) return 10;
        if (delayMs <= 3600000) return 11;
        if (delayMs <= 7200000) return 12;
        if (delayMs <= 14400000) return 13;
        if (delayMs <= 28800000) return 14;
        if (delayMs <= 43200000) return 15;
        if (delayMs <= 86400000) return 16;
        if (delayMs <= 172800000) return 17;
        return 18;
    }
}
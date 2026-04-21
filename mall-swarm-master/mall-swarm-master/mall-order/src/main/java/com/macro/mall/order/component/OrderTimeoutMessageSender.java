package com.macro.mall.order.component;

import com.alibaba.fastjson.JSON;
import com.macro.mall.order.domain.OrderTimeoutMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 订单超时消息发送者
 */
@Slf4j
@Component
public class OrderTimeoutMessageSender {

    @Autowired
    private DefaultMQProducer orderPersistProducer;

    @Value("${rocketmq.topic.order-timeout:order-timeout-topic}")
    private String orderTimeoutTopic;

    /**
     * 发送订单超时取消消息（延迟消息）
     * 
     * @param message 超时消息
     * @param delayMs 延迟时间（毫秒）
     */
    public void sendTimeoutMessage(OrderTimeoutMessage message, long delayMs) {
        try {
            String messageJson = JSON.toJSONString(message);
            Message msg = new Message(orderTimeoutTopic, 
                    message.getOrderSn(), 
                    messageJson.getBytes(StandardCharsets.UTF_8));
            
            // 设置消息属性
            msg.putUserProperty("traceId", message.getTraceId());
            msg.putUserProperty("messageType", "ORDER_TIMEOUT");
            msg.putUserProperty("orderSn", message.getOrderSn());
            
            // 设置延迟级别
            int delayLevel = calculateDelayLevel(delayMs);
            msg.setDelayTimeLevel(delayLevel);
            
            // 发送消息
            orderPersistProducer.send(msg);
            
            log.info("订单超时消息已发送: orderSn={}, delayMs={}, delayLevel={}", 
                    message.getOrderSn(), delayMs, delayLevel);
            
        } catch (Exception e) {
            log.error("发送订单超时消息失败: orderSn={}", message.getOrderSn(), e);
            throw new RuntimeException("发送订单超时消息失败", e);
        }
    }

    /**
     * 发送订单超时消息（使用配置的默认超时时间）
     */
    public void sendTimeoutMessage(OrderTimeoutMessage message) {
        long delayMs = message.getTimeoutMinutes() * 60 * 1000L;
        sendTimeoutMessage(message, delayMs);
    }

    /**
     * 取消订单超时消息（用于订单支付成功后取消超时任务）
     */
    public void cancelTimeoutMessage(String orderSn) {
        log.info("取消订单超时任务: orderSn={}", orderSn);
        // RocketMQ延迟消息不支持直接取消
        // 消费者端通过检查订单状态来判断是否需要处理
    }

    /**
     * 计算延迟级别（RocketMQ延迟级别：1-18对应1s-2h）
     */
    private int calculateDelayLevel(long delayMs) {
        if (delayMs <= 1000) return 1;
        if (delayMs <= 5000) return 2;
        if (delayMs <= 10000) return 3;
        if (delayMs <= 30000) return 4;
        if (delayMs <= 60000) return 5;          // 1分钟
        if (delayMs <= 120000) return 6;         // 2分钟
        if (delayMs <= 180000) return 7;         // 3分钟
        if (delayMs <= 300000) return 8;         // 5分钟
        if (delayMs <= 600000) return 9;         // 10分钟
        if (delayMs <= 900000) return 10;        // 15分钟
        if (delayMs <= 1800000) return 11;       // 30分钟
        if (delayMs <= 3600000) return 12;       // 1小时
        if (delayMs <= 7200000) return 13;       // 2小时
        return 14;                                // 超过2小时用最大延迟
    }
}
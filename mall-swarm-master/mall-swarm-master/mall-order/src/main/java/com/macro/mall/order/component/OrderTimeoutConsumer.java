package com.macro.mall.order.component;

import com.alibaba.fastjson.JSON;
import com.macro.mall.order.domain.OrderTimeoutMessage;
import com.macro.mall.order.service.OrderTimeoutService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 订单超时消费者
 */
@Slf4j
@Component
public class OrderTimeoutConsumer implements MessageListenerConcurrently {

    @Autowired
    private OrderTimeoutService orderTimeoutService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${rocketmq.topic.order-timeout}")
    private String orderTimeoutTopic;

    private static final String ORDER_TIMEOUT_LOCK_KEY = "order:timeout:lock:";
    private static final String ORDER_PAID_KEY = "order:paid:";
    private static final int MAX_RETRY_COUNT = 3;

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, 
                                                     ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            try {
                String messageJson = new String(msg.getBody(), StandardCharsets.UTF_8);
                OrderTimeoutMessage message = JSON.parseObject(messageJson, OrderTimeoutMessage.class);
                
                String orderSn = message.getOrderSn();
                log.info("收到订单超时消息: orderSn={}, traceId={}", orderSn, message.getTraceId());
                
                // 1. 检查订单是否已支付（幂等）
                if (isOrderPaid(orderSn)) {
                    log.info("订单已支付，跳过超时处理: orderSn={}", orderSn);
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                
                // 2. 获取处理锁
                String lockKey = ORDER_TIMEOUT_LOCK_KEY + orderSn;
                if (!acquireLock(lockKey)) {
                    log.warn("订单超时处理中: orderSn={}", orderSn);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
                
                try {
                    // 3. 执行超时取消
                    orderTimeoutService.cancelTimeoutOrder(orderSn, message);
                    
                    log.info("订单超时处理完成: orderSn={}", orderSn);
                    
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    
                } finally {
                    releaseLock(lockKey);
                }
                
            } catch (Exception e) {
                log.error("处理订单超时消息失败: msgId={}", msg.getMsgId(), e);
                
                try {
                    String messageJson = new String(msg.getBody(), StandardCharsets.UTF_8);
                    OrderTimeoutMessage message = JSON.parseObject(messageJson, OrderTimeoutMessage.class);
                    
                    if (message.getRetryCount() < MAX_RETRY_COUNT) {
                        context.setDelayLevelWhenNextConsume(3);
                        log.info("准备重试订单超时处理: orderSn={}, retryCount={}", 
                                message.getOrderSn(), message.getRetryCount());
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    } else {
                        log.error("订单超时处理重试次数超限: orderSn={}", message.getOrderSn());
                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    }
                } catch (Exception ex) {
                    log.error("解析消息失败: msgId={}", msg.getMsgId(), ex);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    /**
     * 检查订单是否已支付
     */
    private boolean isOrderPaid(String orderSn) {
        String key = ORDER_PAID_KEY + orderSn;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 获取处理锁
     */
    private boolean acquireLock(String lockKey) {
        Boolean lock = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", 10, TimeUnit.MINUTES);
        return Boolean.TRUE.equals(lock);
    }

    /**
     * 释放锁
     */
    private void releaseLock(String lockKey) {
        redisTemplate.delete(lockKey);
    }
}
package com.macro.mall.order.component;

import com.alibaba.fastjson.JSON;
import com.macro.mall.order.domain.OrderPersistMessage;
import com.macro.mall.order.service.OrderAsyncPersistService;
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
 * 订单落库消费者
 */
@Slf4j
@Component
public class OrderPersistConsumer implements MessageListenerConcurrently {

    @Autowired
    private OrderAsyncPersistService orderAsyncPersistService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${rocketmq.topic.order-persist}")
    private String orderPersistTopic;

    private static final String ORDER_PERSIST_LOCK_KEY = "order:persist:lock:";
    private static final String ORDER_PERSISTED_KEY = "order:persisted:";
    private static final int MAX_RETRY_COUNT = 3;

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, 
                                                     ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            try {
                String messageJson = new String(msg.getBody(), StandardCharsets.UTF_8);
                OrderPersistMessage message = JSON.parseObject(messageJson, OrderPersistMessage.class);
                
                String orderSn = message.getOrderSn();
                log.info("收到订单落库消息: orderSn={}, traceId={}", orderSn, message.getTraceId());
                
                // 1. 幂等校验
                if (isOrderPersisted(orderSn)) {
                    log.info("订单已落库，跳过: orderSn={}", orderSn);
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                
                // 2. 获取处理锁
                String lockKey = ORDER_PERSIST_LOCK_KEY + orderSn;
                if (!acquireLock(lockKey)) {
                    log.warn("订单正在处理中: orderSn={}", orderSn);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
                
                try {
                    // 3. 异步落库
                    orderAsyncPersistService.persistOrder(message);
                    
                    // 4. 标记已落库
                    markOrderPersisted(orderSn);
                    
                    log.info("订单落库成功: orderSn={}", orderSn);
                    
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    
                } finally {
                    releaseLock(lockKey);
                }
                
            } catch (Exception e) {
                log.error("处理订单落库消息失败: msgId={}", msg.getMsgId(), e);
                
                // 解析消息获取重试次数
                try {
                    String messageJson = new String(msg.getBody(), StandardCharsets.UTF_8);
                    OrderPersistMessage message = JSON.parseObject(messageJson, OrderPersistMessage.class);
                    
                    if (message.getRetryCount() < MAX_RETRY_COUNT) {
                        // 重试
                        context.setDelayLevelWhenNextConsume(3);
                        log.info("准备重试订单落库: orderSn={}, retryCount={}", 
                                message.getOrderSn(), message.getRetryCount());
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    } else {
                        // 超过重试次数，记录到死信
                        log.error("订单落库重试次数超限: orderSn={}", message.getOrderSn());
                        // TODO: 写入死信表
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
     * 检查订单是否已落库
     */
    private boolean isOrderPersisted(String orderSn) {
        String key = ORDER_PERSISTED_KEY + orderSn;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 标记订单已落库
     */
    private void markOrderPersisted(String orderSn) {
        String key = ORDER_PERSISTED_KEY + orderSn;
        redisTemplate.opsForValue().set(key, "1", 24, TimeUnit.HOURS);
    }

    /**
     * 获取处理锁
     */
    private boolean acquireLock(String lockKey) {
        Boolean lock = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", 5, TimeUnit.MINUTES);
        return Boolean.TRUE.equals(lock);
    }

    /**
     * 释放锁
     */
    private void releaseLock(String lockKey) {
        redisTemplate.delete(lockKey);
    }
}
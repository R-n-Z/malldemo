package com.macro.mall.stock.component;

import com.alibaba.fastjson.JSON;
import com.macro.mall.stock.domain.StockDeductMessage;
import com.macro.mall.stock.service.StockDeductService;
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
 * 库存扣减消费者 - 库存服务
 */
@Slf4j
@Component
public class StockDeductConsumer implements MessageListenerConcurrently {

    @Autowired
    private StockDeductService stockDeductService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String STOCK_LOCK_KEY = "stock:lock:";
    private static final String STOCK_PROCESSED_KEY = "stock:processed:";

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, 
                                                     ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            try {
                String messageJson = new String(msg.getBody(), StandardCharsets.UTF_8);
                StockDeductMessage deductMessage = JSON.parseObject(messageJson, 
                        StockDeductMessage.class);
                
                String orderSn = deductMessage.getOrderSn();
                String idempotentKey = deductMessage.getIdempotentToken();
                
                log.info("收到库存扣减消息: orderSn={}, operationType={}, traceId={}", 
                        orderSn, deductMessage.getOperationType(), deductMessage.getTraceId());
                
                // 1. 幂等校验
                if (isProcessed(idempotentKey)) {
                    log.info("库存操作已处理，跳过: idempotentKey={}", idempotentKey);
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                
                // 2. 获取库存锁
                String lockKey = STOCK_LOCK_KEY + deductMessage.getProductId();
                if (!acquireLock(lockKey)) {
                    log.warn("库存操作进行中: productId={}", deductMessage.getProductId());
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
                
                try {
                    // 3. 执行库存操作
                    if (deductMessage.getOperationType() == 1) {
                        // 扣减库存
                        stockDeductService.deductStock(deductMessage.getProductId(), 
                                deductMessage.getCount());
                    } else if (deductMessage.getOperationType() == 2) {
                        // 回滚库存
                        stockDeductService.rollbackStock(deductMessage.getProductId(), 
                                deductMessage.getCount());
                    }
                    
                    // 4. 标记已处理
                    markProcessed(idempotentKey);
                    
                    log.info("库存操作成功: orderSn={}, operationType={}", 
                            orderSn, deductMessage.getOperationType());
                    
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    
                } finally {
                    releaseLock(lockKey);
                }
                
            } catch (Exception e) {
                log.error("处理库存扣减消息失败: msgId={}", msg.getMsgId(), e);
                context.setDelayLevelWhenNextConsume(3);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    /**
     * 检查是否已处理
     */
    private boolean isProcessed(String idempotentKey) {
        String key = STOCK_PROCESSED_KEY + idempotentKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 标记已处理
     */
    private void markProcessed(String idempotentKey) {
        String key = STOCK_PROCESSED_KEY + idempotentKey;
        redisTemplate.opsForValue().set(key, "1", 24, TimeUnit.HOURS);
    }

    /**
     * 获取锁
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
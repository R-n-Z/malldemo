package com.macro.mall.portal.component;

import com.alibaba.fastjson.JSON;
import com.macro.mall.portal.domain.PaySuccessMessage;
import com.macro.mall.portal.service.OmsPortalOrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Kafka支付成功消息消费者
 */
@Slf4j
@Component
public class KafkaPayConsumer {

    private static final String PAY_LOCK_KEY = "pay:lock:";
    private static final int LOCK_EXPIRE_MINUTES = 5;

    @Autowired
    private OmsPortalOrderService portalOrderService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private KafkaPayMessageSender kafkaPayMessageSender;

    /**
     * 消费支付成功消息
     */
    @KafkaListener(
            topics = "mall-pay-success",
            groupId = "${spring.kafka.consumer.group-id:pay-consumer-group}",
            containerFactory = "payKafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String orderSn = record.key();
        String messageJson = record.value();
        
        log.info("收到Kafka支付消息: orderSn={}, partition={}, offset={}", 
                orderSn, record.partition(), record.offset());
        
        try {
            // 1. 幂等校验
            if (!acquireLock(orderSn)) {
                log.info("订单正在处理中，跳过: orderSn={}", orderSn);
                acknowledgment.acknowledge();
                return;
            }
            
            // 2. 解析消息
            PaySuccessMessage message = JSON.parseObject(messageJson, PaySuccessMessage.class);
            
            // 3. 调用订单服务更新状态
            portalOrderService.paySuccessByOrderSn(message.getOrderSn(), message.getPayType());
            
            log.info("订单状态更新成功: orderSn={}", orderSn);
            
            // 4. 手动提交offset
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("处理支付消息失败: orderSn={}", orderSn, e);
            
            // 5. 释放锁，允许重试
            releaseLock(orderSn);
            
            // 6. 发送重试消息（最多重试3次）
            try {
                PaySuccessMessage message = JSON.parseObject(messageJson, PaySuccessMessage.class);
                if (message.getRetryCount() < 3) {
                    kafkaPayMessageSender.sendDelayRetryMessage(message, 5000); // 5秒后重试
                } else {
                    log.error("重试次数超过限制，消息进入死信: orderSn={}", orderSn);
                    // TODO: 记录到数据库死信表
                }
            } catch (Exception ex) {
                log.error("发送重试消息失败: orderSn={}", orderSn, ex);
            }
        } finally {
            // 释放锁
            releaseLock(orderSn);
        }
    }

    /**
     * 获取分布式锁
     */
    private boolean acquireLock(String orderSn) {
        String lockKey = PAY_LOCK_KEY + orderSn;
        Boolean lock = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_EXPIRE_MINUTES, TimeUnit.MINUTES);
        return Boolean.TRUE.equals(lock);
    }

    /**
     * 释放分布式锁
     */
    private void releaseLock(String orderSn) {
        String lockKey = PAY_LOCK_KEY + orderSn;
        redisTemplate.delete(lockKey);
    }
}
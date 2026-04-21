package com.macro.mall.seckill.component;

import com.alibaba.fastjson.JSON;
import com.macro.mall.seckill.domain.SeckillOrderMessage;
import com.macro.mall.seckill.service.OrderLockService;
import com.macro.mall.seckill.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.redisson.api.RLock;

import java.util.Date;

/**
 * 秒杀订单消费者
 */
@Slf4j
@Component
public class OrderConsumer {

    @Autowired
    private OrderLockService orderLockService;

    @Autowired
    private StockService stockService;

    @Value("${seckill.order.retry-count:3}")
    private int maxRetryCount;

    /**
     * 消费秒杀订单消息
     */
    @KafkaListener(
            topics = "seckill-order-topic",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "seckillKafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String orderSn = record.key();
        String messageJson = record.value();
        
        log.info("收到秒杀订单消息: orderSn={}, partition={}, offset={}", 
                orderSn, record.partition(), record.offset());
        
        SeckillOrderMessage message = JSON.parseObject(messageJson, SeckillOrderMessage.class);
        
        // 获取订单锁，防止重复处理
        RLock orderLock = orderLockService.getOrderLock(orderSn);
        
        try {
            if (orderLock.tryLock(10, 60, java.util.concurrent.TimeUnit.SECONDS)) {
                try {
                    // 创建订单
                    createOrder(message);
                    
                    // 手动提交offset
                    acknowledgment.acknowledge();
                    
                    log.info("秒杀订单创建成功: orderSn={}", orderSn);
                    
                } finally {
                    orderLockService.unlock(orderLock);
                }
            } else {
                log.warn("订单正在处理中，跳过: orderSn={}", orderSn);
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            log.error("处理秒杀订单失败: orderSn={}", orderSn, e);
            
            // 重试逻辑
            if (message.getRetryCount() < maxRetryCount) {
                // 发送重试消息
                // 这里简化处理，实际应该发送到重试Topic
                log.info("准备重试: orderSn={}, retryCount={}", orderSn, message.getRetryCount());
            } else {
                log.error("重试次数超过限制，订单创建失败: orderSn={}", orderSn);
                // 回滚库存
                stockService.rollbackStock(message.getProductId(), message.getCount());
            }
        }
    }

    /**
     * 创建订单（实际业务逻辑）
     */
    private void createOrder(SeckillOrderMessage message) {
        // 1. 真实扣减库存（从预扣减转为真实扣减）
        boolean deducted = stockService.deductStock(message.getProductId(), message.getCount());
        if (!deducted) {
            throw new RuntimeException("库存扣减失败");
        }
        
        // 2. 创建订单记录到数据库
        // 这里简化处理，实际应该调用订单服务或直接操作数据库
        log.info("创建订单记录: orderSn={}, userId={}, productId={}, price={}",
                message.getOrderSn(),
                message.getUserId(),
                message.getProductId(),
                message.getSeckillPrice());
        
        // 3. 发送订单成功通知（可选）
        // messageSender.sendOrderSuccessNotification(message);
    }
}
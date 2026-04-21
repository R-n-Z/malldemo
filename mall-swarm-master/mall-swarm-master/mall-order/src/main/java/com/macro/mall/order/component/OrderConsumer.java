package com.macro.mall.order.component;

import com.alibaba.fastjson.JSON;
import com.macro.mall.order.domain.OrderPersistMessage;
import com.macro.mall.order.domain.SeckillOrderMessage;
import com.macro.mall.order.domain.StockDeductMessage;
import com.macro.mall.order.service.OrderCompensateService;
import com.macro.mall.order.service.OrderConsistencyService;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀订单消费者 - 订单模块
 * 
 * 优化：使用异步落库，将订单写入MQ，由消费者异步落库
 * 防护：集成订单一致性校验，防止幽灵订单
 */
@Slf4j
@Component
public class OrderConsumer implements MessageListenerConcurrently {

    @Autowired
    private OrderPersistMessageSender orderPersistMessageSender;

    @Autowired
    private StockMessageSender stockMessageSender;

    @Autowired
    private OrderTimeoutService orderTimeoutService;

    @Autowired
    private OrderConsistencyService consistencyService;

    @Autowired
    private OrderCompensateService compensateService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${rocketmq.topic.stock-deduct}")
    private String stockDeductTopic;

    private static final String ORDER_LOCK_KEY = "order:lock:";
    private static final String ORDER_PROCESSED_KEY = "order:processed:";

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, 
                                                     ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            try {
                String messageJson = new String(msg.getBody(), StandardCharsets.UTF_8);
                SeckillOrderMessage orderMessage = JSON.parseObject(messageJson, 
                        SeckillOrderMessage.class);
                
                String orderSn = orderMessage.getOrderSn();
                log.info("收到秒杀订单消息: orderSn={}, traceId={}", orderSn, orderMessage.getTraceId());
                
                // 1. 幂等校验
                if (isOrderProcessed(orderSn)) {
                    log.info("订单已处理，跳过: orderSn={}", orderSn);
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                
                // 2. 获取订单锁
                if (!acquireOrderLock(orderSn)) {
                    log.warn("订单正在处理中: orderSn={}", orderSn);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
                
                try {
                    // 3. 构建异步落库消息（不再直接写库）
                    OrderPersistMessage persistMessage = OrderPersistMessage.create(
                            orderSn,
                            orderMessage.getUserId(),
                            orderMessage.getProductId(),
                            "秒杀商品", // 商品名称，可从商品服务获取
                            orderMessage.getCount(),
                            orderMessage.getSeckillPrice(),
                            orderMessage.getSeckillPrice()
                    );
                    
                    // 4. 保存待落库数据（用于补偿）
                    compensateService.savePendingPersistData(
                            orderSn,
                            orderMessage.getUserId(),
                            orderMessage.getProductId(),
                            orderMessage.getCount(),
                            orderMessage.getSeckillPrice()
                    );
                    
                    // 5. 发送异步落库消息（立即返回，不阻塞）
                    orderPersistMessageSender.sendPersistMessage(persistMessage);
                    
                    // 6. 同步订单状态到Redis
                    consistencyService.syncOrderStatusToRedis(orderSn, 0); // 待付款
                    
                    // 7. 发送库存扣减消息
                    StockDeductMessage deductMessage = StockDeductMessage.deduct(
                            orderSn, 
                            orderMessage.getProductId(), 
                            orderMessage.getCount());
                    stockMessageSender.sendDeductMessage(deductMessage);
                    
                    // 8. 启动订单超时任务（30分钟未支付则取消）
                    orderTimeoutService.startTimeoutTask(
                            orderSn,
                            orderMessage.getUserId(),
                            orderMessage.getProductId(),
                            orderMessage.getCount()
                    );
                    
                    // 9. 标记订单已处理
                    markOrderProcessed(orderSn);
                    
                    log.info("秒杀订单处理成功（异步落库）: orderSn={}", orderSn);
                    
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    
                } finally {
                    releaseOrderLock(orderSn);
                }
                
            } catch (Exception e) {
                log.error("处理秒杀订单消息失败: msgId={}", msg.getMsgId(), e);
                // 失败重试
                context.setDelayLevelWhenNextConsume(3);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    /**
     * 检查订单是否已处理
     */
    private boolean isOrderProcessed(String orderSn) {
        String key = ORDER_PROCESSED_KEY + orderSn;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 标记订单已处理
     */
    private void markOrderProcessed(String orderSn) {
        String key = ORDER_PROCESSED_KEY + orderSn;
        redisTemplate.opsForValue().set(key, "1", 24, TimeUnit.HOURS);
    }

    /**
     * 获取订单锁
     */
    private boolean acquireOrderLock(String orderSn) {
        String lockKey = ORDER_LOCK_KEY + orderSn;
        Boolean lock = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", 5, TimeUnit.MINUTES);
        return Boolean.TRUE.equals(lock);
    }

    /**
     * 释放订单锁
     */
    private void releaseOrderLock(String orderSn) {
        String lockKey = ORDER_LOCK_KEY + orderSn;
        redisTemplate.delete(lockKey);
    }
}
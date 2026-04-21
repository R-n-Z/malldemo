package com.macro.mall.portal.component;

import com.macro.mall.portal.domain.SeckillMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 库存消息发送者
 */
@Slf4j
@Component
public class StockMessageSender {

    private static final String STOCK_LOCK_DELAY_QUEUE = "mall.stock.lock.delay";
    private static final String STOCK_RELEASE_QUEUE = "mall.stock.release";
    private static final String STOCK_SYNC_QUEUE = "mall.stock.sync";
    private static final String SECKILL_ORDER_QUEUE = "mall.seckill.order";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送库存预扣超时消息（5分钟后检查）
     */
    public void sendStockLockTimeoutMessage(String lockToken, Long skuId, Integer quantity) {
        Map<String, Object> message = new HashMap<>();
        message.put("lockToken", lockToken);
        message.put("skuId", skuId);
        message.put("quantity", quantity);
        message.put("sendTime", new Date());
        
        rabbitTemplate.convertAndSend(STOCK_LOCK_DELAY_QUEUE, message);
        log.info("库存预扣超时消息已发送: lockToken={}, skuId={}", lockToken, skuId);
    }

    /**
     * 发送库存释放消息
     */
    public void sendStockReleaseMessage(String lockToken, Long skuId, Integer quantity) {
        Map<String, Object> message = new HashMap<>();
        message.put("lockToken", lockToken);
        message.put("skuId", skuId);
        message.put("quantity", quantity);
        message.put("sendTime", new Date());
        
        rabbitTemplate.convertAndSend(STOCK_RELEASE_QUEUE, message);
        log.info("库存释放消息已发送: lockToken={}, skuId={}", lockToken, skuId);
    }

    /**
     * 发送库存同步消息
     */
    public void sendStockSyncMessage(Long skuId) {
        Map<String, Object> message = new HashMap<>();
        message.put("skuId", skuId);
        message.put("sendTime", new Date());
        
        rabbitTemplate.convertAndSend(STOCK_SYNC_QUEUE, message);
        log.info("库存同步消息已发送: skuId={}", skuId);
    }

    /**
     * 发送秒杀订单消息
     */
    public void sendSeckillMessage(SeckillMessage message) {
        rabbitTemplate.convertAndSend(SECKILL_ORDER_QUEUE, message);
        log.info("秒杀订单消息已发送: productId={}, memberId={}, orderSn={}",
                message.getProductId(), message.getMemberId(), message.getOrderSn());
    }
}
package com.macro.mall.portal.component;

import com.macro.mall.portal.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 库存消息消费者
 */
@Slf4j
@Component
public class StockMessageConsumer {

    @Autowired
    private StockService stockService;

    /**
     * 处理库存预扣超时消息
     * 5分钟后检查订单是否支付，未支付则释放库存
     */
    @RabbitListener(queues = "mall.stock.release")
    public void handleStockReleaseMessage(Map<String, Object> message) {
        String lockToken = (String) message.get("lockToken");
        Long skuId = (Long) message.get("skuId");
        Integer quantity = (Integer) message.get("quantity");
        
        log.info("收到库存释放消息: lockToken={}, skuId={}, quantity={}", 
                lockToken, skuId, quantity);
        
        try {
            // 释放库存
            boolean success = stockService.releaseStock(lockToken);
            if (success) {
                log.info("库存释放成功: lockToken={}", lockToken);
            } else {
                log.warn("库存释放失败: lockToken={}", lockToken);
            }
        } catch (Exception e) {
            log.error("处理库存释放消息失败: lockToken={}", lockToken, e);
        }
    }

    /**
     * 处理库存同步消息
     */
    @RabbitListener(queues = "mall.stock.sync")
    public void handleStockSyncMessage(Map<String, Object> message) {
        Long skuId = (Long) message.get("skuId");
        
        log.info("收到库存同步消息: skuId={}", skuId);
        
        try {
            stockService.syncStockToRedis(skuId);
            log.info("库存同步成功: skuId={}", skuId);
        } catch (Exception e) {
            log.error("处理库存同步消息失败: skuId={}", skuId, e);
        }
    }
}
package com.macro.mall.order.component;

import com.alibaba.fastjson.JSON;
import com.macro.mall.order.domain.StockDeductMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 库存消息发送者 - 订单模块发送扣减消息给库存模块
 */
@Slf4j
@Component
public class StockMessageSender {

    @Autowired
    private DefaultMQProducer stockProducer;

    @Value("${rocketmq.topic.stock-deduct}")
    private String stockDeductTopic;

    /**
     * 发送库存扣减消息
     */
    public void sendDeductMessage(StockDeductMessage message) {
        try {
            String messageJson = JSON.toJSONString(message);
            
            org.apache.rocketmq.common.message.Message msg = 
                    new org.apache.rocketmq.common.message.Message(
                            stockDeductTopic,
                            message.getOrderSn(),
                            messageJson.getBytes(StandardCharsets.UTF_8));
            
            msg.putUserProperty("traceId", message.getTraceId());
            msg.putUserProperty("messageType", "STOCK_DEDUCT");
            msg.putUserProperty("operationType", String.valueOf(message.getOperationType()));
            
            stockProducer.send(msg);
            
            log.info("库存扣减消息发送成功: orderSn={}, productId={}, count={}", 
                    message.getOrderSn(), message.getProductId(), message.getCount());
            
        } catch (Exception e) {
            log.error("库存扣减消息发送失败: orderSn={}", message.getOrderSn(), e);
            throw new RuntimeException("发送库存消息失败", e);
        }
    }

    /**
     * 发送库存回滚消息
     */
    public void sendRollbackMessage(StockDeductMessage message) {
        message.setOperationType(2); // 回滚
        message.setTraceId(java.util.UUID.randomUUID().toString().replace("-", ""));
        sendDeductMessage(message);
    }
}
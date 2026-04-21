package com.macro.mall.seckill.component;

import com.alibaba.fastjson.JSON;
import com.macro.mall.seckill.domain.SeckillOrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * RocketMQ事务消息发送者
 */
@Slf4j
@Component
public class TransactionMessageSender {

    @Autowired
    private TransactionMQProducer seckillTransactionProducer;

    @Value("${rocketmq.topic.seckill-order:seckill-order-topic}")
    private String seckillOrderTopic;

    @Autowired
    private StockService stockService;

    /**
     * 发送秒杀订单事务消息
     * 
     * @param message 订单消息
     * @return 发送结果
     */
    public boolean sendSeckillOrderMessage(SeckillOrderMessage message) {
        try {
            String messageJson = JSON.toJSONString(message);
            Message msg = new Message(seckillOrderTopic, 
                    message.getOrderSn(), 
                    messageJson.getBytes(StandardCharsets.UTF_8));
            
            // 设置消息属性
            msg.putUserProperty("traceId", message.getTraceId());
            msg.putUserProperty("messageType", "SECKILL_ORDER");
            msg.putUserProperty("userId", String.valueOf(message.getUserId()));
            msg.putUserProperty("productId", String.valueOf(message.getProductId()));
            
            // 发送事务消息
            seckillTransactionProducer.sendMessageInTransaction(msg, new TransactionListener() {
                
                /**
                 * 本地事务执行
                 * 在这里执行库存预扣减等操作
                 */
                @Override
                public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
                    try {
                        SeckillOrderMessage orderMessage = JSON.parseObject(
                                new String(msg.getBody(), StandardCharsets.UTF_8), 
                                SeckillOrderMessage.class);
                        
                        log.info("执行本地事务: orderSn={}, traceId={}", 
                                orderMessage.getOrderSn(), orderMessage.getTraceId());
                        
                        // 预扣减库存（Redis）
                        boolean deducted = stockService.preDeductStock(
                                orderMessage.getProductId(), 
                                orderMessage.getCount());
                        
                        if (deducted) {
                            // 标记为已预扣减（用于回滚判断）
                            stockService.markPreDeducted(orderMessage.getOrderSn(), 
                                    orderMessage.getProductId());
                            return LocalTransactionState.COMMIT_MESSAGE;
                        } else {
                            return LocalTransactionState.ROLLBACK_MESSAGE;
                        }
                        
                    } catch (Exception e) {
                        log.error("本地事务执行失败: msgId={}", msg.getMsgId(), e);
                        return LocalTransactionState.ROLLBACK_MESSAGE;
                    }
                }
                
                /**
                 * 回查本地事务状态
                 * 当事务消息超时时，RocketMQ会回调此方法
                 */
                @Override
                public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                    try {
                        SeckillOrderMessage orderMessage = JSON.parseObject(
                                new String(msg.getBody(), StandardCharsets.UTF_8), 
                                SeckillOrderMessage.class);
                        
                        log.info("回查本地事务状态: orderSn={}, traceId={}", 
                                orderMessage.getOrderSn(), orderMessage.getTraceId());
                        
                        // 检查是否已预扣减
                        boolean preDeducted = stockService.isPreDeducted(
                                orderMessage.getOrderSn(), 
                                orderMessage.getProductId());
                        
                        if (preDeducted) {
                            return LocalTransactionState.COMMIT_MESSAGE;
                        } else {
                            return LocalTransactionState.ROLLBACK_MESSAGE;
                        }
                        
                    } catch (Exception e) {
                        log.error("回查本地事务失败: msgId={}", msg.getMsgId(), e);
                        return LocalTransactionState.ROLLBACK_MESSAGE;
                    }
                }
            }, null);
            
            log.info("事务消息发送成功: orderSn={}, traceId={}", 
                    message.getOrderSn(), message.getTraceId());
            return true;
            
        } catch (Exception e) {
            log.error("发送事务消息失败: orderSn={}", message.getOrderSn(), e);
            return false;
        }
    }
}
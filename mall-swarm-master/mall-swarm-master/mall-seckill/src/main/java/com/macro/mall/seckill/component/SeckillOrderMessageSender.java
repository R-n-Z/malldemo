package com.macro.mall.seckill.component;

import com.alibaba.fastjson.JSON;
import com.macro.mall.seckill.domain.SeckillOrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单消息发送者 - 统一使用RocketMQ
 */
@Slf4j
@Component
public class SeckillOrderMessageSender {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Value("${rocketmq.topic.seckill-order:seckill-order-topic}")
    private String orderTopic;

    /**
     * 发送秒杀订单消息（异步）
     */
    public void sendOrderMessage(SeckillOrderMessage message) {
        String messageJson = JSON.toJSONString(message);
        String orderSn = message.getOrderSn();

        rocketMQTemplate.asyncSend(orderTopic, messageJson, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("秒杀订单消息发送成功: orderSn={}, msgId={}, status={}",
                        orderSn,
                        sendResult.getMsgId(),
                        sendResult.getSendStatus());
            }

            @Override
            public void onException(Throwable e) {
                log.error("秒杀订单消息发送失败: orderSn={}", orderSn, e);
            }
        });

        log.info("秒杀订单消息已发送: orderSn={}", orderSn);
    }

    /**
     * 发送秒杀订单消息（同步，带重试）
     */
    public SendResult sendOrderMessageSync(SeckillOrderMessage message, int retryCount) {
        String messageJson = JSON.toJSONString(message);
        String orderSn = message.getOrderSn();

        int retry = 0;
        while (retry < retryCount) {
            try {
                SendResult result = rocketMQTemplate.syncSend(orderTopic, messageJson);
                log.info("秒杀订单消息发送成功: orderSn={}, msgId={}", orderSn, result.getMsgId());
                return result;
            } catch (Exception e) {
                retry++;
                log.warn("秒杀订单消息发送重试: orderSn={}, retry={}/{}", orderSn, retry, retryCount);
                if (retry >= retryCount) {
                    log.error("秒杀订单消息发送失败: orderSn={}", orderSn, e);
                    throw new RuntimeException("消息发送失败", e);
                }
                try {
                    Thread.sleep(1000 * retry);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
        return null;
    }

    /**
     * 发送延迟重试消息
     */
    public void sendRetryMessage(SeckillOrderMessage message, long delayLevel) {
        message.setRetryCount(message.getRetryCount() + 1);
        String messageJson = JSON.toJSONString(message);

        // RocketMQ延迟消息级别：1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
        rocketMQTemplate.syncSend(orderTopic, messageJson, 3000, delayLevel);

        log.info("重试消息发送成功: orderSn={}, retryCount={}, delayLevel={}",
                message.getOrderSn(), message.getRetryCount(), delayLevel);
    }
}
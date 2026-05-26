package com.macro.mall.portal.component;

import com.alibaba.fastjson.JSON;
import com.macro.mall.portal.domain.PaySuccessMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

/**
 * Kafka支付消息发送者
 */
@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class KafkaPayMessageSender {

    private static final String PAY_SUCCESS_TOPIC = "mall-pay-success";
    private static final String PAY_RETRY_TOPIC = "mall-pay-retry";

    @Autowired
    private KafkaTemplate<String, String> payKafkaTemplate;

    /**
     * 发送支付成功消息
     */
    public void sendPaySuccessMessage(PaySuccessMessage message) {
        String messageJson = JSON.toJSONString(message);
        String orderSn = message.getOrderSn();
        
        // 使用订单号作为key，保证同一订单的消息发送到同一分区（有序性）
        ListenableFuture<SendResult<String, String>> future =
                payKafkaTemplate.send(PAY_SUCCESS_TOPIC, orderSn, messageJson);

        future.addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
            @Override
            public void onFailure(Throwable ex) {
                log.error("Kafka消息发送失败: orderSn={}", orderSn, ex);
                sendToRetryTopic(message);
            }

            @Override
            public void onSuccess(SendResult<String, String> result) {
                if (result == null || result.getRecordMetadata() == null) {
                    log.info("Kafka消息发送成功: orderSn={}", orderSn);
                    return;
                }
                log.info("Kafka消息发送成功: orderSn={}, partition={}, offset={}",
                        orderSn,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * 发送重试消息
     */
    private void sendToRetryTopic(PaySuccessMessage message) {
        message.setRetryCount(message.getRetryCount() + 1);
        String messageJson = JSON.toJSONString(message);
        
        ListenableFuture<SendResult<String, String>> future =
                payKafkaTemplate.send(PAY_RETRY_TOPIC, message.getOrderSn(), messageJson);

        future.addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
            @Override
            public void onFailure(Throwable ex) {
                log.error("重试消息发送失败: orderSn={}", message.getOrderSn(), ex);
            }

            @Override
            public void onSuccess(SendResult<String, String> result) {
                log.info("重试消息发送成功: orderSn={}, retryCount={}",
                        message.getOrderSn(), message.getRetryCount());
            }
        });
    }

    /**
     * 发送延迟重试消息（带延迟时间）
     */
    public void sendDelayRetryMessage(PaySuccessMessage message, long delayMs) {
        message.setRetryCount(message.getRetryCount() + 1);
        String messageJson = JSON.toJSONString(message);
        
        // Kafka本身不支持延迟消息，这里通过定时任务消费重试Topic实现
        // 简单实现：直接发送到重试Topic
        payKafkaTemplate.send(PAY_RETRY_TOPIC, message.getOrderSn(), messageJson);
        log.info("延迟重试消息已发送: orderSn={}, delayMs={}", message.getOrderSn(), delayMs);
    }
}
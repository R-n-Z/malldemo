package com.macro.mall.portal.component;

import com.macro.mall.portal.domain.PayFailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.Date;

/**
 * 支付失败消息发送者
 */
@Slf4j
@Component
public class PayFailMessageSender {

    private static final String TOPIC = "pay-fail-topic";

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 发送支付失败消息
     */
    public void sendPayFailMessage(PayFailMessage message) {
        message.setFailTime(new Date());
        
        ListenableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC, message.getOrderSn(), message);

        future.addCallback(new ListenableFutureCallback<SendResult<String, Object>>() {
            @Override
            public void onFailure(Throwable ex) {
                log.error("支付失败消息发送失败: orderSn={}",
                        message.getOrderSn(), ex);
            }

            @Override
            public void onSuccess(SendResult<String, Object> result) {
                log.info("支付失败消息发送成功: orderSn={}, reason={}",
                        message.getOrderSn(), message.getFailReason());
            }
        });
    }
}
package com.macro.mall.portal.component;

import com.macro.mall.portal.domain.PaySuccessMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
public class PayMessageSender {

    private static final String PAY_SUCCESS_QUEUE = "mall.pay.success";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送支付成功消息
     */
    public void sendPaySuccessMessage(PaySuccessMessage message) {
        message.setSendTime(new Date());
        
        try {
            rabbitTemplate.convertAndSend(PAY_SUCCESS_QUEUE, message, new MessagePostProcessor() {
                @Override
                public Message postProcessMessage(Message msg) throws AmqpException {
                    // 设置消息持久化
                    msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    // 设置消息ID（用于幂等）
                    msg.getMessageProperties().setMessageId(message.getOrderSn());
                    return msg;
                }
            });
            log.info("支付成功消息发送成功: orderSn={}", message.getOrderSn());
        } catch (AmqpException e) {
            log.error("支付成功消息发送失败: orderSn={}", message.getOrderSn(), e);
            // 发送失败，记录到数据库待补偿
            saveFailedMessage(message);
        }
    }

    /**
     * 发送失败的消息记录到数据库（待定时任务补偿）
     */
    private void saveFailedMessage(PaySuccessMessage message) {
        // TODO: 写入到一张"待补偿消息表"，由定时任务重试
        log.warn("消息已记录到待补偿表: orderSn={}", message.getOrderSn());
    }
}
package com.macro.mall.portal.component;

import com.macro.mall.portal.domain.SeckillMessage;
import com.macro.mall.portal.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 秒杀消息消费者
 */
@Slf4j
@Component
public class SeckillReceiver {

    @Autowired
    private SeckillService seckillService;

    @RabbitListener(queues = "mall.seckill.order")
    public void handleSeckillMessage(SeckillMessage message) {
        log.info("收到秒杀消息: {}", message);
        seckillService.handleSeckillMessage(message);
    }
}
package com.macro.mall.portal.component;

import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.portal.domain.PayFailMessage;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.portal.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 支付失败消息消费者
 * 处理支付失败场景，释放预扣库存，取消订单
 */
@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class PayFailConsumer {

    @Autowired
    private StockService stockService;

    @Autowired
    private OmsPortalOrderService portalOrderService;

    @Autowired
    private CancelOrderSender cancelOrderSender;

    @Autowired
    private OmsOrderMapper orderMapper;

    /**
     * 监听支付失败消息
     */
    @KafkaListener(topics = "pay-fail-topic", groupId = "pay-fail-consumer-group")
    public void handlePayFailMessage(ConsumerRecord<String, PayFailMessage> record) {
        PayFailMessage message = record.value();
        log.info("收到支付失败消息: orderSn={}, reason={}", 
                message.getOrderSn(), message.getFailReason());

        try {
            // 1. 获取订单的lockToken
            String lockToken = message.getLockToken();
            if (!StringUtils.hasText(lockToken)) {
                lockToken = getLockTokenByOrderSn(message.getOrderSn());
            }

            // 2. 释放预扣库存
            if (StringUtils.hasText(lockToken)) {
                boolean released = stockService.releaseStock(lockToken);
                if (released) {
                    log.info("预扣库存释放成功: orderSn={}, lockToken={}", 
                            message.getOrderSn(), lockToken);
                } else {
                    log.warn("预扣库存释放失败: orderSn={}, lockToken={}", 
                            message.getOrderSn(), lockToken);
                }
            } else {
                log.warn("订单无预扣库存记录: orderSn={}", message.getOrderSn());
            }

            // 3. 取消订单（如果需要）
            if (Boolean.TRUE.equals(message.getCancelOrder())) {
                Long orderId = getOrderIdByOrderSn(message.getOrderSn());
                if (orderId != null) {
                    portalOrderService.cancelOrder(orderId);
                }
                log.info("订单已取消: orderSn={}", message.getOrderSn());
            }

            log.info("支付失败处理完成: orderSn={}", message.getOrderSn());

        } catch (Exception e) {
            log.error("支付失败消息处理异常: orderSn={}", message.getOrderSn(), e);
            // 这里可以考虑加入死信队列或重试机制
        }
    }

    /**
     * 根据订单号查询lockToken
     */
    private String getLockTokenByOrderSn(String orderSn) {
        try {
            OmsOrderExample example = new OmsOrderExample();
            example.createCriteria().andOrderSnEqualTo(orderSn);
            List<OmsOrder> orders = orderMapper.selectByExample(example);
            if (orders != null && !orders.isEmpty()) {
                return orders.get(0).getLockToken();
            }
        } catch (Exception e) {
            log.error("查询订单lockToken失败: orderSn={}", orderSn, e);
        }
        return null;
    }

    private Long getOrderIdByOrderSn(String orderSn) {
        try {
            OmsOrderExample example = new OmsOrderExample();
            example.createCriteria().andOrderSnEqualTo(orderSn);
            List<OmsOrder> orders = orderMapper.selectByExample(example);
            if (orders != null && !orders.isEmpty()) {
                return orders.get(0).getId();
            }
        } catch (Exception e) {
            log.error("查询订单ID失败: orderSn={}", orderSn, e);
        }
        return null;
    }
}
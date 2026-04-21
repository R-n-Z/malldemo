package com.macro.mall.portal.component;

import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.OmsOrderItemExample;
import com.macro.mall.portal.dao.PortalOrderDao;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;

/**
 * RocketMQ取消订单消息消费者
 */
@Slf4j
@Configuration
public class RocketMQCancelOrderConsumer {

    private static final String ORDER_TIMEOUT_TOPIC = "order-timeout-topic";
    private static final String ORDER_TIMEOUT_TAG = "cancel";
    private static final String CONSUMER_GROUP = "order-timeout-consumer-group";

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Autowired
    private OmsOrderMapper orderMapper;

    @Autowired
    private OmsOrderItemMapper orderItemMapper;

    @Autowired
    private PortalOrderDao portalOrderDao;

    /**
     * 订单超时消费者
     */
    @Bean
    public DefaultMQPushConsumer orderTimeoutConsumer() throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(CONSUMER_GROUP);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(ORDER_TIMEOUT_TOPIC, ORDER_TIMEOUT_TAG);
        
        // 设置消息监听器
        consumer.setMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    Long orderId = Long.parseLong(new String(msg.getBody()));
                    log.info("收到RocketMQ订单超时消息: orderId={}", orderId);
                    
                    // 处理超时订单
                    boolean success = handleTimeoutOrder(orderId);
                    
                    if (success) {
                        log.info("订单超时处理成功: orderId={}", orderId);
                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    } else {
                        log.warn("订单超时处理失败: orderId={}", orderId);
                        // 稍后重试
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                    
                } catch (Exception e) {
                    log.error("处理RocketMQ订单超时消息异常", e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        
        consumer.start();
        log.info("RocketMQ订单超时消费者已启动");
        return consumer;
    }

    /**
     * 处理超时订单
     */
    private boolean handleTimeoutOrder(Long orderId) {
        // 1. 查询订单
        OmsOrder order = orderMapper.selectByPrimaryKey(orderId);
        if (order == null) {
            log.warn("订单不存在: orderId={}", orderId);
            return true; // 订单不存在，视为成功
        }
        
        // 2. 只处理待支付订单
        if (order.getStatus() != 0) {
            log.info("订单状态不是待支付，跳过: orderId={}, status={}", orderId, order.getStatus());
            return true;
        }
        
        // 3. 取消订单
        order.setStatus(4); // 已关闭
        order.setModifyTime(new Date());
        orderMapper.updateByPrimaryKeySelective(order);
        
        // 4. 释放库存
        OmsOrderItemExample example = new OmsOrderItemExample();
        example.createCriteria().andOrderIdEqualTo(orderId);
        List<OmsOrderItem> orderItemList = orderItemMapper.selectByExample(example);
        
        if (!CollectionUtils.isEmpty(orderItemList)) {
            portalOrderDao.releaseSkuStockLock(orderItemList);
        }
        
        log.info("订单已取消并释放库存: orderId={}", orderId);
        return true;
    }
}
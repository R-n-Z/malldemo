package com.macro.mall.order.service;

import com.macro.mall.order.domain.OrderTimeoutMessage;
import com.macro.mall.order.domain.OmsOrder;
import com.macro.mall.order.mapper.OmsOrderMapper;
import com.macro.mall.order.component.OrderTimeoutMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 订单超时服务
 */
@Slf4j
@Service
public class OrderTimeoutService {

    @Autowired
    private OmsOrderMapper orderMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private OrderTimeoutMessageSender timeoutMessageSender;

    @Value("${order.timeout.default-minutes:30}")
    private int defaultTimeoutMinutes;

    @Value("${order.timeout.scan-interval-seconds:60}")
    private int scanIntervalSeconds;

    private static final String ORDER_PAID_KEY = "order:paid:";
    private static final String ORDER_CANCELLED_KEY = "order:cancelled:";

    /**
     * 创建订单时启动超时任务
     * 
     * @param orderSn 订单号
     * @param userId 用户ID
     * @param productId 商品ID
     * @param count 数量
     */
    public void startTimeoutTask(String orderSn, Long userId, Long productId, Integer count) {
        startTimeoutTask(orderSn, userId, productId, count, defaultTimeoutMinutes);
    }

    /**
     * 创建订单时启动超时任务（自定义超时时间）
     */
    public void startTimeoutTask(String orderSn, Long userId, Long productId, 
                                  Integer count, Integer timeoutMinutes) {
        log.info("启动订单超时任务: orderSn={}, timeoutMinutes={}", orderSn, timeoutMinutes);
        
        // 构建超时消息
        OrderTimeoutMessage message = OrderTimeoutMessage.create(
                orderSn, userId, productId, count, timeoutMinutes);
        
        // 发送延迟消息
        long delayMs = timeoutMinutes * 60 * 1000L;
        timeoutMessageSender.sendTimeoutMessage(message, delayMs);
    }

    /**
     * 取消订单超时任务（订单支付成功后调用）
     */
    public void cancelTimeoutTask(String orderSn) {
        log.info("取消订单超时任务: orderSn={}", orderSn);
        
        // 标记订单已支付，消费者会跳过处理
        redisTemplate.opsForValue().set(ORDER_PAID_KEY + orderSn, "1", 24, TimeUnit.HOURS);
        
        // 取消超时消息发送
        timeoutMessageSender.cancelTimeoutMessage(orderSn);
    }

    /**
     * 处理超时订单（被消费者调用）
     */
    @Transactional
    public void cancelTimeoutOrder(String orderSn, OrderTimeoutMessage message) {
        log.info("开始处理超时订单: orderSn={}", orderSn);
        
        // 1. 查询订单
        OmsOrder order = orderMapper.selectByOrderSn(orderSn);
        if (order == null) {
            log.warn("订单不存在: orderSn={}", orderSn);
            return;
        }
        
        // 2. 检查订单状态
        if (order.getStatus() != 0) {
            // 非待付款状态，跳过
            log.info("订单状态非待付款，跳过: orderSn={}, status={}", orderSn, order.getStatus());
            return;
        }
        
        // 3. 检查是否已取消
        if (isOrderCancelled(orderSn)) {
            log.info("订单已取消，跳过: orderSn={}", orderSn);
            return;
        }
        
        // 4. 取消订单
        int affected = orderMapper.updateStatus(order.getId(), 4); // 4-已取消
        if (affected > 0) {
            // 5. 标记已取消
            markOrderCancelled(orderSn);
            
            // 6. 恢复库存（发送消息给库存服务）
            // TODO: 发送库存恢复消息
            
            log.info("订单超时取消成功: orderSn={}", orderSn);
        } else {
            log.warn("订单取消失败，可能已被处理: orderSn={}", orderSn);
        }
    }

    /**
     * 扫描超时订单（定时任务调用）
     * 作为RocketMQ延迟消息的补充，处理遗漏的订单
     */
    public void scanTimeoutOrders() {
        log.info("开始扫描超时订单...");
        
        try {
            // 查询所有待付款订单
            List<OmsOrder> pendingOrders = orderMapper.selectByStatus(0);
            
            Date now = new Date();
            long timeoutMillis = defaultTimeoutMinutes * 60 * 1000L;
            
            int cancelCount = 0;
            for (OmsOrder order : pendingOrders) {
                // 检查是否超时
                if (order.getCreateTime() != null) {
                    long elapsed = now.getTime() - order.getCreateTime().getTime();
                    if (elapsed > timeoutMillis) {
                        // 超时，取消订单
                        try {
                            OrderTimeoutMessage message = OrderTimeoutMessage.create(
                                    order.getOrderSn(),
                                    order.getMemberId(),
                                    null, // productId从订单商品表获取
                                    1,
                                    defaultTimeoutMinutes
                            );
                            cancelTimeoutOrder(order.getOrderSn(), message);
                            cancelCount++;
                        } catch (Exception e) {
                            log.error("扫描取消订单失败: orderSn={}", order.getOrderSn(), e);
                        }
                    }
                }
            }
            
            log.info("扫描超时订单完成: 检查数量={}, 取消数量={}", pendingOrders.size(), cancelCount);
            
        } catch (Exception e) {
            log.error("扫描超时订单失败", e);
        }
    }

    /**
     * 检查订单是否已取消
     */
    private boolean isOrderCancelled(String orderSn) {
        String key = ORDER_CANCELLED_KEY + orderSn;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 标记订单已取消
     */
    private void markOrderCancelled(String orderSn) {
        String key = ORDER_CANCELLED_KEY + orderSn;
        redisTemplate.opsForValue().set(key, "1", 24, TimeUnit.HOURS);
    }

    /**
     * 获取订单超时时间配置
     */
    public int getDefaultTimeoutMinutes() {
        return defaultTimeoutMinutes;
    }

    /**
     * 设置订单超时时间配置
     */
    public void setDefaultTimeoutMinutes(int minutes) {
        this.defaultTimeoutMinutes = minutes;
    }
}
package com.macro.mall.order.service;

import com.macro.mall.order.domain.OrderPersistMessage;
import com.macro.mall.order.domain.OmsOrder;
import com.macro.mall.order.mapper.OmsOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Set;

/**
 * 订单补偿服务 - 处理幽灵订单
 * 
 * 补偿场景：
 * 1. Redis订单存在，数据库订单不存在
 * 2. 订单状态不一致
 * 3. 异步落库失败
 */
@Slf4j
@Service
public class OrderCompensateService {

    private static final String ORDER_STATUS_KEY = "order:status:";
    private static final String ORDER_COMPENSATING_KEY = "order:compensating:";
    private static final String ORDER_PENDING_PERSIST_KEY = "order:pending-persist:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private OmsOrderMapper orderMapper;

    @Autowired
    private OrderPersistMessageSender messageSender;

    @Autowired
    private OrderConsistencyService consistencyService;

    /**
     * 补偿订单（异步执行）
     * 
     * @param orderSn 订单号
     */
    @Async("orderPersistExecutor")
    public void compensateOrder(String orderSn) {
        log.info("开始补偿订单: orderSn={}", orderSn);
        
        try {
            // 1. 设置补偿中标记，防止重复补偿
            String compensatingKey = ORDER_COMPENSATING_KEY + orderSn;
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(compensatingKey, "1", 5, java.util.concurrent.TimeUnit.MINUTES);
            
            if (!Boolean.TRUE.equals(acquired)) {
                log.info("订单正在补偿中，跳过: orderSn={}", orderSn);
                return;
            }
            
            try {
                // 2. 检查数据库订单是否存在
                OmsOrder dbOrder = orderMapper.selectByOrderSn(orderSn);
                
                if (dbOrder != null) {
                    // 订单已存在，修复状态不一致
                    fixOrderStatus(orderSn, dbOrder);
                } else {
                    // 订单不存在，需要重新落库
                    compensatePersist(orderSn);
                }
                
            } finally {
                // 3. 清除补偿标记
                redisTemplate.delete(compensatingKey);
            }
            
        } catch (Exception e) {
            log.error("补偿订单失败: orderSn={}", orderSn, e);
            // 记录到告警队列
            sendAlert(orderSn, "compensate_failed", e.getMessage());
        }
    }

    /**
     * 修复订单状态不一致
     */
    private void fixOrderStatus(String orderSn, OmsOrder dbOrder) {
        String redisStatus = consistencyService.getRedisOrderStatus(orderSn);
        
        if (redisStatus == null) {
            // Redis状态丢失，从数据库恢复
            consistencyService.syncOrderStatusToRedis(orderSn, dbOrder.getStatus());
            log.info("恢复Redis订单状态: orderSn={}, status={}", orderSn, dbOrder.getStatus());
        } else {
            // 状态不一致，以数据库为准
            if (!redisStatus.equals(String.valueOf(dbOrder.getStatus()))) {
                log.warn("修复订单状态不一致: orderSn={}, redis={}, db={}", 
                        orderSn, redisStatus, dbOrder.getStatus());
                consistencyService.syncOrderStatusToRedis(orderSn, dbOrder.getStatus());
            }
        }
    }

    /**
     * 补偿落库（重新发送落库消息）
     */
    private void compensatePersist(String orderSn) {
        log.info("补偿落库: orderSn={}", orderSn);
        
        try {
            // 1. 从Redis获取订单信息
            String pendingKey = ORDER_PENDING_PERSIST_KEY + orderSn;
            Object pendingData = redisTemplate.opsForValue().get(pendingKey);
            
            if (pendingData == null) {
                log.error("无法获取订单落库信息: orderSn={}", orderSn);
                sendAlert(orderSn, "no_pending_data", "无法获取订单落库信息");
                return;
            }
            
            // 2. 重新发送落库消息
            OrderPersistMessage message = parsePendingData(pendingData.toString());
            if (message != null) {
                messageSender.sendPersistMessage(message);
                log.info("重新发送落库消息: orderSn={}", orderSn);
            }
            
        } catch (Exception e) {
            log.error("补偿落库失败: orderSn={}", orderSn, e);
            sendAlert(orderSn, "persist_failed", e.getMessage());
        }
    }

    /**
     * 批量补偿订单（定时任务调用）
     * 
     * @param orderSns 订单号列表
     */
    public void compensateOrdersBatch(java.util.List<String> orderSns) {
        log.info("开始批量补偿: count={}", orderSns.size());
        
        for (String orderSn : orderSns) {
            try {
                compensateOrder(orderSn);
            } catch (Exception e) {
                log.error("批量补偿中单个订单失败: orderSn={}", orderSn, e);
            }
        }
        
        log.info("批量补偿完成: count={}", orderSns.size());
    }

    /**
     * 扫描并补偿幽灵订单（定时任务）
     */
    public void scanAndCompensateGhostOrders() {
        log.info("开始扫描幽灵订单");
        
        try {
            // 1. 获取所有Redis中的订单
            Set<String> keys = consistencyService.getAllRedisOrderStatus();
            
            if (keys == null || keys.isEmpty()) {
                log.info("无Redis订单需要扫描");
                return;
            }
            
            int totalCount = 0;
            int compensateCount = 0;
            
            for (String key : keys) {
                totalCount++;
                String orderSn = key.replace(ORDER_STATUS_KEY, "");
                
                // 检查数据库是否存在
                OmsOrder dbOrder = orderMapper.selectByOrderSn(orderSn);
                
                if (dbOrder == null) {
                    // 幽灵订单，触发补偿
                    log.warn("发现幽灵订单: orderSn={}", orderSn);
                    compensateOrder(orderSn);
                    compensateCount++;
                } else {
                    // 检查状态一致性
                    String redisStatus = key.substring(key.lastIndexOf(":") + 1);
                    if (!redisStatus.equals(String.valueOf(dbOrder.getStatus()))) {
                        log.warn("发现状态不一致订单: orderSn={}, redis={}, db={}", 
                                orderSn, redisStatus, dbOrder.getStatus());
                        fixOrderStatus(orderSn, dbOrder);
                    }
                }
            }
            
            log.info("扫描幽灵订单完成: total={}, compensated={}", totalCount, compensateCount);
            
        } catch (Exception e) {
            log.error("扫描幽灵订单失败", e);
        }
    }

    /**
     * 解析待落库���据
     */
    private OrderPersistMessage parsePendingData(String data) {
        try {
            // 数据格式: userId:productId:count:price
            String[] parts = data.split(":");
            if (parts.length < 4) {
                log.error("待落库数据格式错误: {}", data);
                return null;
            }
            
            OrderPersistMessage message = new OrderPersistMessage();
            message.setOrderSn(parts[0]);
            message.setUserId(Long.parseLong(parts[1]));
            message.setProductId(Long.parseLong(parts[2]));
            message.setCount(Integer.parseInt(parts[3]));
            message.setSeckillPrice(new java.math.BigDecimal(parts[4]));
            message.setCreateTime(new Date());
            
            return message;
        } catch (Exception e) {
            log.error("解析待落库数据失败: {}", data, e);
            return null;
        }
    }

    /**
     * 发送告警
     */
    private void sendAlert(String orderSn, String type, String message) {
        log.error("幽灵订单告警: orderSn={}, type={}, message={}", orderSn, type, message);
        // TODO: 接入告警系统（短信、邮件、钉钉等）
    }

    /**
     * 保存待落库数据到Redis
     * 
     * @param orderSn 订单号
     * @param userId 用户ID
     * @param productId 商品ID
     * @param count 数量
     * @param price 价格
     */
    public void savePendingPersistData(String orderSn, Long userId, Long productId, 
                                        Integer count, java.math.BigDecimal price) {
        try {
            String key = ORDER_PENDING_PERSIST_KEY + orderSn;
            String value = orderSn + ":" + userId + ":" + productId + ":" + count + ":" + price;
            redisTemplate.opsForValue().set(key, value, 24, java.util.concurrent.TimeUnit.HOURS);
            log.debug("保存待落库数据: orderSn={}", orderSn);
        } catch (Exception e) {
            log.error("保存待落库数据失败: orderSn={}", orderSn, e);
        }
    }

    /**
     * 清除待落库数据
     * 
     * @param orderSn 订单号
     */
    public void clearPendingPersistData(String orderSn) {
        try {
            String key = ORDER_PENDING_PERSIST_KEY + orderSn;
            redisTemplate.delete(key);
            log.debug("清除待落库数据: orderSn={}", orderSn);
        } catch (Exception e) {
            log.error("清除待落库数据失败: orderSn={}", orderSn, e);
        }
    }
}
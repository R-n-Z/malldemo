package com.macro.mall.order.service;

import com.macro.mall.order.domain.OmsOrder;
import com.macro.mall.order.mapper.OmsOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 订单一致性校验服务 - 防止幽灵订单
 * 
 * 幽灵订单场景：
 * 1. 预占库存成功，订单未落库（异步落库失败）
 * 2. 订单创建成功，支付时订单不存在（数据不一致）
 * 3. 并发重复下单（幂等失效）
 * 4. 订单已取消，库存未释放（状态不同步）
 */
@Slf4j
@Service
public class OrderConsistencyService {

    private static final String ORDER_STATUS_KEY = "order:status:";
    private static final String ORDER_PRE_LOCK_KEY = "order:pre-lock:";
    private static final String ORDER_COMPENSATING_KEY = "order:compensating:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private OmsOrderMapper orderMapper;

    @Autowired
    private OrderCompensateService compensateService;

    /**
     * 校验订单状态一致性（支付前调用）
     * 
     * @param orderSn 订单号
     * @return true-有效订单，false-无效订单
     */
    public boolean validateOrderBeforePayment(String orderSn) {
        log.info("校验订单状态: orderSn={}", orderSn);
        
        try {
            // 1. 检查Redis订单状态
            String redisStatus = getRedisOrderStatus(orderSn);
            if (redisStatus == null) {
                log.warn("Redis订单不存在: orderSn={}", orderSn);
                // 尝试从数据库查询
                return validateFromDatabase(orderSn);
            }
            
            // 2. 检查数据库订单状态
            OmsOrder dbOrder = orderMapper.selectByOrderSn(orderSn);
            if (dbOrder == null) {
                log.error("数据库订单不存在: orderSn={}", orderSn);
                // 触发补偿
                compensateService.compensateOrder(orderSn);
                return false;
            }
            
            // 3. 状态必须一致
            if (!redisStatus.equals(String.valueOf(dbOrder.getStatus()))) {
                log.error("订单状态不一致: orderSn={}, redis={}, db={}", 
                        orderSn, redisStatus, dbOrder.getStatus());
                // 触发补偿
                compensateService.compensateOrder(orderSn);
                return false;
            }
            
            // 4. 必须是待付款状态
            if (dbOrder.getStatus() != 0) {
                log.warn("订单状态非待付款: orderSn={}, status={}", orderSn, dbOrder.getStatus());
                return false;
            }
            
            // 5. 检查是否正在补偿中
            if (Boolean.TRUE.equals(redisTemplate.hasKey(ORDER_COMPENSATING_KEY + orderSn))) {
                log.warn("订单正在补偿中: orderSn={}", orderSn);
                return false;
            }
            
            log.info("订单校验通过: orderSn={}", orderSn);
            return true;
            
        } catch (Exception e) {
            log.error("订单校验异常: orderSn={}", orderSn, e);
            // 降级：只检查数据库
            return validateFromDatabase(orderSn);
        }
    }

    /**
     * 校验订单是否存在且有效
     * 
     * @param orderSn 订单号
     * @return true-有效，false-无效
     */
    public boolean validateOrderExists(String orderSn) {
        try {
            OmsOrder order = orderMapper.selectByOrderSn(orderSn);
            if (order == null) {
                log.warn("订单不存在: orderSn={}", orderSn);
                return false;
            }
            
            // 检查是否已删除
            if (order.getDeleteStatus() != null && order.getDeleteStatus() == 1) {
                log.warn("订单已删除: orderSn={}", orderSn);
                return false;
            }
            
            // 检查是否已取消或已完成
            if (order.getStatus() != null && (order.getStatus() == 4 || order.getStatus() == 3)) {
                log.warn("订单已结束: orderSn={}, status={}", orderSn, order.getStatus());
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("校验订单存在异常: orderSn={}", orderSn, e);
            return false;
        }
    }

    /**
     * 同步订单状态到Redis
     * 
     * @param orderSn 订单号
     * @param status 订单状态
     */
    public void syncOrderStatusToRedis(String orderSn, Integer status) {
        try {
            String key = ORDER_STATUS_KEY + orderSn;
            redisTemplate.opsForValue().set(key, status.toString(), 24, java.util.concurrent.TimeUnit.HOURS);
            log.debug("同步订单状态到Redis: orderSn={}, status={}", orderSn, status);
        } catch (Exception e) {
            log.error("同步订单状态到Redis失败: orderSn={}", orderSn, e);
        }
    }

    /**
     * 清除Redis订单状态
     * 
     * @param orderSn 订单号
     */
    public void clearRedisOrderStatus(String orderSn) {
        try {
            String key = ORDER_STATUS_KEY + orderSn;
            redisTemplate.delete(key);
            log.debug("清除Redis订单状态: orderSn={}", orderSn);
        } catch (Exception e) {
            log.error("清除Redis订单状态失败: orderSn={}", orderSn, e);
        }
    }

    /**
     * 设置订单预占标记
     * 
     * @param orderSn 订单号
     * @param productId 商品ID
     * @param count 数量
     */
    public void setPreLock(String orderSn, Long productId, Integer count) {
        try {
            String key = ORDER_PRE_LOCK_KEY + orderSn;
            String value = productId + ":" + count;
            redisTemplate.opsForValue().set(key, value, 24, java.util.concurrent.TimeUnit.HOURS);
            log.info("设置订单预占标记: orderSn={}, productId={}, count={}", orderSn, productId, count);
        } catch (Exception e) {
            log.error("设置订单预占标记失败: orderSn={}", orderSn, e);
        }
    }

    /**
     * 清除订单预占标记
     * 
     * @param orderSn 订单号
     */
    public void clearPreLock(String orderSn) {
        try {
            String key = ORDER_PRE_LOCK_KEY + orderSn;
            redisTemplate.delete(key);
            log.info("清除订单预占标记: orderSn={}", orderSn);
        } catch (Exception e) {
            log.error("清除订单预占标记失败: orderSn={}", orderSn, e);
        }
    }

    /**
     * 获取订单预占信息
     * 
     * @param orderSn 订单号
     * @return 预占信息，格式: productId:count
     */
    public String getPreLock(String orderSn) {
        try {
            String key = ORDER_PRE_LOCK_KEY + orderSn;
            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.error("获取订单预占信息失败: orderSn={}", orderSn, e);
            return null;
        }
    }

    /**
     * 从数据库校验订单
     */
    private boolean validateFromDatabase(String orderSn) {
        OmsOrder order = orderMapper.selectByOrderSn(orderSn);
        if (order == null) {
            log.error("数据库订单不存在: orderSn={}", orderSn);
            compensateService.compensateOrder(orderSn);
            return false;
        }
        
        if (order.getStatus() != 0) {
            log.warn("订单状态非待付款: orderSn={}, status={}", orderSn, order.getStatus());
            return false;
        }
        
        return true;
    }

    /**
     * 获取Redis订单状态
     */
    private String getRedisOrderStatus(String orderSn) {
        String key = ORDER_STATUS_KEY + orderSn;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取所有Redis中的订单状态（用于扫描补偿）
     */
    public Set<String> getAllRedisOrderStatus() {
        try {
            return redisTemplate.keys(ORDER_STATUS_KEY + "*");
        } catch (Exception e) {
            log.error("获取所有Redis订单状态失败", e);
            return java.util.Collections.emptySet();
        }
    }
}
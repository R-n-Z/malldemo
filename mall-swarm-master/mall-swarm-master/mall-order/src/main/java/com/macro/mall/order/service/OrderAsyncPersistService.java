package com.macro.mall.order.service;

import com.macro.mall.order.domain.OrderPersistMessage;
import com.macro.mall.order.mapper.OmsOrderMapper;
import com.macro.mall.order.domain.OmsOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单异步落库服务
 */
@Slf4j
@Service
public class OrderAsyncPersistService {

    @Autowired
    private OmsOrderMapper orderMapper;

    /**
     * 异步落库订单
     * 使用@Async注解实现异步执行
     * 
     * @param message 订单落库消息
     */
    @Async("orderPersistExecutor")
    @Transactional
    public void persistOrder(OrderPersistMessage message) {
        log.info("开始异步落库: orderSn={}", message.getOrderSn());
        
        try {
            // 1. 构建订单实体
            OmsOrder order = buildOrder(message);
            
            // 2. 插入订单
            orderMapper.insert(order);
            
            // 3. 落库成功
            log.info("订单异步落库成功: orderSn={}, orderId={}", 
                    message.getOrderSn(), order.getId());
            
        } catch (Exception e) {
            log.error("订单异步落库失败: orderSn={}", message.getOrderSn(), e);
            throw new RuntimeException("订单落库失败", e);
        }
    }

    /**
     * 批量落库订单（提高性能）
     * 
     * @param messages 订单消息列表
     */
    @Async("orderPersistExecutor")
    @Transactional
    public void persistOrdersBatch(java.util.List<OrderPersistMessage> messages) {
        log.info("开始批量落库: count={}", messages.size());
        
        try {
            for (OrderPersistMessage message : messages) {
                try {
                    OmsOrder order = buildOrder(message);
                    orderMapper.insert(order);
                } catch (Exception e) {
                    log.error("批量落库中单个订单失败: orderSn={}", message.getOrderSn(), e);
                    // 继续处理其他订单
                }
            }
            
            log.info("批量落库完成: count={}", messages.size());
            
        } catch (Exception e) {
            log.error("批量落库失败", e);
            throw new RuntimeException("批量落库失败", e);
        }
    }

    /**
     * 构建订单实体
     */
    private OmsOrder buildOrder(OrderPersistMessage message) {
        OmsOrder order = new OmsOrder();
        order.setOrderSn(message.getOrderSn());
        order.setMemberId(message.getUserId());
        order.setOrderType(message.getOrderType() != null ? message.getOrderType() : 2);
        order.setStatus(0); // 待付款
        order.setTotalAmount(message.getTotalAmount());
        order.setPayAmount(message.getPayAmount());
        order.setFreightAmount(java.math.BigDecimal.ZERO);
        order.setPromotionAmount(java.math.BigDecimal.ZERO);
        order.setIntegrationAmount(java.math.BigDecimal.ZERO);
        order.setCouponAmount(java.math.BigDecimal.ZERO);
        order.setPayType(message.getPayType() != null ? message.getPayType() : 1);
        order.setSourceType(message.getSourceType() != null ? message.getSourceType() : 1);
        order.setCreateTime(message.getCreateTime());
        order.setModifyTime(new java.util.Date());
        order.setDeleteStatus(0);
        
        // 设置收货信息（如果有）
        if (message.getReceiverName() != null) {
            order.setReceiverName(message.getReceiverName());
        }
        if (message.getReceiverPhone() != null) {
            order.setReceiverPhone(message.getReceiverPhone());
        }
        if (message.getReceiverAddress() != null) {
            order.setReceiverAddress(message.getReceiverAddress());
        }
        if (message.getNote() != null) {
            order.setNote(message.getNote());
        }
        
        return order;
    }

    /**
     * 补偿落库（用于定时任务补偿失败订单）
     */
    public void compensatePersist(java.util.List<String> orderSns) {
        log.info("开始补偿落库: count={}", orderSns.size());
        
        for (String orderSn : orderSns) {
            try {
                OmsOrder existingOrder = orderMapper.selectByOrderSn(orderSn);
                if (existingOrder == null) {
                    log.warn("订单不存在，无法补偿: orderSn={}", orderSn);
                    continue;
                }
                
                // 订单已存在，跳过
                log.info("订单已存在，跳过补偿: orderSn={}", orderSn);
                
            } catch (Exception e) {
                log.error("补偿落库失败: orderSn={}", orderSn, e);
            }
        }
    }
}
package com.macro.mall.order.service;

import com.macro.mall.order.domain.OmsOrder;
import com.macro.mall.order.domain.SeckillOrderMessage;
import com.macro.mall.order.mapper.OmsOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单创建服务
 */
@Slf4j
@Service
public class OrderCreateService {

    @Autowired
    private OmsOrderMapper orderMapper;

    /**
     * 创建秒杀订单
     */
    public void createSeckillOrder(SeckillOrderMessage message) {
        log.info("创建秒杀订单: orderSn={}, userId={}, productId={}", 
                message.getOrderSn(), message.getUserId(), message.getProductId());
        
        // 1. 构建订单
        OmsOrder order = new OmsOrder();
        order.setOrderSn(message.getOrderSn());
        order.setMemberId(message.getUserId());
        order.setOrderType(2); // 秒杀订单
        order.setStatus(0); // 待付款
        order.setTotalAmount(message.getSeckillPrice().multiply(new BigDecimal(message.getCount())));
        order.setPayAmount(message.getSeckillPrice().multiply(new BigDecimal(message.getCount())));
        order.setFreightAmount(BigDecimal.ZERO);
        order.setPromotionAmount(BigDecimal.ZERO);
        order.setIntegrationAmount(BigDecimal.ZERO);
        order.setCouponAmount(BigDecimal.ZERO);
        order.setPayType(1); // 支付宝
        order.setSourceType(1); // APP
        order.setCreateTime(new Date());
        order.setModifyTime(new Date());
        order.setDeleteStatus(0);
        
        // 2. 插入订单
        orderMapper.insert(order);
        
        log.info("秒杀订单创建完成: orderSn={}, orderId={}", 
                message.getOrderSn(), order.getId());
    }
}
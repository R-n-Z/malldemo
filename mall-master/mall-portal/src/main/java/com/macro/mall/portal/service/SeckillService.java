package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.SeckillMessage;
import com.macro.mall.portal.domain.SeckillPrepareResult;

import java.util.Map;

/**
 * 秒杀Service
 */
public interface SeckillService {

    /**
     * 获取秒杀商品详情
     */
    Map<String, Object> getSeckillDetail(Long productId);

    /**
     * 秒杀准备（预检、获取秒杀地址）
     */
    SeckillPrepareResult prepareSeckill(Long productId, Long sessionId);

    /**
     * 执行秒杀（下单）
     */
    Map<String, Object> doSeckill(Long productId, Long sessionId, String seckillToken);

    /**
     * 获取秒杀结果
     */
    Map<String, Object> getSeckillResult(Long orderId);

    /**
     * 处理秒杀消息（消费者调用）
     */
    void handleSeckillMessage(SeckillMessage message);
}
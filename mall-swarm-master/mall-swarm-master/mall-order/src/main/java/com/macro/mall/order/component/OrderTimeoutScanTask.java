package com.macro.mall.order.component;

import com.macro.mall.order.service.OrderTimeoutService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单超时扫描定时任务
 * 
 * 作为RocketMQ延迟消息的补充，确保超时订单被正确处理
 */
@Slf4j
@Component
public class OrderTimeoutScanTask {

    @Autowired
    private OrderTimeoutService orderTimeoutService;

    /**
     * 每分钟扫描一次超时订单
     * 补充处理RocketMQ延迟消息可能遗漏的情况
     */
    @Scheduled(cron = "0 * * * * ?") // 每分钟执行
    public void scanTimeoutOrders() {
        log.info("定时任务：扫描超时订单 - {}", LocalDateTime.now());
        
        try {
            orderTimeoutService.scanTimeoutOrders();
        } catch (Exception e) {
            log.error("扫描超时订单任务执行失败", e);
        }
    }

    /**
     * 每5分钟清理过期的Redis标记
     */
    @Scheduled(cron = "0 0/5 * * * ?") // 每5分钟执行
    public void cleanExpiredMarks() {
        log.info("定时任务：清理过期标记 - {}", LocalDateTime.now());
        // Redis key会自动过期，这里可以做一些额外的清理工作
        // 目前Redis的TTL设置已经足够，不需要额外清理
    }
}
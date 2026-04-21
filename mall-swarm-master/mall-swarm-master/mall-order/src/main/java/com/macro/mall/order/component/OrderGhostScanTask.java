package com.macro.mall.order.component;

import com.macro.mall.order.service.OrderCompensateService;
import com.macro.mall.order.service.OrderConsistencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 幽灵订单扫描任务
 * 
 * 定时扫描Redis中的订单，检查数据库中是否存在，
 * 对幽灵订单进行补偿处理
 */
@Slf4j
@Component
public class OrderGhostScanTask {

    @Autowired
    private OrderConsistencyService consistencyService;

    @Autowired
    private OrderCompensateService compensateService;

    /**
     * 扫描幽灵订单（每5分钟执行一次）
     */
    @Scheduled(fixedRate = 300000)
    public void scanGhostOrders() {
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
            int fixCount = 0;
            
            for (String key : keys) {
                totalCount++;
                String orderSn = key.replace("order:status:", "");
                
                try {
                    // 2. 检查数据库订单是否存在
                    boolean exists = consistencyService.validateOrderExists(orderSn);
                    
                    if (!exists) {
                        // 幽灵订单，触发补偿
                        log.warn("发现幽灵订单: orderSn={}", orderSn);
                        compensateService.compensateOrder(orderSn);
                        compensateCount++;
                    }
                } catch (Exception e) {
                    log.error("扫描订单异常: orderSn={}", orderSn, e);
                }
            }
            
            log.info("扫描幽灵订单完成: total={}, compensated={}", totalCount, compensateCount);
            
        } catch (Exception e) {
            log.error("扫描幽灵订单失败", e);
        }
    }

    /**
     * 扫描预占库存未释放订单（每10分钟执行一次）
     * 
     * 检查预占标记是否存在但订单已取消的情况
     */
    @Scheduled(fixedRate = 600000)
    public void scanPreLockNotReleased() {
        log.info("开始扫描预占未释放订单");
        
        try {
            // TODO: 实现预占库存扫描逻辑
            // 1. 获取所有预占标记
            // 2. 检查对应订单状态
            // 3. 如果订单已取消但预占未释放，触发释放
            
        } catch (Exception e) {
            log.error("扫描预占未释放订单失败", e);
        }
    }

    /**
     * 清理过期数据（每天凌晨2点执行）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredData() {
        log.info("开始清理过期数据");
        
        try {
            // TODO: 清理超过24小时的已完成的订单状态
            // 清理超过7天的补偿记录
            
        } catch (Exception e) {
            log.error("清理过期数据失败", e);
        }
    }
}
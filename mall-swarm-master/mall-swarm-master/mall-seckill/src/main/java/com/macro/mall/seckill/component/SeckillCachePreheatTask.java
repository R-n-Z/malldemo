package com.macro.mall.seckill.component;

import com.macro.mall.seckill.service.impl.SeckillProductCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 缓存预热定时任务
 */
@Slf4j
@Component
public class SeckillCachePreheatTask {

    @Autowired
    private SeckillProductCacheService cacheService;

    /**
     * 每5分钟刷新一次活动商品缓存
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void refreshActiveProductsCache() {
        log.info("定时任务：刷新活动商品缓存 - {}", LocalDateTime.now());
        
        try {
            cacheService.preheatAllActiveProducts();
        } catch (Exception e) {
            log.error("刷新活动商品缓存失败", e);
        }
    }

    /**
     * 每小时检查并预热即将开始的秒杀活动
     * 预热未来1小时内的活动商品
     */
    @Scheduled(cron = "0 0 * * * ?") // 每小时整点执行
    public void preheatUpcomingActivities() {
        log.info("定时任务：预热即将开始的秒杀活动 - {}", LocalDateTime.now());
        
        try {
            java.util.Date startTime = new java.util.Date();
            java.util.Date endTime = new java.util.Date(System.currentTimeMillis() + 60 * 60 * 1000); // 1小时后
            
            cacheService.preheatByTimeRange(startTime, endTime);
        } catch (Exception e) {
            log.error("预热即将开始的秒杀活动失败", e);
        }
    }

    /**
     * 每天凌晨2点执行全量预热
     * 预热所有进行中的活动
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点
    public void fullPreheat() {
        log.info("定时任务：全量预热秒杀商品缓存 - {}", LocalDateTime.now());
        
        try {
            cacheService.preheatAllActiveProducts();
        } catch (Exception e) {
            log.error("全量预热失败", e);
        }
    }
}
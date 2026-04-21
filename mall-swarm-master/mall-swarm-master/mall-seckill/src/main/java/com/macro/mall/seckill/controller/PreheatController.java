package com.macro.mall.seckill.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.seckill.service.impl.SeckillProductCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 缓存预热管理接口
 */
@Slf4j
@Tag(name = "缓存预热管理")
@RestController
@RequestMapping("/preheat")
public class PreheatController {

    @Autowired
    private SeckillProductCacheService cacheService;

    @Operation(summary = "预热所有活动商品")
    @PostMapping("/all")
    public CommonResult<String> preheatAll() {
        log.info("手动触发：预热所有活动商品");
        cacheService.preheatAllActiveProducts();
        return CommonResult.success(null, "预热完成");
    }

    @Operation(summary = "预热指定商品")
    @PostMapping("/product/{productId}")
    public CommonResult<String> preheatProduct(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        log.info("手动触发：预热指定商品 productId={}", productId);
        cacheService.preheatProductById(productId);
        return CommonResult.success(null, "预热完成");
    }

    @Operation(summary = "预热指定时间范围内的商品")
    @PostMapping("/time-range")
    public CommonResult<String> preheatByTimeRange(
            @Parameter(description = "开始时间") @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @Parameter(description = "结束时间") @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime) {
        log.info("手动触发：预热时间范围内的商品 startTime={}, endTime={}", startTime, endTime);
        cacheService.preheatByTimeRange(startTime, endTime);
        return CommonResult.success(null, "预热完成");
    }

    @Operation(summary = "刷新商品缓存")
    @PutMapping("/refresh/{productId}")
    public CommonResult<String> refreshCache(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        log.info("手动触发：刷新商品缓存 productId={}", productId);
        cacheService.refreshProductCache(productId);
        return CommonResult.success(null, "刷新完成");
    }

    @Operation(summary = "清除商品缓存")
    @DeleteMapping("/clear/{productId}")
    public CommonResult<String> clearCache(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        log.info("手动触发：清除商品缓存 productId={}", productId);
        cacheService.clearProductCache(productId);
        return CommonResult.success(null, "清除完成");
    }

    @Operation(summary = "获取活动商品列表")
    @GetMapping("/products")
    public CommonResult<List<Long>> getActiveProducts() {
        List<Long> productIds = cacheService.getActiveProductIds();
        return CommonResult.success(productIds);
    }

    @Operation(summary = "获取预热状态")
    @GetMapping("/status")
    public CommonResult<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("activeProductCount", cacheService.getActiveProductIds().size());
        status.put("timestamp", System.currentTimeMillis());
        return CommonResult.success(status);
    }
}
package com.macro.mall.stock.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.stock.service.StockDeductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 库存管理接口
 */
@Slf4j
@Tag(name = "库存管理")
@RestController
@RequestMapping("/stock")
public class StockController {

    @Autowired
    private StockDeductService stockDeductService;

    @Operation(summary = "获取库存")
    @GetMapping("/{productId}")
    public CommonResult<Integer> getStock(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        Integer stock = stockDeductService.getStock(productId);
        return CommonResult.success(stock);
    }

    @Operation(summary = "初始化库存（管理接口）")
    @PostMapping("/init")
    public CommonResult<String> initStock(
            @RequestParam Long productId,
            @RequestParam Integer stock) {
        stockDeductService.initStock(productId, stock);
        return CommonResult.success(null, "库存初始化成功");
    }

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public CommonResult<Map<String, String>> health() {
        return CommonResult.success(Map.of("status", "UP"));
    }
}
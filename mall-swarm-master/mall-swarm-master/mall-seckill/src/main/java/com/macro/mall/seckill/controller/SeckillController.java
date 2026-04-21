package com.macro.mall.seckill.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.seckill.service.SeckillService;
import com.macro.mall.seckill.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 秒杀接口
 */
@Slf4j
@Tag(name = "秒杀管理")
@RestController
@RequestMapping("/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private StockService stockService;

    @Operation(summary = "秒杀商品")
    @PostMapping("/do")
    public CommonResult<String> doSeckill(
            @Parameter(description = "商品ID") @RequestParam Long productId,
            @Parameter(description = "验证码") @RequestParam String code) {
        return seckillService.seckill(productId, code);
    }

    @Operation(summary = "获取商品库存")
    @GetMapping("/stock/{productId}")
    public CommonResult<Integer> getStock(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        return seckillService.getStock(productId);
    }

    @Operation(summary = "生成验证码")
    @GetMapping("/captcha/{productId}")
    public CommonResult<String> generateCaptcha(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        return seckillService.generateCaptcha(productId);
    }

    @Operation(summary = "初始化库存（管理员接口）")
    @PostMapping("/stock/init")
    public CommonResult<String> initStock(
            @RequestParam Long productId,
            @RequestParam Integer stock) {
        stockService.initStock(productId, stock);
        return CommonResult.success(null, "库存初始化成功");
    }

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public CommonResult<Map<String, String>> health() {
        return CommonResult.success(Map.of("status", "UP"));
    }
}
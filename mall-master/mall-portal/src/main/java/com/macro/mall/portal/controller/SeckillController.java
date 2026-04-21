package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.SeckillPrepareResult;
import com.macro.mall.portal.service.SeckillService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 秒杀Controller
 * Created by macro
 */
@Controller
@Api(tags = "SeckillController")
@Tag(name = "SeckillController", description = "秒杀管理")
@RequestMapping("/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    @ApiOperation("秒杀商品详情页")
    @RequestMapping(value = "/detail/{productId}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<Map<String, Object>> getSeckillDetail(@PathVariable Long productId) {
        Map<String, Object> result = seckillService.getSeckillDetail(productId);
        return CommonResult.success(result);
    }

    @ApiOperation("秒杀准备（获取秒杀地址、库存预检）")
    @RequestMapping(value = "/prepare/{productId}", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<SeckillPrepareResult> prepareSeckill(
            @PathVariable Long productId,
            @RequestParam Long sessionId) {
        SeckillPrepareResult result = seckillService.prepareSeckill(productId, sessionId);
        return CommonResult.success(result);
    }

    @ApiOperation("执行秒杀（下单）")
    @RequestMapping(value = "/do/{productId}", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<Map<String, Object>> doSeckill(
            @PathVariable Long productId,
            @RequestParam Long sessionId,
            @RequestParam String seckillToken) {
        Map<String, Object> result = seckillService.doSeckill(productId, sessionId, seckillToken);
        return CommonResult.success(result);
    }

    @ApiOperation("获取秒杀结果")
    @RequestMapping(value = "/result/{orderId}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<Map<String, Object>> getSeckillResult(@PathVariable Long orderId) {
        Map<String, Object> result = seckillService.getSeckillResult(orderId);
        return CommonResult.success(result);
    }
}
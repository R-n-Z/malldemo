package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.annotation.RateLimit;
import com.macro.mall.portal.annotation.RateLimit.Algorithm;
import com.macro.mall.portal.annotation.RateLimit.LimitType;
import com.macro.mall.portal.component.OrderIdempotentChecker;
import com.macro.mall.portal.domain.ConfirmOrderResult;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.domain.OrderParam;
import com.macro.mall.portal.service.OmsPortalOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单管理Controller
 * Created by macro on 2018/8/30.
 */
@Slf4j
@Api(tags = "OmsPortalOrderController")
@Tag(name = "OmsPortalOrderController", description = "订单管理")
@RequestMapping("/order")
@Controller
public class OmsPortalOrderController {
    @Autowired
    private OmsPortalOrderService portalOrderService;

    @Autowired
    private OrderIdempotentChecker idempotentChecker;

    @ApiOperation("根据购物车信息生成确认单")
    @RequestMapping(value = "/generateConfirmOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<ConfirmOrderResult> generateConfirmOrder(@RequestBody List<Long> cartIds) {
        ConfirmOrderResult confirmOrderResult = portalOrderService.generateConfirmOrder(cartIds);
        return CommonResult.success(confirmOrderResult);
    }

    @ApiOperation("根据购物车信息生成订单")
    @RateLimit(type = LimitType.MEMBER, algorithm = Algorithm.FIXED_WINDOW, param1 = 2, param2 = 60)
    @RequestMapping(value = "/generateOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult generateOrder(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-Order-Sign", required = false) String sign,
            @RequestHeader(value = "X-Order-Timestamp", required = false) String timestamp,
            @RequestBody OrderParam orderParam) {
        
        // 1. 参数校验
        if (memberId == null) {
            return CommonResult.failed("用户未登录");
        }
        if (!StringUtils.hasText(sign)) {
            return CommonResult.failed("缺少请求签名");
        }
        if (!StringUtils.hasText(timestamp)) {
            return CommonResult.failed("缺少请求时间戳");
        }
        
        // 2. 验证时间戳（防止重放攻击）
        if (!validateTimestamp(timestamp, 300)) {
            return CommonResult.failed("请求已过期，请重新提交");
        }
        
        // 3. 幂等性校验
        if (!idempotentChecker.tryAcquire(memberId, sign)) {
            return CommonResult.failed("请勿重复提交订单");
        }
        
        try {
            // 4. 执行下单逻辑
            Map<String, Object> result = portalOrderService.generateOrder(orderParam);
            return CommonResult.success(result, "下单成功");
        } finally {
            // 5. 下单成功后释放幂等锁
        }
    }

    /**
     * 验证时间戳
     */
    private boolean validateTimestamp(String timestampStr, long expireSeconds) {
        try {
            long timestamp = Long.parseLong(timestampStr);
            long now = System.currentTimeMillis();
            long diff = Math.abs(now - timestamp);
            return diff < expireSeconds * 1000;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @ApiOperation("用户支付成功的回调")
    @RequestMapping(value = "/paySuccess", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult paySuccess(@RequestParam Long orderId,@RequestParam Integer payType) {
        Integer count = portalOrderService.paySuccess(orderId,payType);
        return CommonResult.success(count, "支付成功");
    }

    @ApiOperation("自动取消超时订单")
    @RequestMapping(value = "/cancelTimeOutOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult cancelTimeOutOrder() {
        portalOrderService.cancelTimeOutOrder();
        return CommonResult.success(null);
    }

    @ApiOperation("取消单个超时订单")
    @RequestMapping(value = "/cancelOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult cancelOrder(Long orderId) {
        portalOrderService.sendDelayMessageCancelOrder(orderId);
        return CommonResult.success(null);
    }

    @ApiOperation("按状态分页获取用户订单列表")
    @ApiImplicitParam(name = "status", value = "订单状态：-1->全部；0->待付款；1->待发货；2->已发货；3->已完成；4->已关闭",
            defaultValue = "-1", allowableValues = "-1,0,1,2,3,4", paramType = "query", dataType = "int")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<CommonPage<OmsOrderDetail>> list(@RequestParam Integer status,
                                                   @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                   @RequestParam(required = false, defaultValue = "5") Integer pageSize) {
        CommonPage<OmsOrderDetail> orderPage = portalOrderService.list(status,pageNum,pageSize);
        return CommonResult.success(orderPage);
    }

    @ApiOperation("根据ID获取订单详情")
    @RequestMapping(value = "/detail/{orderId}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<OmsOrderDetail> detail(@PathVariable Long orderId) {
        OmsOrderDetail orderDetail = portalOrderService.detail(orderId);
        return CommonResult.success(orderDetail);
    }

    @ApiOperation("用户取消订单")
    @RequestMapping(value = "/cancelUserOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult cancelUserOrder(Long orderId) {
        portalOrderService.cancelOrder(orderId);
        return CommonResult.success(null);
    }

    @ApiOperation("用户确认收货")
    @RequestMapping(value = "/confirmReceiveOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult confirmReceiveOrder(Long orderId) {
        portalOrderService.confirmReceiveOrder(orderId);
        return CommonResult.success(null);
    }

    @ApiOperation("用户删除订单")
    @RequestMapping(value = "/deleteOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult deleteOrder(Long orderId) {
        portalOrderService.deleteOrder(orderId);
        return CommonResult.success(null);
    }
}

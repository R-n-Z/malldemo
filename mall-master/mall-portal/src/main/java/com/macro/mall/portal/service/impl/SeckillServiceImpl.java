package com.macro.mall.portal.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.component.CancelOrderSender;
import com.macro.mall.portal.component.HotDataPreheater;
import com.macro.mall.portal.component.StockMessageSender;
import com.macro.mall.portal.domain.*;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.portal.service.SeckillService;
import com.macro.mall.portal.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 秒杀Service实现
 */
@Slf4j
@Service
public class SeckillServiceImpl implements SeckillService {

    @Value("${redis.database:mall}")
    private String redisDatabase;

    private static final String SECKILL_STOCK_KEY = "seckill:stock:";
    private static final String SECKILL_TOKEN_KEY = "seckill:token:";
    private static final String SECKILL_ORDER_KEY = "seckill:order:";
    private static final String SECKILL_USER_KEY = "seckill:user:";
    private static final String SECKILL_PRODUCT_KEY = "seckill:product:";
    private static final String SECKILL_INFO_KEY = "seckill:info:";
    private static final String SECKILL_SCRIPT_PATH = "lua/seckill.lua";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private SmsFlashPromotionMapper flashPromotionMapper;

    @Autowired
    private SmsFlashPromotionSessionMapper sessionMapper;

    @Autowired
    private SmsFlashPromotionProductRelationMapper productRelationMapper;

    @Autowired
    private PmsProductMapper productMapper;

    @Autowired
    private PmsSkuStockMapper skuStockMapper;

    @Autowired
    private StockService stockService;

    @Autowired
    private OmsPortalOrderService portalOrderService;

    @Autowired
    private CancelOrderSender cancelOrderSender;

    @Autowired
    private StockMessageSender stockMessageSender;

    @Autowired
    private HotDataPreheater hotDataPreheater;

    /**
     * Load Lua script from resources
     */
    private String loadSeckillScript() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(SECKILL_SCRIPT_PATH)) {
            if (is == null) {
                throw new RuntimeException("Seckill Lua script not found: " + SECKILL_SCRIPT_PATH);
            }
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            return result.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load seckill Lua script", e);
        }
    }

    @Override
    public Map<String, Object> getSeckillDetail(Long productId) {
        Map<String, Object> result = new HashMap<>();

        // 优先从Redis获取（预热数据）
        String productKey = redisDatabase + ":" + SECKILL_PRODUCT_KEY + productId;
        Object cachedProduct = redisTemplate.opsForValue().get(productKey);

        PmsProduct product;
        if (cachedProduct != null) {
            product = (PmsProduct) cachedProduct;
            log.debug("秒杀商品详情从Redis获取: productId={}", productId);
        } else {
            // 降级：从数据库获取
            product = productMapper.selectByPrimaryKey(productId);
            log.warn("秒杀商品详情从数据库获取（未预热）: productId={}", productId);
        }

        if (product == null) {
            return null;
        }

        result.put("product", product);

        // 获取库存信息
        String stockKey = redisDatabase + ":" + SECKILL_STOCK_KEY + productId;
        Map<Object, Object> stocks = redisTemplate.opsForHash().entries(stockKey);
        result.put("stocks", stocks);

        // 获取秒杀配置信息
        String infoKey = redisDatabase + ":" + SECKILL_INFO_KEY + productId;
        Map<Object, Object> info = redisTemplate.opsForHash().entries(infoKey);
        result.put("seckillInfo", info);

        return result;
    }

    @Override
    public SeckillPrepareResult prepareSeckill(Long productId, Long sessionId) {
        // 1. 检查秒杀活动状态
        String infoKey = redisDatabase + ":" + SECKILL_INFO_KEY + productId;
        Object infoObj = redisTemplate.opsForHash().get(infoKey, "productId");

        if (infoObj == null) {
            // 降级：查询数据库
            SmsFlashPromotionProductRelation relation = productRelationMapper.selectByPrimaryKey(productId);
            if (relation == null) {
                return SeckillPrepareResult.fail("秒杀活动不存在");
            }

            // 预热该商品
            hotDataPreheater.preheatSeckillProduct(productId);
        }

        // 2. 检查库存
        String stockKey = redisDatabase + ":" + SECKILL_STOCK_KEY + productId;
        Long totalStock = redisTemplate.opsForValue().increment(stockKey, 0);

        if (totalStock == null || totalStock <= 0) {
            return SeckillPrepareResult.fail("商品已售罄");
        }

        // 3. 生成秒杀令牌
        String seckillToken = IdUtil.fastSimpleUUID();
        String tokenKey = redisDatabase + ":" + SECKILL_TOKEN_KEY + productId + ":" + seckillToken;

        // 令牌有效期5分钟
        redisTemplate.opsForValue().set(tokenKey, "1", 5, java.util.concurrent.TimeUnit.MINUTES);

        log.info("秒杀令牌生成: productId={}, token={}", productId, seckillToken);

        return SeckillPrepareResult.success(seckillToken, totalStock.intValue());
    }

    @Override
    public Map<String, Object> doSeckill(Long productId, Long sessionId, String seckillToken) {
        Map<String, Object> result = new HashMap<>();

        // 1. 获取当前用户ID（从JWT或Session获取）
        Long memberId = 1L;  // 实际应从登录信息获取

        // 2. Execute Lua script (atomic operation)
        String stockKey = redisDatabase + ":" + SECKILL_STOCK_KEY + productId;
        String userKey = redisDatabase + ":" + SECKILL_USER_KEY + productId;
        String tokenKey = redisDatabase + ":" + SECKILL_TOKEN_KEY + productId + ":" + seckillToken;

        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(loadSeckillScript());
        redisScript.setResultType(List.class);

        List<Long> scriptResult = redisTemplate.execute(
                redisScript,
                Arrays.asList(stockKey, userKey, tokenKey),
                memberId, 1, 1, seckillToken  // quantity=1, limit=1
        );

        if (scriptResult == null || scriptResult.isEmpty()) {
            result.put("success", false);
            result.put("message", "秒杀失败");
            return result;
        }

        int code = scriptResult.get(0).intValue();
        String message = scriptResult.get(1).toString();

        if (code == 1) {
            // 3. 秒杀成功，创建订单
            result.put("success", true);
            result.put("message", message);

            // 发送订单创建消息到MQ
            SeckillMessage messageObj = new SeckillMessage();
            messageObj.setProductId(productId);
            messageObj.setMemberId(memberId);
            messageObj.setQuantity(1);
            messageObj.setOrderSn(generateOrderSn());
            stockMessageSender.sendSeckillMessage(messageObj);

            result.put("orderSn", messageObj.getOrderSn());
            log.info("秒杀成功: productId={}, memberId={}, orderSn={}",
                    productId, memberId, messageObj.getOrderSn());
        } else {
            result.put("success", false);
            result.put("message", message);
            log.warn("秒杀失败: productId={}, memberId={}, code={}, message={}",
                    productId, memberId, code, message);
        }

        return result;
    }

    @Override
    public Map<String, Object> getSeckillResult(Long orderId) {
        Map<String, Object> result = new HashMap<>();

        // 从Redis获取订单状态
        String orderKey = redisDatabase + ":" + SECKILL_ORDER_KEY + orderId;
        Object status = redisTemplate.opsForValue().get(orderKey);

        if (status != null) {
            result.put("status", status);
            result.put("orderId", orderId);
        } else {
            // 降级：查询数据库
            result.put("status", "PROCESSING");
            result.put("orderId", orderId);
        }

        return result;
    }

    @Override
    public void handleSeckillMessage(SeckillMessage message) {
        try {
            log.info("处理秒杀消息: productId={}, memberId={}",
                    message.getProductId(), message.getMemberId());

            // 创建订单
            OrderParam orderParam = new OrderParam();
            orderParam.setProductId(message.getProductId());
            orderParam.setIsSeckillOrder(true);
            orderParam.setQuantity(message.getQuantity());

            Map<String, Object> orderResult = portalOrderService.generateOrder(orderParam);

            // 发送延迟消息用于超时取消
            if (orderResult.containsKey("order")) {
                OmsOrder order = (OmsOrder) orderResult.get("order");
                cancelOrderSender.sendMessage(order.getId(), 30 * 60 * 1000);  // 30分钟
            }

        } catch (Exception e) {
            log.error("处理秒杀消息失败", e);
            // 释放库存
            stockService.releaseStock(message.getLockToken());
        }
    }

    /**
     * 生成订单号
     */
    private String generateOrderSn() {
        StringBuilder sb = new StringBuilder();
        String date = new java.text.SimpleDateFormat("yyyyMMdd").format(new Date());
        sb.append(date);
        sb.append(IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase());
        return sb.toString();
    }
}
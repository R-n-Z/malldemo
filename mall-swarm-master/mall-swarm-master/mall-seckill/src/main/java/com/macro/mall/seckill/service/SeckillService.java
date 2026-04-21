package com.macro.mall.seckill.service;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.fastjson.JSON;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.seckill.component.SeckillOrderMessageSender;
import com.macro.mall.seckill.domain.SeckillOrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀服务
 */
@Slf4j
@Service
public class SeckillService {

    @Autowired
    private StockService stockService;

    @Autowired
    private OrderLockService orderLockService;

    @Autowired
    private SeckillOrderMessageSender messageSender;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${seckill.rate-limit.per-user-qps:10}")
    private int perUserQps;

    private static final String USER_ORDER_KEY = "seckill:user:order:";
    private static final String CAPTCHA_KEY = "seckill:captcha:";

    /**
     * 秒杀商品
     * 
     * @param productId 商品ID
     * @param code 验证码
     * @return 秒杀结果
     */
    @SentinelResource(value = "seckill", blockHandler = "seckillBlockHandler")
    public CommonResult<String> seckill(Long productId, String code) {
        Long userId = StpUtil.getLoginIdAsLong();
        
        // 1. 验证码校验
        if (!verifyCaptcha(userId, productId, code)) {
            return CommonResult.failed("验证码错误");
        }
        
        // 2. 检查用户是否已购买（每人限购）
        if (hasUserOrdered(userId, productId)) {
            return CommonResult.failed("您已购买过该商品");
        }
        
        // 3. 库存预扣减
        if (!stockService.preDeductStock(productId, 1)) {
            return CommonResult.failed("库存不足");
        }
        
        // 4. 获取用户锁，防止重复提交
        if (!orderLockService.tryLock(userId, productId, 10, 30)) {
            // 释放库存
            stockService.rollbackStock(productId, 1);
            return CommonResult.failed("请勿重复提交");
        }
        
        try {
            // 5. 构建订单消息
            SeckillOrderMessage message = new SeckillOrderMessage();
            message.setOrderSn(generateOrderSn());
            message.setUserId(userId);
            message.setProductId(productId);
            message.setSeckillPrice(getSeckillPrice(productId));
            message.setCount(1);
            
            // 6. 发送订单消息（异步处理）
            messageSender.sendOrderMessage(message);
            
            // 7. 标记用户已购买
            markUserOrdered(userId, productId);
            
            log.info("秒杀成功: userId={}, productId={}, orderSn={}", 
                    userId, productId, message.getOrderSn());
            
            return CommonResult.success(message.getOrderSn(), "秒杀成功，请等待订单处理");
            
        } finally {
            orderLockService.unlock(userId, productId);
        }
    }

    /**
     * 限流降级处理
     */
    public CommonResult<String> seckillBlockHandler(Long productId, String code, BlockException ex) {
        log.warn("秒杀接口限流: productId={}", productId);
        return CommonResult.failed("系统繁忙，请稍后再试");
    }

    /**
     * 获取库存（带限流）
     */
    @SentinelResource(value = "getStock", blockHandler = "getStockBlockHandler")
    public CommonResult<Integer> getStock(Long productId) {
        Integer stock = stockService.getStock(productId);
        return CommonResult.success(stock);
    }

    public CommonResult<Integer> getStockBlockHandler(Long productId, BlockException ex) {
        return CommonResult.success(0);
    }

    /**
     * 生成验证码
     */
    @SentinelResource(value = "captcha", blockHandler = "captchaBlockHandler")
    public CommonResult<String> generateCaptcha(Long productId) {
        Long userId = StpUtil.getLoginIdAsLong();
        
        // 生成4位数字验证码
        String captcha = String.valueOf((int) (Math.random() * 9000) + 1000);
        String captchaKey = CAPTCHA_KEY + userId + ":" + productId;
        
        // 存入Redis，5分钟有效
        redisTemplate.opsForValue().set(captchaKey, captcha, 5, TimeUnit.MINUTES);
        
        log.info("验证码生成: userId={}, productId={}, captcha={}", 
                userId, productId, captcha);
        
        // 实际项目中这里应该返回图片Base64，这里简化处理返回验证码
        return CommonResult.success(captcha);
    }

    public CommonResult<String> captchaBlockHandler(Long productId, BlockException ex) {
        return CommonResult.failed("验证码获取失败");
    }

    /**
     * 校验验证码
     */
    private boolean verifyCaptcha(Long userId, Long productId, String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        String captchaKey = CAPTCHA_KEY + userId + ":" + productId;
        String storedCaptcha = (String) redisTemplate.opsForValue().get(captchaKey);
        return code.equals(storedCaptcha);
    }

    /**
     * 检查用户是否已购买
     */
    private boolean hasUserOrdered(Long userId, Long productId) {
        String key = USER_ORDER_KEY + userId + ":" + productId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 标记用户已购买
     */
    private void markUserOrdered(Long userId, Long productId) {
        String key = USER_ORDER_KEY + userId + ":" + productId;
        redisTemplate.opsForValue().set(key, "1", 24, TimeUnit.HOURS);
    }

    /**
     * 获取秒杀价格（从Redis获取）
     */
    private BigDecimal getSeckillPrice(Long productId) {
        String priceKey = "seckill:price:" + productId;
        Object price = redisTemplate.opsForValue().get(priceKey);
        if (price != null) {
            return new BigDecimal(price.toString());
        }
        return BigDecimal.ZERO;
    }

    /**
     * 生成订单号
     */
    private String generateOrderSn() {
        return "SK" + System.currentTimeMillis() + 
               String.valueOf((int) (Math.random() * 9000) + 1000);
    }
}
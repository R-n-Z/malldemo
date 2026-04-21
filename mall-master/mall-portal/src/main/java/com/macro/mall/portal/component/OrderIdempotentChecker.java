package com.macro.mall.portal.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 订单幂等性校验器
 * 防止恶意重复下单请求
 */
@Slf4j
@Component
public class OrderIdempotentChecker {

    @Value("${redis.database:mall}")
    private String redisDatabase;

    private static final String ORDER_IDEMPOTENT_KEY = "order:idempotent:";
    private static final long IDEMPOTENT_EXPIRE_SECONDS = 600; // 10分钟内有效

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 生成请求签名
     * 签名格式: MD5(userId + cartIds + addressId + timestamp)
     */
    public String generateSign(Long memberId, String cartIds, Long addressId, Long timestamp) {
        StringBuilder sb = new StringBuilder();
        sb.append(memberId).append(":");
        sb.append(cartIds).append(":");
        sb.append(addressId).append(":");
        sb.append(timestamp);
        return DigestUtils.md5DigestAsHex(sb.toString().getBytes());
    }

    /**
     * 尝试获取幂等锁
     * @return true=可以继续处理请求, false=请求已存在，请勿重复提交
     */
    public boolean tryAcquire(Long memberId, String sign) {
        String key = redisDatabase + ":" + ORDER_IDEMPOTENT_KEY + memberId + ":" + sign;
        
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", IDEMPOTENT_EXPIRE_SECONDS, TimeUnit.SECONDS);
        
        if (Boolean.TRUE.equals(success)) {
            log.info("幂等锁获取成功: memberId={}, sign={}", memberId, sign);
            return true;
        } else {
            log.warn("幂等锁获取失败，可能重复请求: memberId={}, sign={}", memberId, sign);
            return false;
        }
    }

    /**
     * 释放幂等锁（下单成功后调用）
     */
    public void release(Long memberId, String sign) {
        String key = redisDatabase + ":" + ORDER_IDEMPOTENT_KEY + memberId + ":" + sign;
        redisTemplate.delete(key);
        log.info("幂等锁已释放: memberId={}, sign={}", memberId, sign);
    }

    /**
     * 检查请求是否已存在
     */
    public boolean exists(Long memberId, String sign) {
        String key = redisDatabase + ":" + ORDER_IDEMPOTENT_KEY + memberId + ":" + sign;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 生成请求唯一标识
     * 用于前端生成请求签名
     */
    public String generateRequestId(Long memberId) {
        return memberId + ":" + System.currentTimeMillis();
    }
}
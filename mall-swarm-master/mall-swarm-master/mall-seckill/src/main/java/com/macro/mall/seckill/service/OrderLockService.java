package com.macro.mall.seckill.service;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 订单锁服务 - 分布式锁
 */
@Slf4j
@Service
public class OrderLockService {

    @Autowired
    private RedissonClient redissonClient;

    private static final String ORDER_LOCK_PREFIX = "seckill:lock:order:";
    private static final String USER_LOCK_PREFIX = "seckill:lock:user:";

    /**
     * 获取订单创建锁（防止重复下单）
     * 
     * @param orderSn 订单号
     * @return 锁对象
     */
    public RLock getOrderLock(String orderSn) {
        String lockKey = ORDER_LOCK_PREFIX + orderSn;
        return redissonClient.getLock(lockKey);
    }

    /**
     * 获取用户秒杀锁（防止用户重复提交）
     * 
     * @param userId 用户ID
     * @param productId 商品ID
     * @return 锁对象
     */
    public RLock getUserSeckillLock(Long userId, Long productId) {
        String lockKey = USER_LOCK_PREFIX + userId + ":" + productId;
        return redissonClient.getLock(lockKey);
    }

    /**
     * 尝试获取用户秒杀锁
     * 
     * @param userId 用户ID
     * @param productId 商品ID
     * @param waitTime 等待时间（秒）
     * @param leaseTime 持有时间（秒）
     * @return true-获取成功，false-锁已被占用
     */
    public boolean tryLock(Long userId, Long productId, long waitTime, long leaseTime) {
        RLock lock = getUserSeckillLock(userId, productId);
        try {
            return lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("获取用户秒杀锁被中断: userId={}, productId={}", userId, productId);
            return false;
        }
    }

    /**
     * 释放锁
     */
    public void unlock(RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * 释放用户秒杀锁
     */
    public void unlock(Long userId, Long productId) {
        RLock lock = getUserSeckillLock(userId, productId);
        unlock(lock);
    }

    /**
     * 带锁执行任务
     * 
     * @param userId 用户ID
     * @param productId 商品ID
     * @param task 任务
     * @param <T> 返回类型
     * @return 任务执行结果
     */
    public <T> T executeWithLock(Long userId, Long productId, Supplier<T> task) {
        RLock lock = getUserSeckillLock(userId, productId);
        try {
            if (lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                try {
                    return task.get();
                } finally {
                    unlock(lock);
                }
            } else {
                log.warn("用户正在秒杀中，无法获取锁: userId={}, productId={}", userId, productId);
                throw new RuntimeException("请勿重复提交");
            }
        } catch (InterruptedException e) {
            log.error("秒杀任务被中断: userId={}, productId={}", userId, productId);
            throw new RuntimeException("系统繁忙，请重试");
        }
    }
}
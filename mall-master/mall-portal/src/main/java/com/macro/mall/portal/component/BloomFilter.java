package com.macro.mall.portal.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.BitSet;
import java.util.List;

/**
 * 布隆过滤器组件
 * 用于防止缓存穿透，支持Redis持久化
 */
@Slf4j
@Component
public class BloomFilter {

    @Value("${redis.database:mall}")
    private String redisDatabase;

    private static final String BLOOM_FILTER_KEY = "bloom:filter:";

    /**
     * 默认配置
     */
    private static final int DEFAULT_SIZE = 1 << 26;  // 约6700万位，够用
    private static final int DEFAULT_HASH_FUNCTIONS = 6;

    private final RedisTemplate<String, Object> redisTemplate;

    public BloomFilter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 添加元素到布隆过滤器
     * @param filterName 过滤器名称
     * @param value 值
     */
    public void add(String filterName, String value) {
        String key = redisDatabase + ":" + BLOOM_FILTER_KEY + filterName;
        int[] hashValues = hash(value);
        for (int hashValue : hashValues) {
            redisTemplate.opsForValue().setBit(key, hashValue, true);
        }
    }

    /**
     * 批量添加元素
     */
    public void addAll(String filterName, List<String> values) {
        for (String value : values) {
            add(filterName, value);
        }
    }

    /**
     * 检查元素是否可能存在
     * @return true=可能存在, false=一定不存在
     */
    public boolean mightContain(String filterName, String value) {
        String key = redisDatabase + ":" + BLOOM_FILTER_KEY + filterName;
        int[] hashValues = hash(value);
        for (int hashValue : hashValues) {
            if (!Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(key, hashValue))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 删除布隆过滤器
     */
    public void delete(String filterName) {
        String key = redisDatabase + ":" + BLOOM_FILTER_KEY + filterName;
        redisTemplate.delete(key);
    }

    /**
     * 计算元素在布隆过滤器中的位置
     * 使用MurmurHash或MD5计算多个哈希值
     */
    private int[] hash(String value) {
        int[] result = new int[DEFAULT_HASH_FUNCTIONS];
        
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(value.getBytes(StandardCharsets.UTF_8));
            
            // 使用MD5的多个部分生成多个哈希值
            for (int i = 0; i < DEFAULT_HASH_FUNCTIONS; i++) {
                long hash = ((long) (digest[i * 4] & 0xFF) << 24) |
                           ((long) (digest[i * 4 + 1] & 0xFF) << 16) |
                           ((long) (digest[i * 4 + 2] & 0xFF) << 8) |
                           ((long) (digest[i * 4 + 3] & 0xFF));
                
                result[i] = (int) (Math.abs(hash) % DEFAULT_SIZE);
            }
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 algorithm not found", e);
            // 备用方案：使用Java内置hashCode
            for (int i = 0; i < DEFAULT_HASH_FUNCTIONS; i++) {
                result[i] = (value.hashCode() + i * 31) % DEFAULT_SIZE;
            }
        }
        
        return result;
    }

    /**
     * 获取布隆过滤器当前容量使用情况
     */
    public double getFillRatio(String filterName) {
        String key = redisDatabase + ":" + BLOOM_FILTER_KEY + filterName;
        // 采样检查前1000位的使用情况
        int checked = 0;
        int setBits = 0;
        for (int i = 0; i < 1000; i++) {
            if (Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(key, i))) {
                setBits++;
            }
            checked++;
        }
        return (double) setBits / checked;
    }

    /**
     * 预热布隆过滤器
     * 从数据库加载所有存在的ID
     */
    public void warmUp(String filterName, List<String> values) {
        log.info("开始预热布隆过滤器: filterName={}, count={}", filterName, values.size());
        addAll(filterName, values);
        log.info("布隆过滤器预热完成: filterName={}", filterName);
    }
}
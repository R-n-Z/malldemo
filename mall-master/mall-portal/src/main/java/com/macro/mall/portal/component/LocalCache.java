package com.macro.mall.portal.component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 本地缓存组件
 * 基于Caffeine实现高性能本地缓存
 */
@Slf4j
@Component
public class LocalCache {

    /**
     * 默认配置
     */
    private static final int DEFAULT_MAX_SIZE = 10000;
    private static final int DEFAULT_EXPIRE_SECONDS = 300;

    /**
     * 商品详情缓存
     */
    private Cache<String, Object> productCache;

    /**
     * 分类缓存
     */
    private Cache<String, Object> categoryCache;

    /**
     * 品牌缓存
     */
    private Cache<String, Object> brandCache;

    /**
     * 配置缓存
     */
    private Cache<String, Object> configCache;

    /**
     * 热点数据缓存（容量大，过期时间长）
     */
    private Cache<String, Object> hotDataCache;

    @Value("${cache.local.product.max-size:5000}")
    private int productMaxSize;

    @Value("${cache.local.product.expire-seconds:300}")
    private int productExpireSeconds;

    @Value("${cache.local.category.max-size:1000}")
    private int categoryMaxSize;

    @Value("${cache.local.category.expire-seconds:600}")
    private int categoryExpireSeconds;

    @Value("${cache.local.hot-data.max-size:10000}")
    private int hotDataMaxSize;

    @Value("${cache.local.hot-data.expire-seconds:3600}")
    private int hotDataExpireSeconds;

    /**
     * 初始化缓存
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        // 商品缓存
        productCache = Caffeine.newBuilder()
                .maximumSize(productMaxSize > 0 ? productMaxSize : DEFAULT_MAX_SIZE)
                .expireAfterWrite(productExpireSeconds > 0 ? productExpireSeconds : DEFAULT_EXPIRE_SECONDS, TimeUnit.SECONDS)
                .recordStats()
                .build();

        // 分类缓存
        categoryCache = Caffeine.newBuilder()
                .maximumSize(categoryMaxSize > 0 ? categoryMaxSize : 1000)
                .expireAfterWrite(categoryExpireSeconds > 0 ? categoryExpireSeconds : 600, TimeUnit.SECONDS)
                .recordStats()
                .build();

        // 品牌缓存
        brandCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(3600, TimeUnit.SECONDS)
                .recordStats()
                .build();

        // 配置缓存
        configCache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(1800, TimeUnit.SECONDS)
                .recordStats()
                .build();

        // 热点数据缓存
        hotDataCache = Caffeine.newBuilder()
                .maximumSize(hotDataMaxSize > 0 ? hotDataMaxSize : DEFAULT_MAX_SIZE)
                .expireAfterWrite(hotDataExpireSeconds > 0 ? hotDataExpireSeconds : 3600, TimeUnit.SECONDS)
                .recordStats()
                .build();

        log.info("本地缓存初始化完成: product={}, category={}, brand={}, hotData={}",
                productMaxSize, categoryMaxSize, 500, hotDataMaxSize);
    }

    // ==================== 商品缓存 ====================

    /**
     * 获取商品缓存
     */
    public Object getProduct(String key) {
        return productCache.getIfPresent(key);
    }

    /**
     * 设置商品缓存
     */
    public void putProduct(String key, Object value) {
        productCache.put(key, value);
    }

    /**
     * 删除商品缓存
     */
    public void evictProduct(String key) {
        productCache.invalidate(key);
    }

    // ==================== 分类缓存 ====================

    /**
     * 获取分类缓存
     */
    public Object getCategory(String key) {
        return categoryCache.getIfPresent(key);
    }

    /**
     * 设置分类缓存
     */
    public void putCategory(String key, Object value) {
        categoryCache.put(key, value);
    }

    /**
     * 删除分类缓存
     */
    public void evictCategory(String key) {
        categoryCache.invalidate(key);
    }

    // ==================== 品牌缓存 ====================

    /**
     * 获取品牌缓存
     */
    public Object getBrand(String key) {
        return brandCache.getIfPresent(key);
    }

    /**
     * 设置品牌缓存
     */
    public void putBrand(String key, Object value) {
        brandCache.put(key, value);
    }

    // ==================== 配置缓存 ====================

    /**
     * 获取配置缓存
     */
    public Object getConfig(String key) {
        return configCache.getIfPresent(key);
    }

    /**
     * 设置配置缓存
     */
    public void putConfig(String key, Object value) {
        configCache.put(key, value);
    }

    // ==================== 热点数据缓存 ====================

    /**
     * 获取热点数据缓存
     */
    public Object getHotData(String key) {
        return hotDataCache.getIfPresent(key);
    }

    /**
     * 设置热点数据缓存
     */
    public void putHotData(String key, Object value) {
        hotDataCache.put(key, value);
    }

    /**
     * 删除热点数据缓存
     */
    public void evictHotData(String key) {
        hotDataCache.invalidate(key);
    }

    // ==================== 通用方法 ====================

    /**
     * 清空所有缓存
     */
    public void clearAll() {
        productCache.invalidateAll();
        categoryCache.invalidateAll();
        brandCache.invalidateAll();
        configCache.invalidateAll();
        hotDataCache.invalidateAll();
        log.info("本地缓存已清空");
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        CacheStats stats = new CacheStats();
        stats.setProductSize(productCache.estimatedSize());
        stats.setCategorySize(categoryCache.estimatedSize());
        stats.setBrandSize(brandCache.estimatedSize());
        stats.setConfigSize(configCache.estimatedSize());
        stats.setHotDataSize(hotDataCache.estimatedSize());
        
        // 命中率统计
        stats.setProductHitRate(productCache.stats().hitRate());
        stats.setCategoryHitRate(categoryCache.stats().hitRate());
        stats.setHotDataHitRate(hotDataCache.stats().hitRate());
        
        return stats;
    }

    /**
     * 缓存统计
     */
    @lombok.Data
    public static class CacheStats {
        private long productSize;
        private long categorySize;
        private long brandSize;
        private long configSize;
        private long hotDataSize;
        private double productHitRate;
        private double categoryHitRate;
        private double hotDataHitRate;
    }
}
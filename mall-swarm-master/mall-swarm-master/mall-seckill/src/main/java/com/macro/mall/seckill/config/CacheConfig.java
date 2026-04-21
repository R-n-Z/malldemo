package com.macro.mall.seckill.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine本地缓存配置 - 三级缓存L1
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${seckill.cache.local.size:1000}")
    private int localCacheSize;

    @Value("${seckill.cache.local.timeout:5}")
    private int localCacheTimeout;

    /**
     * 商品信息缓存名称
     */
    public static final String PRODUCT_INFO_CACHE = "productInfoCache";

    /**
     * 库存缓存名称
     */
    public static final String STOCK_CACHE = "stockCache";

    /**
     * 秒杀价格缓存名称
     */
    public static final String PRICE_CACHE = "priceCache";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        // 商品信息缓存
        CaffeineCache productInfoCache = new CaffeineCache(PRODUCT_INFO_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(localCacheSize)
                        .expireAfterWrite(localCacheTimeout, TimeUnit.SECONDS)
                        .recordStats()
                        .build());

        // 库存缓存
        CaffeineCache stockCache = new CaffeineCache(STOCK_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(localCacheSize)
                        .expireAfterWrite(localCacheTimeout, TimeUnit.SECONDS)
                        .recordStats()
                        .build());

        // 秒杀价格缓存
        CaffeineCache priceCache = new CaffeineCache(PRICE_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(localCacheSize)
                        .expireAfterWrite(localCacheTimeout, TimeUnit.SECONDS)
                        .recordStats()
                        .build());

        List<CaffeineCache> caches = new ArrayList<>();
        caches.add(productInfoCache);
        caches.add(stockCache);
        caches.add(priceCache);

        cacheManager.setCaches(caches);
        return cacheManager;
    }
}
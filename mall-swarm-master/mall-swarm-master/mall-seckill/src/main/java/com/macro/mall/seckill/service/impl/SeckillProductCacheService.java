package com.macro.mall.seckill.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.macro.mall.seckill.config.CacheConfig;
import com.macro.mall.seckill.domain.SeckillProduct;
import com.macro.mall.seckill.mapper.SeckillProductMapper;
import com.macro.mall.seckill.service.CachePreheatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 缓存预热服务
 */
@Slf4j
@Service
public class SeckillProductCacheService {

    @Autowired
    private SeckillProductMapper seckillProductMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private Cache<String, Object> productInfoCache;

    @Autowired
    private Cache<String, Object> stockCache;

    @Autowired
    private Cache<String, Object> priceCache;

    @Value("${seckill.stock.cache-key:seckill:stock:}")
    private String stockKeyPrefix;

    @Value("${seckill.product.info-key:seckill:product:}")
    private String productInfoKeyPrefix;

    @Value("${seckill.product.price-key:seckill:price:}")
    private String priceKeyPrefix;

    private static final String PRODUCT_LIST_KEY = "seckill:product:list:active";

    /**
     * 预热所有活动商品缓存
     */
    public void preheatAllActiveProducts() {
        log.info("开始预热秒杀商品缓存...");
        
        try {
            // 1. 查询所有进行中的秒杀商品
            List<SeckillProduct> activeProducts = seckillProductMapper.selectActive();
            
            if (activeProducts.isEmpty()) {
                log.info("没有进行中的秒杀活动");
                return;
            }
            
            // 2. 预热每个商品
            int successCount = 0;
            for (SeckillProduct product : activeProducts) {
                try {
                    preheatProduct(product);
                    successCount++;
                } catch (Exception e) {
                    log.error("预热商品失败: productId={}", product.getProductId(), e);
                }
            }
            
            log.info("预热完成: 总数={}, 成功={}", activeProducts.size(), successCount);
            
        } catch (Exception e) {
            log.error("预热秒杀商品缓存失败", e);
        }
    }

    /**
     * 预热单个商品
     */
    public void preheatProduct(SeckillProduct product) {
        Long productId = product.getProductId();
        
        // 1. 预热Redis库存
        String stockKey = stockKeyPrefix + productId;
        redisTemplate.opsForValue().set(stockKey, product.getSeckillStock());
        log.debug("预热库存: productId={}, stock={}", productId, product.getSeckillStock());
        
        // 2. 预热Redis商品信息
        String productInfoKey = productInfoKeyPrefix + productId;
        redisTemplate.opsForValue().set(productInfoKey, product);
        log.debug("预热商品信息: productId={}", productId);
        
        // 3. 预热Redis秒杀价格
        String priceKey = priceKeyPrefix + productId;
        redisTemplate.opsForValue().set(priceKey, product.getSeckillPrice());
        log.debug("预热价格: productId={}, price={}", productId, product.getSeckillPrice());
        
        // 4. 预热本地缓存(Caffeine)
        productInfoCache.put("product:" + productId, product);
        stockCache.put("stock:" + productId, product.getSeckillStock());
        priceCache.put("price:" + productId, product.getSeckillPrice());
        log.debug("预热本地缓存: productId={}", productId);
        
        // 5. 更新活动商品列表
        redisTemplate.opsForSet().add(PRODUCT_LIST_KEY, productId);
    }

    /**
     * 预热指定商品
     */
    public void preheatProductById(Long productId) {
        SeckillProduct product = seckillProductMapper.selectByProductId(productId);
        if (product != null) {
            preheatProduct(product);
            log.info("预热指定商品成功: productId={}", productId);
        } else {
            log.warn("商品不存在: productId={}", productId);
        }
    }

    /**
     * 预热指定时间范围内的商品
     */
    public void preheatByTimeRange(Date startTime, Date endTime) {
        log.info("预热时间范围内的商品: startTime={}, endTime={}", startTime, endTime);
        
        List<SeckillProduct> products = seckillProductMapper.selectByTimeRange(startTime, endTime);
        for (SeckillProduct product : products) {
            preheatProduct(product);
        }
        
        log.info("预热完成: 数量={}", products.size());
    }

    /**
     * 刷新商品缓存（用于库存变化时）
     */
    public void refreshProductCache(Long productId) {
        // 删除旧缓存
        clearProductCache(productId);
        
        // 重新预热
        preheatProductById(productId);
        
        log.info("刷新商品缓存: productId={}", productId);
    }

    /**
     * 清除商品缓存
     */
    public void clearProductCache(Long productId) {
        // Redis缓存
        redisTemplate.delete(stockKeyPrefix + productId);
        redisTemplate.delete(productInfoKeyPrefix + productId);
        redisTemplate.delete(priceKeyPrefix + productId);
        
        // 本地缓存
        productInfoCache.invalidate("product:" + productId);
        stockCache.invalidate("stock:" + productId);
        priceCache.invalidate("price:" + productId);
        
        // 活动列表
        redisTemplate.opsForSet().remove(PRODUCT_LIST_KEY, productId);
        
        log.debug("清除商品缓存: productId={}", productId);
    }

    /**
     * 获取商品信息（多级缓存读取）
     */
    public SeckillProduct getProductInfo(Long productId) {
        // 1. 先查本地缓存
        Object cached = productInfoCache.get("product:" + productId);
        if (cached != null) {
            log.debug("本地缓存命中: productId={}", productId);
            return (SeckillProduct) cached;
        }
        
        // 2. 再查Redis缓存
        String productInfoKey = productInfoKeyPrefix + productId;
        cached = redisTemplate.opsForValue().get(productInfoKey);
        if (cached != null) {
            log.debug("Redis缓存命中: productId={}", productId);
            // 写入本地缓存
            productInfoCache.put("product:" + productId, cached);
            return (SeckillProduct) cached;
        }
        
        // 3. 查数据库
        SeckillProduct product = seckillProductMapper.selectByProductId(productId);
        if (product != null) {
            // 写入缓存
            preheatProduct(product);
        }
        
        return product;
    }

    /**
     * 获取库存（多级缓存读取）
     */
    public Integer getStock(Long productId) {
        // 1. 先查本地缓存
        Object cached = stockCache.get("stock:" + productId);
        if (cached != null) {
            return Integer.parseInt(cached.toString());
        }
        
        // 2. 再查Redis缓存
        String stockKey = stockKeyPrefix + productId;
        cached = redisTemplate.opsForValue().get(stockKey);
        if (cached != null) {
            // 写入本地缓存
            stockCache.put("stock:" + productId, cached);
            return Integer.parseInt(cached.toString());
        }
        
        // 3. 查数据库
        SeckillProduct product = seckillProductMapper.selectByProductId(productId);
        if (product != null) {
            return product.getSeckillStock();
        }
        
        return 0;
    }

    /**
     * 获取秒杀价格（多级缓存读取）
     */
    public BigDecimal getSeckillPrice(Long productId) {
        // 1. 先查本地缓存
        Object cached = priceCache.get("price:" + productId);
        if (cached != null) {
            return new BigDecimal(cached.toString());
        }
        
        // 2. 再查Redis缓存
        String priceKey = priceKeyPrefix + productId;
        cached = redisTemplate.opsForValue().get(priceKey);
        if (cached != null) {
            // 写入本地缓存
            priceCache.put("price:" + productId, cached);
            return new BigDecimal(cached.toString());
        }
        
        // 3. 查数据库
        SeckillProduct product = seckillProductMapper.selectByProductId(productId);
        if (product != null) {
            return product.getSeckillPrice();
        }
        
        return BigDecimal.ZERO;
    }

    /**
     * 获取所有活动商品ID
     */
    public List<Long> getActiveProductIds() {
        return redisTemplate.opsForSet().members(PRODUCT_LIST_KEY)
                .stream()
                .map(id -> Long.parseLong(id.toString()))
                .toList();
    }
}
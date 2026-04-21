package com.macro.mall.portal.component;

import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.mapper.SmsFlashPromotionProductRelationMapper;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductExample;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.PmsSkuStockExample;
import com.macro.mall.model.SmsFlashPromotionProductRelation;
import com.macro.mall.model.SmsFlashPromotionProductRelationExample;
import com.macro.mall.portal.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 热点数据预热组件
 * 在秒杀开始前将热点数据加载到Redis，防止数据库被击穿
 */
@Slf4j
@Component
public class HotDataPreheater {

    @Value("${redis.database:mall}")
    private String redisDatabase;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PmsProductMapper productMapper;

    @Autowired
    private PmsSkuStockMapper skuStockMapper;

    @Autowired
    private SmsFlashPromotionProductRelationMapper flashProductRelationMapper;

    @Autowired
    private CacheService cacheService;

    // 预热缓存Key前缀
    private static final String SECKILL_PRODUCT_KEY = "seckill:product:";
    private static final String SECKILL_STOCK_KEY = "seckill:stock:";
    private static final String SECKILL_INFO_KEY = "seckill:info:";
    private static final String HOT_PRODUCT_KEY = "hot:product:";
    private static final String PREHEAT_STATUS_KEY = "preheat:status:";

    // 预热配置
    @Value("${seckill.preheat-minutes:30}")
    private int preheatMinutes;  // 提前多少分钟预热

    @Value("${seckill.preheat-enabled:true}")
    private boolean preheatEnabled;

    /**
     * 系统启动时预热
     */
    @PostConstruct
    public void init() {
        if (preheatEnabled) {
            log.info("系统启动，开始预热热点数据...");
            preheatAllSeckillProducts();
        }
    }

    // ==================== 1. 秒杀商品预热 ====================

    /**
     * 预热所有秒杀商品数据
     */
    public void preheatAllSeckillProducts() {
        log.info("开始预热秒杀商品数据...");
        long startTime = System.currentTimeMillis();

        try {
            // 1. 获取所有秒杀商品
            List<SmsFlashPromotionProductRelation> seckillProducts = getAllSeckillProducts();
            
            if (seckillProducts.isEmpty()) {
                log.info("没有秒杀商品需要预热");
                return;
            }

            log.info("发现 {} 个秒杀商品需要预热", seckillProducts.size());

            // 2. 批量预热
            int successCount = 0;
            int failCount = 0;

            for (SmsFlashPromotionProductRelation product : seckillProducts) {
                try {
                    preheatSeckillProduct(product.getProductId());
                    successCount++;
                } catch (Exception e) {
                    log.error("预热商品失败: productId={}", product.getProductId(), e);
                    failCount++;
                }
            }

            // 3. 更新预热状态
            updatePreheatStatus("ALL", successCount, failCount);

            long cost = System.currentTimeMillis() - startTime;
            log.info("秒杀商品预热完成: 成功={}, 失败={}, 耗时={}ms", 
                    successCount, failCount, cost);

        } catch (Exception e) {
            log.error("预热秒杀商品数据失败", e);
        }
    }

    /**
     * 预热单个秒杀商品
     */
    public void preheatSeckillProduct(Long productId) {
        log.info("预热秒杀商品: productId={}", productId);

        // 1. 加载商品信息
        PmsProduct product = productMapper.selectByPrimaryKey(productId);
        if (product == null) {
            log.warn("商品不存在: productId={}", productId);
            return;
        }

        // 2. 预热商品基本信息
        String productKey = redisDatabase + ":" + SECKILL_PRODUCT_KEY + productId;
        redisTemplate.opsForValue().set(productKey, product, 24 * 3600, TimeUnit.SECONDS);

        // 3. 预热库存信息
        PmsSkuStockExample example = new PmsSkuStockExample();
        example.createCriteria().andProductIdEqualTo(productId);
        List<PmsSkuStock> skuStocks = skuStockMapper.selectByExample(example);

        String stockKey = redisDatabase + ":" + SECKILL_STOCK_KEY + productId;
        Map<String, Integer> stockMap = skuStocks.stream()
                .collect(Collectors.toMap(
                        PmsSkuStock::getSkuCode,
                        PmsSkuStock::getStock,
                        (a, b) -> a
                ));
        redisTemplate.opsForHash().putAll(stockKey, stockMap);

        // 4. 预热秒杀配置信息
        Map<String, Object> seckillInfo = new HashMap<>();
        seckillInfo.put("productId", productId);
        seckillInfo.put("productName", product.getName());
        seckillInfo.put("price", product.getPrice());
        seckillInfo.put("seckillPrice", product.getPromotionPrice());
        seckillInfo.put("totalStock", stockMap.values().stream().mapToInt(Integer::intValue).sum());
        seckillInfo.put("preheatTime", System.currentTimeMillis());

        String infoKey = redisDatabase + ":" + SECKILL_INFO_KEY + productId;
        redisTemplate.opsForHash().putAll(infoKey, seckillInfo);

        log.debug("商品预热完成: productId={}, stock={}", productId, stockMap.values().stream().mapToInt(Integer::intValue).sum());
    }

    /**
     * 批量预热秒杀商品
     */
    public void preheatSeckillProducts(List<Long> productIds) {
        log.info("批量预热秒杀商品: count={}", productIds.size());
        
        for (Long productId : productIds) {
            preheatSeckillProduct(productId);
        }
        
        log.info("批量预热完成: count={}", productIds.size());
    }

    // ==================== 2. 热点商品预热 ====================

    /**
     * 预热热点商品（访问量大的商品）
     */
    public void preheatHotProducts(List<Long> productIds) {
        log.info("开始预热热点商品: count={}", productIds.size());
        long startTime = System.currentTimeMillis();

        for (Long productId : productIds) {
            try {
                PmsProduct product = productMapper.selectByPrimaryKey(productId);
                if (product != null) {
                    String key = redisDatabase + ":" + HOT_PRODUCT_KEY + productId;
                    redisTemplate.opsForValue().set(key, product, 2 * 3600, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                log.error("预热热点商品失败: productId={}", productId, e);
            }
        }

        long cost = System.currentTimeMillis() - startTime;
        log.info("热点商品预热完成: count={}, 耗时={}ms", productIds.size(), cost);
    }

    /**
     * 预热首页推荐商品
     */
    public void preheatHomeProducts() {
        log.info("开始预热首页推荐商品");
        
        // 获取销量前100的商品
        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andDeleteStatusEqualTo(0)
                .andPublishStatusEqualTo(1);
        example.setOrderByClause("sale desc");
        // PmsProductExample 未生成 limit 字段，降级为仅按销量排序取前N（由 mapper/数据库配置决定）
        
        List<PmsProduct> products = productMapper.selectByExample(example);
        
        for (PmsProduct product : products) {
            String key = redisDatabase + ":" + HOT_PRODUCT_KEY + product.getId();
            redisTemplate.opsForValue().set(key, product, 3600, TimeUnit.SECONDS);
        }
        
        log.info("首页推荐商品预热完成: count={}", products.size());
    }

    // ==================== 3. 定时预热 ====================

    /**
     * 定时预热任务
     * 每小时执行一次，检查是否有需要预热的秒杀商品
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scheduledPreheat() {
        if (!preheatEnabled) {
            return;
        }
        
        log.info("执行定时预热任务...");
        preheatAllSeckillProducts();
    }

    /**
     * 手动触发预热（管理员接口调用）
     */
    public PreheatResult manualPreheat() {
        log.info("手动触发预热");
        long startTime = System.currentTimeMillis();
        
        preheatAllSeckillProducts();
        preheatHomeProducts();
        
        long cost = System.currentTimeMillis() - startTime;
        
        return new PreheatResult(true, "预热完成", cost);
    }

    /**
     * 预热指定商品
     */
    public PreheatResult preheatProducts(List<Long> productIds) {
        log.info("预热指定商品: count={}", productIds.size());
        long startTime = System.currentTimeMillis();
        
        preheatSeckillProducts(productIds);
        preheatHotProducts(productIds);
        
        long cost = System.currentTimeMillis() - startTime;
        
        return new PreheatResult(true, "预热完成", cost);
    }

    // ==================== 4. 预热状态查询 ====================

    /**
     * 获取预热状态
     */
    public PreheatStatus getPreheatStatus() {
        String key = redisDatabase + ":" + PREHEAT_STATUS_KEY + "ALL";
        Object status = redisTemplate.opsForValue().get(key);
        
        if (status == null) {
            return new PreheatStatus("NOT_PREHEATED", 0, 0, 0);
        }
        
        return (PreheatStatus) status;
    }

    /**
     * 更新预热状态
     */
    private void updatePreheatStatus(String type, int success, int fail) {
        String key = redisDatabase + ":" + PREHEAT_STATUS_KEY + type;
        PreheatStatus status = new PreheatStatus(
                "PREHEATED",
                success,
                fail,
                System.currentTimeMillis()
        );
        redisTemplate.opsForValue().set(key, status, 24 * 3600, TimeUnit.SECONDS);
    }

    /**
     * 获取所有秒杀商品
     */
    private List<SmsFlashPromotionProductRelation> getAllSeckillProducts() {
        SmsFlashPromotionProductRelationExample example = 
                new SmsFlashPromotionProductRelationExample();
        return flashProductRelationMapper.selectByExample(example);
    }

    // ==================== 5. 预热结果类 ====================

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class PreheatResult {
        private boolean success;
        private String message;
        private long costMs;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class PreheatStatus {
        private String status;
        private int successCount;
        private int failCount;
        private long preheatTime;
    }
}
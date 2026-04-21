package com.macro.mall.portal.service.impl;

import cn.hutool.core.util.IdUtil;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.PmsSkuStockExample;
import com.macro.mall.portal.domain.StockDeductItem;
import com.macro.mall.portal.domain.StockLockResult;
import com.macro.mall.portal.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 库存服务实现
 */
@Slf4j
@Service
public class StockServiceImpl implements StockService {

    @Value("${redis.database:mall}")
    private String redisDatabase;

    private static final String STOCK_KEY = "stock:";
    private static final String STOCK_LOCK_KEY = "stock:lock:";
    private static final String STOCK_LOCK_TOKEN_KEY = "stock:token:";
    private static final String STOCK_TOKEN_INFO_KEY = "stock:token:info:";
    private static final long LOCK_EXPIRE_SECONDS = 300; // Pre-lock expiration 5 minutes

    private static final String STOCK_LOCK_SCRIPT_PATH = "lua/stock_lock.lua";
    private static final String STOCK_CONFIRM_SCRIPT_PATH = "lua/stock_confirm.lua";
    private static final String STOCK_RELEASE_SCRIPT_PATH = "lua/stock_release.lua";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PmsSkuStockMapper skuStockMapper;

    /**
     * Load Lua script from resources
     */
    private String loadScript(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Lua script not found: " + path);
            }
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            return result.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lua script: " + path, e);
        }
    }

    @Override
    public StockLockResult lockStock(Long skuId, Integer quantity, Long orderId) {
        String stockKey = getStockKey(skuId);
        String lockKey = getLockKey(skuId);
        String tokenKey = getTokenKey(skuId);
        String lockToken = IdUtil.fastSimpleUUID();
        
        // 初始化Redis库存（如果不存在）
        initStockIfNotExists(skuId, stockKey);
        
        // 执行Lua脚本
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(loadScript(STOCK_LOCK_SCRIPT_PATH));
        redisScript.setResultType(List.class);
        
        String tokenInfoKey = getTokenInfoKey(lockToken);
        
        List<Long> result = redisTemplate.execute(
                redisScript,
                Arrays.asList(stockKey, lockKey, tokenKey, tokenInfoKey),
                quantity, LOCK_EXPIRE_SECONDS, lockToken, orderId
        );
        
        if (result == null || result.isEmpty()) {
            return StockLockResult.fail("预扣失败");
        }
        
        int code = result.get(0).intValue();
        if (code == 1) {
            long expireTime = System.currentTimeMillis() + LOCK_EXPIRE_SECONDS * 1000;
            return StockLockResult.success(lockToken, skuId, quantity, expireTime);
        } else {
            return StockLockResult.fail(result.get(1).toString());
        }
    }

    @Override
    public Map<Long, StockLockResult> lockStockBatch(List<StockDeductItem> items) {
        Map<Long, StockLockResult> results = new HashMap<>();
        if (CollectionUtils.isEmpty(items)) {
            return results;
        }
        
        // 批量预扣
        for (StockDeductItem item : items) {
            StockLockResult result = lockStock(item.getSkuId(), item.getQuantity(), item.getOrderId());
            results.put(item.getSkuId(), result);
            
            // 如果有任何一个失败，回滚已成功的
            if (!result.getSuccess()) {
                rollbackBatch(results, item.getSkuId());
                break;
            }
        }
        
        return results;
    }

    @Override
    @Transactional
    public boolean confirmStock(String lockToken) {
        if (lockToken == null || lockToken.isEmpty()) {
            return false;
        }
        
        // 从Redis获取预扣信息
        String[] tokenInfo = getTokenInfo(lockToken);
        if (tokenInfo == null) {
            log.warn("预扣凭证不存在: {}", lockToken);
            return false;
        }
        
        Long skuId = Long.parseLong(tokenInfo[0]);
        Integer lockCount = Integer.parseInt(tokenInfo[1]);
        
        String lockKey = getLockKey(skuId);
        String tokenKey = getTokenKey(skuId);
        
        // 执行Lua脚本确认扣减
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(loadScript(STOCK_CONFIRM_SCRIPT_PATH));
        redisScript.setResultType(List.class);
        
        List<Long> result = redisTemplate.execute(
                redisScript,
                Arrays.asList(lockKey, tokenKey),
                lockToken, lockCount
        );
        
        if (result == null || result.isEmpty()) {
            return false;
        }
        
        int code = result.get(0).intValue();
        if (code == 1) {
            // 扣减数据库真实库存
            PmsSkuStock skuStock = skuStockMapper.selectByPrimaryKey(skuId);
            if (skuStock != null) {
                skuStock.setStock(skuStock.getStock() - lockCount);
                skuStock.setLockStock(skuStock.getLockStock() - lockCount);
                skuStockMapper.updateByPrimaryKeySelective(skuStock);
                
                // 同步Redis库存
                syncStockToRedis(skuId);
                
                log.info("库存确认成功: skuId={}, count={}", skuId, lockCount);
                return true;
            }
        }
        
        log.warn("库存确认失败: lockToken={}, code={}", lockToken, code);
        return false;
    }

    @Override
    public boolean releaseStock(String lockToken) {
        if (lockToken == null || lockToken.isEmpty()) {
            return false;
        }
        
        String[] tokenInfo = getTokenInfo(lockToken);
        if (tokenInfo == null) {
            log.warn("预扣凭证不存在: {}", lockToken);
            return true; // 凭证不存在也视为成功
        }
        
        Long skuId = Long.parseLong(tokenInfo[0]);
        Integer lockCount = Integer.parseInt(tokenInfo[1]);
        
        String stockKey = getStockKey(skuId);
        String lockKey = getLockKey(skuId);
        String tokenKey = getTokenKey(skuId);
        
        // 执行Lua脚本释放库存
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(loadScript(STOCK_RELEASE_SCRIPT_PATH));
        redisScript.setResultType(List.class);
        
        List<Long> result = redisTemplate.execute(
                redisScript,
                Arrays.asList(stockKey, lockKey, tokenKey),
                lockToken, lockCount
        );
        
        if (result == null || result.isEmpty()) {
            return false;
        }
        
        int code = result.get(0).intValue();
        if (code == 1) {
            log.info("库存释放成功: skuId={}, count={}", skuId, lockCount);
            return true;
        }
        
        log.warn("库存释放失败: lockToken={}, code={}", lockToken, code);
        return false;
    }

    @Override
    public boolean rollbackStock(String lockToken) {
        // 回滚等同于释放
        return releaseStock(lockToken);
    }

    @Override
    public Map<String, Object> getStock(Long skuId) {
        Map<String, Object> result = new HashMap<>();
        
        // 从Redis获取
        String stockKey = getStockKey(skuId);
        String lockKey = getLockKey(skuId);
        
        Object stock = redisTemplate.opsForValue().get(stockKey);
        Object lockStock = redisTemplate.opsForValue().get(lockKey);
        
        if (stock == null) {
            // 从数据库加载
            PmsSkuStock skuStock = skuStockMapper.selectByPrimaryKey(skuId);
            if (skuStock != null) {
                stock = skuStock.getStock();
                lockStock = skuStock.getLockStock();
                // 同步到Redis
                syncStockToRedis(skuId);
            }
        }
        
        int stockVal = stock != null ? Integer.parseInt(stock.toString()) : 0;
        int lockVal = lockStock != null ? Integer.parseInt(lockStock.toString()) : 0;
        
        result.put("skuId", skuId);
        result.put("stock", stockVal);
        result.put("lockStock", lockVal);
        result.put("availableStock", stockVal - lockVal);
        
        return result;
    }

    @Override
    @Transactional
    public boolean deductStock(List<StockDeductItem> items) {
        if (CollectionUtils.isEmpty(items)) {
            return true;
        }
        
        for (StockDeductItem item : items) {
            PmsSkuStock skuStock = skuStockMapper.selectByPrimaryKey(item.getSkuId());
            if (skuStock == null) {
                log.error("SKU不存在: skuId={}", item.getSkuId());
                return false;
            }
            
            int availableStock = skuStock.getStock() - skuStock.getLockStock();
            if (availableStock < item.getQuantity()) {
                log.error("库存不足: skuId={}, available={}, request={}", 
                        item.getSkuId(), availableStock, item.getQuantity());
                return false;
            }
            
            // 扣减库存
            skuStock.setStock(skuStock.getStock() - item.getQuantity());
            skuStock.setLockStock(skuStock.getLockStock() + item.getQuantity());
            skuStockMapper.updateByPrimaryKeySelective(skuStock);
            
            // 同步Redis
            syncStockToRedis(item.getSkuId());
        }
        
        log.info("库存扣减成功: items={}", items.size());
        return true;
    }

    @Override
    @Transactional
    public boolean addStock(Long skuId, Integer quantity) {
        PmsSkuStock skuStock = skuStockMapper.selectByPrimaryKey(skuId);
        if (skuStock == null) {
            log.error("SKU不存在: skuId={}", skuId);
            return false;
        }
        
        skuStock.setStock(skuStock.getStock() + quantity);
        skuStockMapper.updateByPrimaryKeySelective(skuStock);
        
        // 同步Redis
        syncStockToRedis(skuId);
        
        log.info("库存增加成功: skuId={}, quantity={}", skuId, quantity);
        return true;
    }

    @Override
    public boolean syncStockToRedis(Long skuId) {
        PmsSkuStock skuStock = skuStockMapper.selectByPrimaryKey(skuId);
        if (skuStock == null) {
            return false;
        }
        
        String stockKey = getStockKey(skuId);
        String lockKey = getLockKey(skuId);
        
        redisTemplate.opsForValue().set(stockKey, skuStock.getStock());
        redisTemplate.opsForValue().set(lockKey, skuStock.getLockStock());
        
        log.info("库存同步到Redis: skuId={}, stock={}, lockStock={}", 
                skuId, skuStock.getStock(), skuStock.getLockStock());
        return true;
    }

    // ==================== 私有方法 ====================

    private void initStockIfNotExists(Long skuId, String stockKey) {
        Object stock = redisTemplate.opsForValue().get(stockKey);
        if (stock == null) {
            syncStockToRedis(skuId);
        }
    }

    private String[] getTokenInfo(String lockToken) {
        // 预扣凭证信息格式: token:skuId:count:orderId
        String tokenInfoKey = getTokenInfoKey(lockToken);
        Object tokenInfo = redisTemplate.opsForValue().get(tokenInfoKey);
        
        if (tokenInfo == null) {
            return null;
        }
        
        String[] parts = tokenInfo.toString().split(":");
        if (parts.length < 4) {
            return null;
        }
        
        // 返回 [skuId, count, orderId]
        return new String[]{parts[1], parts[2], parts[3]};
    }

    private void rollbackBatch(Map<Long, StockLockResult> results, Long failedSkuId) {
        for (Map.Entry<Long, StockLockResult> entry : results.entrySet()) {
            if (entry.getValue().getSuccess()) {
                releaseStock(entry.getValue().getLockToken());
            }
        }
    }

    private String getStockKey(Long skuId) {
        // 使用hash tag确保同一sku的所有key在同一个slot
        return redisDatabase + ":{" + skuId + "}:" + STOCK_KEY;
    }

    private String getLockKey(Long skuId) {
        return redisDatabase + ":{" + skuId + "}:" + STOCK_LOCK_KEY;
    }

    private String getTokenKey(Long skuId) {
        return redisDatabase + ":{" + skuId + "}:" + STOCK_LOCK_TOKEN_KEY;
    }

    private String getTokenInfoKey(String lockToken) {
        // 从lockToken中提取skuId以使用hash tag
        // 格式: token:skuId:count:orderId
        String[] parts = lockToken.split(":");
        if (parts.length >= 2) {
            try {
                Long skuId = Long.parseLong(parts[1]);
                return redisDatabase + ":{" + skuId + "}:" + STOCK_TOKEN_INFO_KEY + lockToken;
            } catch (NumberFormatException e) {
                // 如果解析失败，使用原方式（可能跨slot）
            }
        }
        return redisDatabase + ":" + STOCK_TOKEN_INFO_KEY + lockToken;
    }
}
package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.StockDeductItem;
import com.macro.mall.portal.domain.StockLockResult;

import java.util.List;
import java.util.Map;

/**
 * 库存服务接口
 */
public interface StockService {

    /**
     * 预扣库存（下单时调用）
     * @param skuId SKU ID
     * @param quantity 预扣数量
     * @param orderId 订单ID（用于关联）
     * @return 预扣结果
     */
    StockLockResult lockStock(Long skuId, Integer quantity, Long orderId);

    /**
     * 批量预扣库存
     * @param items 预扣项列表
     * @return 预扣结果（key: skuId, value: 预扣结果）
     */
    Map<Long, StockLockResult> lockStockBatch(List<StockDeductItem> items);

    /**
     * 确认扣减（支付成功后调用）
     * @param lockToken 预扣凭证
     * @return 是否成功
     */
    boolean confirmStock(String lockToken);

    /**
     * 释放库存（取消订单/超时未支付）
     * @param lockToken 预扣凭证
     * @return 是否成功
     */
    boolean releaseStock(String lockToken);

    /**
     * 回滚库存（异常情况）
     * @param lockToken 预扣凭证
     * @return 是否成功
     */
    boolean rollbackStock(String lockToken);

    /**
     * 查询库存
     * @param skuId SKU ID
     * @return 库存信息
     */
    Map<String, Object> getStock(Long skuId);

    /**
     * 批量扣减库存（用于普通订单，非预扣模式）
     * @param items 扣减项列表
     * @return 是否成功
     */
    boolean deductStock(List<StockDeductItem> items);

    /**
     * 增加库存（退货/取消）
     * @param skuId SKU ID
     * @param quantity 增加数量
     * @return 是否成功
     */
    boolean addStock(Long skuId, Integer quantity);

    /**
     * 同步库存到Redis（初始化或补偿）
     * @param skuId SKU ID
     * @return 是否成功
     */
    boolean syncStockToRedis(Long skuId);
}
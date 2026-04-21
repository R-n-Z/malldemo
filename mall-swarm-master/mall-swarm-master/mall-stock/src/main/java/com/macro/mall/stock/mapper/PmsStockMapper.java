package com.macro.mall.stock.mapper;

import com.macro.mall.stock.domain.PmsStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 库存Mapper
 */
@Mapper
public interface PmsStockMapper {
    
    PmsStock selectByProductId(@Param("productId") Long productId);
    
    int deductStock(@Param("productId") Long productId, @Param("count") Integer count);
    
    int rollbackStock(@Param("productId") Long productId, @Param("count") Integer count);
    
    int updateStock(@Param("productId") Long productId, @Param("stock") Integer stock);
}
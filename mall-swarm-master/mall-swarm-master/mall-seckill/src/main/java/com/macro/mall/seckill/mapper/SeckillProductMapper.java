package com.macro.mall.seckill.mapper;

import com.macro.mall.seckill.domain.SeckillProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 秒杀商品Mapper
 */
@Mapper
public interface SeckillProductMapper {
    
    /**
     * 查询所有秒杀活动商品
     */
    List<SeckillProduct> selectAll();
    
    /**
     * 查询指定时间范围内的秒杀商品
     */
    List<SeckillProduct> selectByTimeRange(@Param("startTime") Date startTime, 
                                            @Param("endTime") Date endTime);
    
    /**
     * 查询进行中的秒杀商品
     */
    List<SeckillProduct> selectActive();
    
    /**
     * 根据ID查询
     */
    SeckillProduct selectById(@Param("id") Long id);
    
    /**
     * 根据商品ID查询
     */
    SeckillProduct selectByProductId(@Param("productId") Long productId);
    
    /**
     * 更新库存
     */
    int updateStock(@Param("id") Long id, @Param("stock") Integer stock);
}
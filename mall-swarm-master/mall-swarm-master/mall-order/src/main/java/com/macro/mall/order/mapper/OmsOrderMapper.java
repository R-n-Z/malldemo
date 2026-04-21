package com.macro.mall.order.mapper;

import com.macro.mall.order.domain.OmsOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单Mapper
 */
@Mapper
public interface OmsOrderMapper {
    
    int insert(OmsOrder order);
    
    OmsOrder selectByOrderSn(@Param("orderSn") String orderSn);
    
    OmsOrder selectById(@Param("id") Long id);
    
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
    
    int delete(@Param("id") Long id);
}
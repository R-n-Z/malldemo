package com.macro.mall.order.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单实体
 */
@Data
public class OmsOrder implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Long id;
    
    /**
     * 订单号
     */
    private String orderSn;
    
    /**
     * 用户ID
     */
    private Long memberId;
    
    /**
     * 订单类型：1-普通订单，2-秒杀订单
     */
    private Integer orderType;
    
    /**
     * 订单状态：0-待付款，1-已付款，2-已发货，3-已完成，4-已取消
     */
    private Integer status;
    
    /**
     * 订单金额
     */
    private BigDecimal totalAmount;
    
    /**
     * 应付金额
     */
    private BigDecimal payAmount;
    
    /**
     * 运费金额
     */
    private BigDecimal freightAmount;
    
    /**
     * 促销优惠金额
     */
    private BigDecimal promotionAmount;
    
    /**
     * 积分抵扣金额
     */
    private BigDecimal integrationAmount;
    
    /**
     * 优惠券抵扣金额
     */
    private BigDecimal couponAmount;
    
    /**
     * 收货人姓名
     */
    private String receiverName;
    
    /**
     * 收货人电话
     */
    private String receiverPhone;
    
    /**
     * 收货人地址
     */
    private String receiverAddress;
    
    /**
     * 订单备注
     */
    private String note;
    
    /**
     * 支付类型：1-支付宝，2-微信
     */
    private Integer payType;
    
    /**
     * 订单来源：1-APP，2-小程序，3-H5
     */
    private Integer sourceType;
    
    /**
     * 创建时间
     */
    private Date createTime;
    
    /**
     * 修改时间
     */
    private Date modifyTime;
    
    /**
     * 支付时间
     */
    private Date paymentTime;
    
    /**
     * 发货时间
     */
    private Date deliveryTime;
    
    /**
     * 完成时间
     */
    private Date finishTime;
    
    /**
     * 取消时间
     */
    private Date cancelTime;
    
    /**
     * 删除状态：0-未删除，1-已删除
     */
    private Integer deleteStatus;
}
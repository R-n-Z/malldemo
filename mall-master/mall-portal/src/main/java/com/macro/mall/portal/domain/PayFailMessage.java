package com.macro.mall.portal.domain;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 支付失败消息
 */
@Data
public class PayFailMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 订单号
     */
    private String orderSn;
    
    /**
     * 支付方式: 1-支付宝 2-微信
     */
    private Integer payType;
    
    /**
     * 失败原因: TRADE_CLOSED/SIGN_FAILED/USER_CANCEL/TIMEOUT
     */
    private String failReason;
    
    /**
     * 失败时间
     */
    private Date failTime;
    
    /**
     * 预扣库存凭证（如果有）
     */
    private String lockToken;
    
    /**
     * 是否需要取消订单
     */
    private Boolean cancelOrder;
}
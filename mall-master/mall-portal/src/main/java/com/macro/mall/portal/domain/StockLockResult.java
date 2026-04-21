package com.macro.mall.portal.domain;

import java.io.Serializable;

/**
 * 库存预扣结果
 */
public class StockLockResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 预扣凭证（用于后续确认/释放）
     */
    private String lockToken;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 预扣数量
     */
    private Integer lockCount;

    /**
     * 过期时间（时间戳）
     */
    private Long expireTime;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 消息
     */
    private String message;

    public String getLockToken() {
        return lockToken;
    }

    public void setLockToken(String lockToken) {
        this.lockToken = lockToken;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Integer getLockCount() {
        return lockCount;
    }

    public void setLockCount(Integer lockCount) {
        this.lockCount = lockCount;
    }

    public Long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Long expireTime) {
        this.expireTime = expireTime;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static StockLockResult success(String lockToken, Long skuId, Integer lockCount, Long expireTime) {
        StockLockResult result = new StockLockResult();
        result.setLockToken(lockToken);
        result.setSkuId(skuId);
        result.setLockCount(lockCount);
        result.setExpireTime(expireTime);
        result.setSuccess(true);
        result.setMessage("预扣成功");
        return result;
    }

    public static StockLockResult fail(String message) {
        StockLockResult result = new StockLockResult();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }
}
package com.macro.mall.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存保护配置
 */
@Configuration
@ConfigurationProperties(prefix = "cache.protection")
public class CacheProtectionConfig {

    private BloomFilterConfig bloomFilter = new BloomFilterConfig();
    private LockConfig lock = new LockConfig();
    private NullValueConfig nullValue = new NullValueConfig();

    public BloomFilterConfig getBloomFilter() {
        return bloomFilter;
    }

    public void setBloomFilter(BloomFilterConfig bloomFilter) {
        this.bloomFilter = bloomFilter;
    }

    public LockConfig getLock() {
        return lock;
    }

    public void setLock(LockConfig lock) {
        this.lock = lock;
    }

    public NullValueConfig getNullValue() {
        return nullValue;
    }

    public void setNullValue(NullValueConfig nullValue) {
        this.nullValue = nullValue;
    }

    public static class BloomFilterConfig {
        private long expectedInsertions = 100000;
        private double falsePositiveRate = 0.01;

        public long getExpectedInsertions() {
            return expectedInsertions;
        }

        public void setExpectedInsertions(long expectedInsertions) {
            this.expectedInsertions = expectedInsertions;
        }

        public double getFalsePositiveRate() {
            return falsePositiveRate;
        }

        public void setFalsePositiveRate(double falsePositiveRate) {
            this.falsePositiveRate = falsePositiveRate;
        }
    }

    public static class LockConfig {
        private int timeoutSeconds = 10;
        private int retryTimes = 3;

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getRetryTimes() {
            return retryTimes;
        }

        public void setRetryTimes(int retryTimes) {
            this.retryTimes = retryTimes;
        }
    }

    public static class NullValueConfig {
        private int expireSeconds = 60;

        public int getExpireSeconds() {
            return expireSeconds;
        }

        public void setExpireSeconds(int expireSeconds) {
            this.expireSeconds = expireSeconds;
        }
    }
}
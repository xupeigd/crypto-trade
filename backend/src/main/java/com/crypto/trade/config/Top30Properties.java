package com.crypto.trade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Top30Properties
 * 配置类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Component
@ConfigurationProperties(prefix = "top30")
public class Top30Properties {

    /**
     * 基础配置
     */
    private int cacheTtlMinutes = 5;
    private String defaultVendor = "OKX";
    private String defaultInstType = "SWAP";
    private int defaultLimit = 30;

    /**
     * 异步更新配置
     */
    private boolean asyncUpdateEnabled = true;
    private int updateIntervalSeconds = 60;
    private int precomputeHours = 24;

    /**
     * 缓存配置
     */
    private boolean cacheEnabled = true;
    private boolean fallbackEnabled = true;

    /**
     * 获取缓存TTL（毫秒）
     */
    public long getCacheTtlMillis() {
        return cacheTtlMinutes * 60 * 1000L;
    }

    /**
     * 获取更新间隔（毫秒）
     */
    public long getUpdateIntervalMillis() {
        return updateIntervalSeconds * 1000L;
    }
}
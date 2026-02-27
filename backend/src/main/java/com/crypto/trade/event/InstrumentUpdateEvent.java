package com.crypto.trade.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * InstrumentUpdateEvent
 * 事件类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentUpdateEvent {

    /**
     * 事件ID（自动生成的UUID）
     */
    private String eventId;

    /**
     * 提供商名称（如OKX, Binance）
     */
    private String provider;

    /**
     * API密钥ID
     */
    private String apiKeyId;

    /**
     * 合约ID
     */
    private String instId;

    /**
     * 更新时间戳
     */
    private LocalDateTime updateTime;

    /**
     * 24小时交易额（USDT计价）
     */
    private java.math.BigDecimal volume24hUsdt;

    /**
     * 交易额排名（在所有合约中的排名）
     */
    private Integer volumeRank;

    /**
     * 是否为活跃合约
     */
    private Boolean isActive;

    /**
     * 合约类型
     */
    private String instType;

    /**
     * 基础资产
     */
    private String baseAsset;

    /**
     * 计价资产
     */
    private String quoteAsset;

    /**
     * 创建时间
     */
    @lombok.Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 事件类型标识
     */
    @lombok.Builder.Default
    private String eventType = "INSTRUMENT_UPDATE";

    /**
     * 事件来源
     */
    @lombok.Builder.Default
    private String source = "UnifiedInstrumentService";

    /**
     * 获取事件描述
     */
    public String getDescription() {
        return String.format("Provider: %s, InstId: %s, VolumeRank: %d, UpdateTime: %s",
                provider, instId, volumeRank, updateTime);
    }

    /**
     * 检查事件是否有效
     */
    public boolean isValid() {
        return provider != null && !provider.trim().isEmpty() &&
                instId != null && !instId.trim().isEmpty() &&
                updateTime != null &&
                eventType != null;
    }
}
package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * KlineData
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_kline_data",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "inst_id", "timeframe", "kline_time"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KlineData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "inst_id", nullable = false, length = 100)
    private String instId;

    @Column(name = "timeframe", nullable = false, length = 10)
    private String timeframe;

    @Column(name = "kline_time", nullable = false)
    private Long klineTime;

    @Column(name = "open_price", precision = 20, scale = 8)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 20, scale = 8)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 20, scale = 8)
    private BigDecimal lowPrice;

    @Column(name = "close_price", precision = 20, scale = 8)
    private BigDecimal closePrice;

    @Column(name = "volume", precision = 30, scale = 8)
    private BigDecimal volume;

    @Column(name = "quote_volume", precision = 30, scale = 8)
    private BigDecimal quoteVolume;

    @Column(name = "quote_asset", length = 20)
    private String quoteAsset;

    @Column(name = "base_asset", length = 20)
    private String baseAsset;

    /**
     * K线状态
     * 0 代表 K 线未完结，1 代表 K 线已完结
     */
    @Column(name = "confirm", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Integer confirm;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 便利方法

    /**
     * 获取交易变化百分比
     */
    public BigDecimal getPriceChangePercent() {
        if (openPrice != null && openPrice.compareTo(BigDecimal.ZERO) != 0 && closePrice != null) {
            return closePrice.subtract(openPrice)
                    .divide(openPrice, 8, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        return BigDecimal.ZERO;
    }

    /**
     * 检查是否为阳线
     */
    public boolean isGreen() {
        if (openPrice != null && closePrice != null) {
            return closePrice.compareTo(openPrice) > 0;
        }
        return false;
    }

    /**
     * 检查是否为阴线
     */
    public boolean isRed() {
        if (openPrice != null && closePrice != null) {
            return closePrice.compareTo(openPrice) < 0;
        }
        return false;
    }

    /**
     * 获取K线区间（最高价-最低价）
     */
    public BigDecimal getPriceRange() {
        if (highPrice != null && lowPrice != null) {
            return highPrice.subtract(lowPrice);
        }
        return BigDecimal.ZERO;
    }

    /**
     * 获取实体ID（用于日志记录）
     */
    public String getEntityId() {
        return String.format("%s-%s-%s-%d", provider, instId, timeframe, id);
    }
}
package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * FuturesTickerData
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_futures_ticker_data", uniqueConstraints = {
        @UniqueConstraint(name = "uk_vendor_inst_id_hour_trading",
                columnNames = {"vendor", "inst_id", "ts_hour_str", "is_live_trading"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FuturesTickerData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vendor", nullable = false, length = 10)
    private String vendor;

    @Column(name = "is_live_trading", nullable = false)
    private Boolean isLiveTrading;

    @Column(name = "inst_type", nullable = false, length = 20)
    private String instType;

    @Column(name = "data_ingestion_time", nullable = false)
    private LocalDateTime dataIngestionTime;

    @Column(name = "inst_id", nullable = false, length = 50)
    private String instId;

    @Column(name = "last", precision = 38, scale = 8)
    private BigDecimal last;

    @Column(name = "last_sz", precision = 38, scale = 8)
    private BigDecimal lastSz;

    @Column(name = "bid_price", precision = 38, scale = 8)
    private BigDecimal bidPrice;

    @Column(name = "bid_sz", precision = 38, scale = 8)
    private BigDecimal bidSz;

    @Column(name = "ask_price", precision = 38, scale = 8)
    private BigDecimal askPrice;

    @Column(name = "ask_sz", precision = 38, scale = 8)
    private BigDecimal askSz;

    @Column(name = "open_24h", precision = 38, scale = 8)
    private BigDecimal open24h;

    @Column(name = "high_24h", precision = 38, scale = 8)
    private BigDecimal high24h;

    @Column(name = "low_24h", precision = 38, scale = 8)
    private BigDecimal low24h;

    @Column(name = "sod_utc0", precision = 38, scale = 8)
    private BigDecimal sodUtc0;

    @Column(name = "sod_utc8", precision = 38, scale = 8)
    private BigDecimal sodUtc8;

    @Column(name = "ts")
    private Long ts;

    @Column(name = "ts_mins_str", length = 20)
    private String tsMinsStr;

    @Column(name = "ts_hour_str", length = 20)
    private String tsHourStr;

    @Column(name = "vol_ccy_24h", precision = 38, scale = 8)
    private BigDecimal volCcy24h;

    @Column(name = "vol_24h", precision = 38, scale = 8)
    private BigDecimal vol24h;
}
package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * CexInstrument
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_cex_instruments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "inst_id", "is_live_trading"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CexInstrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "is_live_trading", nullable = false)
    private Boolean isLiveTrading;

    @Column(name = "inst_type", nullable = false, length = 20)
    private String instType;

    @Column(name = "inst_id", nullable = false, length = 100)
    private String instId;

    @Column(name = "base_ccy", length = 20)
    private String baseCcy;

    @Column(name = "quote_ccy", length = 20)
    private String quoteCcy;

    @Column(name = "settle_ccy", length = 20)
    private String settleCcy;

    @Column(name = "category", length = 20)
    private String category;

    @Column(name = "ct_val", length = 50)
    private String ctVal;

    @Column(name = "ct_mult", length = 50)
    private String ctMult;

    @Column(name = "ct_val_ccy", length = 20)
    private String ctValCcy;

    @Column(name = "opt_type", length = 10)
    private String optType;

    @Column(name = "stk", length = 50)
    private String stk;

    @Column(name = "list_time", length = 50)
    private String listTime;

    @Column(name = "exp_time", length = 50)
    private String expTime;

    @Column(name = "lever", length = 20)
    private String lever;

    @Column(name = "tick_sz", length = 50)
    private String tickSz;

    @Column(name = "lot_sz", length = 50)
    private String lotSz;

    @Column(name = "min_sz", length = 50)
    private String minSz;

    @Column(name = "max_lmt_sz", length = 50)
    private String maxLmtSz;

    @Column(name = "max_mkt_sz", length = 50)
    private String maxMktSz;

    @Column(name = "max_ts_sz", length = 50)
    private String maxTsSz;

    @Column(name = "state", length = 20)
    private String state;

    @Column(name = "alias", length = 100)
    private String alias;

    @Column(name = "max_lmt", length = 50)
    private String maxLmt;

    @Column(name = "max_mkt", length = 50)
    private String maxMkt;

    @Column(name = "position_idx", length = 20)
    private String positionIdx;

    @Column(name = "is_leverage", length = 10)
    private String isLeverage;

    @Column(name = "fee_rate", length = 20)
    private String feeRate;

    @Column(name = "data_ingestion_time", nullable = false)
    private LocalDateTime dataIngestionTime;

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
}
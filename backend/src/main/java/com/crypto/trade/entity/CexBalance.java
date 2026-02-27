package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CexBalance
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_cex_balances")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CexBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "balance_id")
    private Long balanceId;

    @Column(name = "cex_name", nullable = false, length = 50)
    private String cexName;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "total_balance", nullable = false, precision = 38, scale = 8)
    private BigDecimal totalBalance;

    @Column(name = "available_balance", nullable = false, precision = 38, scale = 8)
    private BigDecimal availableBalance;

    @Column(name = "locked_balance", nullable = false, precision = 38, scale = 8)
    private BigDecimal lockedBalance;

    @Column(name = "usd_value", precision = 38, scale = 8)
    private BigDecimal usdValue;

    @Column(name = "data_ingestion_time", nullable = false)
    private LocalDateTime dataIngestionTime;

    @Column(name = "api_key_id")
    private Long apiKeyId;

    @Column(name = "created_time")
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private LocalDateTime createdTime = LocalDateTime.now();

    @Column(name = "updated_time")
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private LocalDateTime updatedTime = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createdTime = LocalDateTime.now();
        updatedTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
    }
}
package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AccountEquitySnapshot
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Entity
@Table(name = "t_account_equity_snapshot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"api_key_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountEquitySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "equity_id")
    private Long equityId;

    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    @Column(name = "total_equity_usdt", nullable = false, precision = 38, scale = 8)
    private BigDecimal totalEquityUsdt;

    @Column(name = "available_equity_usdt", nullable = false, precision = 38, scale = 8)
    private BigDecimal availableEquityUsdt;

    @Column(name = "frozen_equity_usdt", precision = 38, scale = 8)
    private BigDecimal frozenEquityUsdt = BigDecimal.ZERO;

    @Column(name = "margin_equity_usdt", precision = 38, scale = 8)
    private BigDecimal marginEquityUsdt = BigDecimal.ZERO;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();

    @Column(name = "updated_time")
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
package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * FundingRateData
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_funding_rate_data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundingRateData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inst_id", nullable = false, length = 50)
    private String instId;

    @Column(name = "inst_type", nullable = false, length = 20)
    private String instType;

    @Column(name = "funding_rate", length = 20)
    private String fundingRate;

    @Column(name = "next_funding_rate", length = 20)
    private String nextFundingRate;

    @Column(name = "funding_time", length = 50)
    private String fundingTime;

    @Column(name = "data_ingestion_time", nullable = false)
    private LocalDateTime dataIngestionTime;

    @Column(name = "vendor", nullable = false, length = 10)
    private String vendor;

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
package com.crypto.trade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PositionSummaryModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionSummaryModel {

    /**
     * API密钥ID
     */
    private Long apiKeyId;

    /**
     * 总持仓数量
     */
    private Integer totalPositions;

    /**
     * 多头持仓数量
     */
    private Integer longPositions;

    /**
     * 空头持仓数量
     */
    private Integer shortPositions;

    /**
     * 总未实现盈亏
     */
    private BigDecimal totalUnrealizedPnl;

    /**
     * 多头未实现盈亏
     */
    private BigDecimal longUnrealizedPnl;

    /**
     * 空头未实现盈亏
     */
    private BigDecimal shortUnrealizedPnl;

    /**
     * 总保证金占用
     */
    private BigDecimal totalMargin;

    /**
     * 总持仓价值（按标记价格计算）
     */
    private BigDecimal totalNotional;

    /**
     * 风险等级（LOW/MEDIUM/HIGH）
     */
    private String riskLevel;

    /**
     * 风险评分（0-100）
     */
    private Integer riskScore;

    /**
     * 最大单个持仓未实现盈亏
     */
    private BigDecimal maxSinglePositionPnl;

    /**
     * 最大单个持仓保证金占用
     */
    private BigDecimal maxSinglePositionMargin;

    /**
     * 保证金使用率（百分比）
     */
    private BigDecimal marginUsageRate;

    /**
     * 数据更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建空的仓位汇总模型
     */
    public static PositionSummaryModel empty() {
        return PositionSummaryModel.builder()
                .totalPositions(0)
                .longPositions(0)
                .shortPositions(0)
                .totalUnrealizedPnl(BigDecimal.ZERO)
                .longUnrealizedPnl(BigDecimal.ZERO)
                .shortUnrealizedPnl(BigDecimal.ZERO)
                .totalMargin(BigDecimal.ZERO)
                .totalNotional(BigDecimal.ZERO)
                .riskLevel("LOW")
                .riskScore(0)
                .maxSinglePositionPnl(BigDecimal.ZERO)
                .maxSinglePositionMargin(BigDecimal.ZERO)
                .marginUsageRate(BigDecimal.ZERO)
                .updateTime(LocalDateTime.now())
                .build();
    }
}
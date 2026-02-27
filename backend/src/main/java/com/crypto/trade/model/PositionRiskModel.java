package com.crypto.trade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PositionRiskModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionRiskModel {

    /**
     * API密钥ID
     */
    private Long apiKeyId;

    /**
     * 整体风险等级（LOW/MEDIUM/HIGH/CRITICAL）
     */
    private String overallRiskLevel;

    /**
     * 风险评分（0-100，分数越高风险越大）
     */
    private Integer riskScore;

    /**
     * 仓位集中度风险（0-100）
     */
    private Integer concentrationRisk;

    /**
     * 杠杆风险评分（0-100）
     */
    private Integer leverageRisk;

    /**
     * 市场风险评分（0-100）
     */
    private Integer marketRisk;

    /**
     * 流动性风险评分（0-100）
     */
    private Integer liquidityRisk;

    /**
     * 最大回撤风险（0-100）
     */
    private Integer drawdownRisk;

    /**
     * 保证金使用率（百分比）
     */
    private BigDecimal marginUsageRate;

    /**
     * 总未实现盈亏占账户权益比例（百分比）
     */
    private BigDecimal pnlToEquityRatio;

    /**
     * 最大单个仓位占比（百分比）
     */
    private BigDecimal maxPositionRatio;

    /**
     * 净值波动率（百分比）
     */
    private BigDecimal volatilityRatio;

    /**
     * 风险预警信息
     */
    private String riskWarning;

    /**
     * 建议操作（REDUCE/MAINTAIN/EXPAND）
     */
    private String recommendedAction;

    /**
     * 止损建议价格
     */
    private BigDecimal stopLossPrice;

    /**
     * 止盈建议价格
     */
    private BigDecimal takeProfitPrice;

    /**
     * 最大可承受亏损金额
     */
    private BigDecimal maxAcceptableLoss;

    /**
     * 当前风险敞口
     */
    private BigDecimal currentExposure;

    /**
     * 风险评估时间
     */
    private LocalDateTime assessmentTime;

    /**
     * 创建默认的低风险模型
     */
    public static PositionRiskModel lowRisk() {
        return PositionRiskModel.builder()
                .overallRiskLevel("LOW")
                .riskScore(20)
                .concentrationRisk(15)
                .leverageRisk(10)
                .marketRisk(25)
                .liquidityRisk(20)
                .drawdownRisk(15)
                .marginUsageRate(BigDecimal.valueOf(0.3))
                .pnlToEquityRatio(BigDecimal.valueOf(0.05))
                .maxPositionRatio(BigDecimal.valueOf(0.2))
                .volatilityRatio(BigDecimal.valueOf(0.15))
                .riskWarning("当前风险水平较低，可适当增加仓位")
                .recommendedAction("MAINTAIN")
                .currentExposure(BigDecimal.ZERO)
                .assessmentTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建中等风险模型
     */
    public static PositionRiskModel mediumRisk() {
        return PositionRiskModel.builder()
                .overallRiskLevel("MEDIUM")
                .riskScore(50)
                .concentrationRisk(40)
                .leverageRisk(35)
                .marketRisk(50)
                .liquidityRisk(45)
                .drawdownRisk(40)
                .marginUsageRate(BigDecimal.valueOf(0.6))
                .pnlToEquityRatio(BigDecimal.valueOf(0.1))
                .maxPositionRatio(BigDecimal.valueOf(0.4))
                .volatilityRatio(BigDecimal.valueOf(0.25))
                .riskWarning("当前风险水平适中，需要密切关注市场变化")
                .recommendedAction("MAINTAIN")
                .currentExposure(BigDecimal.ZERO)
                .assessmentTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建高风险模型
     */
    public static PositionRiskModel highRisk() {
        return PositionRiskModel.builder()
                .overallRiskLevel("HIGH")
                .riskScore(75)
                .concentrationRisk(70)
                .leverageRisk(65)
                .marketRisk(75)
                .liquidityRisk(60)
                .drawdownRisk(65)
                .marginUsageRate(BigDecimal.valueOf(0.8))
                .pnlToEquityRatio(BigDecimal.valueOf(0.15))
                .maxPositionRatio(BigDecimal.valueOf(0.6))
                .volatilityRatio(BigDecimal.valueOf(0.35))
                .riskWarning("当前风险水平较高，建议减少仓位")
                .recommendedAction("REDUCE")
                .currentExposure(BigDecimal.ZERO)
                .assessmentTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建极高风险模型
     */
    public static PositionRiskModel criticalRisk() {
        return PositionRiskModel.builder()
                .overallRiskLevel("CRITICAL")
                .riskScore(95)
                .concentrationRisk(90)
                .leverageRisk(85)
                .marketRisk(90)
                .liquidityRisk(80)
                .drawdownRisk(85)
                .marginUsageRate(BigDecimal.valueOf(0.95))
                .pnlToEquityRatio(BigDecimal.valueOf(0.25))
                .maxPositionRatio(BigDecimal.valueOf(0.8))
                .volatilityRatio(BigDecimal.valueOf(0.5))
                .riskWarning("当前风险水平极高，立即减少仓位")
                .recommendedAction("REDUCE")
                .currentExposure(BigDecimal.ZERO)
                .assessmentTime(LocalDateTime.now())
                .build();
    }
}
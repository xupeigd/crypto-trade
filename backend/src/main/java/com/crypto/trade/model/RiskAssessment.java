package com.crypto.trade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * RiskAssessment
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessment {

    private String userId;
    private LocalDateTime assessmentTime;
    private double riskScore; // 0-1之间的风险评分
    private RiskLevel riskLevel;
    private BigDecimal totalExposure; // 总敞口
    private BigDecimal leverageRatio; // 杠杆比率
    private BigDecimal marginUsageRatio; // 保证金使用率
    private BigDecimal accountBalance; // 账户余额
    private BigDecimal availableBalance; // 可用余额
    private List<RiskFactor> riskFactors;
    private Map<String, BigDecimal> positionRisks; // 各持仓风险
    private RiskAlert highestRiskAlert;
    private boolean withinRiskTolerance;

    /**
     * 创建低风险评估
     */
    public static RiskAssessment lowRisk(String userId) {
        return RiskAssessment.builder()
                .userId(userId)
                .riskScore(0.2)
                .riskLevel(RiskLevel.CONSERVATIVE)
                .withinRiskTolerance(true)
                .assessmentTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建中等风险评估
     */
    public static RiskAssessment mediumRisk(String userId) {
        return RiskAssessment.builder()
                .userId(userId)
                .riskScore(0.5)
                .riskLevel(RiskLevel.NORMAL)
                .withinRiskTolerance(true)
                .assessmentTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建高风险评估
     */
    public static RiskAssessment highRisk(String userId) {
        return RiskAssessment.builder()
                .userId(userId)
                .riskScore(0.8)
                .riskLevel(RiskLevel.AGGRESSIVE)
                .withinRiskTolerance(false)
                .assessmentTime(LocalDateTime.now())
                .build();
    }

    /**
     * 是否存在高风险
     */
    public boolean hasHighRisk() {
        return riskScore > 0.7 || !withinRiskTolerance;
    }

    /**
     * 是否存在极端风险
     */
    public boolean hasExtremeRisk() {
        return riskScore > 0.9 || marginUsageRatio.compareTo(BigDecimal.valueOf(0.9)) > 0;
    }

    /**
     * 获取最高风险因素
     */
    public RiskFactor getHighestRiskFactor() {
        return riskFactors != null && !riskFactors.isEmpty() ?
                riskFactors.stream()
                        .max((a, b) -> Double.compare(a.getScore(), b.getScore()))
                        .orElse(null) : null;
    }

    /**
     * 风险因素
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactor {
        private String name;
        private String description;
        private double score; // 0-1
        private RiskLevel level;
        private String recommendation;

        public enum RiskLevel {
            LOW, MEDIUM, HIGH, CRITICAL
        }
    }

    /**
     * 风险预警
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAlert {
        private AlertType type;
        private String message;
        private double threshold;
        private double currentValue;
        private LocalDateTime alertTime;

        public enum AlertType {
            MARGIN_CALL("保证金不足预警"),
            HIGH_LEVERAGE("高杠杆预警"),
            CONCENTRATION("集中度预警"),
            CORRELATION("相关性预警"),
            LIQUIDITY("流动性预警");

            private final String description;

            AlertType(String description) {
                this.description = description;
            }

            public String getDescription() {
                return description;
            }
        }
    }
}
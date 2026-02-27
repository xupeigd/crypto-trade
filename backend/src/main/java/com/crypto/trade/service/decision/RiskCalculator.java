package com.crypto.trade.service.decision;

import com.crypto.trade.model.MarketContext;
import com.crypto.trade.model.RiskAssessment;
import com.crypto.trade.model.RiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RiskCalculator
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class RiskCalculator {

    /**
     * 计算完整的风险评估
     *
     * @param marketContext 市场上下文
     * @param riskLevel     目标风险等级
     * @return 风险评估结果
     */
    public RiskAssessment calculateRisk(MarketContext marketContext, RiskLevel riskLevel) {
        try {
            log.debug("开始风险评估: riskLevel={}", riskLevel.getDescription());

            // 计算基础风险评分
            double baseRiskScore = calculateBaseRiskScore(marketContext);

            // 根据目标风险等级调整
            double adjustedRiskScore = adjustRiskScore(baseRiskScore, riskLevel);

            // 识别风险因素
            List<RiskAssessment.RiskFactor> riskFactors = identifyRiskFactors(marketContext);

            // 创建风险评估
            RiskAssessment assessment = RiskAssessment.builder()
                    .userId(marketContext.getUserId())
                    .assessmentTime(LocalDateTime.now())
                    .riskScore(adjustedRiskScore)
                    .riskLevel(determineRiskLevel(adjustedRiskScore))
                    .totalExposure(calculateTotalExposure())
                    .leverageRatio(calculateLeverageRatio())
                    .marginUsageRatio(calculateMarginUsageRatio())
                    .accountBalance(BigDecimal.ZERO) // TODO: 从实际数据获取
                    .availableBalance(BigDecimal.ZERO) // TODO: 从实际数据获取
                    .riskFactors(riskFactors)
                    .positionRisks(calculatePositionRisks(marketContext))
                    .withinRiskTolerance(isWithinTolerance(adjustedRiskScore, riskLevel))
                    .build();

            log.debug("风险评估完成: userId={}, riskScore={}, riskLevel={}",
                    marketContext.getUserId(), adjustedRiskScore, assessment.getRiskLevel());

            return assessment;

        } catch (Exception e) {
            log.error("风险评估失败: userId={}", marketContext.getUserId(), e);
            return RiskAssessment.highRisk(marketContext.getUserId());
        }
    }

    /**
     * 快速风险评估
     *
     * @param marketContext 市场上下文
     * @return 快速风险评估
     */
    public RiskAssessment quickRiskAssessment(MarketContext marketContext) {
        try {
            double riskScore = calculateBaseRiskScore(marketContext);
            RiskLevel riskLevel = determineRiskLevel(riskScore);

            return RiskAssessment.builder()
                    .userId(marketContext.getUserId())
                    .assessmentTime(LocalDateTime.now())
                    .riskScore(riskScore)
                    .riskLevel(riskLevel)
                    .withinRiskTolerance(riskScore < 0.8)
                    .build();

        } catch (Exception e) {
            log.error("快速风险评估失败: userId={}", marketContext.getUserId(), e);
            return RiskAssessment.mediumRisk(marketContext.getUserId());
        }
    }

    /**
     * 计算基础风险评分
     */
    private double calculateBaseRiskScore(MarketContext marketContext) {
        double score = 0.0;

        // 市场波动性风险
        if (marketContext.getVolatility() == MarketContext.MarketVolatility.EXTREME) {
            score += 0.3;
        } else if (marketContext.getVolatility() == MarketContext.MarketVolatility.HIGH) {
            score += 0.2;
        } else if (marketContext.getVolatility() == MarketContext.MarketVolatility.MEDIUM) {
            score += 0.1;
        }

        // 市场情绪风险
        if (marketContext.getSentiment() == MarketContext.MarketSentiment.EXTREME_FEAR) {
            score += 0.2;
        } else if (marketContext.getSentiment() == MarketContext.MarketSentiment.EXTREME_GREED) {
            score += 0.15;
        }

        // 价格变化风险
        double avgChange = marketContext.getPriceChange24h().values().stream()
                .mapToDouble(change -> Math.abs(change.doubleValue()))
                .average()
                .orElse(0.0);
        if (avgChange > 10.0) {
            score += 0.2;
        } else if (avgChange > 5.0) {
            score += 0.1;
        }

        return Math.min(score, 1.0);
    }

    /**
     * 根据目标风险等级调整风险评分
     */
    private double adjustRiskScore(double baseScore, RiskLevel targetLevel) {
        switch (targetLevel) {
            case CONSERVATIVE:
                return baseScore * 0.5; // 降低风险评分
            case NORMAL:
                return baseScore; // 保持原评分
            case AGGRESSIVE:
                return Math.min(baseScore * 1.2, 1.0); // 提高风险评分
            default:
                return baseScore;
        }
    }

    /**
     * 确定风险等级
     */
    private RiskLevel determineRiskLevel(double riskScore) {
        if (riskScore <= 0.3) {
            return RiskLevel.CONSERVATIVE;
        } else if (riskScore <= 0.6) {
            return RiskLevel.NORMAL;
        } else {
            return RiskLevel.AGGRESSIVE;
        }
    }

    /**
     * 识别风险因素
     */
    private List<RiskAssessment.RiskFactor> identifyRiskFactors(MarketContext marketContext) {
        List<RiskAssessment.RiskFactor> factors = new ArrayList<>();

        // 波动性风险因素
        if (marketContext.getVolatility() == MarketContext.MarketVolatility.EXTREME) {
            factors.add(RiskAssessment.RiskFactor.builder()
                    .name("EXTREME_VOLATILITY")
                    .description("市场极端波动")
                    .score(0.4)
                    .level(RiskAssessment.RiskFactor.RiskLevel.CRITICAL)
                    .recommendation("降低仓位或暂停交易")
                    .build());
        }

        // 集中度风险因素
        Map<String, BigDecimal> volumes = marketContext.getVolume24h();
        if (volumes != null && !volumes.isEmpty()) {
            BigDecimal totalVolume = volumes.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal maxVolume = volumes.values().stream()
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            if (maxVolume.compareTo(totalVolume.multiply(BigDecimal.valueOf(0.5))) > 0) {
                factors.add(RiskAssessment.RiskFactor.builder()
                        .name("CONCENTRATION_RISK")
                        .description("单一合约交易量过高")
                        .score(0.3)
                        .level(RiskAssessment.RiskFactor.RiskLevel.HIGH)
                        .recommendation("分散投资到多个合约")
                        .build());
            }
        }

        // 技术指标风险因素
        if (marketContext.getTechnicalIndicators() != null) {
            long sellSignals = marketContext.getTechnicalIndicators().stream()
                    .mapToLong(indicator -> indicator.isSellSignal() ? 1 : 0)
                    .sum();

            long totalSignals = marketContext.getTechnicalIndicators().size();
            if (totalSignals > 0 && (double) sellSignals / totalSignals > 0.7) {
                factors.add(RiskAssessment.RiskFactor.builder()
                        .name("TECHNICAL_SELL_SIGNALS")
                        .description("多数技术指标显示卖出信号")
                        .score(0.25)
                        .level(RiskAssessment.RiskFactor.RiskLevel.MEDIUM)
                        .recommendation("谨慎交易，等待确认信号")
                        .build());
            }
        }

        return factors;
    }

    /**
     * 计算总敞口
     */
    private BigDecimal calculateTotalExposure() {
        // TODO: 从实际持仓数据计算
        return BigDecimal.ZERO;
    }

    /**
     * 计算杠杆比率
     */
    private BigDecimal calculateLeverageRatio() {
        // TODO: 从实际账户数据计算
        return BigDecimal.ONE;
    }

    /**
     * 计算保证金使用率
     */
    private BigDecimal calculateMarginUsageRatio() {
        // TODO: 从实际账户数据计算
        return BigDecimal.ZERO;
    }

    /**
     * 计算各持仓风险
     */
    private Map<String, BigDecimal> calculatePositionRisks(MarketContext marketContext) {
        Map<String, BigDecimal> positionRisks = new HashMap<>();

        if (marketContext.getCurrentPrices() != null) {
            marketContext.getCurrentPrices().keySet().forEach(symbol -> {
                // 简化的风险计算：基于价格变化幅度
                BigDecimal priceChange = marketContext.getPriceChange24h(symbol);
                if (priceChange != null) {
                    BigDecimal risk = priceChange.abs().divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    positionRisks.put(symbol, risk);
                }
            });
        }

        return positionRisks;
    }

    /**
     * 检查是否在风险容忍范围内
     */
    private boolean isWithinTolerance(double riskScore, RiskLevel targetLevel) {
        switch (targetLevel) {
            case CONSERVATIVE:
                return riskScore <= 0.4;
            case NORMAL:
                return riskScore <= 0.7;
            case AGGRESSIVE:
                return riskScore <= 0.9;
            default:
                return true;
        }
    }
}
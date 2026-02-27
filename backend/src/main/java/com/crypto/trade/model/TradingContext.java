package com.crypto.trade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * TradingContext
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingContext {

    private String userId;
    private String strategy;
    private RiskLevel riskLevel;
    private MarketContext marketContext;
    private RiskAssessment riskAssessment;
    private LocalDateTime decisionTime;
    private String sessionId; // 会话ID，用于追踪
    private String requestId; // 请求ID，用于追踪

    /**
     * 创建基础交易上下文
     */
    public static TradingContext createBasic(String userId, String strategy) {
        return TradingContext.builder()
                .userId(userId)
                .strategy(strategy)
                .riskLevel(RiskLevel.NORMAL)
                .decisionTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建保守型交易上下文
     */
    public static TradingContext createConservative(String userId, String strategy) {
        return TradingContext.builder()
                .userId(userId)
                .strategy(strategy)
                .riskLevel(RiskLevel.CONSERVATIVE)
                .decisionTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建激进型交易上下文
     */
    public static TradingContext createAggressive(String userId, String strategy) {
        return TradingContext.builder()
                .userId(userId)
                .strategy(strategy)
                .riskLevel(RiskLevel.AGGRESSIVE)
                .decisionTime(LocalDateTime.now())
                .build();
    }

    /**
     * 检查上下文是否完整
     */
    public boolean isComplete() {
        return userId != null &&
                strategy != null &&
                riskLevel != null &&
                marketContext != null &&
                riskAssessment != null;
    }

    /**
     * 检查是否可以安全交易
     */
    public boolean canTradeSafely() {
        if (!isComplete()) {
            return false;
        }

        // 检查风险评估
        if (riskAssessment.hasExtremeRisk()) {
            return false;
        }

        // 检查市场条件
        if (marketContext.getMarketCondition() == MarketContext.MarketCondition.UNCERTAIN) {
            return false;
        }

        // 检查可用余额
        return riskAssessment.getAvailableBalance().compareTo(java.math.BigDecimal.ZERO) > 0;
    }

    /**
     * 获取上下文摘要信息
     */
    public String getSummary() {
        if (!isComplete()) {
            return "交易上下文不完整";
        }

        return String.format("用户:%s, 策略:%s, 风险:%s, 市场状况:%s, 风险评分:%.2f",
                userId,
                strategy,
                riskLevel.getDescription(),
                marketContext.getMarketCondition().getDescription(),
                riskAssessment.getRiskScore());
    }
}
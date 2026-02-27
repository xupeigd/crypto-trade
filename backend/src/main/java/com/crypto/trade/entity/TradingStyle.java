package com.crypto.trade.entity;

/**
 * TradingStyle
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public enum TradingStyle {
    C1_CONSERVATIVE("保守型", 0.03, 0.05),
    C2_CAUTIOUS("谨慎型", 0.05, 0.10),
    C3_MODERATE("稳健型", 0.08, 0.15),
    C4_ACTIVE("积极型", 0.12, 0.20),
    C5_AGGRESSIVE("激进型", 0.18, 0.30);

    private final String description;
    private final double maxLossRate;      // 最大亏损率
    private final double targetProfitRate;  // 目标盈利率

    TradingStyle(String description, double maxLossRate, double targetProfitRate) {
        this.description = description;
        this.maxLossRate = maxLossRate;
        this.targetProfitRate = targetProfitRate;
    }

    public String getDescription() {
        return description;
    }

    public double getMaxLossRate() {
        return maxLossRate;
    }

    public double getTargetProfitRate() {
        return targetProfitRate;
    }
}
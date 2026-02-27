package com.crypto.trade.model;

/**
 * RiskLevel
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public enum RiskLevel {
    CONSERVATIVE(0.3, "保守"),
    NORMAL(0.6, "正常"),
    AGGRESSIVE(0.8, "激进");

    private final double threshold;
    private final String description;

    RiskLevel(double threshold, String description) {
        this.threshold = threshold;
        this.description = description;
    }

    /**
     * 根据阈值获取对应的风险等级
     */
    public static RiskLevel fromThreshold(double threshold) {
        if (threshold <= 0.3) {
            return CONSERVATIVE;
        } else if (threshold <= 0.6) {
            return NORMAL;
        } else {
            return AGGRESSIVE;
        }
    }

    public double getThreshold() {
        return threshold;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 比较风险等级
     */
    public boolean isHigherThan(RiskLevel other) {
        return this.ordinal() > other.ordinal();
    }

    /**
     * 是否为高风险等级
     */
    public boolean isHighRisk() {
        return this == AGGRESSIVE;
    }

    /**
     * 是否为低风险等级
     */
    public boolean isLowRisk() {
        return this == CONSERVATIVE;
    }
}
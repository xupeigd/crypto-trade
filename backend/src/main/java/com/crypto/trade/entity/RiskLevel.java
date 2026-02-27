package com.crypto.trade.entity;

/**
 * RiskLevel
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public enum RiskLevel {
    /**
     * 高风险（对应保守型交易风格）
     */
    HIGH("高风险"),

    /**
     * 中风险（对应中性型交易风格）
     */
    MEDIUM("中风险"),

    /**
     * 低风险（对应激进型交易风格）
     */
    LOW("低风险");

    private final String description;

    RiskLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
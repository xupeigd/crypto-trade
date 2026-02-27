package com.crypto.trade.entity;

/**
 * RiskMode
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public enum RiskMode {
    AUTO("自动模式"),
    MANUAL("手动模式");

    private final String description;

    RiskMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
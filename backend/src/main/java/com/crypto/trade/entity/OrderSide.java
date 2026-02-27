package com.crypto.trade.entity;

/**
 * OrderSide
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public enum OrderSide {
    /**
     * 买入
     */
    BUY("买入"),

    /**
     * 卖出
     */
    SELL("卖出");

    private final String description;

    OrderSide(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
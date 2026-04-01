package com.crypto.trade.entity;

/**
 * ExecutionMode
 * 执行模式枚举
 *
 * @author page
 * @date 2026-03-16
 */
public enum ExecutionMode {
    /**
     * 实盘模式 - 真实下单到交易所
     */
    LIVE,
    
    /**
     * 模拟模式 - 不提交订单，仅模拟执行
     */
    DRY_RUN
}

package com.crypto.trade.dto.cex.model;

import java.math.BigDecimal;

/**
 * CexAccountBalance
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public abstract class CexAccountBalance {

    /**
     * 获取币种
     *
     * @return 币种代码(如 USDT, BTC)
     */
    public abstract String getCurrency();

    /**
     * 获取总余额
     *
     * @return 总余额
     */
    public abstract BigDecimal getTotalBalance();

    /**
     * 获取可用余额
     *
     * @return 可用余额
     */
    public abstract BigDecimal getAvailableBalance();

    /**
     * 获取冻结余额
     *
     * @return 冻结余额
     */
    public abstract BigDecimal getFrozenBalance();

    /**
     * 获取USD权益
     *
     * @return 折算为USD的权益
     */
    public abstract BigDecimal getEquityInUsd();

    /**
     * 获取已用保证金
     * <p>
     * 初始保证金要求(Initial Margin Requirement)
     * </p>
     *
     * @return 已用保证金
     */
    public abstract BigDecimal getUsedMargin();

    /**
     * 获取未实现盈亏
     *
     * @return 未实现盈亏
     */
    public abstract BigDecimal getUnrealizedPnl();

    /**
     * 获取更新时间
     *
     * @return 更新时间戳
     */
    public abstract Long getUpdateTime();
}

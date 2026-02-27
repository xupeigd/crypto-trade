package com.crypto.trade.dto.cex.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CexAccountPortfolio
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class CexAccountPortfolio {

    /**
     * 总权益 (美元计价)
     */
    private BigDecimal totalEquity;

    /**
     * 可用权益 (美元计价)
     */
    private BigDecimal availableEquity;

    /**
     * 冻结权益 (美元计价)
     */
    private BigDecimal frozenEquity;

    /**
     * 币种详情列表
     * 每个币种的余额明细
     */
    private List<CexAccountBalance> balances;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 获取总权益
     */
    public BigDecimal getTotalEquity() {
        return totalEquity;
    }

    /**
     * 设置总权益
     */
    public void setTotalEquity(BigDecimal totalEquity) {
        this.totalEquity = totalEquity;
    }

    /**
     * 获取可用权益
     */
    public BigDecimal getAvailableEquity() {
        return availableEquity;
    }

    /**
     * 设置可用权益
     */
    public void setAvailableEquity(BigDecimal availableEquity) {
        this.availableEquity = availableEquity;
    }

    /**
     * 获取冻结权益
     */
    public BigDecimal getFrozenEquity() {
        return frozenEquity;
    }

    /**
     * 设置冻结权益
     */
    public void setFrozenEquity(BigDecimal frozenEquity) {
        this.frozenEquity = frozenEquity;
    }

    /**
     * 获取币种详情列表
     */
    public List<CexAccountBalance> getBalances() {
        return balances;
    }

    /**
     * 设置币种详情列表
     */
    public void setBalances(List<CexAccountBalance> balances) {
        this.balances = balances;
    }

    /**
     * 获取更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /**
     * 设置更新时间
     */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}

package com.crypto.trade.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AccountEquityData
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountEquityData {

    /**
     * API密钥ID
     */
    private Long apiKeyId;

    /**
     * 总权益(USDT)
     */
    private BigDecimal totalEquityUsdt;

    /**
     * 可用权益(USDT)
     */
    private BigDecimal availableEquityUsdt;

    /**
     * 冻结权益(USDT)
     */
    private BigDecimal frozenEquityUsdt;

    /**
     * 保证金权益(USDT)
     */
    private BigDecimal marginEquityUsdt;

    /**
     * 数据更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 构造函数 - 仅包含基本信息
     */
    public AccountEquityData(Long apiKeyId, BigDecimal totalEquityUsdt, BigDecimal availableEquityUsdt) {
        this.apiKeyId = apiKeyId;
        this.totalEquityUsdt = totalEquityUsdt;
        this.availableEquityUsdt = availableEquityUsdt;
        this.frozenEquityUsdt = BigDecimal.ZERO;
        this.marginEquityUsdt = BigDecimal.ZERO;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 转换为TAccountEquitySnapshot实体
     */
    public AccountEquitySnapshot toSnapshotEntity() {
        AccountEquitySnapshot snapshot = new AccountEquitySnapshot();
        snapshot.setApiKeyId(this.apiKeyId);
        snapshot.setTotalEquityUsdt(this.totalEquityUsdt);
        snapshot.setAvailableEquityUsdt(this.availableEquityUsdt);
        snapshot.setFrozenEquityUsdt(this.frozenEquityUsdt);
        snapshot.setMarginEquityUsdt(this.marginEquityUsdt);
        snapshot.setUpdateTime(this.updateTime);
        return snapshot;
    }
}
package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * TradeBalanceSnapshot
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_trade_balance_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeBalanceSnapshot {

    /**
     * 快照ID（主键）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;

    /**
     * API Key ID（关联到 t_api_keys 表）
     */
    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    /**
     * 交易所名称（okx/binance/bybit）
     */
    @Column(name = "cex_name", nullable = false, length = 50)
    private String cexName;

    /**
     * 账户权益（USDT）
     * 包含保证金+未实现盈亏
     */
    @Column(name = "total_equity_usdt", nullable = false, precision = 38, scale = 8)
    private BigDecimal totalEquityUsdt;

    @Column(name = "display_total_equity_usdt", precision = 38, scale = 8)
    private BigDecimal displayTotalEquityUsdt;

    /**
     * 可用权益（USDT）
     * 扣除保证金后的可用资金
     */
    @Column(name = "available_equity_usdt", nullable = false, precision = 38, scale = 8)
    private BigDecimal availableEquityUsdt;

    /**
     * 已使用保证金（USDT）
     */
    @Column(name = "used_margin_usdt", nullable = false, precision = 38, scale = 8)
    private BigDecimal usedMarginUsdt;

    /**
     * 未实现盈亏（USDT）
     */
    @Column(name = "unrealized_pnl_usdt", precision = 38, scale = 8)
    private BigDecimal unrealizedPnlUsdt;

    /**
     * 保证金使用率（百分比）
     */
    @Column(name = "margin_ratio", precision = 10, scale = 4)
    private BigDecimal marginRatio;

    /**
     * 最大可用金额（AI交易资金限制）
     * 配置中的maxAccountBalance值
     */
    @Column(name = "max_available_amount", precision = 38, scale = 8)
    private BigDecimal maxAvailableAmount;

    /**
     * 快照来源
     * INITIAL - 初始Prompt生成
     * REPLAY - 动作重放
     */
    @Column(name = "source", nullable = false, length = 20)
    private String source;

    /**
     * 关联的LlmCallRecord ID
     * 记录生成该快照的AI调用记录ID
     * 在快照创建时为null，AI调用后更新
     */
    @Column(name = "record_id")
    private Long recordId;

    /**
     * 快照时间点
     */
    @Column(name = "snapshot_time", nullable = false)
    private LocalDateTime snapshotTime;

    /**
     * 创建时间
     */
    @Column(name = "created_time")
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    // ============================================
    // 生命周期回调
    // ============================================

    @PrePersist
    protected void onCreate() {
        createdTime = LocalDateTime.now();
        updatedTime = LocalDateTime.now();
        if (snapshotTime == null) {
            snapshotTime = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
    }

    // ============================================
    // 业务方法
    // ============================================

    /**
     * 判断快照是否来自初始Prompt生成
     *
     * @return true if source is INITIAL
     */
    public boolean isInitial() {
        return "INITIAL".equals(source);
    }

    /**
     * 判断快照是否来自动作重放
     *
     * @return true if source is REPLAY
     */
    public boolean isReplay() {
        return "REPLAY".equals(source);
    }

    /**
     * 获取保证金使用率描述
     *
     * @return 使用率描述
     */
    public String getMarginRatioDescription() {
        if (marginRatio == null) {
            return "未知";
        }
        if (marginRatio.compareTo(new BigDecimal("50")) > 0) {
            return "高";
        } else if (marginRatio.compareTo(new BigDecimal("20")) > 0) {
            return "中";
        } else {
            return "低";
        }
    }

    /**
     * 计算可用金额使用率
     *
     * @return 使用率（百分比）
     */
    public BigDecimal calculateAvailableUsageRatio() {
        if (maxAvailableAmount == null || maxAvailableAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (totalEquityUsdt == null) {
            return BigDecimal.ZERO;
        }
        return totalEquityUsdt.divide(maxAvailableAmount, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }
}

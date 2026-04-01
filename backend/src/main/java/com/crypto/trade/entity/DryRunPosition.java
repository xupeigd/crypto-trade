package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DryRunPosition
 * 模拟持仓实体类
 *
 * @author page
 * @date 2026-03-16
 */
@Entity
@Table(name = "t_dry_run_position",
        indexes = {
                @Index(name = "idx_dry_run_position", columnList = "api_key_id,inst_id,pos_side"),
                @Index(name = "idx_dry_run_position_status", columnList = "api_key_id,inst_id,pos_side,status"),
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DryRunPosition {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * API Key ID
     */
    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    /**
     * 合约品种
     */
    @Column(name = "inst_id", nullable = false, length = 50)
    private String instId;

    /**
     * 持仓方向: long/short
     */
    @Column(name = "pos_side", nullable = false, length = 20)
    private String posSide;

    /**
     * 持仓数量(张)
     */
    @Column(name = "pos", nullable = false, precision = 38, scale = 8)
    private BigDecimal pos;

    /**
     * 平均开仓价格
     */
    @Column(name = "avg_px", precision = 38, scale = 8)
    private BigDecimal avgPx;

    /**
     * 杠杆倍数
     */
    @Column(name = "lever", precision = 8, scale = 2)
    private BigDecimal lever;

    /**
     * 未实现盈亏
     */
    @Column(name = "unrealized_pnl", precision = 38, scale = 8)
    @Builder.Default
    private BigDecimal unrealizedPnl = BigDecimal.ZERO;

    /**
     * 保证金
     */
    @Column(name = "margin", precision = 38, scale = 8)
    private BigDecimal margin;

    /**
     * 开仓时的资金费率
     */
    @Column(name = "funding_rate", precision = 12, scale = 8)
    private BigDecimal fundingRate;

    /**
     * 止盈价格
     */
    @Column(name = "take_profit_price", precision = 38, scale = 8)
    private BigDecimal takeProfitPrice;

    /**
     * 止损价格
     */
    @Column(name = "stop_loss_price", precision = 38, scale = 8)
    private BigDecimal stopLossPrice;

    /**
     * 持仓状态: pending(委托中) / open(持仓中) / closed(已平仓)
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "open";

    /**
     * 订单类型: limit(限价单) / market(市价单)
     */
    @Column(name = "order_type", length = 20)
    private String orderType;

    /**
     * 委托价格（限价单专用）
     */
    @Column(name = "pending_px", precision = 38, scale = 8)
    private BigDecimal pendingPx;

    /**
     * 委托数量（限价单专用）
     */
    @Column(name = "pending_sz", precision = 38, scale = 8)
    private BigDecimal pendingSz;

    /**
     * 是否已平仓（已废弃，使用status字段）
     */
    @Column(name = "closed")
    @Deprecated
    @Builder.Default
    private Boolean closed = false;

    /**
     * 平仓原因: MANUAL(手动) / TAKE_PROFIT(止盈) / STOP_LOSS(止损)
     */
    @Column(name = "close_reason", length = 20)
    private String closeReason;

    /**
     * 平仓价格
     */
    @Column(name = "close_price", precision = 38, scale = 8)
    private BigDecimal closePrice;

    /**
     * 已实现盈亏
     */
    @Column(name = "realized_pnl", precision = 38, scale = 8)
    private BigDecimal realizedPnl;

    /**
     * 结算时的累计资金费
     */
    @Column(name = "settled_funding_fee", precision = 38, scale = 8)
    private BigDecimal settledFundingFee;

    /**
     * 平仓时间
     */
    @Column(name = "close_time")
    private LocalDateTime closeTime;

    /**
     * 创建时间
     */
    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    @PrePersist
    protected void onCreate() {
        createdTime = LocalDateTime.now();
        updatedTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
    }
}

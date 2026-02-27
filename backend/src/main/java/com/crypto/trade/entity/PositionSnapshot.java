package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PositionSnapshot
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_position_snapshot",
        indexes = {
                @Index(name = "idx_position_snapshot", columnList = "api_key_id,inst_id,pos_side,pos_id,type,ctime,utime", unique = true),
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PositionSnapshot {
    /**
     * 快照ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;

    /**
     * 关联的API密钥ID
     */
    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    /**
     * 合约代码(如 ETH-USDT-SWAP)
     */
    @Column(name = "inst_id", nullable = false)
    private String instId;

    /**
     * 合约类型(SWAP/FUTURES/OPTION)
     */
    @Column(name = "inst_type", nullable = false)
    private String instType;

    /**
     * 持仓方向(long/short/net)
     */
    @Column(name = "pos_side", nullable = false)
    private String posSide;

    /**
     * 持仓ID (OKX返回的持仓唯一标识)
     * 用于唯一标识一个仓位,同一合约的不同仓位有不同的posId
     */
    @Column(name = "pos_id")
    private String posId;

    /**
     * 持仓数量(张)
     */
    @Column(name = "pos", nullable = false, precision = 38, scale = 8)
    private BigDecimal pos;

    /**
     * 可用持仓数量
     */
    @Column(name = "avail_pos", precision = 38, scale = 8)
    private BigDecimal availPos = BigDecimal.ZERO;

    /**
     * 平均开仓价格
     */
    @Column(name = "avg_px", precision = 38, scale = 8)
    private BigDecimal avgPx;

    /**
     * 标记价格
     */
    @Column(name = "mark_px", precision = 38, scale = 8)
    private BigDecimal markPx;

    /**
     * 最新成交价格
     */
    @Column(name = "last_px", precision = 38, scale = 8)
    private BigDecimal lastPx;

    /**
     * 杠杆倍数
     */
    @Column(name = "lever", precision = 8, scale = 2)
    private BigDecimal lever;

    /**
     * 保证金
     */
    @Column(name = "margin", precision = 38, scale = 8)
    private BigDecimal margin;

    /**
     * 初始保证金
     */
    @Column(name = "imr", precision = 38, scale = 8)
    private BigDecimal imr;

    /**
     * 维持保证金
     */
    @Column(name = "mmr", precision = 38, scale = 8)
    private BigDecimal mmr;

    /**
     * 保证金率
     */
    @Column(name = "mgn_ratio", precision = 38, scale = 8)
    private BigDecimal mgnRatio;

    /**
     * 保证金模式(isolated/cross)
     */
    @Column(name = "mgn_mode")
    private String mgnMode;

    /**
     * 未实现盈亏(按标记价格)
     */
    @Column(name = "upl", precision = 38, scale = 8)
    private BigDecimal upl;

    /**
     * 未实现盈亏(按最新价)
     */
    @Column(name = "upl_last_px", precision = 38, scale = 8)
    private BigDecimal uplLastPx;

    /**
     * 已实现盈亏
     */
    @Column(name = "realized_pnl", precision = 38, scale = 8)
    private BigDecimal realizedPnl;

    /**
     * 开仓时最大持仓数量
     * 历史仓位特有字段
     */
    @Column(name = "open_max_pos", precision = 38, scale = 8)
    private BigDecimal openMaxPos;

    /**
     * 平仓时的总持仓数量
     * 历史仓位特有字段
     */
    @Column(name = "close_total_pos", precision = 38, scale = 8)
    private BigDecimal closeTotalPos;

    /**
     * 已结算盈亏
     * 历史仓位特有字段
     */
    @Column(name = "settled_pnl", precision = 38, scale = 8)
    private BigDecimal settledPnl;

    /**
     * 盈亏比率
     * 历史仓位特有字段
     */
    @Column(name = "pnl_ratio", precision = 38, scale = 8)
    private BigDecimal pnlRatio;

    /**
     * 持仓名义价值(USD)
     */
    @Column(name = "notional_usd", precision = 38, scale = 8)
    private BigDecimal notionalUsd;

    /**
     * 预估强平价
     */
    @Column(name = "liq_px", precision = 38, scale = 8)
    private BigDecimal liqPx;

    /**
     * 累计手续费
     */
    @Column(name = "fee", precision = 38, scale = 8)
    private BigDecimal fee;

    /**
     * 累计资金费用
     */
    @Column(name = "funding_fee", precision = 38, scale = 8)
    private BigDecimal fundingFee;

    /**
     * 保证金币种
     */
    @Column(name = "ccy")
    private String ccy;

    /**
     * 创建时间(毫秒)
     */
    @Column(name = "ctime")
    private Long ctime;

    /**
     * 持仓更新时间(毫秒)
     */
    @Column(name = "utime")
    private Long utime;

    /**
     * 数据摄入时间(毫秒)
     */
    @Column(name = "data_ingestion_time")
    private Long dataIngestionTime;

    /**
     * 快照更新时间
     */
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /**
     * 记录创建时间
     */
    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();

    /**
     * 记录更新时间
     */
    @Column(name = "updated_time")
    private LocalDateTime updatedTime = LocalDateTime.now();

    /**
     * 最近一次平仓的类型
     * 1：部分平仓
     * 2：完全平仓
     * 3：强平
     * 4：强减
     * 5：ADL自动减仓
     * 状态叠加时，以最新的平仓类型为准状态为准。
     */
    @Column(name = "type")
    private String type;

    @Column(name = "open_avg_px")
    private BigDecimal openAvgPx;

    @Column(name = "close_avg_px")
    private BigDecimal closeAvgPx;

    /**
     * 创建前回调
     */
    @PrePersist
    protected void onCreate() {
        createdTime = LocalDateTime.now();
        updatedTime = LocalDateTime.now();
    }

    /**
     * 更新前回调
     */
    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
    }
}

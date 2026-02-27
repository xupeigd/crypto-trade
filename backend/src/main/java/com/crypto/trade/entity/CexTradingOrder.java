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
 * CexTradingOrder
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_cex_trading_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CexTradingOrder {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * CEX订单ID（CEX返回的唯一标识）
     */
    @Column(name = "order_id", nullable = false, unique = true, length = 50)
    private String orderId;

    /**
     * 客户自定义订单ID
     */
    @Column(name = "cl_ord_id", length = 50)
    private String clOrdId;

    /**
     * 交易所（okx/binance/bybit）
     */
    @Column(name = "exchange", nullable = false, length = 10)
    private String exchange;

    /**
     * API Key ID
     */
    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    /**
     * 来源TradeAction ID
     * 记录该订单是由哪个TradeAction产生的
     */
    @Column(name = "action_id")
    private Long actionId;

    /**
     * 来源调用记录ID (冗余字段)
     * 记录该订单是由哪个LlmCallRecord产生的
     */
    @Column(name = "record_id")
    private Long recordId;

    /**
     * 合约品种
     */
    @Column(name = "inst_id", nullable = false, length = 50)
    private String instId;

    /**
     * 产品类型
     */
    @Column(name = "inst_type", length = 20)
    private String instType;

    /**
     * 订单方向（buy/sell）
     */
    @Column(name = "side", nullable = false, length = 20)
    private String side;

    /**
     * 持仓方向（long/short/net）
     */
    @Column(name = "pos_side", length = 20)
    private String posSide;

    /**
     * 订单类型（market/limit）
     */
    @Column(name = "order_type", nullable = false, length = 20)
    private String orderType;

    /**
     * 交易模式（isolated/cross）
     */
    @Column(name = "td_mode", length = 20)
    private String tdMode;

    /**
     * 保证金币种
     */
    @Column(name = "ccy", length = 10)
    private String ccy;

    /**
     * 委托数量
     */
    @Column(name = "sz", precision = 38, scale = 8)
    private BigDecimal sz;

    /**
     * 委托价格
     */
    @Column(name = "px", precision = 38, scale = 8)
    private BigDecimal px;

    /**
     * 委托金额
     */
    @Column(name = "amt", precision = 38, scale = 8)
    private BigDecimal amt;

    /**
     * 杠杆倍数
     */
    @Column(name = "lever", precision = 8, scale = 2)
    private BigDecimal lever;

    /**
     * 订单状态（CEX视角）
     * live - 待成交
     * partially_filled - 部分成交
     * filled - 完全成交
     * canceled - 已撤销
     * failed - 失败
     */
    @Column(name = "order_state", nullable = false, length = 20)
    private String orderState;

    /**
     * 状态描述
     */
    @Column(name = "state_msg", length = 100)
    private String stateMsg;

    /**
     * 成交均价
     */
    @Column(name = "avg_px", precision = 38, scale = 8)
    private BigDecimal avgPx;

    /**
     * 已成交数量
     */
    @Column(name = "filled_sz", precision = 38, scale = 8)
    private BigDecimal filledSz;

    /**
     * 已成交金额
     */
    @Column(name = "filled_amt", precision = 38, scale = 8)
    private BigDecimal filledAmt;

    /**
     * 成交比例（%）
     */
    @Column(name = "fill_ratio", precision = 8, scale = 4)
    private BigDecimal fillRatio;

    /**
     * 手续费
     */
    @Column(name = "fee", precision = 38, scale = 8)
    private BigDecimal fee;

    /**
     * 手续费币种
     */
    @Column(name = "fee_ccy", length = 10)
    private String feeCcy;

    /**
     * 返佣
     */
    @Column(name = "rebate", precision = 38, scale = 8)
    private BigDecimal rebate;

    /**
     * 返佣币种
     */
    @Column(name = "rebate_ccy", length = 10)
    private String rebateCcy;

    /**
     * 累计成交数量字符串（CEX原始数据）
     */
    @Column(name = "acc_fill_sz", length = 50)
    private String accFillSz;

    /**
     * CEX订单创建时间
     */
    @Column(name = "c_time")
    private LocalDateTime cTime;

    /**
     * CEX订单最后更新时间
     */
    @Column(name = "u_time")
    private LocalDateTime uTime;

    /**
     * 最后同步时间
     */
    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

    /**
     * 同步状态
     * synced - 已同步
     * syncing - 同步中
     * failed - 同步失败
     * stale - 数据陈旧
     */
    @Column(name = "sync_status", length = 20)
    private String syncStatus;

    /**
     * 同步错误信息
     */
    @Column(name = "sync_error_msg", columnDefinition = "TEXT")
    private String syncErrorMsg;

    /**
     * 同步重试次数
     */
    @Column(name = "sync_retry_count")
    private Integer syncRetryCount;

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

    /**
     * 最后成交时间
     */
    @Column(name = "exec_time")
    private LocalDateTime execTime;

    // ============================================
    // 关联实体
    // ============================================

    /**
     * 关联的API Key实体
     * 注意：不使用数据库外键约束，关联关系由代码层面维护
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_key_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "none", foreignKeyDefinition = ""))
    private ApiKey apiKey;

    // ============================================
    // 生命周期回调
    // ============================================

    @PrePersist
    protected void onCreate() {
        createdTime = LocalDateTime.now();
        updatedTime = LocalDateTime.now();
        if (syncStatus == null) {
            syncStatus = "pending"; // 修复：初始状态为pending（待同步）
        }
        if (syncRetryCount == null) {
            syncRetryCount = 0;
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
     * 判断订单是否活跃（待成交或部分成交）
     *
     * @return true if order is active
     */
    public boolean isActive() {
        return "live".equals(orderState) || "partially_filled".equals(orderState);
    }

    /**
     * 判断订单是否已完成（完全成交或已撤销）
     *
     * @return true if order is completed
     */
    public boolean isCompleted() {
        return "filled".equals(orderState) || "canceled".equals(orderState);
    }

    /**
     * 判断订单是否失败
     *
     * @return true if order is failed
     */
    public boolean isFailed() {
        return "failed".equals(orderState);
    }

    /**
     * 判断是否需要同步
     *
     * @return true if sync needed
     */
    public boolean needsSync() {
        return isActive() && "synced".equals(syncStatus);
    }

    /**
     * 判断同步是否超时（超过5分钟未同步）
     *
     * @return true if sync is stale
     */
    public boolean isSyncStale() {
        if (lastSyncTime == null) {
            return true;
        }
        return lastSyncTime.isBefore(LocalDateTime.now().minusMinutes(5));
    }

    /**
     * 计算成交比例
     *
     * @return 成交比例（百分比）
     */
    public BigDecimal calculateFillRatio() {
        if (sz == null || sz.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (filledSz == null) {
            return BigDecimal.ZERO;
        }
        return filledSz.divide(sz, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * 获取订单状态描述
     *
     * @return 状态描述
     */
    public String getStateDescription() {
        return switch (orderState) {
            case "live" -> "待成交";
            case "partially_filled" -> "部分成交";
            case "filled" -> "完全成交";
            case "canceled" -> "已撤销";
            case "failed" -> "失败";
            default -> "未知";
        };
    }
}

package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * TradingOrder
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_trading_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 系统订单UUID（与CEX无关的唯一标识）
     */
    @Column(name = "order_uuid", nullable = false, length = 50, unique = true)
    private String orderUuid;

    /**
     * API Key ID
     */
    @Column(name = "api_key_id")
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
     * 订单来源（web/bot/api）
     */
    @Column(name = "source", length = 20)
    private String source = "web";

    // ============================================
    // 交易意图（用户填写的参数）
    // ============================================

    /**
     * 合约品种
     */
    @Column(name = "inst_id", nullable = false, length = 50)
    private String instId;

    /**
     * 订单方向（buy/sell）
     */
    @Column(name = "side", nullable = false, length = 20)
    private String side;

    /**
     * 持仓方向（long/short）
     */
    @Column(name = "pos_side", length = 20)
    private String posSide;

    /**
     * 订单类型（market/limit）
     */
    @Column(name = "order_type", nullable = false, length = 20)
    private String orderType;

    /**
     * 委托金额（USDT）
     */
    @Column(name = "amt", nullable = false, precision = 38, scale = 8)
    private BigDecimal amt;

    /**
     * 委托数量（合约张数）
     */
    @Column(name = "sz", precision = 38, scale = 8)
    private BigDecimal sz;

    /**
     * 杠杆倍数
     */
    @Column(name = "lever", nullable = false, precision = 8, scale = 2)
    private BigDecimal lever;

    // ============================================
    // 止盈止损设置
    // ============================================

    /**
     * 止盈开关
     */
    @Column(name = "take_profit_enabled")
    private Boolean takeProfitEnabled = false;

    /**
     * 止损开关
     */
    @Column(name = "stop_loss_enabled")
    private Boolean stopLossEnabled = false;

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
     * 止盈百分比（%）
     */
    @Column(name = "take_profit_pct", precision = 8, scale = 4)
    private BigDecimal takeProfitPct;

    /**
     * 止损百分比（%）
     */
    @Column(name = "stop_loss_pct", precision = 8, scale = 4)
    private BigDecimal stopLossPct;

    // ============================================
    // 订单执行结果
    // ============================================

    /**
     * 系统订单状态
     * pending - 准备中
     * submitted - 已提交到CEX
     * success - 执行成功
     * failed - 执行失败
     * canceling - 撤销中
     * canceled - 已撤销
     */
    @Column(name = "order_status", nullable = false, length = 20)
    private String orderStatus = "pending";

    /**
     * 关联的CEX订单ID（外键引用CexTradingOrder）
     */
    @Column(name = "cex_order_id", length = 50)
    private String cexOrderId;

    /**
     * 是否为dry run订单
     */
    @Column(name = "is_dry_run", nullable = false)
    private Boolean isDryRun = false;

    // ============================================
    // 业务扩展字段
    // ============================================

    /**
     * 策略ID
     */
    @Column(name = "strategy_id")
    private Long strategyId;

    /**
     * 机器人ID
     */
    @Column(name = "bot_id")
    private Long botId;

    /**
     * 风控ID
     */
    @Column(name = "risk_control_id")
    private Long riskControlId;

    // ============================================
    // 时间戳
    // ============================================

    /**
     * 创建时间
     */
    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();

    /**
     * 更新时间
     */
    @Column(name = "updated_time")
    private LocalDateTime updatedTime = LocalDateTime.now();

    /**
     * 提交到CEX的时间
     */
    @Column(name = "submitted_time")
    private LocalDateTime submittedTime;

    /**
     * 完成时间
     */
    @Column(name = "completed_time")
    private LocalDateTime completedTime;

    // ============================================
    // 错误信息
    // ============================================

    /**
     * 错误信息
     */
    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    // ============================================
    // 关联实体
    // ============================================

    /**
     * 关联的CEX订单
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cex_order_id", referencedColumnName = "order_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "none", foreignKeyDefinition = ""))
    private CexTradingOrder cexOrder;

    /**
     * 关联API Key实体
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
        // 如果没有设置orderUuid，生成一个
        if (orderUuid == null) {
            orderUuid = generateOrderUuid();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
    }

    /**
     * 生成系统订单UUID
     *
     * @return UUID
     */
    private String generateOrderUuid() {
        return "CO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss")) + "x" + (int) (Math.random() * 10000);
    }

    // ============================================
    // 业务方法
    // ============================================

    /**
     * 判断订单是否已完成
     *
     * @return true if completed
     */
    public boolean isCompleted() {
        return "success".equals(orderStatus) ||
                "failed".equals(orderStatus) ||
                "canceled".equals(orderStatus);
    }

    /**
     * 判断订单是否活跃（正在执行中）
     *
     * @return true if active
     */
    public boolean isActive() {
        return "pending".equals(orderStatus) ||
                "submitted".equals(orderStatus) ||
                "canceling".equals(orderStatus);
    }

    /**
     * 判断是否已提交到CEX
     *
     * @return true if submitted
     */
    public boolean isSubmitted() {
        return cexOrderId != null && !cexOrderId.isEmpty();
    }

    /**
     * 判断订单是否成功
     *
     * @return true if success
     */
    public boolean isSuccess() {
        return "success".equals(orderStatus);
    }

    /**
     * 判断订单是否失败
     *
     * @return true if failed
     */
    public boolean isFailed() {
        return "failed".equals(orderStatus);
    }

    /**
     * 获取订单状态描述
     *
     * @return 状态描述
     */
    public String getStatusDescription() {
        return switch (orderStatus) {
            case "pending" -> "准备中";
            case "submitted" -> "已提交";
            case "success" -> "执行成功";
            case "failed" -> "执行失败";
            case "canceling" -> "撤销中";
            case "canceled" -> "已撤销";
            default -> "未知状态";
        };
    }

    /**
     * 标记订单已提交到CEX
     *
     * @param cexOrderId CEX订单ID
     */
    public void markAsSubmitted(String cexOrderId) {
        this.cexOrderId = cexOrderId;
        this.orderStatus = "submitted";
        this.submittedTime = LocalDateTime.now();
    }

    /**
     * 标记订单执行成功
     */
    public void markAsSuccess() {
        this.orderStatus = "success";
        this.completedTime = LocalDateTime.now();
    }

    /**
     * 标记订单执行失败
     *
     * @param errorMsg 错误信息
     */
    public void markAsFailed(String errorMsg) {
        this.orderStatus = "failed";
        this.errorMsg = errorMsg;
        this.completedTime = LocalDateTime.now();
    }

    /**
     * 标记订单为撤销中
     */
    public void markAsCanceling() {
        this.orderStatus = "canceling";
    }

    /**
     * 标记订单已撤销
     */
    public void markAsCanceled() {
        this.orderStatus = "canceled";
        this.completedTime = LocalDateTime.now();
    }
}

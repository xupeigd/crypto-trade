package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RiskControlOrder
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_risk_control_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskControlOrder {

    /**
     * 订单ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    /**
     * 原始订单ID（关联到交易订单表）
     */
    @Column(name = "original_order_id", nullable = false, unique = true, length = 100)
    private String originalOrderId;

    /**
     * API密钥ID
     */
    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    /**
     * 交易对（如BTC-USDT-SWAP）
     */
    @Column(name = "symbol", nullable = false, length = 50)
    private String symbol;

    /**
     * 订单类型（limit/market）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    /**
     * 订单方向（buy/sell）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 20)
    private OrderSide side;

    /**
     * 订单数量
     */
    @Column(name = "quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    /**
     * 订单价格（限价单必填）
     */
    @Column(name = "price", precision = 20, scale = 8)
    private BigDecimal price;

    /**
     * 止盈价格
     */
    @Column(name = "take_profit_price", precision = 20, scale = 8)
    private BigDecimal takeProfitPrice;

    /**
     * 止损价格
     */
    @Column(name = "stop_loss_price", precision = 20, scale = 8)
    private BigDecimal stopLossPrice;

    /**
     * 风控审核状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "audit_status", nullable = false, length = 20)
    private AuditStatus auditStatus;

    /**
     * 审核人（暂时为空，后续可扩展）
     */
    @Column(name = "auditor", length = 100)
    private String auditor;

    /**
     * 审核时间
     */
    @Column(name = "audit_time")
    private LocalDateTime auditTime;

    /**
     * 驳回原因
     */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /**
     * 创建时间
     */
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 订单来源（okx_trading）
     */
    @Column(name = "order_source", nullable = false, length = 50)
    private String orderSource;

    /**
     * 持仓方向（long/short）
     */
    @Column(name = "pos_side", length = 20)
    private String posSide;

    /**
     * 杠杆倍数
     */
    @Column(name = "lever", precision = 10, scale = 2)
    private BigDecimal lever;

    /**
     * 原始成本金额（USDT）
     */
    @Column(name = "original_amount", precision = 20, scale = 8)
    private BigDecimal originalAmount;

    /**
     * 预估总占用资金(保证金+开仓手续费+平仓手续费)
     */
    @Column(name = "estimated_total_capital", precision = 20, scale = 8)
    private BigDecimal estimatedTotalCapital;

    /**
     * 风控等级（由交易风格决定）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    /**
     * 关联的TradeAction ID
     * 用于记录该风控订单是由哪个TradeAction产生的
     */
    @Column(name = "action_id")
    private Long actionId;

    /**
     * 便利构造方法
     */
    public RiskControlOrder(String originalOrderId, Long apiKeyId, Long actionId, String symbol,
                            OrderType orderType, OrderSide side, BigDecimal quantity,
                            BigDecimal price, String orderSource, RiskLevel riskLevel) {
        this.originalOrderId = originalOrderId;
        this.apiKeyId = apiKeyId;
        this.actionId = actionId;
        this.symbol = symbol;
        this.orderType = orderType;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.orderSource = orderSource;
        this.riskLevel = riskLevel;
        this.auditStatus = AuditStatus.PENDING;
        this.createTime = LocalDateTime.now();
    }

    /**
     * 完整构造方法（包含止盈止损）
     */
    public RiskControlOrder(String originalOrderId, Long apiKeyId, Long actionId, String symbol,
                            OrderType orderType, OrderSide side, BigDecimal quantity,
                            BigDecimal price, BigDecimal takeProfitPrice, BigDecimal stopLossPrice,
                            String orderSource, RiskLevel riskLevel) {
        this.originalOrderId = originalOrderId;
        this.apiKeyId = apiKeyId;
        this.actionId = actionId;
        this.symbol = symbol;
        this.orderType = orderType;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.takeProfitPrice = takeProfitPrice;
        this.stopLossPrice = stopLossPrice;
        this.orderSource = orderSource;
        this.riskLevel = riskLevel;
        this.auditStatus = AuditStatus.PENDING;
        this.createTime = LocalDateTime.now();
    }

    /**
     * 完整构造方法（包含所有风控审核必要字段）
     */
    public RiskControlOrder(String originalOrderId, Long apiKeyId, Long actionId, String symbol,
                            OrderType orderType, OrderSide side, BigDecimal quantity,
                            BigDecimal price, BigDecimal takeProfitPrice, BigDecimal stopLossPrice,
                            String posSide, BigDecimal lever, BigDecimal originalAmount,
                            String orderSource, RiskLevel riskLevel) {
        this.originalOrderId = originalOrderId;
        this.apiKeyId = apiKeyId;
        this.actionId = actionId;
        this.symbol = symbol;
        this.orderType = orderType;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.takeProfitPrice = takeProfitPrice;
        this.stopLossPrice = stopLossPrice;
        this.posSide = posSide;
        this.lever = lever;
        this.originalAmount = originalAmount;
        this.orderSource = orderSource;
        this.riskLevel = riskLevel;
        this.auditStatus = AuditStatus.PENDING;
        this.createTime = LocalDateTime.now();
    }

    /**
     * 审核通过
     */
    public void approve(String auditor) {
        this.auditStatus = AuditStatus.APPROVED;
        this.auditor = auditor;
        this.auditTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 审核驳回
     */
    public void reject(String auditor, String rejectionReason) {
        this.auditStatus = AuditStatus.REJECTED;
        this.auditor = auditor;
        this.rejectionReason = rejectionReason;
        this.auditTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    /**
     * JPA生命周期回调，自动设置更新时间
     */
    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}
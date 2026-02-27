package com.crypto.trade.entity;

import com.crypto.trade.enums.OpenCloseType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * TradeAction
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_trade_actions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeAction {

    /**
     * 订单类型
     */
    @Column(name = "order_type", length = 50)
    OrderType orderType;
    /**
     * 杠杆倍数
     */
    @Column(name = "lever")
    Integer lever;
    /**
     * 成本金额
     */
    @Column(name = "amount")
    BigDecimal amount;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /**
     * 关联的调用记录ID (LlmCallRecord)
     */
    @Column(name = "record_id", nullable = false)
    private Long recordId;
    /**
     * 使用的模型ID
     */
    @Column(name = "model_id", length = 100)
    private String modelId;
    /**
     * API Key ID
     */
    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;
    /**
     * 动作类型 (QUERY, BUY, SELL, HOLD, ATTENTION)
     */
    @Column(name = "action_type", length = 50)
    private String actionType;
    /**
     * 优先级
     */
    @Column(name = "priority")
    private Integer priority;
    /**
     * 目标合约代码
     */
    @Column(name = "inst_id", length = 50)
    private String instId;
    /**
     * 持仓方向 (LONG, SHORT)
     */
    @Column(name = "pos_side", length = 20)
    private String posSide;
    /**
     * 时间周期 (1m, 5m, 1H, etc.)
     */
    @Column(name = "timeframe", length = 20)
    private String timeframe;
    /**
     * 查询条数限制
     */
    @Column(name = "query_limit")
    private Integer queryLimit;
    /**
     * 价格
     */
    @Column(name = "price", precision = 20, scale = 8)
    private BigDecimal price;
    /**
     * 数量
     */
    @Column(name = "quantity", precision = 20, scale = 8)
    private BigDecimal quantity;
    /**
     * 止盈价格
     */
    @Column(name = "take_profit", precision = 20, scale = 8)
    private BigDecimal takeProfit;
    /**
     * 止损价格
     */
    @Column(name = "stop_loss", precision = 20, scale = 8)
    private BigDecimal stopLoss;
    /**
     * 置信度 (0-100)
     */
    @Column(name = "confidence")
    private Integer confidence;
    /**
     * 决策理由
     */
    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    // ========== 执行结果字段 ==========
    /**
     * 状态 (PENDING, EXECUTED, FAILED, IGNORED)
     */
    @Column(name = "status", length = 20)
    private String status;
    /**
     * 创建时间
     */
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
    /**
     * 更新时间
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;
    /**
     * 执行时间
     */
    @Column(name = "executed_time")
    private LocalDateTime executedTime;
    /**
     * 执行耗时(毫秒)
     */
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;
    /**
     * 订单ID
     */
    @Column(name = "order_id", length = 100)
    private String orderId;

    // ========== 重放支持字段 ==========
    /**
     * 实际成交价
     */
    @Column(name = "executed_price", precision = 20, scale = 8)
    private BigDecimal executedPrice;
    /**
     * 实际成交数量
     */
    @Column(name = "executed_size", precision = 20, scale = 8)
    private BigDecimal executedSize;
    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    /**
     * 父动作ID - 重放时指向原始动作
     */
    @Column(name = "parent_action_id")
    private Long parentActionId;
    /**
     * 执行来源 - INITIAL=首次执行, REPLAY=重放执行
     */
    @Column(name = "execution_source", length = 20)
    private String executionSource = "INITIAL";
    /**
     * 重放次数
     */
    @Column(name = "replay_count")
    private Integer replayCount = 0;
    /**
     * 开平仓类型
     */
    @Column(name = "open_close", length = 20)
    private OpenCloseType openClose;

    /**
     * 风控状态
     * PENDING - 待审核(MANUAL模式下创建时的初始状态)
     * APPROVED - 审核通过(风控审核通过,可以执行交易)
     * REJECTED - 审核驳回(风控审核驳回,不执行交易)
     * BYPASSED - 绕过风控(AUTO模式下自动下单,不经过风控审核)
     */
    @Column(name = "risk_control_status", length = 20)
    private String riskControlStatus;

    /**
     * 关联的风控订单ID
     * 用于关联RiskControlOrder表,建立TradeAction与风控审核的关系
     */
    @Column(name = "risk_control_id")
    private Long riskControlId;

    /**
     * 创建重放动作
     */
    public static TradeAction createReplay(TradeAction original) {
        TradeAction replay = new TradeAction();
        replay.setRecordId(original.getRecordId());
        replay.setParentActionId(original.getId());
        replay.setExecutionSource("REPLAY");
        replay.setReplayCount(original.getReplayCount() + 1);
        replay.setActionType(original.getActionType());
        replay.setApiKeyId(original.getApiKeyId());
        replay.setModelId(original.getModelId());
        replay.setInstId(original.getInstId());
        replay.setOrderId(original.getOrderId());

        replay.setPrice(original.getPrice());
        replay.setLever(original.getLever());
        replay.setOrderType(original.getOrderType());
        replay.setAmount(original.getAmount());

        replay.setQuantity(original.getQuantity());
        replay.setConfidence(original.getConfidence());
        replay.setReasoning("重放执行 - " + original.getReasoning());
        replay.setStatus("PENDING");
        return replay;
    }

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
        if (updateTime == null) {
            updateTime = LocalDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
        if (executionSource == null) {
            executionSource = "INITIAL";
        }
        if (replayCount == null) {
            replayCount = 0;
        }
        // 只有BUY/SELL动作才需要设置默认的openClose值
        if (openClose == null && ("BUY".equals(actionType) || "SELL".equals(actionType))) {
            openClose = OpenCloseType.OPEN;
        }
    }

    // ========== 状态更新方法 ==========

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }

    /**
     * 标记为执行成功
     */
    public void markAsExecuted(String orderId, BigDecimal executedPrice, BigDecimal executedSize, Long executionTimeMs) {
        this.status = "SUCCESS";
        this.executedTime = LocalDateTime.now();
        this.orderId = orderId;
        this.executedPrice = executedPrice;
        this.executedSize = executedSize;
        this.executionTimeMs = executionTimeMs;
    }

    /**
     * 标记为执行失败
     */
    public void markAsFailed(String errorMessage, Long executionTimeMs) {
        this.status = "FAILED";
        this.executedTime = LocalDateTime.now();
        this.errorMessage = errorMessage;
        this.executionTimeMs = executionTimeMs;
    }
}

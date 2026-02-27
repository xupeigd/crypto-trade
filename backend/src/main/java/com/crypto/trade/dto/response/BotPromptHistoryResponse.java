package com.crypto.trade.dto.response;

import com.crypto.trade.model.SegmentModel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * BotPromptHistoryResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BotPromptHistoryResponse {

    /**
     * 决策ID
     */
    @JsonProperty("decisionId")
    private String decisionId;

    /**
     * API Key ID
     */
    @JsonProperty("apiKeyId")
    private Long apiKeyId;

    /**
     * 模型名称
     */
    @JsonProperty("modelName")
    private String modelName;

    /**
     * 创建时间（毫秒时间戳）
     */
    @JsonProperty("createdTime")
    private Long createdTime;

    // === 核心决策字段（与action item对应） ===

    /**
     * 决策动作 (BUY/SELL/HOLD/QUERY/ATTENTION)
     */
    @JsonProperty("action")
    private String action;

    /**
     * 目标合约代码
     */
    @JsonProperty("instId")
    private String instId;

    /**
     * 决策价格
     */
    @JsonProperty("price")
    private BigDecimal price;

    /**
     * 决策数量
     */
    @JsonProperty("quantity")
    private BigDecimal quantity;

    /**
     * 止盈价格
     */
    @JsonProperty("takeProfit")
    private BigDecimal takeProfit;

    /**
     * 止损价格
     */
    @JsonProperty("stopLoss")
    private BigDecimal stopLoss;

    /**
     * 置信度 (0-100)
     */
    @JsonProperty("confidence")
    private BigDecimal confidence;

    /**
     * 决策推理过程
     */
    @JsonProperty("reasoning")
    private String reasoning;

    /**
     * 时间周期
     */
    @JsonProperty("timeframe")
    private String timeframe;

    /**
     * 持仓方向 (long/short)
     */
    @JsonProperty("posSide")
    private String posSide;

    /**
     * 开平仓类型集合（英文逗号分隔，如："open,close"）
     * 从TradeAction表中查询该决策对应的所有开平仓类型并去重
     */
    @JsonProperty("openCloses")
    private String openCloses;

    /**
     * 优先级（工具调用时使用）
     */
    @JsonProperty("priority")
    private Integer priority;

    /**
     * 查询条数（工具调用时使用）
     */
    @JsonProperty("limitCount")
    private Integer limitCount;

    // === 元数据字段 ===

    /**
     * 任务ID（用于异步任务跟踪）
     */
    @JsonProperty("taskId")
    private String taskId;

    /**
     * 任务提交时间（毫秒时间戳）
     */
    @JsonProperty("submittedTime")
    private Long submittedTime;

    /**
     * 任务开始处理时间（毫秒时间戳）
     */
    @JsonProperty("startedTime")
    private Long startedTime;

    /**
     * Prompt内容（简化显示）
     */
    @JsonProperty("promptContent")
    private String promptContent;

    /**
     * 处理状态 (PENDING/PROCESSED/ERROR)
     */
    @JsonProperty("status")
    private String status;

    /**
     * 错误信息
     */
    @JsonProperty("errorMessage")
    private String errorMessage;

    /**
     * 是否已执行
     */
    @JsonProperty("executed")
    private Boolean executed;

    /**
     * 执行时间
     */
    @JsonProperty("executionTime")
    private LocalDateTime executionTime;

    /**
     * 父级记录ID（用于多轮对话的父子关系）
     */
    @JsonProperty("parentId")
    private Long parentId;

    /**
     * 聊天会话ID（关联ChatSession）
     * 用于获取ChatMessage消息列表
     */
    @JsonProperty("chatSessionId")
    private Long chatSessionId;

    /**
     * 调用来源
     * SCHEDULED - 定时任务触发
     * DIRECT - API直接调用
     * MANUAL - 页面手动提交
     */
    @JsonProperty("callSource")
    private String callSource;

    /**
     * 执行结果
     */
    @JsonProperty("executionResult")
    private String executionResult;

    /**
     * 处理时间（毫秒）
     */
    @JsonProperty("processingTimeMs")
    private Long processingTimeMs;

    /**
     * 输入数据 - 总权益（从LlmCallRecord的promptContent解析）
     */
    @JsonProperty("inputTotalEquity")
    private BigDecimal inputTotalEquity;

    /**
     * 输入数据 - 可用余额
     */
    @JsonProperty("inputAvailableBalance")
    private BigDecimal inputAvailableBalance;

    /**
     * 输入数据 - 已用保证金
     */
    @JsonProperty("inputUsedMargin")
    private BigDecimal inputUsedMargin;

    /**
     * 输入数据 - 未实现盈亏
     */
    @JsonProperty("inputUnrealizedPnl")
    private BigDecimal inputUnrealizedPnl;

    /**
     * 输入数据 - 保证金使用率
     */
    @JsonProperty("inputMarginRatio")
    private BigDecimal inputMarginRatio;

    /**
     * 关联订单数量
     */
    @JsonProperty("relatedOrderCount")
    private Integer relatedOrderCount;

    /**
     * 结构化的Prompt段落数据
     * <p>
     * 注意：此字段不再序列化到前端，前端应从promptContent字段动态解析segments
     * 使用切割格式：=== {title} ===
     * </p>
     */
    @JsonIgnore
    @JsonProperty("segments")
    private List<SegmentModel> segments;

    /**
     * 结构化的AI响应段落数据
     * <p>
     * 将AI模型的responseContent解析为结构化的段落列表，包括：
     * <ul>
     *   <li>思考过程（从&lt;thinking&gt;标签提取）</li>
     *   <li>决策JSON（清理markdown标记后的JSON）</li>
     * </ul>
     * </p>
     */
    @JsonProperty("responseSegments")
    private List<SegmentModel> responseSegments;

    /**
     * 风控状态
     * PASSED - 通过
     * BLOCKED - 阻止
     * WARNING - 警告
     */
    @JsonProperty("riskControlStatus")
    private String riskControlStatus;

    /**
     * 交易动作状态
     * PENDING - 待执行
     * EXECUTING - 执行中
     * SUCCESS - 成功
     * FAILED - 失败
     */
    @JsonProperty("tradeActionStatus")
    private String tradeActionStatus;
}

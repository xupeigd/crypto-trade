package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LlmCallRecord
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_llm_call_records", indexes = {
        @Index(name = "idx_api_key_id", columnList = "api_key_id"),
        @Index(name = "idx_session_id", columnList = "session_id"),
        @Index(name = "idx_parent_id", columnList = "parent_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_call_start_time", columnList = "call_start_time")
})
public class LlmCallRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 父调用ID，用于多轮对话
     * 第一轮对话为null，后续轮次指向上一轮的id
     */
    @Column(name = "parent_id")
    private Long parentId;

    /**
     * 调用来源
     * SCHEDULED - 定时任务触发
     * DIRECT - API直接调用
     * MANUAL - 页面手动提交
     */
    @Column(name = "call_source", length = 20)
    private String callSource;

    /**
     * API密钥ID
     */
    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    /**
     * 会话ID，用于关联ChatSession
     * 存储ChatSession.sessionId的值
     * 建立LlmCallRecord与ChatSession的关联关系
     */
    @Column(name = "session_id")
    private Long sessionId;

    /**
     * 用户消息ID（关联ChatMessage, role=user）
     * 用于关联用户发送的Prompt内容
     */
    @Column(name = "user_message_id")
    private Long userMessageId;

    /**
     * 助手消息ID（关联ChatMessage, role=assistant）
     * 用于关联AI助手的响应内容
     */
    @Column(name = "assistant_message_id")
    private Long assistantMessageId;

    /**
     * 使用的模型名称
     */
    @Column(name = "model_name", length = 100)
    private String modelName;

    /**
     * 调用次数（针对该API Key的累计调用次数）
     */
    @Column(name = "call_count")
    private Integer callCount;

    /**
     * 对话轮次,第1轮为1,第2轮为2...
     * 用于标识多轮对话的顺序
     */
    @Column(name = "round_number")
    private Integer roundNumber;

    /**
     * 会话状态
     * PROCESSING - 处理中
     * COMPLETED - 已完成
     * TERMINATED - 已终止(达到最大轮次)
     * NEED_MORE_INFO - 需要更多信息
     * ERROR_STATE - 错误状态
     */
    @Column(name = "conversation_state", length = 50)
    private String conversationState;

    /**
     * 调用开始时间
     */
    @Column(name = "call_start_time")
    private LocalDateTime callStartTime;

    /**
     * 调用结束时间
     */
    @Column(name = "call_end_time")
    private LocalDateTime callEndTime;

    /**
     * 处理时长(毫秒)
     * 已废弃,保留用于向后兼容,建议使用细分耗时字段
     */
    @Deprecated
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    /**
     * Prompt生成耗时(毫秒)
     * 记录生成完整Prompt所需的时间,包括模板渲染、数据组装等
     */
    @Column(name = "prompt_generation_time_ms")
    private Long promptGenerationTimeMs;

    /**
     * 大模型调用耗时(毫秒)
     * 记录AI模型API调用的实际耗时,从请求发送到响应返回的时间
     */
    @Column(name = "llm_call_time_ms")
    private Long llmCallTimeMs;

    /**
     * 后置动作耗时(毫秒)
     * 记录响应解析、决策提取、结果保存等后置处理时间
     */
    @Column(name = "post_action_time_ms")
    private Long postActionTimeMs;

    /**
     * 调用状态
     * PROCESSING - 处理中
     * SUCCESS - 成功
     * FAILED - 失败
     */
    @Column(name = "status", length = 20)
    private String status;

    /**
     * 响应内容
     * 仅在ClearVisionUtils处理后的响应与原始响应不同时存储处理后的响应
     * 若ClearVisionUtils处理无变化，则为空
     * 原始响应通过 assistantMessageId 关联 ChatMessage 获取
     */
    @Column(name = "response_content", columnDefinition = "LONGTEXT")
    private String responseContent;

    @Column(name = "flow_nodes_json", columnDefinition = "LONGTEXT")
    private String flowNodesJson;


    /**
     * 错误信息（调用失败时记录）
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 决策动作（BUY/SELL/HOLD）
     */
    @Column(name = "decision_action", length = 20)
    private String decisionAction;

    /**
     * 目标合约
     */
    @Column(name = "target_inst_id", length = 50)
    private String targetInstId;

    /**
     * 决策价格
     */
    @Column(name = "decision_price", precision = 20, scale = 8)
    private BigDecimal decisionPrice;

    /**
     * 决策数量
     */
    @Column(name = "decision_quantity")
    private BigDecimal decisionQuantity;

    /**
     * 决策置信度
     */
    @Column(name = "decision_confidence", precision = 5, scale = 2)
    private BigDecimal decisionConfidence;

    /**
     * 是否已执行
     */
    @Column(name = "is_executed")
    private Boolean isExecuted;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 创建前自动设置创建时间
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * 更新前自动设置更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 标记为处理中
     * 注意：不再设置callStartTime，避免覆盖AI实际调用的开始时间
     */
    public void markAsProcessing() {
        this.status = "PROCESSING";
        // 删除: this.callStartTime = LocalDateTime.now();
        // callStartTime应该在创建记录时设置，或在AI调用前明确设置
    }

    /**
     * 标记为成功
     */
    public void markAsSuccess() {
        this.status = "SUCCESS";
        this.callEndTime = LocalDateTime.now();
        if (this.callStartTime != null) {
            this.processingTimeMs = java.time.Duration.between(this.callStartTime, this.callEndTime).toMillis();
        }
    }

    /**
     * 标记为失败
     */
    public void markAsFailed(String errorMessage) {
        this.status = "FAILED";
        this.callEndTime = LocalDateTime.now();
        this.errorMessage = errorMessage;
        if (this.callStartTime != null) {
            this.processingTimeMs = java.time.Duration.between(this.callStartTime, this.callEndTime).toMillis();
        }
    }

    /**
     * 设置决策信息
     */
    public void setDecisionInfo(String action, String instId, BigDecimal price, BigDecimal quantity, BigDecimal confidence) {
        this.decisionAction = action;
        this.targetInstId = instId;
        this.decisionPrice = price;
        this.decisionQuantity = quantity;
        this.decisionConfidence = confidence;
    }
}

package com.crypto.trade.service.conversation;

import com.crypto.trade.model.AccountDetailModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ConversationContext
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationContext {
    /**
     * 会话ID(Long类型,关联ChatSession)
     */
    private Long sessionId;

    /**
     * 第一轮调用记录ID
     */
    private Long firstCallRecordId;

    /**
     * 当前父调用记录ID(用于下一轮的parentId)
     */
    private Long currentParentId;

    /**
     * API密钥ID
     */
    private Long apiKeyId;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 账户详情
     */
    private AccountDetailModel accountDetail;

    /**
     * 持仓详情
     */
    private String positionDetails;

    /**
     * 当前AI响应
     */
    private String currentAiResponse;

    /**
     * 当前轮次
     */
    private int currentRound;

    /**
     * 最大轮数
     */
    @Builder.Default
    private Integer maxRounds = 10;

    /**
     * 会话状态
     * INITIALIZED - 已初始化
     * IN_PROGRESS - 进行中
     * WAITING_FOR_TOOL - 等待工具执行
     * COMPLETED - 已完成
     * TERMINATED - 已终止
     * ERROR - 错误
     */
    @Builder.Default
    private ConversationState state = ConversationState.INITIALIZED;

    /**
     * 历史对话记录
     */
    @Builder.Default
    private List<RoundRecord> history = new ArrayList<>();

    /**
     * 当前轮次的Prompt
     */
    private String currentPrompt;

    /**
     * 当前轮次的响应
     */
    private String currentResponse;

    /**
     * 当前轮次的工具执行结果
     */
    @Builder.Default
    private List<ToolExecutionResult> toolResults = new ArrayList<>();

    /**
     * 累计工具调用次数
     */
    @Builder.Default
    private Integer totalToolCalls = 0;

    /**
     * 是否为复杂决策
     */
    @Builder.Default
    private Boolean isComplex = false;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 自定义数据
     */
    @Builder.Default
    private Map<String, Object> customData = new HashMap<>();

    /**
     * 总处理时间(毫秒)
     */
    private long totalProcessingTime;

    /**
     * 当前轮的动作列表
     */
    private List<ActionParser.ParsedAction> actions;

    /**
     * 所有工具执行结果累积
     */
    private List<ToolExecutionResult> allToolResults;

    /**
     * 【新增】最近连续相同的QUERY工具调用记录
     * 用于检测AI模型陷入死循环（连续调用相同的QUERY）
     * 记录格式：instId_timeframe_limit
     */
    private String lastQuerySignature;

    /**
     * 【新增】连续相同QUERY的次数
     */
    private int consecutiveSameQueryCount = 0;

    /**
     * 完整构造函数（向后兼容）
     */
    public ConversationContext(
            Long sessionId,
            Long firstCallRecordId,
            Long apiKeyId,
            String modelName,
            AccountDetailModel accountDetail,
            String positionDetails,
            String initialAiResponse,
            long initialProcessingTime,
            List<ActionParser.ParsedAction> actions
    ) {
        this.sessionId = sessionId;
        this.firstCallRecordId = firstCallRecordId;
        this.currentParentId = firstCallRecordId;
        this.apiKeyId = apiKeyId;
        this.modelName = modelName;
        this.accountDetail = accountDetail;
        this.positionDetails = positionDetails;
        this.currentAiResponse = initialAiResponse;
        this.currentResponse = initialAiResponse;
        this.currentRound = 1;
        this.totalProcessingTime = initialProcessingTime;
        this.actions = actions;
        this.allToolResults = new ArrayList<>();
        this.toolResults = new ArrayList<>();
        this.history = new ArrayList<>();
        this.state = ConversationState.INITIALIZED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 判断是否达到最大轮次
     */
    public boolean isMaxRoundsReached() {
        return currentRound >= maxRounds;
    }

    /**
     * 判断是否有工具调用
     */
    public boolean hasToolCalls() {
        return toolResults != null && !toolResults.isEmpty();
    }

    /**
     * 判断是否为终止状态
     */
    public boolean isTerminated() {
        return state == ConversationState.TERMINATED
                || state == ConversationState.ERROR;
    }

    /**
     * 判断是否为完成状态
     */
    public boolean isCompleted() {
        return state == ConversationState.COMPLETED;
    }

    /**
     * 更新上下文,准备进入下一轮（向后兼容）
     *
     * @param newAiResponse  新的AI响应
     * @param newToolResults 新的工具执行结果
     * @param newParentId    新的父记录ID
     * @return 更新后的上下文
     */
    public ConversationContext updateForNextRound(
            String newAiResponse,
            List<ToolExecutionResult> newToolResults,
            Long newParentId
    ) {
        this.currentAiResponse = newAiResponse;
        this.currentResponse = newAiResponse;
        this.currentRound++;
        this.allToolResults.addAll(newToolResults);
        this.toolResults = new ArrayList<>(newToolResults);
        this.totalToolCalls += newToolResults.size();
        this.currentParentId = newParentId;
        this.updatedAt = LocalDateTime.now();

        // 添加到历史记录
        this.history.add(RoundRecord.builder()
                .round(this.currentRound)
                .response(newAiResponse)
                .toolResults(new ArrayList<>(newToolResults))
                .timestamp(System.currentTimeMillis())
                .build());

        return this;
    }

    /**
     * 会话状态枚举
     */
    public enum ConversationState {
        INITIALIZED,
        IN_PROGRESS,
        WAITING_FOR_TOOL,
        COMPLETED,
        TERMINATED,
        ERROR
    }

    /**
     * 轮次记录
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoundRecord {
        private Integer round;
        private String prompt;
        private String response;
        private List<ToolExecutionResult> toolResults;
        private Long timestamp;
    }
}

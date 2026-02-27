package com.crypto.trade.service.conversation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * RoundResult
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoundResult {
    /**
     * AI响应内容
     */
    private String aiResponse;

    /**
     * 工具执行结果列表
     */
    private List<ToolExecutionResult> toolResults;

    /**
     * 是否应该继续下一轮对话
     */
    private boolean shouldContinue;

    /**
     * 本轮处理的LlmCallRecord ID
     */
    private Long recordId;

    /**
     * 本轮处理耗时(毫秒)
     */
    private long processingTimeMs;

    /**
     * 构造函数 - 用于无法继续的场景
     */
    public RoundResult(String aiResponse, List<ToolExecutionResult> toolResults, boolean shouldContinue) {
        this.aiResponse = aiResponse;
        this.toolResults = toolResults != null ? toolResults : Collections.emptyList();
        this.shouldContinue = shouldContinue;
    }

    /**
     * 创建终止结果
     */
    public static RoundResult termination(String aiResponse) {
        return new RoundResult(aiResponse, Collections.emptyList(), false);
    }

    /**
     * 创建继续结果
     */
    public static RoundResult continueRound(String aiResponse, List<ToolExecutionResult> toolResults, Long recordId, long processingTimeMs) {
        return new RoundResult(aiResponse, toolResults, true, recordId, processingTimeMs);
    }
}

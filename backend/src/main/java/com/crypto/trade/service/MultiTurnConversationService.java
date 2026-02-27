package com.crypto.trade.service;

import com.crypto.trade.entity.LlmCallRecord;
import com.crypto.trade.service.conversation.HistorySummarizer;
import com.crypto.trade.service.conversation.ToolExecutionResult;
import com.crypto.trade.service.prompt.MultiTurnPromptTemplate;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.util.AiResponseParserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MultiTurnConversationService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiTurnConversationService {

    // 默认最大轮次(简单决策)
    public static final int DEFAULT_MAX_ROUNDS = 5;

    // 复杂决策最大轮次
    public static final int COMPLEX_MAX_ROUNDS = 15;

    // 向后兼容:保留原有MAX_ROUNDS常量,指向默认值
    public static final int MAX_ROUNDS = DEFAULT_MAX_ROUNDS;

    private final LlmCallRecordService llmCallRecordService;
    private final HistorySummarizer historySummarizer;
    private final MultiTurnPromptTemplate promptTemplate;

    /**
     * 判断是否需要多轮对话
     *
     * @param aiResponse AI响应内容
     * @return true表示需要多轮对话
     */
    public boolean needsMultiTurn(String aiResponse) {
        // 检测是否包含工具调用
        return hasToolCall(aiResponse);
    }

    /**
     * 检测响应是否包含工具调用(公开访问)
     */
    public boolean hasToolCall(String aiResponse) {
        if (null == aiResponse) {
            return false;
        }
        return aiResponse.contains("\"action\"")
                && (aiResponse.contains("QUERY") || aiResponse.contains("query"));
    }

    /**
     * 动态确定最大轮数
     * 根据决策复杂度和当前轮次确定合理的最大轮数
     *
     * @param aiResponse   AI响应
     * @param currentRound 当前轮次
     * @param toolResults  工具执行结果
     * @return 最大轮数
     */
    public int determineMaxRounds(String aiResponse, int currentRound, List<ToolExecutionResult> toolResults) {
        // 复杂决策判断标准
        boolean isComplex = isComplexDecision(aiResponse, currentRound, toolResults);

        if (isComplex) {
            log.debug("识别为复杂决策,使用最大轮数: {}", COMPLEX_MAX_ROUNDS);
            return COMPLEX_MAX_ROUNDS;
        } else {
            log.debug("识别为简单决策,使用默认轮数: {}", DEFAULT_MAX_ROUNDS);
            return DEFAULT_MAX_ROUNDS;
        }
    }

    /**
     * 判断是否为复杂决策
     * 复杂决策特征:
     * 1. 工具调用次数 >= 3
     * 2. AI响应长度 > 2000字符
     * 3. 当前轮次 >= 3
     *
     * @param aiResponse   AI响应
     * @param currentRound 当前轮次
     * @param toolResults  工具执行结果
     * @return true表示复杂决策
     */
    private boolean isComplexDecision(String aiResponse, int currentRound, List<ToolExecutionResult> toolResults) {
        // 标准1: 工具调用次数较多
        int toolCallCount = (toolResults != null) ? toolResults.size() : 0;
        if (toolCallCount >= 3) {
            log.info("复杂决策判断: 工具调用次数 >= 3 (实际: {})", toolCallCount);
            return true;
        }

        // 标准2: AI响应长度较长(表示需要更多思考)
        int responseLength = (aiResponse != null) ? aiResponse.length() : 0;
        if (responseLength > 2000) {
            log.info("复杂决策判断: AI响应长度 > 2000 (实际: {})", responseLength);
            return true;
        }

        // 标准3: 当前轮次较深(表示问题复杂)
        if (currentRound >= 3) {
            log.info("复杂决策判断: 当前轮次 >= 3 (实际: {})", currentRound);
            return true;
        }

        log.debug("未达到复杂决策标准 - 工具调用:{}, 响应长度:{}, 轮次:{}",
                toolCallCount, responseLength, currentRound);
        return false;
    }

    /**
     * 判断是否应该继续对话
     *
     * @param aiResponse   AI响应
     * @param currentRound 当前轮次
     * @param toolResults  工具执行结果
     * @return true表示应该继续
     */
    public boolean shouldContinue(String aiResponse, int currentRound, List<ToolExecutionResult> toolResults) {
        // 条件1: 达到动态确定的最大轮次
        int maxRounds = determineMaxRounds(aiResponse, currentRound, toolResults);
        if (currentRound >= maxRounds) {
            log.info("达到最大轮次限制,终止对话 - 当前轮次: {}, 最大轮次: {}", currentRound, maxRounds);
            return false;
        }

        // 条件2: AI给出最终决策
        if (hasFinalDecision(aiResponse)) {
            log.info("AI给出最终决策,终止对话");
            return false;
        }

        // 条件3: AI明确表示完成
        if (isAIDeclaringCompletion(aiResponse)) {
            log.info("AI明确表示完成,终止对话");
            return false;
        }

        // 条件4: 有工具调用需要执行
        if (hasToolCall(aiResponse)) {
            log.debug("检测到工具调用,继续对话 - 轮次: {}", currentRound);
            return true;
        }

        // 默认: 如果没有明确信号,继续
        return true;
    }

    /**
     * 检测AI响应是否包含最终决策
     */
    public boolean hasFinalDecision(String aiResponse) {
        if (null == aiResponse) {
            return false;
        }

        // 检测BUY/SELL/HOLD决策
        String[] finalActions = {"BUY", "SELL", "HOLD"};
        for (String action : finalActions) {
            if (aiResponse.contains("\"action\":\"" + action + "\"")
                    || aiResponse.contains("\"action\": \"" + action + "\"")) {
                // 确保没有同时包含QUERY(工具调用)
                if (!hasToolCall(aiResponse)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 检测AI是否明确表示完成
     */
    public boolean isAIDeclaringCompletion(String aiResponse) {
        if (null == aiResponse) {
            return false;
        }

        // 提取thinking内容
        String thinking = AiResponseParserUtil.extractThinkingProcess(aiResponse);
        if (null == thinking) {
            return false;
        }

        // 检测完成信号关键词
        String[] completionSignals = {
                "信息已足够",
                "可以做出决策",
                "无需更多数据",
                "基于现有信息",
                "完成分析"
        };

        for (String signal : completionSignals) {
            if (thinking.contains(signal)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 构建下一轮prompt
     *
     * @param sessionId         会话ID
     * @param currentAiResponse 当前AI响应
     * @param recentToolResults 最近工具执行结果
     * @param currentRound      当前轮次
     * @return 下一轮prompt
     */
    public String buildNextTurnPrompt(
            Long sessionId,
            String currentAiResponse,
            List<ToolExecutionResult> recentToolResults,
            int currentRound
    ) {
        // 1. 获取历史对话记录
        List<LlmCallRecord> history = llmCallRecordService.getConversationHistory(sessionId);

        // 2. 生成历史摘要
        String historySummary = historySummarizer.summarize(history);

        // 3. 获取PromptContext（需要从第一轮记录中恢复）
        PromptContext context = buildPromptContextFromHistory(history);

        // 4. 构建下一轮prompt
        return promptTemplate.buildNextTurnPrompt(
                historySummary,
                currentAiResponse,
                recentToolResults,
                currentRound,
                context  // 传入PromptContext
        );
    }

    /**
     * 从历史记录中恢复 PromptContext
     * <p>
     * 从第一轮（parentId为null）的LlmCallRecord中恢复apiKeyId，
     * 用于构建业务上下文（账户、持仓、委托订单等）
     * </p>
     *
     * @param history 历史对话记录
     * @return PromptContext
     */
    private PromptContext buildPromptContextFromHistory(List<LlmCallRecord> history) {
        if (history == null || history.isEmpty()) {
            log.warn("历史记录为空，无法构建PromptContext");
            return PromptContext.builder().build();
        }

        // 从第一轮记录中获取 apiKeyId
        LlmCallRecord firstRecord = history.get(0);
        Long apiKeyId = firstRecord.getApiKeyId();

        if (apiKeyId == null) {
            log.warn("第一轮记录缺少apiKeyId");
            return PromptContext.builder().build();
        }

        // 构建 PromptContext（使用 builder 模式）
        // 修复多轮会话bug：设置source为REPLAY，让后续轮次知道这是多轮对话
        PromptContext context = PromptContext.builder()
                .apiKeyId(apiKeyId)
                .source("REPLAY")
                .build();

        log.debug("从历史记录恢复PromptContext - apiKeyId: {}, source: REPLAY", apiKeyId);
        return context;
    }

//    /**
//     * 创建下一轮调用记录
//     *
//     * @param sessionId     会话ID
//     * @param parentId      父调用ID
//     * @param roundNumber   轮次号
//     * @param apiKeyId      API密钥ID
//     * @param modelName     模型名称
//     * @param promptContent Prompt内容
//     * @return 创建的记录
//     */
//    @Transactional(rollbackFor = Exception.class)
//    public LlmCallRecord createNextTurnRecord(
//            String sessionId,
//            Long parentId,
//            int roundNumber,
//            Long apiKeyId,
//            String modelName,
//            String promptContent
//    ) {
//        log.info("创建第{}轮调用记录 - sessionId: {}, parentId: {}", roundNumber, sessionId, parentId);
//
//        // 获取父记录的调用来源,多轮对话后续轮次继承第一轮的来源类型
//        String parentCallSource = llmCallRecordService.getCallSourceById(parentId);
//
//        return llmCallRecordService.createCallRecord(
//                apiKeyId,
//                modelName,
//                promptContent,
//                sessionId,
//                parentId,
//                roundNumber,
//                parentCallSource
//        );
//    }

    /**
     * 更新会话为完成状态
     *
     * @param sessionId 会话ID(Long类型)
     */
    @Transactional(rollbackFor = Exception.class)
    public void markConversationCompleted(Long sessionId) {
        llmCallRecordService.markConversationAsCompleted(sessionId);
    }

    /**
     * 更新会话为终止状态
     *
     * @param sessionId 会话ID(Long类型)
     * @param reason    终止原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void markConversationTerminated(Long sessionId, String reason) {
        llmCallRecordService.markConversationAsTerminated(sessionId, reason);
    }

    /**
     * 获取会话的当前轮次
     *
     * @param sessionId 会话ID(Long类型)
     * @return 轮次数
     */
    public int getCurrentRound(Long sessionId) {
        return llmCallRecordService.getConversationRoundCount(sessionId);
    }
}

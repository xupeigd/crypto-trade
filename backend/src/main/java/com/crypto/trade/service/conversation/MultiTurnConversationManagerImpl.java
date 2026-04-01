package com.crypto.trade.service.conversation;

import com.crypto.trade.entity.LlmCallRecord;
import com.crypto.trade.service.BotPromptCacheService;
import com.crypto.trade.service.ChatService;
import com.crypto.trade.service.LlmCallRecordService;
import com.crypto.trade.service.MultiTurnConversationService;
import com.crypto.trade.service.UnifiedModelFactory;
import com.crypto.trade.service.prompt.MultiTurnPromptTemplate;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.util.AiResponseParserUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MultiTurnConversationManagerImpl
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class MultiTurnConversationManagerImpl
        implements MultiTurnConversationManager {

    /**
     * 会话上下文缓存
     * Key: sessionId
     * Value: ConversationContext
     */
    private final Map<Long, ConversationContext> contextCache = new ConcurrentHashMap<>();
    @Autowired
    UnifiedModelFactory unifiedModelFactory;
    @Autowired
    ToolExecutionManager toolExecutionManager;
    @Autowired
    LlmCallRecordService llmCallRecordService;
    @Autowired
    HistorySummarizer historySummarizer;
    @Autowired
    MultiTurnPromptTemplate multiTurnPromptTemplate;
    @Autowired
    ChatService chatService;
    @Autowired
    MultiTurnConversationService multiTurnConversationService;
    @Autowired
    BotPromptCacheService botPromptCacheService;

    @Override
    public ConversationContext startConversation(ConversationRequest request) {
        log.info("启动多轮会话 - apiKeyId: {}, modelName: {}",
                request.getApiKeyId(), request.getModelName());

        // 1. 创建ChatSession
        Long sessionId = llmCallRecordService.createChatSession(
                request.getSessionName(), request.getModelName());

        // 2. 创建初始会话上下文
        ConversationContext context = ConversationContext.builder()
                .sessionId(sessionId)
                .currentRound(0)
                .maxRounds(request.getMaxRounds())
                .state(ConversationContext.ConversationState.INITIALIZED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 3. 保存自定义参数
        if (request.getCustomParams() != null) {
            context.setCustomData(new HashMap<>(request.getCustomParams()));
        }

        // 4. 保存API密钥ID和模型名称到自定义数据
        context.getCustomData().put("apiKeyId", request.getApiKeyId());
        context.getCustomData().put("modelName", request.getModelName());
        context.getCustomData().put("callSource", request.getCallSource());

        // 5. 缓存上下文
        contextCache.put(sessionId, context);

        // 6. 执行第一轮对话
        return executeFirstRound(context, request);
    }

    @Override
    public ConversationContext continueConversation(Long sessionId, String currentResponse) {
        log.info("继续多轮会话 - sessionId: {}, round: {}", sessionId, "?");

        // 1. 获取会话上下文
        ConversationContext context = getContext(sessionId);
        if (context == null) {
            log.error("会话不存在 - sessionId: {}", sessionId);
            throw new IllegalArgumentException("会话不存在: " + sessionId);
        }

        // 2. 更新上下文状态
        context.setState(ConversationContext.ConversationState.IN_PROGRESS);
        context.setCurrentResponse(currentResponse);
        context.setUpdatedAt(LocalDateTime.now());

        // 3. 解析当前响应
        ActionParser.ActionPack actionPack = parseResponse(currentResponse);

        // 4. 判断是否有工具调用
        if (ActionParser.containsToolCall(actionPack)) {
            // 执行工具调用
            return executeToolCalls(context, actionPack);
        } else {
            // 无工具调用，会话完成
            completeConversation(sessionId, currentResponse);
            return context;
        }
    }

    @Override
    public ConversationContext getContext(Long sessionId) {
        // 优先从缓存获取
        ConversationContext context = contextCache.get(sessionId);

        // 如果缓存中没有，从数据库恢复
        if (context == null) {
            context = restoreContextFromDatabase(sessionId);
            if (context != null) {
                contextCache.put(sessionId, context);
            }
        }

        return context;
    }

    @Override
    public void completeConversation(Long sessionId, String finalResponse) {
        log.info("完成多轮会话 - sessionId: {}", sessionId);

        ConversationContext context = getContext(sessionId);
        if (context == null) {
            log.error("会话不存在 - sessionId: {}", sessionId);
            return;
        }

        // 更新状态
        context.setState(ConversationContext.ConversationState.COMPLETED);
        context.setCurrentResponse(finalResponse);
        context.setUpdatedAt(LocalDateTime.now());

        // 标记会话完成
        multiTurnConversationService.markConversationCompleted(sessionId);

        // 从缓存中移除
        contextCache.remove(sessionId);
    }

    @Override
    public void terminateConversation(Long sessionId, String reason) {
        log.warn("终止多轮会话 - sessionId: {}, reason: {}", sessionId, reason);

        ConversationContext context = getContext(sessionId);
        if (context == null) {
            log.error("会话不存在 - sessionId: {}", sessionId);
            return;
        }

        // 更新状态
        context.setState(ConversationContext.ConversationState.TERMINATED);
        context.setErrorMessage(reason);
        context.setUpdatedAt(LocalDateTime.now());

        // 标记会话终止
        multiTurnConversationService.markConversationTerminated(sessionId, reason);

        // 从缓存中移除
        contextCache.remove(sessionId);
    }

    @Override
    public boolean shouldContinue(ConversationContext context, String aiResponse) {
        // 1. 检查是否达到最大轮次
        if (context.isMaxRoundsReached()) {
            log.info("达到最大轮次 - sessionId: {}, round: {}",
                    context.getSessionId(), context.getCurrentRound());
            return false;
        }

        // 2. 检查是否有工具调用
        ActionParser.ActionPack actionPack = parseResponse(aiResponse);
        if (ActionParser.containsToolCall(actionPack)) {
            return true;
        }

        // 3. 检查是否有最终决策
        if (hasFinalDecision(aiResponse)) {
            log.info("检测到最终决策 - sessionId: {}", context.getSessionId());
            return false;
        }

        return false;
    }

    /**
     * 执行第一轮对话
     */
    private ConversationContext executeFirstRound(ConversationContext context,
                                                  ConversationRequest request) {
        log.info("执行第一轮对话 - sessionId: {}", context.getSessionId());

        // 1. 提前创建用户消息，以支持PROCESSING状态时返回promptContent
        Long userMessageId = chatService.createUserMessage(
                context.getSessionId(),
                request.getInitialPrompt()
        );

        // 2. 创建调用记录，传入userMessageId
        String callSource = (String) context.getCustomData().get("callSource");
        LlmCallRecord callRecord = llmCallRecordService.createCallRecord(
                request.getApiKeyId(),
                request.getModelName(),
                context.getSessionId(),
                null,
                callSource,
                userMessageId,
                null
        );

        // 2.1 清理缓存，确保PROCESSING状态时promptContent能立即返回
        botPromptCacheService.clearCache();

        // 3. 保存第一条记录ID到上下文
        context.setFirstCallRecordId(callRecord.getId());
        context.setCurrentParentId(callRecord.getId());

        // 4. 调用AI模型
        String aiResponse;
        long llmCallStart = System.currentTimeMillis();
        long llmCallTime;
        try {
            aiResponse = unifiedModelFactory.callWithModel(
                    request.getInitialPrompt(),
                    request.getModelName()
            );
            llmCallTime = System.currentTimeMillis() - llmCallStart;
        } catch (UnifiedModelFactory.ModelCallException e) {
            log.error("AI模型调用失败 - sessionId: {}", context.getSessionId(), e);
            context.setState(ConversationContext.ConversationState.ERROR);
            context.setErrorMessage(e.getMessage());
            return context;
        }

        // 5. 解析响应
        ActionParser.ActionPack actionPack = parseResponse(aiResponse);

        // 6. 保存响应（只创建助手消息，用户消息已提前创建）
        saveResponse(context, callRecord.getId(), userMessageId,
                aiResponse, llmCallTime);

        // 7. 更新上下文
        context.setCurrentRound(1);
        context.setCurrentPrompt(request.getInitialPrompt());
        context.setCurrentResponse(aiResponse);
        context.setUpdatedAt(LocalDateTime.now());

        // 8. 添加到历史记录
        context.getHistory().add(ConversationContext.RoundRecord.builder()
                .round(1)
                .prompt(request.getInitialPrompt())
                .response(aiResponse)
                .timestamp(System.currentTimeMillis())
                .build());

        // 9. 判断是否需要继续
        if (shouldContinue(context, aiResponse)) {
            // 需要多轮对话
            context.setState(ConversationContext.ConversationState.WAITING_FOR_TOOL);
            return executeToolCalls(context, actionPack);
        } else {
            // 单轮对话完成
            context.setState(ConversationContext.ConversationState.COMPLETED);
            return context;
        }
    }

    /**
     * 执行工具调用
     */
    private ConversationContext executeToolCalls(ConversationContext context,
                                                 ActionParser.ActionPack actionPack) {
        log.info("执行工具调用 - sessionId: {}, toolCount: {}",
                context.getSessionId(),
                actionPack.getActions() != null ? actionPack.getActions().size() : 0);

        // 1. 获取API密钥ID
        Long apiKeyId = (Long) context.getCustomData().get("apiKeyId");

        // 2. 执行工具调用
        List<ToolExecutionResult> toolResults = toolExecutionManager.executeTools(
                actionPack.getActions(),
                context.getSessionId(),
                apiKeyId
        );

        // 3. 更新上下文
        context.setToolResults(toolResults);
        context.setTotalToolCalls(context.getTotalToolCalls() + toolResults.size());

        // 4. 判断是否达到最大轮次
        if (context.isMaxRoundsReached()) {
            terminateConversation(context.getSessionId(), "达到最大轮次");
            return context;
        }

        // 5. 构建下一轮Prompt
        String nextPrompt = buildNextPrompt(context);

        // 6. 调用AI模型
        return executeNextRound(context, nextPrompt);
    }

    /**
     * 执行下一轮对话
     */
    private ConversationContext executeNextRound(ConversationContext context, String nextPrompt) {
        log.info("执行第{}轮对话 - sessionId: {}", context.getCurrentRound() + 1, context.getSessionId());

        // 1. 提前创建用户消息，以支持PROCESSING状态时返回promptContent
        Long userMessageId = chatService.createUserMessage(
                context.getSessionId(),
                nextPrompt
        );

        // 2. 创建调用记录，传入userMessageId
        Long apiKeyId = (Long) context.getCustomData().get("apiKeyId");
        String modelName = (String) context.getCustomData().get("modelName");
        String callSource = (String) context.getCustomData().get("callSource");

        LlmCallRecord callRecord = llmCallRecordService.createCallRecord(
                apiKeyId,
                modelName,
                context.getSessionId(),
                context.getCurrentParentId(),
                context.getCurrentRound() + 1,
                callSource,
                userMessageId,
                null
        );

        // 2.1 清理缓存，确保PROCESSING状态时promptContent能立即返回
        botPromptCacheService.clearCache();

        // 3. 调用AI模型
        String aiResponse;
        long llmCallStart = System.currentTimeMillis();
        long llmCallTime;
        try {
            aiResponse = unifiedModelFactory.callWithModel(nextPrompt, modelName);
            llmCallTime = System.currentTimeMillis() - llmCallStart;
        } catch (UnifiedModelFactory.ModelCallException e) {
            log.error("AI模型调用失败 - sessionId: {}, round: {}", context.getSessionId(), context.getCurrentRound(), e);
            context.setState(ConversationContext.ConversationState.ERROR);
            context.setErrorMessage(e.getMessage());
            return context;
        }

        // 4. 解析响应
        ActionParser.ActionPack actionPack = parseResponse(aiResponse);

        // 5. 保存响应（只创建助手消息，用户消息已提前创建）
        saveResponse(context, callRecord.getId(), userMessageId, aiResponse, llmCallTime);

        // 6. 更新上下文
        context.setCurrentRound(context.getCurrentRound() + 1);
        context.setCurrentPrompt(nextPrompt);
        context.setCurrentResponse(aiResponse);
        context.setCurrentParentId(callRecord.getId());
        context.setUpdatedAt(LocalDateTime.now());

        // 7. 添加到历史记录
        context.getHistory().add(ConversationContext.RoundRecord.builder()
                .round(context.getCurrentRound())
                .prompt(nextPrompt)
                .response(aiResponse)
                .toolResults(new ArrayList<>(context.getToolResults()))
                .timestamp(System.currentTimeMillis())
                .build());

        // 8. 判断是否继续
        if (shouldContinue(context, aiResponse)) {
            return executeToolCalls(context, actionPack);
        } else {
            context.setState(ConversationContext.ConversationState.COMPLETED);
            return context;
        }
    }

    /**
     * 构建下一轮Prompt
     */
    private String buildNextPrompt(ConversationContext context) {
        log.debug("构建下一轮Prompt - sessionId: {}, round: {}",
                context.getSessionId(), context.getCurrentRound());

        // 1. 获取历史记录
        List<LlmCallRecord> history = llmCallRecordService.getConversationHistory(
                context.getSessionId()
        );

        // 2. 生成历史摘要
        String historySummary = historySummarizer.summarize(history);

        // 3. 构建PromptContext
        PromptContext promptContext = buildPromptContext(context);

        // 4. 构建下一轮Prompt
        return multiTurnPromptTemplate.buildNextTurnPrompt(
                historySummary,
                context.getCurrentResponse(),
                context.getToolResults(),
                context.getCurrentRound(),
                promptContext
        );
    }

    /**
     * 保存响应
     * <p>
     * 用户消息已在创建LlmCallRecord之前创建，此处只创建助手消息并更新assistantMessageId
     * </p>
     */
    private void saveResponse(ConversationContext context, Long callRecordId,
                              Long userMessageId, String response, long llmCallTime) {
        try {
            // 1. 更新调用记录状态和响应内容
            llmCallRecordService.updateCallRecordSuccessInNewTransaction(
                    callRecordId,
                    response,
                    null, null, null, null, null,
                    LocalDateTime.now(),
                    null,  // promptGenerationTimeMs
                    llmCallTime,
                    null,  // postActionTimeMs
                    userMessageId,
                    null   // assistantMessageId 暂时为空
            );

            // 2. 创建助手消息
            Long assistantMessageId = chatService.createAssistantMessage(
                    context.getSessionId(),
                    response,
                    llmCallTime
            );

            // 3. 更新调用记录的助手消息ID
            if (assistantMessageId != null) {
                llmCallRecordService.updateCallRecordSuccessInNewTransaction(
                        callRecordId,
                        null,
                        null, null, null, null, null,
                        null,
                        null, null, null,
                        null,
                        assistantMessageId
                );
            }

            // 4. 清理缓存，确保更新后的数据能立即返回
            botPromptCacheService.clearCache();

        } catch (Exception e) {
            log.error("保存响应失败 - callRecordId: {}", callRecordId, e);
        }
    }

    /**
     * 解析响应
     */
    private ActionParser.ActionPack parseResponse(String aiResponse) {
        try {
            String jsonContent = AiResponseParserUtil.parseAiResponse(aiResponse, false)
                    .getJsonContent();
            return ActionParser.parseActionPack(jsonContent);
        } catch (Exception e) {
            log.error("解析AI响应失败", e);
            return null;
        }
    }

    /**
     * 判断是否有最终决策
     */
    private boolean hasFinalDecision(String aiResponse) {
        String[] finalActions = {"BUY", "SELL", "HOLD"};
        for (String action : finalActions) {
            if (aiResponse.contains("\"action\":\"" + action + "\"")
                    || aiResponse.contains("\"action\": \"" + action + "\"")) {
                return !aiResponse.contains("QUERY");
            }
        }
        return false;
    }

    /**
     * 构建PromptContext
     */
    private PromptContext buildPromptContext(ConversationContext context) {
        Long apiKeyId = (Long) context.getCustomData().get("apiKeyId");

        return PromptContext.builder()
                .apiKeyId(apiKeyId)
                .source("MULTI_TURN")
                .build();
    }

    /**
     * 从数据库恢复会话上下文
     */
    private ConversationContext restoreContextFromDatabase(Long sessionId) {
        try {
            List<LlmCallRecord> records = llmCallRecordService.getConversationHistory(sessionId);
            if (records.isEmpty()) {
                return null;
            }

            // 从第一条记录恢复基本信息
            LlmCallRecord firstRecord = records.get(0);

            ConversationContext context = ConversationContext.builder()
                    .sessionId(sessionId)
                    .currentRound(records.size())
                    .state(ConversationContext.ConversationState.IN_PROGRESS)
                    .createdAt(firstRecord.getCreatedAt())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // 恢复自定义数据
            Map<String, Object> customData = new HashMap<>();
            customData.put("apiKeyId", firstRecord.getApiKeyId());
            customData.put("modelName", firstRecord.getModelName());
            context.setCustomData(customData);

            return context;

        } catch (Exception e) {
            log.error("从数据库恢复会话上下文失败 - sessionId: {}", sessionId, e);
            return null;
        }
    }
}

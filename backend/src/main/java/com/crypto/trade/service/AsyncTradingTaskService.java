package com.crypto.trade.service;

import com.crypto.trade.config.BotFlowNodeConfig;
import com.crypto.trade.dto.AiResponseParseResult;
import com.crypto.trade.dto.cex.model.CexPosition;
import com.crypto.trade.dto.response.BotPromptGenerateResponse;
import com.crypto.trade.dto.response.FlowNodeStatusResponse;
import com.crypto.trade.entity.*;
import com.crypto.trade.enums.TradeBalanceSnapshotSource;
import com.crypto.trade.model.AccountDetailModel;
import com.crypto.trade.model.PositionModel;
import com.crypto.trade.model.request.BotCallModelRequest;
import com.crypto.trade.repository.ChatMessageRepository;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.conversation.*;
import com.crypto.trade.service.unified.UnifiedBalanceService;
import com.crypto.trade.service.unified.UnifiedPositionService;
import com.crypto.trade.util.AiResponseParserUtil;
import com.crypto.trade.util.ClearVisionUtils;
import com.crypto.trade.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AsyncTradingTaskService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
@EnableAsync
public class AsyncTradingTaskService {

    @Autowired
    AiDecisionService aiDecisionService;
    @Autowired
    UnifiedBalanceService unifiedBalanceService;
    @Autowired
    UnifiedPositionService unifiedPositionService;
    @Autowired
    LlmCallRecordService llmCallRecordService;
    @Autowired
    UnifiedModelFactory unifiedModelFactory;
    @Autowired
    TradeActionProcessor tradeActionProcessor;
    @Autowired
    TradeBalanceSnapshotService tradeBalanceSnapshotService;
    @Autowired
    TradeActionService tradeActionService;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    TradingConfigProperties tradingConfigProperties;
    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    ChatService chatService;
    @Autowired
    ToolResultFormatter toolResultFormatter;
    @Autowired
    ChatMessageRepository chatMessageRepository;
    @Autowired
    AttentionQueueService attentionQueueService;
    @Autowired
    BotFlowNodeConfig botFlowNodeConfig;

    /**
     * 异步执行AI模型调用任务
     *
     * @param request 调用请求
     * @return CompletableFuture 包含任务ID
     * <p>
     * 注意: 这里不使用@Transactional,因为@Async方法在新线程中执行,
     * 而@Transactional默认使用线程绑定的事务上下文,会导致问题。
     * 事务管理已在具体的方法中处理(如LlmCallRecordService的方法)
     */
    @SuppressWarnings("UnusedReturnValue")
    @Async("tradingTaskExecutor")
    public CompletableFuture<String> executeAsyncTradingTask(BotCallModelRequest request) {
        String taskId = request.getTaskId() != null ? request.getTaskId() : UUID.randomUUID().toString();

        // 获取模型名称
        String modelName = request.getModelName();
        if (modelName == null || modelName.trim().isEmpty()) {
            modelName = unifiedModelFactory.getDefaultModelConfig().getModelId();
        }

        // 创建ChatSession并获取其ID
        Long chatSessionId = llmCallRecordService.createChatSession(null, modelName);

        // 为了向后兼容,保留String类型的sessionId用于内部逻辑传递
        String sessionId = chatSessionId.toString();

        // 判断调用来源:
        // taskId以"SCHEDULED_"开头为定时任务调用
        // taskId以"ASYNC_"开头为页面手动提交
        // 其他为API直接调用
        String callSource;
        if (taskId != null && taskId.startsWith("SCHEDULED_")) {
            callSource = "SCHEDULED";
            log.debug("识别为定时任务调用 - taskId: {}", taskId);
        } else if (taskId != null && taskId.startsWith("ASYNC_")) {
            callSource = "MANUAL";
            log.debug("识别为手动触发调用 - taskId: {}", taskId);
        } else {
            callSource = "DIRECT";
            log.debug("识别为API直接调用 - taskId: {}", taskId);
        }

        // [重构] 1. 先创建 LlmCallRecord 记录(使用Long类型的sessionId)
        LlmCallRecord callRecord = llmCallRecordService.createCallRecord(request.getApiKeyId(), modelName,
                chatSessionId, null, callSource, null, null);

        Long callRecordId = callRecord.getId();
        log.info("创建 LlmCallRecord - id: {}, taskId: {}, apiKeyId: {}, chatSessionId: {}",
                callRecordId, taskId, request.getApiKeyId(), chatSessionId);

        // 更新账户余额快照的recordId
        // 必须使用请求中提供的 balanceSnapshotId 进行精确更新，避免并发问题
        try {
            Long snapshotId = request.getBalanceSnapshotId();

            if (snapshotId != null) {
                // 使用请求中提供的 snapshotId 进行精确更新
                boolean updated = tradeBalanceSnapshotService.updateRecordId(snapshotId, callRecordId);
                if (updated) {
                    log.debug("使用指定snapshotId更新recordId成功 - snapshotId: {}, recordId: {}, apiKeyId: {}",
                            snapshotId, callRecordId, request.getApiKeyId());
                } else {
                    log.warn("使用指定snapshotId更新recordId失败 - snapshotId: {}, recordId: {}, apiKeyId: {}",
                            snapshotId, callRecordId, request.getApiKeyId());
                }
            } else {
                // 未提供 balanceSnapshotId，记录警告并跳过更新
                // 这通常意味着前端未正确传递 /generate-prompt 接口返回的 snapshotId
                log.warn("未提供balanceSnapshotId，跳过快照recordId更新 - apiKeyId: {}, recordId: {}, 建议检查前端是否正确传递balanceSnapshotId参数",
                        request.getApiKeyId(), callRecordId);
            }
        } catch (Exception e) {
            // 快照更新失败不应该影响AI调用流程
            log.error("更新快照recordId时发生异常 - apiKeyId: {}, recordId: {}",
                    request.getApiKeyId(), callRecordId, e);
        }

        try {
            log.info("开始执行异步AI交易任务 - sessionId: {}, apiKeyId: {}", sessionId, request.getApiKeyId());

            // 2. 执行AI模型调用(不再需要TradeDecision)
            executeAiCall(request, callRecordId, chatSessionId, null);

            log.info("异步AI交易任务完成 - sessionId: {}", sessionId);

            return CompletableFuture.completedFuture(taskId);

        } catch (Exception e) {
            // 详细的错误日志，包含任务来源、sessionId、apiKeyId等信息
            log.error("【异步任务失败】任务来源: {}, sessionId: {}, apiKeyId: {}, callRecordId: {}, 错误类型: {}, 错误信息: {}",
                    callSource, sessionId, request.getApiKeyId(), callRecordId,
                    e.getClass().getSimpleName(), e.getMessage(), e);

            // 更新记录为失败状态
            llmCallRecordService.updateCallRecordFailed(callRecordId, e.getMessage());

            return CompletableFuture.completedFuture("FAILED");
        }
    }

    /**
     * 执行异步AI交易任务(包含Prompt生成耗时)
     *
     * @param request                BOT触发请求
     * @param promptGenerationTimeMs Prompt生成耗时(毫秒),可为null
     * @return CompletableFuture 包含任务ID
     */
    @SuppressWarnings("UnusedReturnValue")
    @Async("tradingTaskExecutor")
    public CompletableFuture<String> executeAsyncTradingTask(BotCallModelRequest request, Long promptGenerationTimeMs) {
        String taskId = request.getTaskId() != null ? request.getTaskId() : UUID.randomUUID().toString();

        // 获取模型名称
        String modelName = request.getModelName();
        if (null == modelName || modelName.trim().isEmpty()) {
            modelName = unifiedModelFactory.getDefaultModelConfig().getModelId();
        }

        // 创建ChatSession并获取其ID
        Long chatSessionId = llmCallRecordService.createChatSession(null, modelName);

        // 为了向后兼容,保留String类型的sessionId用于内部逻辑传递
        String sessionId = chatSessionId.toString();

        // 判断调用来源:
        // taskId以"TRIGGER_"开头为BOT触发
        // taskId以"SCHEDULED_"开头为定时任务调用
        // taskId以"ASYNC_"开头为页面手动提交
        // 其他为API直接调用
        String callSource;
        if (null != taskId && taskId.startsWith("SCHEDULED_")) {
            callSource = "SCHEDULED";
            log.debug("识别为API直接调用 - taskId: {}", taskId);
        } else if (null != taskId && taskId.startsWith("ASYNC_")) {
            callSource = "MANUAL";
            log.debug("识别为手动触发调用 - taskId: {}", taskId);
        } else if (null != taskId && taskId.startsWith("TRIGGER_")) {
            callSource = "DIRECT";
            log.debug("识别为BOT直接触发调用 - taskId: {}", taskId);
        } else {
            callSource = "DIRECT";
            log.info("识别为API直接调用 - taskId: {}", taskId);
        }

        // [重构] 1. 先创建 LlmCallRecord 记录(使用Long类型的sessionId)
        LlmCallRecord callRecord = llmCallRecordService.createCallRecord(request.getApiKeyId(), modelName,
                chatSessionId, null, callSource, null, null);

        Long callRecordId = callRecord.getId();
        log.info("创建 LlmCallRecord - id: {}, taskId: {}, apiKeyId: {}, chatSessionId: {}",
                callRecordId, taskId, request.getApiKeyId(), chatSessionId);

        // 更新账户余额快照的recordId
        try {
            Long snapshotId = request.getBalanceSnapshotId();

            if (null != snapshotId) {
                boolean updated = tradeBalanceSnapshotService.updateRecordId(snapshotId, callRecordId);
                if (updated) {
                    log.debug("使用指定snapshotId更新recordId成功 - snapshotId: {}, recordId: {}, apiKeyId: {}",
                            snapshotId, callRecordId, request.getApiKeyId());
                } else {
                    log.warn("使用指定snapshotId更新recordId失败 - snapshotId: {}, recordId: {}, apiKeyId: {}",
                            snapshotId, callRecordId, request.getApiKeyId());
                }
            } else {
                log.warn("未提供balanceSnapshotId，跳过快照recordId更新 - apiKeyId: {}, recordId: {}",
                        request.getApiKeyId(), callRecordId);
            }
        } catch (Exception e) {
            log.error("更新快照recordId时发生异常 - apiKeyId: {}, recordId: {}",
                    request.getApiKeyId(), callRecordId, e);
        }

        try {
            log.info("开始执行异步AI交易任务(含Prompt耗时) - sessionId: {}, apiKeyId: {}, prompt耗时: {}ms",
                    sessionId, request.getApiKeyId(), promptGenerationTimeMs);

            // 2. 执行AI模型调用,传递Prompt生成耗时
            executeAiCall(request, callRecordId, chatSessionId, promptGenerationTimeMs);

            log.info("异步AI交易任务完成 - sessionId: {}", sessionId);

            return CompletableFuture.completedFuture(taskId);

        } catch (Exception e) {
            // 详细的错误日志
            log.error("【异步任务失败】任务来源: {}, sessionId: {}, apiKeyId: {}, callRecordId: {}, 错误类型: {}, 错误信息: {}",
                    callSource, sessionId, request.getApiKeyId(), callRecordId,
                    e.getClass().getSimpleName(), e.getMessage(), e);

            // 更新记录为失败状态
            llmCallRecordService.updateCallRecordFailed(callRecordId, e.getMessage());

            return CompletableFuture.completedFuture("FAILED");
        }
    }

    /**
     * 执行AI调用(原方法,保持向后兼容)
     *
     * @param request       请求对象
     * @param callRecordId  调用记录ID
     * @param chatSessionId ChatSession ID(Long类型)
     */
    private void executeAiCall(BotCallModelRequest request, Long callRecordId, Long chatSessionId) {
        executeAiCall(request, callRecordId, chatSessionId, null);
    }

    /**
     * 执行AI调用(包含Prompt生成耗时)
     *
     * @param request                请求对象
     * @param callRecordId           调用记录ID
     * @param chatSessionId          ChatSession ID(Long类型)
     * @param promptGenerationTimeMs Prompt生成耗时(毫秒),可为null
     */
    private void executeAiCall(BotCallModelRequest request, Long callRecordId, Long chatSessionId, Long promptGenerationTimeMs) {
        // 获取账户和持仓数据
        AccountDetailModel accountDetail = unifiedBalanceService.getAccountUsdtDetail(request.getApiKeyId());
        String positionDetails = aiDecisionService.getPositionDetails(request.getApiKeyId());
        List<FlowNodeStatusResponse> flowNodes = initializeFlowNodes(callRecordId);
        markFlowNodeRunning(callRecordId, flowNodes, "PROMPT_BUILD", "开始准备Prompt");

        // 如果没有提供promptContent，则自动生成
        String finalPromptContent = request.getPromptContent();
        if (finalPromptContent == null || finalPromptContent.trim().isEmpty()) {
            log.debug("未提供prompt内容，开始生成prompt - apiKeyId: {}", request.getApiKeyId());
            try {
                // 传递request中的attentions（用于Attention触发时避免从数据库查询不到数据）
                BotPromptGenerateResponse promptResponse = aiDecisionService.generatePromptOnly(request.getApiKeyId(), request.getAttentions());
                if (promptResponse.getSuccess()) {
                    finalPromptContent = promptResponse.getPromptContent();
                    log.debug("prompt生成成功 - 长度: {}", finalPromptContent.length());
                    markFlowNodeSuccess(callRecordId, flowNodes, "PROMPT_BUILD", "Prompt生成完成");
                } else {
                    log.error("prompt生成失败 - apiKeyId: {}, error: {}", request.getApiKeyId(), promptResponse.getErrorMessage());
                    markFlowNodeFailed(callRecordId, flowNodes, "PROMPT_BUILD", promptResponse.getErrorMessage());
                    throw new RuntimeException("prompt生成失败: " + promptResponse.getErrorMessage());
                }
            } catch (Exception e) {
                log.error("prompt生成异常 - apiKeyId: {}", request.getApiKeyId(), e);
                markFlowNodeFailed(callRecordId, flowNodes, "PROMPT_BUILD", e.getMessage());
                throw new RuntimeException("prompt生成异常: " + e.getMessage(), e);
            }
        } else {
            log.debug("使用prompt内容 - 长度: {}", finalPromptContent.length());
            markFlowNodeSuccess(callRecordId, flowNodes, "PROMPT_BUILD", "使用已有Prompt");
        }

        // [重构] 获取模型名称
        String modelName = request.getModelName();
        if (modelName == null || modelName.trim().isEmpty()) {
            modelName = unifiedModelFactory.getDefaultModelConfig().getModelId();
        }

        // [Bug修复] 立即创建用户消息并关联，确保prompt始终关联（即使后续AI调用失败）
        Long userMessageId = null;
        try {
            userMessageId = chatService.createUserMessage(chatSessionId, finalPromptContent);
            if (userMessageId != null) {
                llmCallRecordService.updateUserMessageId(callRecordId, userMessageId);
                log.debug("用户消息已创建并关联 - callRecordId: {}, chatSessionId: {}, userMessageId: {}", callRecordId, chatSessionId, userMessageId);
            }
        } catch (Exception e) {
            log.error("创建用户消息失败（不影响主流程） - callRecordId: {}, chatSessionId: {}", callRecordId, chatSessionId, e);
        }

        // 调用AI模型
        log.debug("执行AI调用 - 使用模型: '{}'", modelName);
        markFlowNodeRunning(callRecordId, flowNodes, "MODEL_CALL", "模型调用中");
        // 记录AI调用开始时间
        LocalDateTime callStartTime = LocalDateTime.now();
        long llmCallStart = System.currentTimeMillis();

        // 使用messages数组格式调用模型
        List<UnifiedModelFactory.Message> messages = new ArrayList<>();

        // 获取当前时间、大模型调用次数及上次调用时间
        LocalDateTime currentTime = LocalDateTime.now();
        Integer callCount = 1;
        LocalDateTime lastCallTime = null;

        try {
            Optional<LlmCallRecord> latestRecord = llmCallRecordService.getLatestRecord(request.getApiKeyId());
            if (latestRecord.isPresent()) {
                callCount = latestRecord.get().getCallCount() + 1;
                lastCallTime = latestRecord.get().getCallStartTime();
            }
        } catch (Exception e) {
            log.warn("获取最新调用记录失败，使用默认值 - apiKeyId: {}, error: {}", request.getApiKeyId(), e.getMessage());
        }

        // 构建system消息，包含当前时间、调用次数和上次调用时间
        String systemMessage = "#Role 你是一位严谨的加密货币量化交易专家。你的任务是根据我提供的实时 OHLC 数据和技术指标，结合#{策略建议} 指定的策略进行逻辑判断，并输出最终的交易决策。\n" +
                "#Invoke Info 当前时间: " + currentTime + "本次调用是第 " + callCount + " 次大模型调用";
        if (lastCallTime != null) {
            systemMessage += ", 上次调用时间: " + lastCallTime;
        }
        systemMessage += "\n";
        systemMessage += """
                # Constraints \n
                - 严禁任何推测，仅基于数据决策。
                
                - 如果没有明确信号，必须保持 HOLD 状态。
                
                """;

        messages.add(UnifiedModelFactory.Message.system(systemMessage));
        messages.add(UnifiedModelFactory.Message.user(finalPromptContent));
        String aiResponse;
        try {
            aiResponse = unifiedModelFactory.callWithMessages(messages, modelName);
        } catch (UnifiedModelFactory.ModelCallException e) {
            log.error("AI调用失败 - 模型: {}, 错误: {}", modelName, e.getMessage(), e);
            markFlowNodeFailed(callRecordId, flowNodes, "MODEL_CALL", e.getMessage());
            markFlowNodeSkipped(callRecordId, flowNodes, "RISK_CONTROL");
            markFlowNodeSkipped(callRecordId, flowNodes, "TRADE_ACTION");
            markFlowNodeSkipped(callRecordId, flowNodes, "COMPLETE");
            llmCallRecordService.updateCallRecordFailed(callRecordId, e.getMessage());
            throw new RuntimeException("AI调用失败: " + e.getMessage(), e);
        }
        markFlowNodeSuccess(callRecordId, flowNodes, "MODEL_CALL", "模型响应完成");

        long llmCallTimeMs = System.currentTimeMillis() - llmCallStart;
        log.debug("AI调用完成 - 耗时: {}ms", llmCallTimeMs);

        // 保存原始响应（仅用于数据库记录）
        String originalResponse = aiResponse;

        aiResponse = StringUtils.hasText(aiResponse) ? aiResponse.replaceAll(", *\\n *}", "\n}") : aiResponse;

        // ===== 后置处理开始 =====
        long postActionStart = System.currentTimeMillis();
        markFlowNodeRunning(callRecordId, flowNodes, "RISK_CONTROL", "开始风控与动作解析");

        // [新增] 检测是否包含工具调用,触发多轮对话
        try {
            String jsonContent = AiResponseParserUtil.parseAiResponse(aiResponse, false).getJsonContent();

            ActionParser.ActionPack actionPack = ActionParser.parseActionPack(jsonContent);

            // ClearVisionUtils处理幻觉
            ActionParser.ActionPack newActionPack = ClearVisionUtils.clearingIllusions(actionPack, request.getPositions());
            boolean responseModified = !Objects.equals(actionPack, newActionPack);
            actionPack = newActionPack;

            // [重构] 保存处理后的TradeAction，获得含id的TradeAction列表
            List<TradeAction> savedTradeActions = null;
            if (actionPack != null) {
                savedTradeActions = tradeActionService.saveTradeActions(callRecordId, request.getApiKeyId(), modelName, actionPack);
                log.debug("【异步第1轮】TradeAction保存完成，获得id - callRecordId: {}, actionCount: {}",
                        callRecordId, savedTradeActions != null ? savedTradeActions.size() : 0);
            }
            markFlowNodeSuccess(callRecordId, flowNodes, "RISK_CONTROL", "风控节点完成");

            // [重构] 将含id的TradeAction转换为新的ActionPack，并替换aiResponse
            if (savedTradeActions != null && !savedTradeActions.isEmpty()) {
                // 将含id的TradeAction转换为ParsedAction
                List<ActionParser.ParsedAction> actionsWithIds = savedTradeActions.stream()
                        .map(ActionParser::convertTradeActionToParsedAction)
                        .collect(Collectors.toList());

                // 创建新的ActionPack（含id）
                ActionParser.ActionPack actionPackWithIds = ActionParser.ActionPack.builder()
                        .actions(actionsWithIds)
                        .build();

                // 提取新的JSON
                String processedJson = objectMapper.writeValueAsString(actionPackWithIds);

                // 替换aiResponse中的actions部分
                aiResponse = aiResponse.replace(jsonContent, processedJson);

                // 更新actionPack引用，后续使用含id的版本
                actionPack = actionPackWithIds;

                log.debug("【异步第1轮】aiResponse已更新为含id的版本 - callRecordId: {}, 原始长度: {}, 新长度: {}",
                        callRecordId, jsonContent.length(), processedJson.length());
            }

            // ===== 后置处理结束 =====
            long postActionTimeMs = System.currentTimeMillis() - postActionStart;
            log.debug("后置处理完成 - 耗时: {}ms", postActionTimeMs);

            // [新增] 立即保存AI响应到独立事务（传递处理后响应和细分耗时）
            llmCallRecordService.updateCallRecordSuccessInNewTransaction(
                    callRecordId,
                    responseModified ? aiResponse : null,  // 处理后的响应（仅当有变化时）
                    null,  // decisionAction
                    null,  // targetInstId
                    null,  // decisionPrice
                    null,  // decisionQuantity
                    null,  // decisionConfidence
                    callStartTime,
                    promptGenerationTimeMs,  // Prompt生成耗时
                    llmCallTimeMs,            // LLM调用耗时
                    postActionTimeMs,         // 后置处理耗时
                    null,  // userMessageId（稍后设置）
                    null   // assistantMessageId（稍后设置）
            );
            log.debug("异步任务AI响应已保存 - callRecordId: {}, prompt耗时: {}ms, llm耗时: {}ms, post耗时: {}ms",
                    callRecordId, promptGenerationTimeMs, llmCallTimeMs, postActionTimeMs);

            // [新增] 创建ChatMessage记录用户消息和AI响应
            try {
                // 处理promptGenerationTimeMs可能为null的情况（当用户直接提供prompt时）
                long promptTime = (null != promptGenerationTimeMs) ? promptGenerationTimeMs : 0L;
                long totalProcessingTime = promptTime + llmCallTimeMs + postActionTimeMs;
                Long[] messageIds = chatService.createAiTradeMessagePair(chatSessionId, finalPromptContent, aiResponse, totalProcessingTime);
                // 注意：userMessageId已在方法开头声明，这里只更新值
                if (messageIds[0] != null) {
                    userMessageId = messageIds[0];
                }
                Long assistantMessageId = messageIds[1];
                log.debug("异步任务ChatMessage已创建 - chatSessionId: {}, callRecordId: {}, userMsgId: {}, assistantMsgId: {}",
                        chatSessionId, callRecordId, userMessageId, assistantMessageId);

                // 更新 LlmCallRecord，关联 messageId
                if (userMessageId != null || assistantMessageId != null) {
                    llmCallRecordService.updateCallRecordSuccessInNewTransaction(
                            callRecordId,
                            null,  // responseContent（已在之前设置）
                            null, null, null, null, null,
                            null,  // callStartTime
                            null, null, null,  // 耗时（已在之前设置）
                            userMessageId,
                            assistantMessageId
                    );
                    log.debug("异步任务LlmCallRecord已关联messageId - callRecordId: {}, userMsgId: {}, assistantMsgId: {}",
                            callRecordId, userMessageId, assistantMessageId);
                }
            } catch (Exception msgEx) {
                log.error("创建ChatMessage失败 - chatSessionId: {}, callRecordId: {}, 不影响主流程",
                        chatSessionId, callRecordId, msgEx);
            }

            if (ActionParser.containsToolCall(actionPack)) {
                log.info("【异步多轮】检测到工具调用,启动异步递归对话处理 - callRecordId: {}, chatSessionId: {}", callRecordId, chatSessionId);
                markFlowNodeSkipped(callRecordId, flowNodes, "TRADE_ACTION");
                markFlowNodeRunning(callRecordId, flowNodes, "COMPLETE", "进入多轮流程");

                // 执行异步递归处理(不再需要返回TradeDecision)
                processAsyncConversationRecursive(request, callRecordId, chatSessionId, modelName, finalPromptContent,
                        aiResponse, accountDetail, positionDetails, actionPack.getActions());
            } else {
                // 单轮对话场景:第一轮无工具调用(直接BUY/SELL/HOLD)
                assert actionPack != null;
                log.info("【异步单轮】第一轮无工具调用,直接执行交易 - callRecordId: {}, actionCount: {}",
                        callRecordId, actionPack.getActions() != null ? actionPack.getActions().size() : 0);
                markFlowNodeRunning(callRecordId, flowNodes, "TRADE_ACTION", "开始执行交易");

                try {
                    // 直接使用已解析的actionPack执行交易
                    if (!ActionParser.containsToolCall(actionPack)) {
                        log.debug("【异步单轮】调用TradeActionProcessor执行交易 - actionCount: {}",
                                actionPack.getActions() != null ? actionPack.getActions().size() : 0);

                        // 查询关联的TradeAction列表,用于后续状态更新
                        List<TradeAction> tradeActions = tradeActionService.findByRecordIdAndExecutionSource(
                                callRecordId, "INITIAL");

                        List<TradeExecutionResult> executionResults = tradeActionProcessor.processTradeActions(
                                actionPack, request.getApiKeyId(), tradeActions);

                        if (executionResults != null && !executionResults.isEmpty()) {
                            log.debug("【异步单轮】交易执行完成 - callRecordId: {}, resultCount: {}",
                                    callRecordId, executionResults.size());
                        }
                        markFlowNodeSuccess(callRecordId, flowNodes, "TRADE_ACTION", "交易动作执行完成");
                    } else {
                        log.warn("【异步单轮】ActionPack为null或包含工具调用,跳过交易执行 - callRecordId: {}, actionPack: {}",
                                callRecordId, actionPack);
                        markFlowNodeSkipped(callRecordId, flowNodes, "TRADE_ACTION");
                    }
                } catch (Exception tradeEx) {
                    log.error("【异步单轮】执行交易动作失败 - callRecordId: {}", callRecordId, tradeEx);
                    markFlowNodeFailed(callRecordId, flowNodes, "TRADE_ACTION", tradeEx.getMessage());
                }
                markFlowNodeRunning(callRecordId, flowNodes, "COMPLETE", "流程收尾中");
                markFlowNodeSuccess(callRecordId, flowNodes, "COMPLETE", "流程完成");
            }
        } catch (Exception e) {
            log.error("【异步多轮】工具调用检测失败 - callRecordId: {}", callRecordId, e);
            markFlowNodeFailed(callRecordId, flowNodes, "RISK_CONTROL", e.getMessage());
            markFlowNodeSkipped(callRecordId, flowNodes, "TRADE_ACTION");
            markFlowNodeFailed(callRecordId, flowNodes, "COMPLETE", e.getMessage());

            // 【修复】解析失败时，保存可能的响应内容
            if (aiResponse != null && !aiResponse.isEmpty()) {
                try {
                    // 创建助手消息保存响应
                    Long assistantMessageId = chatService.createAssistantMessage(chatSessionId, aiResponse, null);
                    if (assistantMessageId != null) {
                        llmCallRecordService.updateAssistantMessageId(callRecordId, assistantMessageId);
                        log.debug("【异步多轮】解析失败但响应已保存 - callRecordId: {}, assistantMsgId: {}", callRecordId, assistantMessageId);
                    }
                } catch (Exception saveEx) {
                    log.error("【异步多轮】保存失败响应时发生异常 - callRecordId: {}", callRecordId, saveEx);
                }
            }
            // 解析失败，继续正常流程
        }

    }


    /**
     * 根据会话ID查询任务状态
     * 注意: taskId现在使用sessionId代替
     */
    @Transactional(readOnly = true)
    public LlmCallRecord getTaskStatus(String taskId) {
        // 通过sessionId查询任务状态
        // taskId是字符串,需要转换为Long类型
        Long sessionIdLong = null;
        try {
            sessionIdLong = Long.parseLong(taskId);
        } catch (NumberFormatException e) {
            log.warn("无法将taskId转换为Long: {}", taskId);
            return null;
        }
        List<LlmCallRecord> records = llmCallRecordService.getConversationHistory(sessionIdLong);
        return records.isEmpty() ? null : records.get(0);
    }

    /**
     * [新增] 异步递归处理多轮对话
     * <p>
     * 为异步任务添加多轮会话支持，处理AI的QUERY工具调用。
     * 复用AiDecisionService的递归逻辑，但适配异步场景。
     * </p>
     *
     * @param request           请求对象
     * @param firstCallRecordId 第一轮调用记录ID
     * @param chatSessionId     ChatSession ID(Long类型)
     * @param modelName         模型名称
     * @param initialPrompt     初始prompt
     * @param initialAiResponse 初始AI响应
     * @param accountDetail     账户详情
     * @param positionDetails   持仓详情
     * @param actions           工具调用列表
     */
    private void processAsyncConversationRecursive(BotCallModelRequest request, Long firstCallRecordId,
                                                   Long chatSessionId, String modelName, String initialPrompt, String initialAiResponse,
                                                   AccountDetailModel accountDetail, String positionDetails, List<ActionParser.ParsedAction> actions) {

        log.info("【异步递归】开始处理多轮对话 - chatSessionId: {}, firstCallRecordId: {}", chatSessionId, firstCallRecordId);

        // 为了向后兼容,将Long类型的chatSessionId转换为String类型
        String sessionId = chatSessionId.toString();

        // 参数验证:actions不能为空
        if (actions == null || actions.isEmpty()) {
            log.warn("【异步递归】初始actions为空,无需处理 - chatSessionId: {}", chatSessionId);
            return;
        }

        try {
            // 初始化循环变量
            Long currentParentId = firstCallRecordId;
            String currentAiResponse = initialAiResponse;
            int currentRound = 1;
            long totalProcessingTime = 0;

            // 多轮对话循环（使用循环而非递归，避免异步栈溢出）
            while (currentRound <= MultiTurnConversationService.MAX_ROUNDS) {
                log.debug("【异步递归】第{}轮对话开始 - sessionId: {}, parentId: {}", currentRound, sessionId, currentParentId);

                // 循环内null检查:防止actions在后续轮次中被重新赋值为null
                if (actions.isEmpty()) {
                    log.warn("【异步递归】第{}轮actions为空,终止多轮对话 - sessionId: {}", currentRound, sessionId);
                    break;
                }

                // 1. 执行工具调用
                // 在执行工具之前，先判断是否为HOLD最终决策（无QUERY工具调用）
                ActionParser.ActionPack initialActionPack = ActionParser.parseActionPack(
                        AiResponseParserUtil.parseAiResponse(currentAiResponse, false).getJsonContent());

                // 如果只有HOLD决策（无QUERY工具调用），直接终止对话，不创建额外记录
                if (hasOnlyHoldDecision(initialActionPack)) {
                    log.info("【异步递归】第{}轮检测到HOLD决策（无需工具执行）,对话终止 - sessionId: {}", currentRound, sessionId);
                    llmCallRecordService.markConversationAsCompleted(chatSessionId);
                    updateCompleteNodeForSession(firstCallRecordId, "SUCCESS", "多轮会话完成");
                    break;
                }

                List<ToolExecutionResult> toolResults = new ArrayList<>();
                for (ActionParser.ParsedAction action : actions) {
                    try {
                        // 执行工具（QUERY）- 使用sessionId代替decision.getDecisionId()
                        ToolExecutionResult result = executeToolAsync(action, sessionId, request.getApiKeyId());
                        toolResults.add(result);

                        // 保存工具执行结果
                        saveToolActionResultAsync(sessionId, action, result);

                        if (!result.getSuccess()) {
                            log.warn("【异步递归】第{}轮工具执行失败 - actionType: {}, error: {}",
                                    currentRound, action.getAction(), result.getErrorMessage());
                        }
                    } catch (Exception e) {
                        log.error("【异步递归】第{}轮工具执行异常 - actionType: {}",
                                currentRound, action.getAction(), e);
                        toolResults.add(ToolExecutionResult.failure(
                                "工具执行异常: " + e.getMessage()
                        ));
                    }
                }

                log.debug("【异步递归】第{}轮工具执行完成 - 成功: {}/{}", currentRound, toolResults.stream()
                        .mapToLong(r -> r.getSuccess() ? 1 : 0).sum(), toolResults.size());

                // 2. 判断是否有最终决策（检查是否有工具调用）
                try {
                    ActionParser.ActionPack actionPack = ActionParser.parseActionPack(
                            AiResponseParserUtil.parseAiResponse(currentAiResponse, false).getJsonContent());

                    // ========== 新增：检查是否有最终决策 ==========
                    if (hasFinalDecision(actionPack)) {
                        log.info("【异步递归】第{}轮检测到最终决策,对话终止 - sessionId: {}", currentRound, sessionId);
                        llmCallRecordService.markConversationAsCompleted(chatSessionId);
                        updateCompleteNodeForSession(firstCallRecordId, "SUCCESS", "多轮会话完成");

                        // 创建snapshot和record（复用现有代码）
                        try {
                            // 1. 获取当前账户信息（用于记录最终决策时的账户状态）
                            AccountDetailModel currentAccountDetail = unifiedBalanceService.getAccountUsdtDetail(request.getApiKeyId());

                            // 2. 创建snapshot
                            ApiKey apiKey = apiKeyService.getDecryptedKey(request.getApiKeyId());
                            Double maxAccountBalance = tradingConfigProperties.getMaxAccountBalance();
                            BigDecimal maxAvailableAmount = (maxAccountBalance != null && maxAccountBalance > 0)
                                    ? new BigDecimal(maxAccountBalance)
                                    : null;

                            TradeBalanceSnapshot finalSnapshot = TradeBalanceSnapshotService.createSnapshotWithAiLimits(
                                    request.getApiKeyId(), apiKey.getCexName(), currentAccountDetail,
                                    TradeBalanceSnapshotSource.REPLAY.name(), maxAvailableAmount, null);

                            // 3. 保存snapshot并创建record
                            if (finalSnapshot != null && tradeBalanceSnapshotService.validateSnapshot(finalSnapshot)) {
                                TradeBalanceSnapshot savedSnapshot = tradeBalanceSnapshotService.saveSnapshot(finalSnapshot);

                                // 4. 更新snapshot.recordId（使用当前轮的recordId，不需要额外创建）
                                boolean updated = tradeBalanceSnapshotService.updateRecordId(
                                        savedSnapshot.getSnapshotId(), currentParentId);

                                if (updated) {
                                    log.debug("【异步递归】最终决策快照recordId更新成功 - snapshotId: {}, recordId: {}, round: {}",
                                            savedSnapshot.getSnapshotId(), currentParentId, currentRound);
                                } else {
                                    log.warn("【异步递归】最终决策快照recordId更新失败 - snapshotId: {}, recordId: {}",
                                            savedSnapshot.getSnapshotId(), currentParentId);
                                }
                            } else {
                                log.warn("【异步递归】最终决策快照验证失败或为空，跳过recordId更新");
                            }
                        } catch (Exception snapshotEx) {
                            log.error("【异步递归】创建最终决策快照失败（不影响主流程） - sessionId: {}", sessionId, snapshotEx);
                        }

                        break;
                    }
                    // ==============================================

                    // 修复bug：HOLD转换为ATTENTION后重复创建LlmCallRecord
                    // 检查是否只有非交易动作（QUERY/ATTENTION），这些动作的TradeAction已在第838行保存
                    else if (!ActionParser.containsToolCall(actionPack)) {
                        assert actionPack != null;
                        boolean hasTradingAction = actionPack.getActions().stream()
                                .anyMatch(action -> {
                                    String actionType = action.getAction() != null ? action.getAction().name() : null;
                                    return "BUY".equals(actionType) || "SELL".equals(actionType);
                                });

                        if (!hasTradingAction) {
                            // 只有QUERY/ATTENTION，不需要创建新的LlmCallRecord（TradeAction已在第838行保存）
                            log.debug("【异步递归】第{}轮仅有非交易动作(QUERY/ATTENTION),TradeAction已保存,对话终止 - sessionId: {}", currentRound, sessionId);
                            llmCallRecordService.markConversationAsCompleted(chatSessionId);
                            updateCompleteNodeForSession(firstCallRecordId, "SUCCESS", "多轮会话完成");
                            break;
                        }

                        // 继续原有逻辑：处理BUY/SELL最终决策...
                        log.info("【异步递归】第{}轮无工具调用,对话终止 - sessionId: {}", currentRound, sessionId);
                        llmCallRecordService.markConversationAsCompleted(chatSessionId);
                        updateCompleteNodeForSession(firstCallRecordId, "SUCCESS", "多轮会话完成");

                        // ✅ 修复P0级bug：在break前创建snapshot和record（针对最终BUY/SELL决策）
                        try {
                            // 1. 获取当前账户信息（用于记录最终决策时的账户状态）
                            AccountDetailModel currentAccountDetail = unifiedBalanceService.getAccountUsdtDetail(request.getApiKeyId());

                            // 2. 创建snapshot
                            ApiKey apiKey = apiKeyService.getDecryptedKey(request.getApiKeyId());
                            Double maxAccountBalance = tradingConfigProperties.getMaxAccountBalance();
                            BigDecimal maxAvailableAmount = (maxAccountBalance != null && maxAccountBalance > 0)
                                    ? new BigDecimal(maxAccountBalance)
                                    : null;

                            TradeBalanceSnapshot finalSnapshot = TradeBalanceSnapshotService.createSnapshotWithAiLimits(
                                    request.getApiKeyId(), apiKey.getCexName(), currentAccountDetail,
                                    TradeBalanceSnapshotSource.REPLAY.name(), maxAvailableAmount, null);

                            // 3. 保存snapshot并创建record
                            if (finalSnapshot != null && tradeBalanceSnapshotService.validateSnapshot(finalSnapshot)) {
                                TradeBalanceSnapshot savedSnapshot = tradeBalanceSnapshotService.saveSnapshot(finalSnapshot);

                                // 4. 创建LlmCallRecord（用于记录最终决策）
                                // 注意：这里使用currentAiResponse作为promptContent，因为这是最终决策的响应
                                LlmCallRecord finalRecord = llmCallRecordService.createCallRecord(
                                        request.getApiKeyId(), modelName,
                                        chatSessionId, currentParentId,
                                        currentRound,  // 保持当前轮次
                                        "ASYNC_CONVERSATION",
                                        null,  // userMessageId
                                        null   // assistantMessageId
                                );

                                // 5. ✅ 更新snapshot.recordId
                                boolean updated = tradeBalanceSnapshotService.updateRecordId(
                                        savedSnapshot.getSnapshotId(), finalRecord.getId());

                                if (updated) {
                                    log.debug("【异步递归】最终决策快照recordId更新成功 - snapshotId: {}, recordId: {}, round: {}",
                                            savedSnapshot.getSnapshotId(), finalRecord.getId(), currentRound);
                                } else {
                                    log.warn("【异步递归】最终决策快照recordId更新失败 - snapshotId: {}, recordId: {}",
                                            savedSnapshot.getSnapshotId(), finalRecord.getId());
                                }
                            } else {
                                log.warn("【异步递归】最终决策快照验证失败或为空，跳过recordId更新");
                            }
                        } catch (Exception snapshotEx) {
                            log.error("【异步递归】创建最终决策快照失败（不影响主流程） - sessionId: {}", sessionId, snapshotEx);
                        }

                        break;
                    }
                } catch (Exception e) {
                    log.error("【异步递归】第{}轮响应解析失败", currentRound, e);
                    updateCompleteNodeForSession(firstCallRecordId, "FAILED", "多轮响应解析失败");
                    break;
                }

                // 3. 构建下一轮messages数组
                List<UnifiedModelFactory.Message> nextMessages = buildNextTurnMessagesAsync(chatSessionId, currentAiResponse, toolResults, currentRound);

                // 4. 调用AI模型获取下一轮响应
                try {
                    // 【修改】第一步：创建TradeBalanceSnapshot（记录当前轮次的账户状态）
                    Long currentSnapshotId = null;
                    try {
                        // 重新获取当前账户信息（可能因上一轮的决策执行而变化）
                        AccountDetailModel currentAccountDetail = unifiedBalanceService.getAccountUsdtDetail(request.getApiKeyId());

                        ApiKey apiKey = apiKeyService.getDecryptedKey(request.getApiKeyId());

                        // 获取AI交易资金限制
                        Double maxAccountBalance = tradingConfigProperties.getMaxAccountBalance();
                        BigDecimal maxAvailableAmount = (maxAccountBalance != null && maxAccountBalance > 0)
                                ? new BigDecimal(maxAccountBalance)
                                : null;

                        // 计算总盈亏
                        BigDecimal totalPnl = null;
                        if (maxAvailableAmount != null) {
                            totalPnl = tradeBalanceSnapshotService.calculateTotalPnl(
                                    currentAccountDetail.getTotalEquity(), request.getApiKeyId());
                        }

                        // ✅ 使用统一方法创建快照（应用AI限制和总盈亏计算，与INITIAL保持一致）
                        TradeBalanceSnapshot currentSnapshot = TradeBalanceSnapshotService.createSnapshotWithAiLimits(
                                request.getApiKeyId(), apiKey.getCexName(), currentAccountDetail,
                                TradeBalanceSnapshotSource.REPLAY.name(), maxAvailableAmount, totalPnl);

                        // 验证并保存快照
                        if (currentSnapshot != null && tradeBalanceSnapshotService.validateSnapshot(currentSnapshot)) {
                            TradeBalanceSnapshot savedSnapshot = tradeBalanceSnapshotService.saveSnapshot(currentSnapshot);
                            currentSnapshotId = savedSnapshot.getSnapshotId();
                        }
                    } catch (Exception snapshotEx) {
                        log.error("【异步递归】第{}轮创建账户余额快照失败（不影响主流程）", currentRound + 1, snapshotEx);
                    }

                    // 【修改】第二步：获取父记录的调用来源,多轮对话后续轮次继承第一轮的来源类型
                    String parentCallSource = llmCallRecordService.getCallSourceById(currentParentId);

                    // 【修改】第三步：创建新的LlmCallRecord
                    LlmCallRecord newRecord = llmCallRecordService.createCallRecord(request.getApiKeyId(), modelName,
                            chatSessionId, currentParentId, currentRound + 1, parentCallSource,
                            null,  // userMessageId（稍后设置）
                            null   // assistantMessageId
                    );

                    currentParentId = newRecord.getId();

                    // 【修复】第三步+1：立即创建用户消息并关联（确保prompt始终关联）
                    Long currentUserMessageId = null;
                    try {
                        // 用户消息内容为工具调用结果
                        String userMessageContent = "";
                        if (toolResults != null && !toolResults.isEmpty()) {
                            userMessageContent = toolResultFormatter.formatToolResultsMarkdown(toolResults);
                        }
                        currentUserMessageId = chatService.createUserMessage(chatSessionId, userMessageContent);
                        if (currentUserMessageId != null) {
                            llmCallRecordService.updateUserMessageId(currentParentId, currentUserMessageId);
                            log.debug("【异步递归】第{}轮用户消息已创建并关联 - recordId: {}, userMsgId: {}",
                                    currentRound + 1, currentParentId, currentUserMessageId);
                        }
                    } catch (Exception msgEx) {
                        log.error("【异步递归】第{}轮创建用户消息失败（不影响主流程） - recordId: {}",
                                currentRound + 1, currentParentId, msgEx);
                    }

                    // 【修改】第四步：更新TradeBalanceSnapshot的recordId
                    if (currentSnapshotId != null) {
                        try {
                            boolean updated = tradeBalanceSnapshotService.updateRecordId(currentSnapshotId, newRecord.getId());
                            if (updated) {
                                log.debug("【异步递归】第{}轮快照recordId更新成功 - snapshotId: {}, recordId: {}",
                                        currentRound + 1, currentSnapshotId, newRecord.getId());
                            } else {
                                log.warn("【异步递归】第{}轮快照recordId更新失败 - snapshotId: {}, recordId: {}",
                                        currentRound + 1, currentSnapshotId, newRecord.getId());
                            }
                        } catch (Exception updateEx) {
                            log.error("【异步递归】第{}轮更新快照recordId时发生异常（不影响主流程）", currentRound + 1, updateEx);
                        }
                    }

                    // 【修改】第五步：调用AI模型（使用messages数组）
                    LocalDateTime callStartTime = LocalDateTime.now();
                    long startTime = System.currentTimeMillis();
                    String nextAiResponse = unifiedModelFactory.callWithMessages(nextMessages, modelName);
                    long processingTime = System.currentTimeMillis() - startTime;
                    totalProcessingTime += processingTime;

                    log.debug("【异步递归】第{}轮AI调用完成 - 耗时: {}ms, response长度: {}",
                            currentRound + 1, processingTime, nextAiResponse.length());

                    currentRound++;

                    // 重新解析获取下一轮的工具调用
                    ActionParser.ActionPack originalNextActionPack = ActionParser.parseActionPack(
                            AiResponseParserUtil.parseAiResponse(nextAiResponse, false).getJsonContent());

                    // [新增] 使用ClearVisionUtils清除幻觉
                    // 获取当前持仓信息
                    List<CexPosition> currentCexPositions = unifiedPositionService.getLatestPositionData(request.getApiKeyId(), false);
                    List<PositionModel> currentPositions = currentCexPositions.stream()
                            .map(cexPos -> {
                                PositionModel model = new PositionModel();
                                model.setInstId(cexPos.getSymbol());
                                model.setPosSide(cexPos.getSide().name());
                                return model;
                            })
                            .toList();

                    ActionParser.ActionPack nextActionPack = ClearVisionUtils.clearingIllusions(originalNextActionPack, currentPositions);

                    // [新增] 比较 ClearVisionUtils 处理前后的 ActionPack
                    boolean responseModified = !Objects.equals(originalNextActionPack, nextActionPack);
                    log.debug("【异步递归】第{}轮ClearVisionUtils处理完成 - recordId: {}, 原始动作数: {}, 处理后动作数: {}, responseModified: {}",
                            currentRound, currentParentId,
                            originalNextActionPack != null ? originalNextActionPack.getActions().size() : 0,
                            nextActionPack != null ? nextActionPack.getActions().size() : 0,
                            responseModified);

                    // [重构] 保存处理后的TradeAction，获得含id的TradeAction列表
                    List<TradeAction> savedTradeActions = null;
                    String originalJson = null; // 在if块外声明,以便后续使用
                    if (nextActionPack != null) {
                        savedTradeActions = tradeActionService.saveTradeActions(currentParentId, request.getApiKeyId(), modelName, nextActionPack);
                        log.debug("【异步递归】第{}轮TradeAction保存完成，获得id - recordId: {}, actionCount: {}",
                                currentRound, currentParentId, savedTradeActions != null ? savedTradeActions.size() : 0);
                    }

                    // [重构] 将含id的TradeAction转换为新的ActionPack，并替换nextAiResponse
                    if (savedTradeActions != null && !savedTradeActions.isEmpty()) {
                        // 将含id的TradeAction转换为ParsedAction
                        List<ActionParser.ParsedAction> actionsWithIds = savedTradeActions.stream()
                                .map(ActionParser::convertTradeActionToParsedAction)
                                .collect(Collectors.toList());

                        // 创建新的ActionPack（含id）
                        ActionParser.ActionPack actionPackWithIds = ActionParser.ActionPack.builder()
                                .actions(actionsWithIds)
                                .build();

                        // 提取原始JSON和新的JSON
                        originalJson = AiResponseParserUtil.parseAiResponse(nextAiResponse, false).getJsonContent();
                        String processedJson = objectMapper.writeValueAsString(actionPackWithIds);

                        // 替换nextAiResponse中的actions部分
                        nextAiResponse = nextAiResponse.replace(originalJson, processedJson);

                        // 更新nextActionPack引用，后续使用含id的版本
                        nextActionPack = actionPackWithIds;

                        log.debug("【异步递归】第{}轮aiResponse已更新为含id的版本 - recordId: {}, 原始长度: {}, 新长度: {}",
                                currentRound, currentParentId, originalJson.length(), processedJson.length());

                    }

                    // 【修复】保存含id的nextAiResponse到数据库
                    try {
                        llmCallRecordService.updateCallRecordSuccessInNewTransaction(
                                currentParentId,
                                responseModified ? nextAiResponse : null,  // 处理后的响应（仅当有变化时）
                                null, null, null, null, null,
                                callStartTime,  // 使用AI调用时记录的开始时间
                                null, null, null,  // 耗时（已在之前设置）
                                null,  // userMessageId（稍后设置）
                                null   // assistantMessageId（稍后设置）
                        );
                        log.debug("【异步递归】第{}轮AI响应已保存（含id） - recordId: {}", currentRound, currentParentId);
                    } catch (Exception e) {
                        log.error("【异步递归】第{}轮保存AI响应失败 - recordId: {}", currentRound, currentParentId, e);
                        // 保存失败不影响继续，因为nextAiResponse已在内存中更新
                    }

                    // [新增] 创建ChatMessage记录多轮对话的用户消息和AI响应
                    try {
                        long roundProcessingTime = System.currentTimeMillis() - startTime;

                        // 修复bug：用户消息只包含当前轮次的工具结果，不包含整个messages数组
                        // 从toolResults格式化为markdown字符串
                        String userMessageContent = "";
                        if (toolResults != null && !toolResults.isEmpty()) {
                            userMessageContent = toolResultFormatter.formatToolResultsMarkdown(toolResults);
                        }

                        Long[] messageIds = chatService.createAiTradeMessagePair(chatSessionId, userMessageContent, nextAiResponse, roundProcessingTime);
                        Long userMessageId = messageIds[0];
                        Long assistantMessageId = messageIds[1];
                        log.debug("【异步递归】第{}轮ChatMessage已创建 - chatSessionId: {}, recordId: {}, userMsgId: {}, assistantMsgId: {}",
                                currentRound, chatSessionId, currentParentId, userMessageId, assistantMessageId);

                        // 更新 LlmCallRecord，关联 messageId
                        if (userMessageId != null || assistantMessageId != null) {
                            llmCallRecordService.updateCallRecordSuccessInNewTransaction(
                                    currentParentId,
                                    null,  // responseContent（已在之前设置）
                                    null, null, null, null, null,
                                    null,  // callStartTime
                                    null, null, null,  // 耗时
                                    userMessageId,
                                    assistantMessageId
                            );
                            log.debug("【异步递归】第{}轮LlmCallRecord已关联messageId - recordId: {}, userMsgId: {}, assistantMsgId: {}",
                                    currentRound, currentParentId, userMessageId, assistantMessageId);
                        }
                    } catch (Exception msgEx) {
                        log.error("【异步递归】第{}轮创建ChatMessage失败 - chatSessionId: {}, recordId: {}, 不影响主流程",
                                currentRound, chatSessionId, currentParentId, msgEx);
                    }

                    // 更新currentAiResponse为含id的版本
                    currentAiResponse = nextAiResponse;

                    // 检查解析结果是否有效
                    if (nextActionPack == null || nextActionPack.getActions() == null
                            || nextActionPack.getActions().isEmpty()) {
                        log.warn("【异步递归】第{}轮响应中无有效工具调用,对话终止 - sessionId: {}",
                                currentRound, sessionId);
                        updateCompleteNodeForSession(firstCallRecordId, "SUCCESS", "无后续工具调用，流程结束");
                        break;
                    }

                    actions = nextActionPack.getActions();

                } catch (Exception e) {
                    log.error("【异步递归】第{}轮AI调用失败", currentRound + 1, e);

                    // 【修复】失败时保存可能的响应内容
                    // 注意：nextAiResponse可能在此作用域不可用，需要检查
                    // 但currentUserMessageId已经在创建记录后设置

                    llmCallRecordService.markAllProcessingAsTerminated(chatSessionId,
                            String.format("第%d轮AI调用失败: %s", currentRound + 1, e.getMessage()));
                    updateCompleteNodeForSession(firstCallRecordId, "FAILED", "多轮AI调用失败");
                    break;
                }
            }

            // 【修复】注释掉重复的parseAndSaveActionsPublic调用
            // 原因：在第457行已经调用过tradeActionService.saveTradeActions保存过了
            // aiDecisionService.parseAndSaveActionsPublic(currentAiResponse, request.getApiKeyId(), currentParentId);

            // 执行交易动作（BUY/SELL）- 多轮对话结束后也需要执行
            // 在try块外初始化变量,避免作用域问题
            ActionParser.ActionPack actionPackForTrade = null;
            try {
                // 解析最终AI响应中的动作包
                AiResponseParseResult parseResult = AiResponseParserUtil.parseAiResponse(
                        currentAiResponse, false);

                if (!parseResult.isValidJson()) {
                    log.warn("【异步递归】AI响应不是有效JSON,跳过交易执行 - callRecordId: {}, hasJson: {}, jsonContentLength: {}",
                            currentParentId, parseResult.isHasJson(),
                            parseResult.getJsonContent() != null ? parseResult.getJsonContent().length() : 0);
                    return;
                }

                actionPackForTrade = ActionParser.parseActionPack(parseResult.getJsonContent());
                if (null == actionPackForTrade) {
                    log.warn("【异步递归】ActionPack解析失败,跳过交易执行 - callRecordId: {}", currentParentId);
                    return;
                }

                // 注释:不再保存,避免与上面的parseAndSaveActionsPublic重复(第407行已调用)
                // tradeActionService.saveTradeActions(currentParentId, request.getApiKeyId(), modelName, actionPackForTrade);

                // 只处理BUY和SELL动作，不处理QUERY/ATTENTION（已由多轮对话处理）
                if (!ActionParser.containsToolCall(actionPackForTrade)) {
                    log.debug("【异步递归】检测到交易动作，调用TradeActionProcessor执行 - actionCount: {}",
                            actionPackForTrade.getActions() != null ? actionPackForTrade.getActions().size() : 0);

                    // 执行交易
                    // 查询关联的TradeAction列表,用于后续状态更新
                    List<TradeAction> tradeActionsRecursive = tradeActionService.findByRecordIdAndExecutionSource(
                            currentParentId, "INITIAL");

                    List<TradeExecutionResult> executionResults = tradeActionProcessor.processTradeActions(
                            actionPackForTrade, request.getApiKeyId(), tradeActionsRecursive);

                    if (executionResults != null && !executionResults.isEmpty()) {
                        log.debug("【异步递归】交易执行完成 - sessionId: {}, resultCount: {}",
                                sessionId, executionResults.size());
                    }
                } else {
                    log.warn("【异步递归】ActionPack仍包含工具调用,不执行交易 - callRecordId: {}, actionTypes: {}",
                            currentParentId,
                            actionPackForTrade.getActions() != null
                                    ? actionPackForTrade.getActions().stream()
                                    .map(a -> a.getAction() != null ? a.getAction().toString() : "null")
                                    .toList()
                                    : "null");
                }
            } catch (Exception tradeEx) {
                log.error("【异步递归】执行交易动作失败 - sessionId: {}, actionPackForTrade: {}",
                        sessionId, actionPackForTrade, tradeEx);
                // 交易执行失败不影响主流程
            }

            log.info("【异步递归】多轮对话完成 - sessionId: {}, 总轮次: {}, 总耗时: {}ms", sessionId, currentRound, totalProcessingTime);
            updateCompleteNodeForSession(firstCallRecordId, "SUCCESS", "多轮会话完成");

        } catch (Exception e) {
            log.error("【异步递归】多轮对话处理失败 - sessionId: {}", sessionId, e);
            llmCallRecordService.markAllProcessingAsTerminated(chatSessionId, "异步递归处理失败: " + e.getMessage());
            updateCompleteNodeForSession(firstCallRecordId, "FAILED", "异步递归处理失败");
        }
    }

    /**
     * 执行工具调用（异步）
     */
    private ToolExecutionResult executeToolAsync(ActionParser.ParsedAction action, String decisionId, Long apiKeyId) {

        try {
            if (ActionParser.ActionType.QUERY == action.getAction()) {
                // 构造KLineParameters
                KLineParameters params = new KLineParameters();
                params.setInstId(action.getInstId());
                params.setTimeframe(action.getTimeframe() != null ? action.getTimeframe() : "1H");
                params.setLimit(action.getLimit() != null ? action.getLimit() : 100);

                log.debug("【异步递归】执行QUERY工具 - instId: {}, timeframe: {}, limit: {}",
                        params.getInstId(), params.getTimeframe(), params.getLimit());

                // 执行K线查询（通过TradeDecisionService）
                return aiDecisionService.executeQueryTool(params, apiKeyId);
            } else if (ActionParser.ActionType.ATTENTION == action.getAction()) {
                // ✅ 新增：处理ATTENTION动作
                log.debug("【异步递归】执行ATTENTION工具 - instId: {}, timeframe: {}, priority: {}",
                        action.getInstId(), action.getTimeframe(), action.getPriority());

                // 保存ATTENTION到队列
                attentionQueueService.save(AttentionQueue.builder()
                        .recordId(Long.parseLong(decisionId))
                        .apiKeyId(apiKeyId)
                        .instId(action.getInstId())
                        .priority(action.getPriority())
                        .timeframe(action.getTimeframe())
                        .queryLimit(action.getLimit())
                        .expectedTriggerTime(attentionQueueService.calculateNextTriggerTime(action.getPriority()))
                        .status("PENDING")
                        .build());

                // 返回成功结果，携带币种信息
                long processingTime = 0L;  // ATTENTION不需要实际处理，时间设为0
                return ToolExecutionResult.success(
                        String.format("已标记关注: %s (优先级: %s)",
                                action.getInstId(),
                                action.getPriority() != null ? action.getPriority() : "normal"),
                        processingTime,
                        "attention"
                );
            }

            log.warn("【异步递归】不支持的工具类型: {}", action.getAction());
            return ToolExecutionResult.failure(
                    "不支持的工具类型: " + action.getAction()
            );

        } catch (Exception e) {
            log.error("【异步递归】工具执行失败 - action: {}, instId: {}", action.getAction(), action.getInstId(), e);
            return ToolExecutionResult.failure(
                    "工具执行失败: " + e.getMessage()
            );
        }
    }

    /**
     * 保存工具执行结果（异步）
     */
    private void saveToolActionResultAsync(String decisionId, ActionParser.ParsedAction action, ToolExecutionResult result) {
        try {
            aiDecisionService.saveToolAction(decisionId, action, result);
        } catch (Exception e) {
            log.error("【异步递归】保存工具执行结果失败 - decisionId: {}", decisionId, e);
            // 保存失败不影响主流程
        }
    }

    /**
     * 判断ActionPack是否包含最终决策（BUY/SELL/HOLD）
     */
    private boolean hasFinalDecision(ActionParser.ActionPack actionPack) {
        if (actionPack == null || actionPack.getActions() == null) {
            return false;
        }
        return actionPack.getActions().stream()
                .anyMatch(action -> {
                    String actionType = action.getAction() != null ? action.getAction().name() : null;
                    return "BUY".equals(actionType) || "SELL".equals(actionType) || "HOLD".equals(actionType) || "ATTENTION".equals(actionType);
                });
    }

    /**
     * 判断ActionPack是否只包含HOLD决策（无工具调用）
     */
    private boolean hasOnlyHoldDecision(ActionParser.ActionPack actionPack) {
        if (actionPack == null || actionPack.getActions() == null) {
            return false;
        }
        List<ActionParser.ParsedAction> actions = actionPack.getActions();
        boolean hasHold = actions.stream()
                .anyMatch(action -> "HOLD".equals(action.getAction() != null ? action.getAction().name() : null));
        boolean hasQuery = actions.stream()
                .anyMatch(action -> "QUERY".equals(action.getAction() != null ? action.getAction().name() : null));
        return hasHold && !hasQuery;
    }

    /**
     * 构建下一轮messages数组
     * <p>
     * 使用messages数组格式，避免重复的prompt，只保留工具调用结果。
     * </p>
     *
     * @param chatSessionId ChatSession ID(Long类型)
     * @param aiResponse    当前AI响应
     * @param toolResults   工具执行结果
     * @param roundNumber   当前轮次
     * @return messages数组
     */
    private List<UnifiedModelFactory.Message> buildNextTurnMessagesAsync(Long chatSessionId, String aiResponse, List<ToolExecutionResult> toolResults,
                                                                         int roundNumber) {

        log.debug("【messages构建】开始构建 - chatSessionId: {}, roundNumber: {}", chatSessionId, roundNumber);

        // 1. 从ChatMessage表获取该sessionId下的所有消息（按created_time排序）
        List<ChatMessage> chatMessages = chatMessageRepository.findBySessionSessionIdOrderByCreatedTimeAsc(chatSessionId);
        log.debug("【messages构建】从ChatMessage表获取到 {} 条消息", chatMessages.size());

        // 2. 构建messages数组
        List<UnifiedModelFactory.Message> messages = new ArrayList<>();

        // 2.1 添加所有历史消息（按照role顺序）
        for (ChatMessage chatMessage : chatMessages) {
            String role = chatMessage.getRole();
            String content = chatMessage.getContent();

            if (content == null || content.isEmpty()) {
                log.warn("【messages构建】跳过空消息 - messageId: {}, role: {}", chatMessage.getMessageId(), role);
                continue;
            }

            // 根据role添加消息
            if ("system".equals(role)) {
                messages.add(UnifiedModelFactory.Message.system(content));
                log.debug("【messages构建】添加system消息 - messageId: {}, length: {}", chatMessage.getMessageId(), content.length());
            } else if ("user".equals(role)) {
                messages.add(UnifiedModelFactory.Message.user(content));
                log.debug("【messages构建】添加user消息 - messageId: {}, length: {}", chatMessage.getMessageId(), content.length());
            } else if ("assistant".equals(role)) {
                messages.add(UnifiedModelFactory.Message.assistant(content));
                log.debug("【messages构建】添加assistant消息 - messageId: {}, length: {}", chatMessage.getMessageId(), content.length());
            } else {
                log.warn("【messages构建】未知的role: {} - messageId: {}", role, chatMessage.getMessageId());
            }
        }

        // 2.2 添加当前的工具结果作为user消息
        if (toolResults != null && !toolResults.isEmpty()) {
            String toolResultsMarkdown = toolResultFormatter.formatToolResultsMarkdown(toolResults);
            messages.add(UnifiedModelFactory.Message.user(toolResultsMarkdown));
            log.debug("【messages构建】添加当前工具结果消息 - round: {}, length: {}, 当前messages.size: {}",
                    roundNumber, toolResultsMarkdown.length(), messages.size());
        }

        // 2.3 验证messages数组的合法性
        log.debug("【messages构建】完成验证 - round: {}, messages.size: {}", roundNumber, messages.size());
        for (int i = 0; i < messages.size(); i++) {
            UnifiedModelFactory.Message msg = messages.get(i);
            String contentPreview = msg.getContent() != null && msg.getContent().length() > 50
                    ? msg.getContent().substring(0, 50) + "..."
                    : (msg.getContent() != null ? msg.getContent() : "");
            log.debug("【messages构建】[.*] role: {}, content.length: {}, preview: {}",
                    i, msg.getRole(), msg.getContent() != null ? msg.getContent().length() : 0, contentPreview);
        }

        // 输出完整的messages数组用于调试
        log.debug("【messages构建】完整messages数组（用于调试）:");
        for (int i = 0; i < messages.size(); i++) {
            UnifiedModelFactory.Message msg = messages.get(i);
            log.debug("【messages构建】--- Message {} ---", i);
            log.debug("【messages构建】Role: {}", msg.getRole());
            log.debug("【messages构建】Content length: {}", msg.getContent() != null ? msg.getContent().length() : 0);
            if (msg.getContent() != null && msg.getContent().length() <= 500) {
                log.debug("【messages构建】Content: {}", msg.getContent());
            } else {
                log.debug("【messages构建】Content: {}...(truncated)",
                        msg.getContent() != null ? msg.getContent().substring(0, 500) : "null");
            }
        }

        // 检查是否有连续的assistant消息
        boolean hasConsecutiveAssistant = false;
        for (int i = 1; i < messages.size(); i++) {
            UnifiedModelFactory.Message prev = messages.get(i - 1);
            UnifiedModelFactory.Message curr = messages.get(i);
            if ("assistant".equals(prev.getRole()) && "assistant".equals(curr.getRole())) {
                log.error("【messages构建】严重错误：在index {} 和 {} 发现连续的assistant消息！", i - 1, i);
                log.error("【messages构建】[{}] role: {}, [{}] role: {}",
                        i - 1, prev.getRole(), i, curr.getRole());
                hasConsecutiveAssistant = true;
            }
        }

        if (hasConsecutiveAssistant) {
            log.error("【messages构建】发现连续的assistant消息，这会导致API调用失败！");
        }

        // 检查messages数组是否以user结尾（这是正确的）
        if (!messages.isEmpty()) {
            UnifiedModelFactory.Message lastMessage = messages.get(messages.size() - 1);
            if ("user".equals(lastMessage.getRole())) {
                log.debug("【messages构建】验证通过：messages数组以user结尾");
            } else if ("assistant".equals(lastMessage.getRole())) {
                log.error("【messages构建】严重错误：messages数组以assistant结尾，这会导致API调用失败！");
            }
        }

        return messages;
    }

    /**
     * 从第一轮prompt中提取业务上下文
     */
    private String extractBusinessContext(String firstPrompt) {
        // 如果是完整的prompt，提取业务上下文
        // 如果已经是纯业务上下文，直接返回
        if (firstPrompt != null && !firstPrompt.contains("## 工具执行结果")) {
            // 不包含工具执行结果，可能已经是业务上下文
            return firstPrompt;
        }

        // 移除历史摘要、当前轮次信息、工具结果等部分，只保留业务上下文
        String prompt = firstPrompt;

        // 移除历史摘要部分
        int historySummaryIndex = prompt.indexOf("## 历史对话摘要");
        if (historySummaryIndex > 0) {
            prompt = prompt.substring(0, historySummaryIndex).trim();
        }

        // 移除当前轮次信息部分
        int currentRoundIndex = prompt.indexOf("## 当前轮次信息");
        if (currentRoundIndex > 0) {
            prompt = prompt.substring(0, currentRoundIndex).trim();
        }

        // 移除工具结果部分
        int toolResultsIndex = prompt.indexOf("## 工具执行结果");
        if (toolResultsIndex > 0) {
            prompt = prompt.substring(0, toolResultsIndex).trim();
        }

        // 如果提取后为空，返回原始内容
        if (prompt.isEmpty()) {
            return firstPrompt;
        }

        return prompt;
    }

    /**
     * 从prompt中提取工具结果
     */
    private String extractToolResultsFromPrompt(String prompt) {
        // 如果包含工具执行结果标记，提取工具结果部分
        int toolResultsIndex = prompt.indexOf("## 工具执行结果");
        if (toolResultsIndex > 0) {
            return prompt.substring(toolResultsIndex).trim();
        }

        // 如果不包含工具执行结果标记，但内容不为空，直接返回
        // 可能是其他格式的消息
        if (prompt != null && !prompt.trim().isEmpty()) {
            return prompt;
        }

        return "";
    }

    /**
     * 从响应中提取决策部分
     */
    private String extractDecisionFromResponse(String response) {
        // 如果包含JSON格式，提取JSON部分
        int decisionIndex = response.indexOf("```json");
        if (decisionIndex > 0) {
            return response.substring(decisionIndex).trim();
        }

        // 如果不包含JSON，但内容不为空，直接返回
        // 可能是其他格式的响应
        if (response != null && !response.trim().isEmpty()) {
            return response;
        }

        return "";
    }

    /**
     * 将messages数组转换为字符串
     */
    private String messagesToString(List<UnifiedModelFactory.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (UnifiedModelFactory.Message message : messages) {
            sb.append("[role: ").append(message.getRole()).append("]\n");
            sb.append(message.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    private List<FlowNodeStatusResponse> initializeFlowNodes(Long callRecordId) {
        if (!botFlowNodeConfig.isEnabled()) {
            return null;
        }
        List<FlowNodeStatusResponse> flowNodes = botFlowNodeConfig.getEnabledNodesSorted().stream()
                .map(node -> FlowNodeStatusResponse.builder()
                        .nodeCode(node.getCode())
                        .nodeName(node.getName())
                        .orderNo(node.getOrderNo())
                        .status("PENDING")
                        .build())
                .collect(Collectors.toList());
        persistFlowNodes(callRecordId, flowNodes);
        return flowNodes;
    }

    private void markFlowNodeRunning(Long callRecordId, List<FlowNodeStatusResponse> flowNodes, String nodeCode, String message) {
        updateFlowNodeStatus(callRecordId, flowNodes, nodeCode, "RUNNING", message);
    }

    private void markFlowNodeSuccess(Long callRecordId, List<FlowNodeStatusResponse> flowNodes, String nodeCode, String message) {
        updateFlowNodeStatus(callRecordId, flowNodes, nodeCode, "SUCCESS", message);
    }

    private void markFlowNodeFailed(Long callRecordId, List<FlowNodeStatusResponse> flowNodes, String nodeCode, String message) {
        updateFlowNodeStatus(callRecordId, flowNodes, nodeCode, "FAILED", message);
    }

    private void markFlowNodeSkipped(Long callRecordId, List<FlowNodeStatusResponse> flowNodes, String nodeCode) {
        updateFlowNodeStatus(callRecordId, flowNodes, nodeCode, "SKIPPED", null);
    }

    private void updateFlowNodeStatus(Long callRecordId, List<FlowNodeStatusResponse> flowNodes, String nodeCode, String status, String message) {
        if (flowNodes == null || flowNodes.isEmpty() || nodeCode == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (FlowNodeStatusResponse node : flowNodes) {
            if (!nodeCode.equals(node.getNodeCode())) {
                continue;
            }
            node.setStatus(status);
            if ("RUNNING".equals(status) && node.getStartTime() == null) {
                node.setStartTime(now);
            }
            if (("SUCCESS".equals(status) || "FAILED".equals(status) || "SKIPPED".equals(status)) && node.getEndTime() == null) {
                node.setEndTime(now);
                if (node.getStartTime() != null) {
                    node.setDurationMs(now - node.getStartTime());
                }
            }
            node.setMessage(message);
            break;
        }
        persistFlowNodes(callRecordId, flowNodes);
    }

    private void persistFlowNodes(Long callRecordId, List<FlowNodeStatusResponse> flowNodes) {
        if (flowNodes == null || flowNodes.isEmpty()) {
            return;
        }
        try {
            llmCallRecordService.updateFlowNodesJsonInNewTransaction(callRecordId, JsonUtils.toJsonString(flowNodes));
        } catch (Exception e) {
            log.warn("更新流程节点状态失败 - callRecordId: {}", callRecordId, e);
        }
    }

    private void updateCompleteNodeForSession(Long firstCallRecordId, String status, String message) {
        try {
            Optional<LlmCallRecord> recordOpt = llmCallRecordService.getRecordById(firstCallRecordId);
            if (recordOpt.isEmpty()) {
                return;
            }
            String flowNodesJson = recordOpt.get().getFlowNodesJson();
            if (!StringUtils.hasText(flowNodesJson)) {
                return;
            }
            List<FlowNodeStatusResponse> flowNodes = objectMapper.readValue(
                    flowNodesJson,
                    new TypeReference<List<FlowNodeStatusResponse>>() {
                    }
            );
            updateFlowNodeStatus(firstCallRecordId, flowNodes, "COMPLETE", status, message);
        } catch (Exception e) {
            log.warn("更新会话完成节点状态失败 - firstCallRecordId: {}", firstCallRecordId, e);
        }
    }

    /**
     * 异步执行BOT触发任务（包含prompt生成和AI调用）
     * <p>
     * 修复假死bug：将prompt生成也放入异步线程，避免HTTP请求超时
     * </p>
     *
     * @param request BOT触发请求（只需apiKeyId、taskId、modelName）
     */
    @Async("tradingTaskExecutor")
    public void executeAsyncTradingTaskWithPromptGeneration(BotCallModelRequest request) {
        Long apiKeyId = request.getApiKeyId();
        String taskId = request.getTaskId();
        String modelName = request.getModelName();

        log.debug("【异步任务开始】BOT触发 - taskId: {}, apiKeyId: {}, modelName: {}",
                taskId, apiKeyId, modelName);

        try {
            // ========== 步骤1: 异步生成prompt ==========
            log.debug("【步骤1】异步生成prompt - taskId: {}, apiKeyId: {}", taskId, apiKeyId);

            // 记录Prompt生成开始时间
            long promptGenStart = System.currentTimeMillis();
            BotPromptGenerateResponse promptResponse = aiDecisionService.generatePromptOnly(apiKeyId);
            long promptGenTimeMs = System.currentTimeMillis() - promptGenStart;

            log.debug("【步骤1完成】Prompt生成成功 - taskId: {}, estimatedTokens: {}, 耗时: {}ms",
                    taskId, promptResponse.getEstimatedTokens(), promptGenTimeMs);

            if (!promptResponse.getSuccess() || promptResponse.getStatus().equals("FAILED")) {
                log.error("【步骤1失败】Prompt生成失败 - taskId: {}, error: {}, 耗时: {}ms",
                        taskId, promptResponse.getErrorMessage(), promptGenTimeMs);
                CompletableFuture.completedFuture("FAILED");
                return;
            }

            // ========== 步骤2: 使用生成的prompt调用AI模型 ==========
            log.debug("【步骤2】调用AI模型 - taskId: {}, modelName: {}", taskId, modelName);

            // 更新request，添加prompt相关数据
            request.setPromptContent(promptResponse.getPromptContent());
            request.setPositions(promptResponse.getPositions());
            request.setBalanceSnapshotId(promptResponse.getBalanceSnapshotId());

            // 调用现有的异步AI执行方法,传递Prompt生成耗时
            executeAsyncTradingTask(request, promptGenTimeMs);

        } catch (Exception e) {
            log.error("【异步任务失败】BOT触发任务执行失败 - taskId: {}", taskId, e);
            CompletableFuture.completedFuture("FAILED");
        }
    }

}

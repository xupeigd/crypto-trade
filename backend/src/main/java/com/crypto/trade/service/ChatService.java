package com.crypto.trade.service;

import com.crypto.trade.entity.AgentConfig;
import com.crypto.trade.entity.ChatMessage;
import com.crypto.trade.entity.ChatSession;
import com.crypto.trade.model.ChatMessageModel;
import com.crypto.trade.model.ChatSessionModel;
import com.crypto.trade.model.SendMessageResponseModel;
import com.crypto.trade.repository.ChatMessageRepository;
import com.crypto.trade.repository.ChatSessionRepository;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.conversation.*;
import com.crypto.trade.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ChatService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class ChatService {

    private static final String DEFAULT_USER_ID = "default";
    // todo
    private static final String TRADING_SYSTEM_PROMPT = """
            你是一个专业的加密货币交易AI助手，具有以下特点：
            
            1. 专业性：深谙区块链技术、加密货币市场、交易策略和技术分析
            2. 实时性：能够分析市场数据、价格趋势和交易量变化
            3. 风险意识：始终强调风险管理和理性投资
            4. 简洁明了：用通俗易懂的语言解释复杂概念
            
            请根据用户的问题提供专业、准确、有帮助的回答。如果涉及具体投资建议，请谨慎处理并提醒风险。
            """;

    @Autowired
    UnifiedModelFactory unifiedModelFactory;
    @Autowired
    SimpMessagingTemplate messagingTemplate;
    @Autowired
    ChatSessionRepository sessionRepository;
    @Autowired
    ChatMessageRepository messageRepository;
    @Autowired
    AuditLogger auditLogger;
    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    KLineQueryExecutor kLineQueryExecutor;
    @Autowired
    PositionQueryExecutor positionQueryExecutor;
    @Autowired
    BalanceQueryExecutor balanceQueryExecutor;
    @Autowired
    AgentConfigService agentConfigService;
    @Autowired
    SkillConfigService skillConfigService;
    @Autowired
    MultiAgentService multiAgentService;

    public List<ChatSession> getUserSessions(String userId) {
        if (null == userId || userId.trim().isEmpty()) {
            userId = DEFAULT_USER_ID;
        }
        return sessionRepository.findValidSessionsByUserId(userId);
    }

    @Transactional
    public ChatSession createNewSession(String userId, String sessionName, Long agentId) {
        if (null == userId || userId.trim().isEmpty()) {
            userId = DEFAULT_USER_ID;
        }
        String actualSessionName = null != sessionName ? sessionName
                : "新对话 " + LocalDateTime.now().getHour() + ":" + LocalDateTime.now().getMinute() + ":" + LocalDateTime.now().getSecond();
        LocalDateTime now = LocalDateTime.now();
        ChatSession newSession = new ChatSession();
        newSession.setUserId(userId);
        newSession.setSessionName(actualSessionName);
        newSession.setStatus(ChatSession.SessionStatus.PRE_ACTIVE);
        // 使用"大模型配置"的默认模型
        String defaultModelName = unifiedModelFactory.getDefaultModelConfig().getModelId();
        newSession.setModelName(defaultModelName);

        // 只有从智能体配置页面进入时才设置agentId
        if (agentId != null) {
            newSession.setAgentId(agentId);
        }

        newSession.setCreatedTime(now);
        newSession.setUpdatedTime(now);
        ChatSession savedSession = sessionRepository.save(newSession);
        log.debug("创建新聊天会话 - sessionId: {}, userId: {}, sessionName: {}, model: {}, agentId: {}",
                savedSession.getSessionId(), userId, actualSessionName, defaultModelName, agentId);
        return savedSession;
    }

    public Optional<ChatSession> getLatestSession(String userId) {
        if (null == userId || userId.trim().isEmpty()) {
            userId = DEFAULT_USER_ID;
        }
        return sessionRepository.findLatestValidSessionByUserId(userId);
    }

    public List<ChatMessage> getSessionMessages(Long sessionId) {
        return messageRepository.findMessagesBySessionId(sessionId);
    }

    @Transactional
    public ChatMessage sendMessage(Long sessionId, String userMessage, String userId, String systemPrompt) {
        log.debug("处理用户消息 - sessionId: {}, userId: {}, message: {}", sessionId, userId, userMessage);
        if (null == userId || userId.trim().isEmpty()) {
            userId = DEFAULT_USER_ID;
        }
        // 获取或创建会话
        ChatSession session;
        if (null != sessionId && sessionId > 0) {
            Optional<ChatSession> sessionOpt = sessionRepository.findBySessionIdAndUserId(sessionId, userId);
            if (sessionOpt.isEmpty()) {
                throw new IllegalArgumentException("Session not found: " + sessionId);
            }
            session = sessionOpt.get();
        } else {
            // 如果没有指定sessionId，获取或创建最新会话
            List<ChatSession> existingSessions = getUserSessions(userId);
            if (!existingSessions.isEmpty()) {
                session = existingSessions.get(0);
            } else {
                session = createNewSession(userId, "默认对话", null);
            }
        }
        try {
            // 使用JPA保存用户消息
            ChatMessage userMessageObj = new ChatMessage();
            userMessageObj.setSession(session);
            userMessageObj.setRole("user");
            userMessageObj.setContent(userMessage);
            userMessageObj.setCreatedTime(LocalDateTime.now());
            ChatMessage savedUserMsg = messageRepository.save(userMessageObj);
            log.debug("用户消息已保存 - messageId: {}, sessionId: {}", savedUserMsg.getMessageId(), session.getSessionId());

            // 如果会话处于 pre_active 状态，转换为 active
            if (ChatSession.SessionStatus.PRE_ACTIVE.equals(session.getStatus())) {
                session.setStatus(ChatSession.SessionStatus.ACTIVE);
                sessionRepository.save(session);
                log.debug("会话状态从 pre_active 转换为 active - sessionId: {}", session.getSessionId());
            }

            // 实时发送用户消息到前端
            messagingTemplate.convertAndSend("/topic/chat/" + session.getSessionId(), savedUserMsg);
            // 构建AI对话上下文 - 标准多轮会话实现
            // 查询该会话的历史消息（最近10条，避免上下文过长）
            List<ChatMessage> historyMessages = messageRepository.findMessagesBySessionId(session.getSessionId());

            // 只保留最近10条历史消息，避免上下文过长
            int maxHistory = 10;
            if (historyMessages.size() > maxHistory) {
                historyMessages = historyMessages.subList(historyMessages.size() - maxHistory, historyMessages.size());
            }

            // 使用标准的messages数组格式
            List<UnifiedModelFactory.Message> messages = new ArrayList<>();

            // 提前获取AgentConfig（用于注入Skill Prompt和过滤工具）
            AgentConfig agentConfig = null;
            if (session.getAgentId() != null) {
                agentConfig = agentConfigService.getAgentConfigById(session.getAgentId()).orElse(null);
            }

            // 检查是否需要多Agent处理
            if (agentConfig != null && multiAgentService.needsMultiAgent(agentConfig.getId())) {
                log.info("检测到多Agent配置，调用多Agent处理 - agentId: {}", agentConfig.getId());
                try {
                    String multiAgentResponse = multiAgentService.multiAgentChat(agentConfig.getId(), userMessage);
                    if (multiAgentResponse != null && !multiAgentResponse.isEmpty()) {
                        // 保存AI回复
                        ChatMessage assistantMessageObj = new ChatMessage();
                        assistantMessageObj.setSession(session);
                        assistantMessageObj.setRole("assistant");
                        assistantMessageObj.setContent(multiAgentResponse);
                        assistantMessageObj.setCreatedTime(LocalDateTime.now());
                        ChatMessage savedAssistantMsg = messageRepository.save(assistantMessageObj);
                        // 实时发送AI回复到前端
                        messagingTemplate.convertAndSend("/topic/chat/" + session.getSessionId(), savedAssistantMsg);
                        return savedAssistantMsg;
                    }
                } catch (Exception e) {
                    log.error("多Agent处理失败，降级为单Agent处理: {}", e.getMessage(), e);
                    // 降级为单Agent处理，继续执行原有逻辑
                }
            }

            // 构建系统提示：优先级为 传入的systemPrompt > AgentConfig.systemPrompt + Skills > 默认TRADING_SYSTEM_PROMPT
            String effectiveSystemPrompt;
            if (null != systemPrompt && !systemPrompt.trim().isEmpty()) {
                // 使用传入的systemPrompt
                effectiveSystemPrompt = systemPrompt;
            } else if (agentConfig != null) {
                // 使用AgentConfig的systemPrompt，并注入Skill Prompt
                effectiveSystemPrompt = buildSystemPromptWithSkills(agentConfig);
            } else {
                // 使用默认Prompt
                effectiveSystemPrompt = TRADING_SYSTEM_PROMPT;
            }
            messages.add(UnifiedModelFactory.Message.system(effectiveSystemPrompt));

            // 添加历史消息
            for (ChatMessage msg : historyMessages) {
                if ("user".equals(msg.getRole())) {
                    messages.add(UnifiedModelFactory.Message.user(msg.getContent()));
                } else if ("assistant".equals(msg.getRole())) {
                    // 跳过包含function_call的assistant消息
                    if (msg.getContent() != null && msg.getContent().startsWith("[function_call]")) {
                        continue;
                    }
                    messages.add(UnifiedModelFactory.Message.assistant(msg.getContent()));
                }
                // 不加载tool消息，因为tool消息必须紧跟在包含tool_calls的assistant消息后面
            }

            // 添加当前用户问题
            messages.add(UnifiedModelFactory.Message.user(userMessage));

            log.debug("构建的对话上下文 - 消息数: {}", messages.size());

            // 生成审计会话ID
            String auditSessionId = auditLogger.generateSessionId();

            // 调用AI模型生成回复（支持Function Calling）
            long startTime = System.currentTimeMillis();
            try {
                // 强制使用大模型配置的默认模型，忽略会话中的模型设置
                String aiResponse = "";
                String actualModelName; // 记录实际使用的模型名称

                var defaultConfig = unifiedModelFactory.getDefaultModelConfig();
                if (null != defaultConfig) {
                    // 根据AgentConfig过滤工具（agentConfig已在前面获取）
                    List<UnifiedModelFactory.Tool> tools = buildTools(agentConfig);
                    actualModelName = defaultConfig.getModelId();
                    log.info("强制使用默认模型: {}", actualModelName);

                    int maxTurns = 10; // 最大对话轮数，防止无限循环
                    int currentTurn = 0;
                    boolean isFinalResponse = false;

                    // 多轮对话循环
                    while (currentTurn < maxTurns && !isFinalResponse) {
                        currentTurn++;
                        log.debug("开始第 {} 轮模型调用", currentTurn);

                        // 调用AI（带tools）
                        aiResponse = unifiedModelFactory.callWithConfigAndMessagesAndTools(messages, defaultConfig, tools);

                        // 检查是否有function_call
                        if (UnifiedModelFactory.isFunctionCallResponse(aiResponse)) {
                            log.info("第 {} 轮检测到function_call请求", currentTurn);

                            // 提取函数名、参数和tool_call_id
                            String functionName = UnifiedModelFactory.extractFunctionName(aiResponse);
                            String args = UnifiedModelFactory.extractFunctionArguments(aiResponse);
                            String toolCallId = UnifiedModelFactory.extractToolCallId(aiResponse);
                            String reasoningContent = UnifiedModelFactory.extractReasoningContent(aiResponse);

                            log.info("函数调用: {}, tool_call_id: {}, 参数: {}, reasoning_content长度: {}",
                                    functionName, toolCallId, args, reasoningContent != null ? reasoningContent.length() : 0);

                            // 执行工具
                            String toolResult = executeTool(functionName, args, agentConfig);

                            // 存储assistant的function_call消息到数据库
                            ChatMessage assistantToolCallMsg = new ChatMessage();
                            assistantToolCallMsg.setSession(session);
                            assistantToolCallMsg.setRole("assistant");
                            assistantToolCallMsg.setContent(String.format("[function_call] %s(%s)", functionName, args));
                            assistantToolCallMsg.setToolCallId(toolCallId);
                            assistantToolCallMsg.setFunctionName(functionName);
                            assistantToolCallMsg.setCreatedTime(LocalDateTime.now());
                            messageRepository.save(assistantToolCallMsg);
                            log.debug("Function call消息已保存 - functionName: {}, toolCallId: {}", functionName, toolCallId);

                            // 存储tool执行结果到数据库
                            ChatMessage toolResultMsg = new ChatMessage();
                            toolResultMsg.setSession(session);
                            toolResultMsg.setRole("tool");
                            toolResultMsg.setContent(toolResult);
                            toolResultMsg.setToolCallId(toolCallId);
                            toolResultMsg.setFunctionName(functionName);
                            toolResultMsg.setCreatedTime(LocalDateTime.now());
                            messageRepository.save(toolResultMsg);
                            log.debug("Tool结果消息已保存 - functionName: {}, toolCallId: {}", functionName, toolCallId);

                            // 构建tool_calls信息
                            UnifiedModelFactory.ToolCall toolCall = UnifiedModelFactory.ToolCall.builder()
                                    .id(toolCallId)
                                    .type("function")
                                    .function(UnifiedModelFactory.ToolCall.ToolCallFunction.builder()
                                            .name(functionName)
                                            .arguments(args)
                                            .build())
                                    .build();

                            // 添加assistant消息（包含tool_calls和reasoning_content）
                            messages.add(UnifiedModelFactory.Message.assistant(null, reasoningContent, List.of(toolCall)));

                            // 将工具结果添加到消息中，使用正确的tool_call_id
                            messages.add(UnifiedModelFactory.Message.tool(toolResult, toolCallId));

                            // 继续下一轮循环，让模型根据工具结果生成回复或继续调用工具
                        } else {
                            // 不是function_call，说明是最终回复
                            isFinalResponse = true;
                            log.info("第 {} 轮获取最终回复", currentTurn);
                        }
                    }

                    if (!isFinalResponse) {
                        log.warn("达到最大对话轮数 ({})，强制结束对话", maxTurns);
                        aiResponse = "抱歉，对话轮数过多，已强制终止。请尝试简化您的问题。";
                    }

                } else {
                    log.error("未找到默认模型配置，系统无法正常运行");
                    aiResponse = "抱歉，系统未配置默认AI模型，请联系管理员设置默认模型配置。";
                    actualModelName = "none";
                }
                long processingTime = System.currentTimeMillis() - startTime;

                // 获取默认API密钥ID用于审计日志
                Long defaultApiKeyId = null;
                try {
                    var defaultApiKey = apiKeyService.getDefaultApiKey();
                    if (defaultApiKey != null) {
                        defaultApiKeyId = defaultApiKey.getKeyId();
                        log.debug("获取到默认API密钥: {}", defaultApiKeyId);
                    } else {
                        log.warn("未找到默认API密钥，使用聊天系统专用ID: 1");
                        // 使用固定的聊天系统API密钥ID，确保满足数据库NOT NULL约束
                        defaultApiKeyId = 1L;
                    }
                } catch (Exception e) {
                    log.error("获取默认API密钥失败，使用聊天系统专用ID: 1", e);
                    // 使用固定的聊天系统API密钥ID，确保满足数据库NOT NULL约束
                    defaultApiKeyId = 1L;
                }

                // 记录成功的LLM调用审计信息
                auditLogger.logSuccessCall(auditSessionId, defaultApiKeyId, null, actualModelName,
                        messagesToString(messages), aiResponse, processingTime);
                // 使用JPA保存AI回复
                ChatMessage aiMessageObj = new ChatMessage();
                aiMessageObj.setSession(session);
                aiMessageObj.setRole("assistant");
                aiMessageObj.setContent(aiResponse);
                aiMessageObj.setProcessingTimeMs(processingTime);
                aiMessageObj.setCreatedTime(LocalDateTime.now());
                ChatMessage savedAiMsg = messageRepository.save(aiMessageObj);
                log.debug("AI回复已保存 - messageId: {}, sessionId: {}", savedAiMsg.getMessageId(), session.getSessionId());
                // 更新会话时间
                session.setUpdatedTime(LocalDateTime.now());
                sessionRepository.save(session);
                // 实时发送AI回复到前端
                messagingTemplate.convertAndSend("/topic/chat/" + session.getSessionId(), savedAiMsg);
                log.debug("AI回复生成成功 - sessionId: {}, processingTime: {}ms", session.getSessionId(), processingTime);
                return savedAiMsg;
            } catch (Exception e) {
                long processingTime = System.currentTimeMillis() - startTime;
                log.error("AI回复生成失败 - sessionId: {}, error: {}", session.getSessionId(), e.getMessage(), e);

                // 记录失败的LLM调用审计信息
                auditLogger.logFailedCall(auditSessionId, null, null, session.getModelName(),
                        messagesToString(messages), processingTime, e.getMessage());

                // 创建错误回复消息
                String errorMsgContent = "抱歉，AI助手暂时无法回复。请稍后再试。错误信息：" + e.getMessage();
                ChatMessage errorMessageObj = new ChatMessage();
                errorMessageObj.setSession(session);
                errorMessageObj.setRole("assistant");
                errorMessageObj.setContent(errorMsgContent);
                errorMessageObj.setCreatedTime(LocalDateTime.now());
                ChatMessage savedErrorMsg = messageRepository.save(errorMessageObj);
                // 实时发送错误消息到前端
                messagingTemplate.convertAndSend("/topic/chat/" + session.getSessionId(), savedErrorMsg);
                return savedErrorMsg;
            }
        } catch (Exception e) {
            log.error("数据库保存失败 - sessionId: {}, error: {}", session.getSessionId(), e.getMessage(), e);
            throw new RuntimeException("保存消息失败", e);
        }
    }

    @Transactional
    public void deleteSession(Long sessionId, String userId) {
        if (null == userId || userId.trim().isEmpty()) {
            userId = DEFAULT_USER_ID;
        }
        // 验证用户权限
        Optional<ChatSession> sessionOpt = sessionRepository.findBySessionIdAndUserId(sessionId, userId);
        if (sessionOpt.isEmpty()) {
            throw new IllegalArgumentException("Session not found or unauthorized: " + sessionId);
        }
        // 使用自定义更新方法删除会话
        sessionRepository.updateSessionStatusArchived(sessionId, userId);
        log.debug("聊天会话已删除 - sessionId: {}, userId: {}", sessionId, userId);
    }

    @Transactional
    public ChatSession updateSessionName(Long sessionId, String sessionName, String userId) {
        if (null == userId || userId.trim().isEmpty()) {
            userId = DEFAULT_USER_ID;
        }
        // 验证用户权限
        Optional<ChatSession> sessionOpt = sessionRepository.findBySessionIdAndUserId(sessionId, userId);
        if (sessionOpt.isEmpty()) {
            throw new IllegalArgumentException("Session not found or unauthorized: " + sessionId);
        }
        // 使用自定义更新方法更新会话名称
        sessionRepository.updateSessionNameAndTime(sessionId, userId, sessionName);
        // 获取更新后的会话
        ChatSession updatedSession = sessionRepository.findById(sessionId).orElseThrow();
        log.debug("聊天会话名称已更新 - sessionId: {}, sessionName: {}, userId: {}",
                sessionId, sessionName, userId);
        return updatedSession;
    }

    // ==================== Model转换方法 ====================

    /**
     * 获取用户会话的Model列表
     *
     * @param userId 用户ID
     * @return ChatSessionModel列表
     */
    public List<ChatSessionModel> getUserSessionsModel(String userId) {
        return getUserSessionsModel(userId, null);
    }

    public List<ChatSessionModel> getUserSessionsModel(String userId, Long agentId) {
        List<ChatSession> sessions;
        if (null != agentId) {
            sessions = sessionRepository.findValidSessionsByUserIdAndAgentId(userId, agentId);
        } else {
            sessions = sessionRepository.findValidSessionsByUserIdAndAgentIdIsNull(userId);
        }
        return sessions.stream()
                .map(ChatSessionModel::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 创建会话的Model
     *
     * @param sessionName 会话名称
     * @param userId      用户ID
     * @return ChatSessionModel
     */
    public ChatSessionModel createSessionModel(String sessionName, String userId, Long agentId) {
        ChatSession session = createNewSession(userId, sessionName, agentId);
        return ChatSessionModel.fromEntity(session);
    }

    /**
     * 获取最新会话的Model
     *
     * @param userId 用户ID
     * @return ChatSessionModel
     */
    public ChatSessionModel getLatestSessionModel(String userId) {
        Optional<ChatSession> sessionOpt = getLatestSession(userId);
        return sessionOpt.map(ChatSessionModel::fromEntity).orElse(null);
    }

    /**
     * 获取会话消息的Model列表
     *
     * @param sessionId 会话ID
     * @return ChatMessageModel列表
     */
    public List<ChatMessageModel> getSessionMessagesModel(Long sessionId) {
        List<ChatMessage> messages = getSessionMessages(sessionId);
        return messages.stream()
                .map(ChatMessageModel::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 发送消息并返回响应Model
     *
     * @param sessionId    会话ID
     * @param message      消息内容
     * @param userId       用户ID
     * @param systemPrompt 系统提示（可选）
     * @return SendMessageResponseModel
     */
    public SendMessageResponseModel sendMessageModel(Long sessionId, String message, String userId, String systemPrompt) {
        try {
            ChatMessage assistantMessage = sendMessage(sessionId, message, userId, systemPrompt);
            return SendMessageResponseModel.fromMessage(assistantMessage);
        } catch (Exception e) {
            log.error("发送消息失败 - sessionId: {}, userId: {}, error: {}", sessionId, userId, e.getMessage(), e);
            return SendMessageResponseModel.failure("发送消息失败: " + e.getMessage());
        }
    }

    /**
     * 更新会话名称并返回Model
     *
     * @param sessionId   会话ID
     * @param sessionName 会话名称
     * @param userId      用户ID
     * @return ChatSessionModel
     */
    public ChatSessionModel updateSessionNameModel(Long sessionId, String sessionName, String userId) {
        ChatSession session = updateSessionName(sessionId, sessionName, userId);
        return ChatSessionModel.fromEntity(session);
    }

    // ==================== AI交易场景专用方法 ====================

    /**
     * 为AI交易场景创建用户和助手消息对
     * <p>
     * 此方法专门用于AI交易系统,在LLM调用完成后记录完整的对话历史。
     * 与普通聊天功能分离,避免影响用户聊天体验。
     * </p>
     *
     * @param chatSessionId     ChatSession的ID
     * @param userPrompt        用户的Prompt内容
     * @param assistantResponse AI助手的响应内容
     * @param processingTimeMs  处理耗时(毫秒)
     * @return 消息ID对 [userMessageId, assistantMessageId]
     */
    @Transactional
    public Long[] createAiTradeMessagePair(Long chatSessionId, String userPrompt, String assistantResponse, Long processingTimeMs) {
        // 验证chatSessionId
        if (null == chatSessionId) {
            log.warn("ChatSession ID为空,跳过创建AI交易消息");
            return new Long[]{null, null};
        }

        // 查询ChatSession
        Optional<ChatSession> sessionOpt = sessionRepository.findById(chatSessionId);
        if (sessionOpt.isEmpty()) {
            log.error("ChatSession不存在 - sessionId: {}, 跳过创建AI交易消息", chatSessionId);
            return new Long[]{null, null};
        }

        ChatSession session = sessionOpt.get();

        try {
            // 创建用户消息
            ChatMessage userMessage = new ChatMessage();
            userMessage.setSession(session);
            userMessage.setRole("user");
            userMessage.setContent(userPrompt);
            userMessage.setCreatedTime(LocalDateTime.now());
            ChatMessage savedUserMsg = messageRepository.save(userMessage);
            log.debug("AI交易用户消息已创建 - messageId: {}, sessionId: {}, content长度: {}",
                    savedUserMsg.getMessageId(), chatSessionId, userPrompt != null ? userPrompt.length() : 0);

            // 创建助手消息
            ChatMessage assistantMessage = new ChatMessage();
            assistantMessage.setSession(session);
            assistantMessage.setRole("assistant");
            assistantMessage.setContent(assistantResponse);
            assistantMessage.setProcessingTimeMs(processingTimeMs);
            assistantMessage.setCreatedTime(LocalDateTime.now());
            ChatMessage savedAssistantMsg = messageRepository.save(assistantMessage);
            log.debug("AI交易助手消息已创建 - messageId: {}, sessionId: {}, content长度: {}, processingTimeMs: {}",
                    savedAssistantMsg.getMessageId(), chatSessionId,
                    assistantResponse != null ? assistantResponse.length() : 0, processingTimeMs);

            // 更新会话时间
            session.setUpdatedTime(LocalDateTime.now());
            sessionRepository.save(session);

            log.info("AI交易消息对创建成功 - sessionId: {}, userMsgId: {}, assistantMsgId: {}",
                    chatSessionId, savedUserMsg.getMessageId(), savedAssistantMsg.getMessageId());

            // 返回消息ID对
            return new Long[]{savedUserMsg.getMessageId(), savedAssistantMsg.getMessageId()};

        } catch (Exception e) {
            log.error("创建AI交易消息失败 - sessionId: {}, error: {}", chatSessionId, e.getMessage(), e);
            // 不抛出异常,避免影响主流程
            return new Long[]{null, null};
        }
    }

    /**
     * 为AI交易场景创建单条消息
     * <p>
     * 此方法用于创建单独的消息,适用于需要单独记录用户消息或助手消息的场景。
     * </p>
     *
     * @param chatSessionId    ChatSession的ID
     * @param role             消息角色("user"或"assistant")
     * @param content          消息内容
     * @param processingTimeMs 处理耗时(毫秒,仅对assistant消息有效)
     * @return 创建的ChatMessage实体
     */
    @Transactional
    public ChatMessage createAiTradeMessage(Long chatSessionId, String role, String content, Long processingTimeMs) {
        // 验证chatSessionId
        if (null == chatSessionId) {
            log.warn("ChatSession ID为空,跳过创建AI交易消息");
            return null;
        }

        // 验证role参数
        if (!"user".equals(role) && !"assistant".equals(role)) {
            log.warn("无效的消息角色: {}, 必须是'user'或'assistant'", role);
            return null;
        }

        // 查询ChatSession
        Optional<ChatSession> sessionOpt = sessionRepository.findById(chatSessionId);
        if (sessionOpt.isEmpty()) {
            log.error("ChatSession不存在 - sessionId: {}, 跳过创建AI交易消息", chatSessionId);
            return null;
        }

        ChatSession session = sessionOpt.get();

        try {
            // 创建消息
            ChatMessage message = new ChatMessage();
            message.setSession(session);
            message.setRole(role);
            message.setContent(content);
            message.setProcessingTimeMs(processingTimeMs);
            message.setCreatedTime(LocalDateTime.now());
            ChatMessage savedMessage = messageRepository.save(message);

            log.debug("AI交易{}消息已创建 - messageId: {}, sessionId: {}, content长度: {}, processingTimeMs: {}",
                    role, savedMessage.getMessageId(), chatSessionId,
                    content != null ? content.length() : 0, processingTimeMs);

            // 更新会话时间
            session.setUpdatedTime(LocalDateTime.now());
            sessionRepository.save(session);

            return savedMessage;

        } catch (Exception e) {
            log.error("创建AI交易{}消息失败 - sessionId: {}, error: {}", role, chatSessionId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 为AI交易场景创建用户消息
     * <p>
     * 用于在LLM调用开始前创建用户消息，以支持PROCESSING状态时返回promptContent。
     * </p>
     *
     * @param chatSessionId ChatSession的ID
     * @param userPrompt    用户的Prompt内容
     * @return 用户消息ID，失败返回null
     */
    @Transactional
    public Long createUserMessage(Long chatSessionId, String userPrompt) {
        ChatMessage message = createAiTradeMessage(chatSessionId, "user", userPrompt, null);
        return message != null ? message.getMessageId() : null;
    }

    /**
     * 为AI交易场景创建助手消息
     * <p>
     * 用于在LLM调用完成后创建助手消息。
     * </p>
     *
     * @param chatSessionId     ChatSession的ID
     * @param assistantResponse AI助手的响应内容
     * @param processingTimeMs  处理耗时(毫秒)
     * @return 助手消息ID，失败返回null
     */
    @Transactional
    public Long createAssistantMessage(Long chatSessionId, String assistantResponse, Long processingTimeMs) {
        ChatMessage message = createAiTradeMessage(chatSessionId, "assistant", assistantResponse, processingTimeMs);
        return message != null ? message.getMessageId() : null;
    }

    /**
     * 将messages数组转换为字符串（用于审计日志）
     */
    private String messagesToString(List<UnifiedModelFactory.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (UnifiedModelFactory.Message msg : messages) {
            sb.append("[").append(msg.getRole()).append("]: ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建包含Skill Prompt的系统提示
     * 将AgentConfig的systemPrompt与Skills的prompt合并
     */
    private String buildSystemPromptWithSkills(AgentConfig agentConfig) {
        StringBuilder sb = new StringBuilder();

        // 1. 添加Agent的系统Prompt
        if (agentConfig.getSystemPrompt() != null && !agentConfig.getSystemPrompt().isEmpty()) {
            sb.append(agentConfig.getSystemPrompt());
        } else {
            // 如果没有配置，使用默认Prompt
            sb.append(TRADING_SYSTEM_PROMPT);
        }

        // 2. 如果配置了Skills，注入Skill Prompt
        if (agentConfig.getSkills() != null && !agentConfig.getSkills().isEmpty()) {
            List<Long> skillIds = skillConfigService.parseSkillIds(agentConfig.getSkills());
            if (!skillIds.isEmpty()) {
                List<com.crypto.trade.entity.SkillConfig> skills = skillConfigService.getSkillConfigsByIds(skillIds);
                for (com.crypto.trade.entity.SkillConfig skill : skills) {
                    if (skill.getIsActive() && skill.getSkillPrompt() != null) {
                        sb.append("\n\n---\n## 技能: ").append(skill.getName()).append("\n");
                        sb.append(skillConfigService.buildFullPrompt(skill));
                    }
                }
            }
        }

        return sb.toString();
    }

    /**
     * 构建工具定义列表（用于Function Calling）
     * 根据AgentConfig的tools配置过滤工具，并合并Skill的requiredTools
     */
    private List<UnifiedModelFactory.Tool> buildTools(AgentConfig agentConfig) {
        // 获取agent配置的工具列表
        List<String> allowedTools = new ArrayList<>();
        if (agentConfig != null && agentConfig.getTools() != null && !agentConfig.getTools().isEmpty()) {
            allowedTools = agentConfigService.parseTools(agentConfig.getTools());
            log.debug("智能体 {} 允许使用的工具: {}", agentConfig.getName(), allowedTools);
        }

        // 合并Skill的requiredTools
        if (agentConfig != null && agentConfig.getSkills() != null && !agentConfig.getSkills().isEmpty()) {
            List<Long> skillIds = skillConfigService.parseSkillIds(agentConfig.getSkills());
            if (!skillIds.isEmpty()) {
                List<com.crypto.trade.entity.SkillConfig> skills = skillConfigService.getSkillConfigsByIds(skillIds);
                for (com.crypto.trade.entity.SkillConfig skill : skills) {
                    if (skill.getIsActive() && skill.getRequiredTools() != null && !skill.getRequiredTools().isEmpty()) {
                        List<String> skillTools = skillConfigService.parseRequiredTools(skill.getRequiredTools());
                        for (String tool : skillTools) {
                            if (!allowedTools.contains(tool)) {
                                allowedTools.add(tool);
                                log.debug("从技能 {} 合并工具: {}", skill.getName(), tool);
                            }
                        }
                    }
                }
            }
        }

        List<UnifiedModelFactory.Tool> tools = new ArrayList<>();

        // K线查询工具 - 只有明确配置才添加
        if (allowedTools.contains("k_line")) {
            tools.add(buildKlineTool());
        }

        // 仓位查询工具 - 只有明确配置才添加
        if (allowedTools.contains("position_info")) {
            tools.add(buildPositionTool());
        }

        // 余额查询工具 - 只有明确配置才添加
        if (allowedTools.contains("balance_info")) {
            tools.add(buildBalanceTool());
        }

        log.info("智能体 {} 允许使用的工具: {}, 实际构建工具数: {}",
                agentConfig != null ? agentConfig.getName() : "null",
                allowedTools, tools.size());
        return tools;
    }

    /**
     * 构建K线查询工具
     */
    private UnifiedModelFactory.Tool buildKlineTool() {
        return UnifiedModelFactory.Tool.builder()
                .type("function")
                .function(UnifiedModelFactory.Tool.Function.builder()
                        .name("get_kline")
                        .description("获取K线数据，用于分析价格走势和技术指标")
                        .parameters(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "instId", Map.of("type", "string", "description", "合约代码如BTC-USDT-SWAP"),
                                        "timeframe", Map.of("type", "string", "enum", List.of("1m", "5m", "15m", "1h", "4h", "1d"), "description", "时间周期"),
                                        "limit", Map.of("type", "integer", "description", "返回条数，默认100"),
                                        "action", Map.of("type", "string", "description", "动作，必须返回k_line")
                                ),
                                "required", List.of("instId", "timeframe", "limit", "action")
                        ))
                        .build())
                .build();
    }

    /**
     * 构建仓位查询工具
     */
    private UnifiedModelFactory.Tool buildPositionTool() {
        return UnifiedModelFactory.Tool.builder()
                .type("function")
                .function(UnifiedModelFactory.Tool.Function.builder()
                        .name("position_info")
                        .description("获取持仓信息，包括当前持仓、委托中、历史仓位")
                        .parameters(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "action", Map.of("type", "string", "description", "动作，必须返回position_info"),
                                        "type", Map.of("type", "string", "enum", List.of("ALIVE", "PENDING", "HISTORY"), "description", "类型：ALIVE-当前持仓, PENDING-委托中, HISTORY-历史仓位"),
                                        "limit", Map.of("type", "integer", "description", "返回数量，默认-1表示全部，最大30")
                                ),
                                "required", List.of("action", "type", "limit")
                        ))
                        .build())
                .build();
    }

    /**
     * 构建余额查询工具
     */
    private UnifiedModelFactory.Tool buildBalanceTool() {
        return UnifiedModelFactory.Tool.builder()
                .type("function")
                .function(UnifiedModelFactory.Tool.Function.builder()
                        .name("balance_info")
                        .description("获取账户余额信息")
                        .parameters(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "action", Map.of("type", "string", "description", "动作，必须返回balance_info"),
                                        "currency", Map.of("type", "string", "description", "特定币种，可选")
                                ),
                                "required", List.of("action")
                        ))
                        .build())
                .build();
    }

    /**
     * 执行工具调用
     *
     * @param functionName 函数名称
     * @param arguments    函数参数（JSON字符串）
     * @return 工具执行结果
     */
    private String executeTool(String functionName, String arguments, AgentConfig agentConfig) {
        try {
            // 验证工具是否在允许列表中
            if (agentConfig != null && agentConfig.getTools() != null && !agentConfig.getTools().isEmpty()) {
                List<String> allowedTools = agentConfigService.parseTools(agentConfig.getTools());
                // 工具名称映射：前端配置使用 k_line，实际调用使用 get_kline
                List<String> mappedTools = allowedTools.stream()
                        .map(tool -> "k_line".equals(tool) ? "get_kline" : tool)
                        .toList();
                if (!mappedTools.contains(functionName)) {
                    log.warn("智能体 {} 未配置工具 {}，拒绝执行", agentConfig.getName(), functionName);
                    return "错误：智能体未配置工具 " + functionName;
                }
            }
            // 获取默认API密钥ID
            Long defaultApiKeyId = 1L;
            try {
                var defaultApiKey = apiKeyService.getDefaultApiKey();
                if (defaultApiKey != null) {
                    defaultApiKeyId = defaultApiKey.getKeyId();
                }
            } catch (Exception e) {
                log.warn("获取默认API密钥失败，使用默认值: {}", e.getMessage());
            }
            if ("get_kline".equals(functionName)) {
                // 使用不带多态类型处理的reader，避免要求action字段
                KLineParameters params = JsonUtils.transform(arguments, KLineParameters.class);

                // 验证参数
                if (params.getInstId() == null || params.getInstId().trim().isEmpty()) {
                    return "错误：缺少必需的参数instId";
                }
                if (params.getTimeframe() == null || params.getTimeframe().trim().isEmpty()) {
                    return "错误：缺少必需的参数timeframe";
                }

                // 设置默认limit
                if (params.getLimit() == null || params.getLimit() <= 0) {
                    params.setLimit(100);
                }

                // 执行K线查询
                var result = kLineQueryExecutor.execute(params, defaultApiKeyId);

                if (Boolean.TRUE.equals(result.getSuccess())) {
                    return result.getData() != null ? result.getData().toString() : "";
                } else {
                    return "错误：" + result.getErrorMessage();
                }
            } else if ("position_info".equals(functionName)) {
                // 解析仓位查询参数
                PositionInfoParameters params = JsonUtils.transform(arguments, PositionInfoParameters.class);

                // 调用PositionQueryExecutor执行查询
                var result = positionQueryExecutor.execute(params, defaultApiKeyId);

                if (Boolean.TRUE.equals(result.getSuccess())) {
                    return result.getData() != null ? result.getData().toString() : "";
                } else {
                    return "错误：" + result.getErrorMessage();
                }
            } else if ("balance_info".equals(functionName)) {
                // 解析余额查询参数
                BalanceInfoParameters params = JsonUtils.transform(arguments, BalanceInfoParameters.class);

                // 调用BalanceQueryExecutor执行查询
                var result = balanceQueryExecutor.execute(params, defaultApiKeyId);

                if (Boolean.TRUE.equals(result.getSuccess())) {
                    return result.getData() != null ? result.getData().toString() : "";
                } else {
                    return "错误：" + result.getErrorMessage();
                }
            } else {
                return "错误：未知工具函数 " + functionName;
            }
        } catch (Exception e) {
            log.error("执行工具失败: {}, 参数: {}", functionName, arguments, e);
            return "执行工具失败: " + e.getMessage();
        }
    }

}
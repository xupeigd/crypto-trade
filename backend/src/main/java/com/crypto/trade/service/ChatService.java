package com.crypto.trade.service;

import com.crypto.trade.entity.ChatMessage;
import com.crypto.trade.entity.ChatSession;
import com.crypto.trade.model.ChatMessageModel;
import com.crypto.trade.model.ChatSessionModel;
import com.crypto.trade.model.SendMessageResponseModel;
import com.crypto.trade.repository.ChatMessageRepository;
import com.crypto.trade.repository.ChatSessionRepository;
import com.crypto.trade.service.cex.ApiKeyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    public List<ChatSession> getUserSessions(String userId) {
        if (null == userId || userId.trim().isEmpty()) {
            userId = DEFAULT_USER_ID;
        }
        return sessionRepository.findActiveSessionsByUserId(userId);
    }

    @Transactional
    public ChatSession createNewSession(String userId, String sessionName) {
        if (null == userId || userId.trim().isEmpty()) {
            userId = DEFAULT_USER_ID;
        }
        String actualSessionName = null != sessionName ? sessionName
                : "新对话 " + LocalDateTime.now().getHour() + ":" + LocalDateTime.now().getMinute() + ":" + LocalDateTime.now().getSecond();
        LocalDateTime now = LocalDateTime.now();
        ChatSession newSession = new ChatSession();
        newSession.setUserId(userId);
        newSession.setSessionName(actualSessionName);
        newSession.setStatus("active");
        // 使用"大模型配置"的默认模型
        String defaultModelName = unifiedModelFactory.getDefaultModelConfig().getModelId();
        newSession.setModelName(defaultModelName);
        newSession.setCreatedTime(now);
        newSession.setUpdatedTime(now);
        ChatSession savedSession = sessionRepository.save(newSession);
        log.debug("创建新聊天会话 - sessionId: {}, userId: {}, sessionName: {}, model: {}",
                savedSession.getSessionId(), userId, actualSessionName, defaultModelName);
        return savedSession;
    }

    public Optional<ChatSession> getLatestSession(String userId) {
        if (null == userId || userId.trim().isEmpty()) {
            userId = DEFAULT_USER_ID;
        }
        return sessionRepository.findLatestActiveSessionByUserId(userId);
    }

    public List<ChatMessage> getSessionMessages(Long sessionId) {
        return messageRepository.findMessagesBySessionId(sessionId);
    }

    @Transactional
    public ChatMessage sendMessage(Long sessionId, String userMessage, String userId) {
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
                session = createNewSession(userId, "默认对话");
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

            // 添加系统提示
            messages.add(UnifiedModelFactory.Message.system(TRADING_SYSTEM_PROMPT));

            // 添加历史消息
            for (ChatMessage msg : historyMessages) {
                if ("user".equals(msg.getRole())) {
                    messages.add(UnifiedModelFactory.Message.user(msg.getContent()));
                } else if ("assistant".equals(msg.getRole())) {
                    messages.add(UnifiedModelFactory.Message.assistant(msg.getContent()));
                }
            }

            // 添加当前用户问题
            messages.add(UnifiedModelFactory.Message.user(userMessage));

            log.debug("构建的对话上下文 - 消息数: {}", messages.size());

            // 生成审计会话ID
            String auditSessionId = auditLogger.generateSessionId();

            // 调用AI模型生成回复
            long startTime = System.currentTimeMillis();
            try {
                // 强制使用大模型配置的默认模型，忽略会话中的模型设置
                String aiResponse;
                String actualModelName; // 记录实际使用的模型名称

                var defaultConfig = unifiedModelFactory.getDefaultModelConfig();
                if (null != defaultConfig) {
                    aiResponse = unifiedModelFactory.callWithConfigAndMessages(messages, defaultConfig);
                    actualModelName = defaultConfig.getModelId();
                    log.info("强制使用默认模型: {}", actualModelName);
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
        List<ChatSession> sessions = getUserSessions(userId);
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
    public ChatSessionModel createSessionModel(String sessionName, String userId) {
        ChatSession session = createNewSession(userId, sessionName);
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
     * @param sessionId 会话ID
     * @param message   消息内容
     * @param userId    用户ID
     * @return SendMessageResponseModel
     */
    public SendMessageResponseModel sendMessageModel(Long sessionId, String message, String userId) {
        try {
            ChatMessage assistantMessage = sendMessage(sessionId, message, userId);
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

}
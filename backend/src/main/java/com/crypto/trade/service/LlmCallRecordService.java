package com.crypto.trade.service;

import com.crypto.trade.entity.ChatSession;
import com.crypto.trade.entity.LlmCallRecord;
import com.crypto.trade.repository.ChatSessionRepository;
import com.crypto.trade.repository.LlmCallRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * LlmCallRecordService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmCallRecordService {

    private final LlmCallRecordRepository llmCallRecordRepository;
    private final ChatSessionRepository chatSessionRepository;

//    /**
//     * 创建新的调用记录（调用开始时）
//     *
//     * @param apiKeyId      API密钥ID
//     * @param modelName     模型名称
//     * @param promptContent Prompt内容
//     * @param sessionId     会话ID（可选，用于多轮对话）
//     * @param parentId      父调用ID（可选，用于多轮对话）
//     * @param callSource    调用来源（SCHEDULED/DIRECT/MANUAL）
//     * @return 创建的记录实体
//     */
//    public LlmCallRecord createCallRecord(Long apiKeyId, String modelName, String promptContent,
//                                          String sessionId, Long parentId, String callSource) {
//        // 第1轮对话,roundNumber默认为1
//        return createCallRecord(apiKeyId, modelName, promptContent, sessionId, parentId, 1, callSource);
//    }

    /**
     * 创建新的调用记录（调用开始时）- 使用Long类型的sessionId
     * 此方法用于与ChatSession建立关联
     *
     * @param apiKeyId           API密钥ID
     * @param modelName          模型名称
     * @param sessionId          会话ID（Long类型，关联ChatSession.sessionId）
     * @param parentId           父调用ID（可选，用于多轮对话）
     * @param callSource         调用来源（SCHEDULED/DIRECT/MANUAL）
     * @param userMessageId      用户消息ID（可选，用于关联ChatMessage）
     * @param assistantMessageId 助手消息ID（可选，用于关联ChatMessage）
     * @return 创建的记录实体
     */
    public LlmCallRecord createCallRecord(Long apiKeyId, String modelName,
                                          Long sessionId, Long parentId, String callSource,
                                          Long userMessageId, Long assistantMessageId) {
        // 第1轮对话,roundNumber默认为1
        return createCallRecord(apiKeyId, modelName, sessionId, parentId, 1, callSource,
                userMessageId, assistantMessageId);
    }

    /**
     * 根据ID获取调用记录
     *
     * @param id 记录ID
     * @return 记录实体Optional
     */
    public Optional<LlmCallRecord> getRecordById(Long id) {
        return llmCallRecordRepository.findById(id);
    }

    /**
     * [新增] 创建新的调用记录（支持指定轮次）
     *
     * @param apiKeyId    API密钥ID
     * @param modelName   模型名称
     * @param sessionId   会话ID（可选，用于多轮对话）
     * @param parentId    父调用ID（可选，用于多轮对话）
     * @param roundNumber 对话轮次
     * @param callSource  调用来源（SCHEDULED/DIRECT/MANUAL）
     * @return 创建的记录实体
     */
    public LlmCallRecord createCallRecord(Long apiKeyId, String modelName,
                                          String sessionId, Long parentId, Integer roundNumber, String callSource) {
        log.debug("创建LLM调用记录 - apiKeyId: {}, modelName: {}, sessionId: {}, parentId: {}, roundNumber: {}, callSource: {}",
                apiKeyId, modelName, sessionId, parentId, roundNumber, callSource);

        // 将String类型的sessionId转换为Long类型
        Long sessionIdLong = null;
        if (sessionId != null) {
            try {
                sessionIdLong = Long.parseLong(sessionId);
            } catch (NumberFormatException e) {
                log.warn("无法将sessionId转换为Long: {}, 将使用null", sessionId);
            }
        }

        // 获取调用次数
        Integer callCount = getNextCallCount(apiKeyId);

        // 构建记录实体
        LlmCallRecord record = LlmCallRecord.builder()
                .apiKeyId(apiKeyId)
                .modelName(modelName)
                .sessionId(sessionIdLong)  // 使用转换后的Long类型
                .parentId(parentId)
                .callCount(callCount)
                .roundNumber(roundNumber)
                .callSource(callSource)
                .conversationState("PROCESSING")
                .status("PROCESSING")
                .callStartTime(LocalDateTime.now())
                .isExecuted(false)
                .build();

        // 标记为处理中
        record.markAsProcessing();

        // 保存并返回
        LlmCallRecord saved = llmCallRecordRepository.save(record);
        log.info("LLM调用记录已创建 - id: {}, callCount: {}, roundNumber: {}", saved.getId(), callCount, roundNumber);

        return saved;
    }

    /**
     * [新增] 创建新的调用记录（支持指定轮次）- 使用Long类型的sessionId
     * 此方法用于与ChatSession建立关联
     *
     * @param apiKeyId           API密钥ID
     * @param modelName          模型名称
     * @param sessionId          会话ID（Long类型，关联ChatSession.sessionId）
     * @param parentId           父调用ID（可选，用于多轮对话）
     * @param roundNumber        对话轮次
     * @param callSource         调用来源（SCHEDULED/DIRECT/MANUAL）
     * @param userMessageId      用户消息ID（可选，用于关联ChatMessage）
     * @param assistantMessageId 助手消息ID（可选，用于关联ChatMessage）
     * @return 创建的记录实体
     */
    public LlmCallRecord createCallRecord(Long apiKeyId, String modelName,
                                          Long sessionId, Long parentId, Integer roundNumber, String callSource,
                                          Long userMessageId, Long assistantMessageId) {
        log.debug("创建LLM调用记录(Long sessionId) - apiKeyId: {}, modelName: {}, sessionId: {}, parentId: {}, roundNumber: {}, callSource: {}, userMsgId: {}, assistantMsgId: {}",
                apiKeyId, modelName, sessionId, parentId, roundNumber, callSource, userMessageId, assistantMessageId);

        // 获取调用次数
        Integer callCount = getNextCallCount(apiKeyId);

        // 构建记录实体
        LlmCallRecord record = LlmCallRecord.builder()
                .apiKeyId(apiKeyId)
                .modelName(modelName)
                .sessionId(sessionId)
                .parentId(parentId)
                .callCount(callCount)
                .roundNumber(roundNumber)
                .callSource(callSource)
                .conversationState("PROCESSING")
                .status("PROCESSING")
                .callStartTime(LocalDateTime.now())
                .isExecuted(false)
                .userMessageId(userMessageId)
                .assistantMessageId(assistantMessageId)
                .build();

        // 标记为处理中
        record.markAsProcessing();

        // 保存并返回
        LlmCallRecord saved = llmCallRecordRepository.save(record);
        log.info("LLM调用记录(Long sessionId)已创建 - id: {}, callCount: {}, roundNumber: {}", saved.getId(), callCount, roundNumber);

        return saved;
    }

    /**
     * 更新调用记录为成功（响应返回时）
     *
     * @param recordId           记录ID
     * @param responseContent    响应内容（仅在ClearVisionUtils处理有变化时存储，否则为空）
     * @param decisionAction     决策动作（可选）
     * @param targetInstId       目标合约（可选）
     * @param decisionPrice      决策价格（可选）
     * @param decisionQuantity   决策数量（可选）
     * @param decisionConfidence 决策置信度（可选）
     * @param callStartTime      AI调用开始时间（可选，用于准确计算处理时间）
     * @param userMessageId      用户消息ID（可选，用于关联ChatMessage）
     * @param assistantMessageId 助手消息ID（可选，用于关联ChatMessage）
     */
    public void updateCallRecordSuccess(Long recordId, String responseContent,
                                        String decisionAction, String targetInstId,
                                        BigDecimal decisionPrice, BigDecimal decisionQuantity,
                                        BigDecimal decisionConfidence,
                                        LocalDateTime callStartTime,
                                        Long userMessageId, Long assistantMessageId) {
        log.debug("更新LLM调用记录为成功 - recordId: {}, responseContent: {}, userMsgId: {}, assistantMsgId: {}",
                recordId, responseContent != null ? "有" : "无", userMessageId, assistantMessageId);

        Optional<LlmCallRecord> recordOpt = llmCallRecordRepository.findById(recordId);
        if (recordOpt.isEmpty()) {
            log.error("LLM调用记录不存在 - recordId: {}", recordId);
            return;
        }

        LlmCallRecord record = recordOpt.get();

        // 如果提供了准确的AI调用开始时间，使用它
        if (callStartTime != null) {
            record.setCallStartTime(callStartTime);
        }

        // 更新消息ID关联
        if (userMessageId != null) {
            record.setUserMessageId(userMessageId);
        }
        if (assistantMessageId != null) {
            record.setAssistantMessageId(assistantMessageId);
        }

        // 更新处理后的响应内容（只在有变化时存储）
        if (responseContent != null && !responseContent.isEmpty()) {
            record.setResponseContent(responseContent);
        } else {
            record.setResponseContent(null);  // 清空，表示无变化
        }

        // 更新决策信息（如果有）
        if (decisionAction != null) {
            record.setDecisionInfo(decisionAction, targetInstId, decisionPrice,
                    decisionQuantity, decisionConfidence);
        }

        // 标记为成功（会设置callEndTime并计算processingTimeMs）
        record.markAsSuccess();

        llmCallRecordRepository.save(record);
        log.debug("LLM调用记录已更新为成功 - recordId: {}, processingTime: {}ms",
                recordId, record.getProcessingTimeMs());
    }

    /**
     * 更新调用记录为成功(包含详细耗时信息)
     * <p>
     * 在原updateCallRecordSuccess基础上,增加三个耗时参数。
     * </p>
     *
     * @param recordId               记录ID
     * @param responseContent        响应内容（仅在ClearVisionUtils处理有变化时存储，否则为空）
     * @param decisionAction         决策动作
     * @param targetInstId           目标合约
     * @param decisionPrice          决策价格
     * @param decisionQuantity       决策数量
     * @param decisionConfidence     决策置信度
     * @param callStartTime          AI调用开始时间
     * @param promptGenerationTimeMs Prompt生成耗时(毫秒)
     * @param llmCallTimeMs          大模型调用耗时(毫秒)
     * @param postActionTimeMs       后置动作耗时(毫秒)
     * @param userMessageId          用户消息ID（可选，用于关联ChatMessage）
     * @param assistantMessageId     助手消息ID（可选，用于关联ChatMessage）
     */
    public void updateCallRecordSuccess(Long recordId, String responseContent,
                                        String decisionAction, String targetInstId, BigDecimal decisionPrice,
                                        BigDecimal decisionQuantity, BigDecimal decisionConfidence,
                                        LocalDateTime callStartTime, Long promptGenerationTimeMs,
                                        Long llmCallTimeMs, Long postActionTimeMs,
                                        Long userMessageId, Long assistantMessageId) {
        log.debug("更新LLM调用记录为成功(含耗时) - recordId: {}, prompt耗时: {}ms, llm耗时: {}ms, post耗时: {}ms",
                recordId, promptGenerationTimeMs, llmCallTimeMs, postActionTimeMs);

        // 先调用原方法更新基本信息
        updateCallRecordSuccess(recordId, responseContent, decisionAction, targetInstId,
                decisionPrice, decisionQuantity, decisionConfidence, callStartTime, userMessageId, assistantMessageId);

        // 再更新耗时字段
        Optional<LlmCallRecord> recordOpt = llmCallRecordRepository.findById(recordId);
        if (recordOpt.isPresent()) {
            LlmCallRecord record = recordOpt.get();
            record.setPromptGenerationTimeMs(promptGenerationTimeMs);
            record.setLlmCallTimeMs(llmCallTimeMs);
            record.setPostActionTimeMs(postActionTimeMs);
            llmCallRecordRepository.save(record);
            log.debug("LLM调用记录耗时字段已更新 - recordId: {}", recordId);
        }
    }

    /**
     * [关键] 在独立事务中更新调用记录为成功
     * <p>
     * 使用REQUIRES_NEW传播级别,确保响应立即提交到数据库,不受外部事务回滚影响。
     * 这是解决"多轮对话失败导致第一轮响应丢失"问题的核心方法。
     * </p>
     * <p>
     * 执行时机: AI响应返回后立即调用,确保响应入库后再进行后续逻辑。
     * </p>
     *
     * @param recordId           记录ID
     * @param responseContent    响应内容（仅在ClearVisionUtils处理有变化时存储，否则为空）
     * @param decisionAction     决策动作（可选）
     * @param targetInstId       目标合约（可选）
     * @param decisionPrice      决策价格（可选）
     * @param decisionQuantity   决策数量（可选）
     * @param decisionConfidence 决策置信度（可选）
     * @param callStartTime      AI调用开始时间（可选）
     * @param userMessageId      用户消息ID（可选，用于关联ChatMessage）
     * @param assistantMessageId 助手消息ID（可选，用于关联ChatMessage）
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void updateCallRecordSuccessInNewTransaction(Long recordId, String responseContent,
                                                        String decisionAction, String targetInstId, BigDecimal decisionPrice,
                                                        BigDecimal decisionQuantity, BigDecimal decisionConfidence,
                                                        LocalDateTime callStartTime,
                                                        Long userMessageId, Long assistantMessageId) {
        log.info("【独立事务】更新LLM调用记录为成功 - recordId: {}, response长度: {}, userMsgId: {}, assistantMsgId: {}",
                recordId,
                responseContent != null ? responseContent.length() : 0,
                userMessageId, assistantMessageId);

        // 调用普通更新方法
        updateCallRecordSuccess(recordId, responseContent, decisionAction, targetInstId,
                decisionPrice, decisionQuantity, decisionConfidence, callStartTime, userMessageId, assistantMessageId);

        log.debug("【独立事务】LLM调用记录已提交 - recordId: {}", recordId);
    }

    /**
     * 更新调用记录为成功(包含详细耗时信息)
     * <p>
     * 在原updateCallRecordSuccessInNewTransaction基础上,增加三个耗时参数。
     * 用于记录Prompt生成、LLM调用和后置处理的细分耗时。
     * </p>
     *
     * @param recordId               记录ID
     * @param responseContent        响应内容（仅在ClearVisionUtils处理有变化时存储，否则为空）
     * @param decisionAction         决策动作
     * @param targetInstId           目标合约
     * @param decisionPrice          决策价格
     * @param decisionQuantity       决策数量
     * @param decisionConfidence     决策置信度
     * @param callStartTime          调用开始时间
     * @param promptGenerationTimeMs Prompt生成耗时(毫秒)
     * @param llmCallTimeMs          大模型调用耗时(毫秒)
     * @param postActionTimeMs       后置动作耗时(毫秒)
     * @param userMessageId          用户消息ID（可选，用于关联ChatMessage）
     * @param assistantMessageId     助手消息ID（可选，用于关联ChatMessage）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void updateCallRecordSuccessInNewTransaction(Long recordId, String responseContent,
                                                        String decisionAction, String targetInstId, BigDecimal decisionPrice,
                                                        BigDecimal decisionQuantity, BigDecimal decisionConfidence,
                                                        LocalDateTime callStartTime, Long promptGenerationTimeMs,
                                                        Long llmCallTimeMs, Long postActionTimeMs,
                                                        Long userMessageId, Long assistantMessageId) {
        log.debug("【独立事务】更新LLM调用记录为成功(带耗时) - recordId: {}, response长度: {}, prompt耗时: {}ms, llm耗时: {}ms, post耗时: {}ms, userMsgId: {}, assistantMsgId: {}",
                recordId, responseContent != null ? responseContent.length() : 0, promptGenerationTimeMs, llmCallTimeMs,
                postActionTimeMs, userMessageId, assistantMessageId);

        // 调用内部更新方法,传入耗时信息
        updateCallRecordSuccess(recordId, responseContent, decisionAction, targetInstId,
                decisionPrice, decisionQuantity, decisionConfidence, callStartTime, promptGenerationTimeMs, llmCallTimeMs,
                postActionTimeMs, userMessageId, assistantMessageId);

        log.debug("【独立事务】LLM调用记录(含耗时)已提交 - recordId: {}", recordId);
    }

    /**
     * 更新调用记录为失败（调用失败时）
     *
     * @param recordId     记录ID
     * @param errorMessage 错误信息
     */
    public void updateCallRecordFailed(Long recordId, String errorMessage) {
        log.debug("更新LLM调用记录为失败 - recordId: {}, error: {}", recordId, errorMessage);

        Optional<LlmCallRecord> recordOpt = llmCallRecordRepository.findById(recordId);
        if (recordOpt.isEmpty()) {
            log.error("LLM调用记录不存在 - recordId: {}", recordId);
            return;
        }

        LlmCallRecord record = recordOpt.get();

        // 标记为失败
        record.markAsFailed(errorMessage);

        llmCallRecordRepository.save(record);
        log.debug("LLM调用记录已更新为失败 - recordId: {}", recordId);
    }

    /**
     * 标记决策为已执行
     *
     * @param recordId 记录ID
     */
    public void markAsExecuted(Long recordId) {
        log.debug("标记LLM调用记录为已执行 - recordId: {}", recordId);

        Optional<LlmCallRecord> recordOpt = llmCallRecordRepository.findById(recordId);
        if (recordOpt.isEmpty()) {
            log.error("LLM调用记录不存在 - recordId: {}", recordId);
            return;
        }

        LlmCallRecord record = recordOpt.get();
        record.setIsExecuted(true);

        llmCallRecordRepository.save(record);
        log.debug("LLM调用记录已标记为已执行 - recordId: {}", recordId);
    }

    /**
     * 根据API Key ID查询调用历史
     *
     * @param apiKeyId API密钥ID
     * @param limit    返回数量限制
     * @return 调用记录列表
     */
    public List<LlmCallRecord> getCallHistory(Long apiKeyId, Integer limit) {
        return getCallHistory(apiKeyId, limit, null, null, null, null);
    }

    /**
     * 根据API Key ID查询调用历史(支持动作筛选)
     *
     * @param apiKeyId     API密钥ID
     * @param limit        返回数量限制
     * @param actionFilter 决策动作筛选(BUY/SELL/HOLD/ATTENTION/CANCEL_ORDER)
     * @return 调用记录列表
     */
    public List<LlmCallRecord> getCallHistory(Long apiKeyId, Integer limit, String actionFilter) {
        return getCallHistory(apiKeyId, limit, actionFilter, null, null, null);
    }

    /**
     * 根据API Key ID查询调用历史(支持多重筛选)
     *
     * @param apiKeyId         API密钥ID
     * @param limit            返回数量限制
     * @param actionFilter     决策动作筛选(BUY/SELL/HOLD/ATTENTION/CANCEL_ORDER)
     * @param modelFilter      AI模型筛选
     * @param callSourceFilter 调用来源筛选(SCHEDULED/DIRECT/MANUAL)
     * @param openCloseFilter  开平仓筛选(OPEN/CLOSE)
     * @return 调用记录列表
     */
    public List<LlmCallRecord> getCallHistory(Long apiKeyId, Integer limit, String actionFilter, String modelFilter, String callSourceFilter, String openCloseFilter) {
        log.debug("查询LLM调用历史 - apiKeyId: {}, limit: {}, actionFilter: {}, modelFilter: {}, callSourceFilter: {}, openCloseFilter: {}",
                apiKeyId, limit, actionFilter, modelFilter, callSourceFilter, openCloseFilter);

        PageRequest pageRequest = PageRequest.of(0, limit != null ? limit : 50);

        List<LlmCallRecord> records;

        // 优先级: (actionFilter + openCloseFilter) > (modelFilter + callSourceFilter) > 无筛选
        if ((actionFilter != null && !actionFilter.isEmpty()) || (openCloseFilter != null && !openCloseFilter.isEmpty())) {
            // 将字符串openCloseFilter转换为OpenCloseType枚举
            com.crypto.trade.enums.OpenCloseType openCloseTypeEnum = null;
            if (openCloseFilter != null && !openCloseFilter.isEmpty()) {
                try {
                    // 尝试从code转换（支持"open"、"close"、"OPEN"、"CLOSE"等）
                    // fromCode方法支持大小写不敏感的转换
                    openCloseTypeEnum = com.crypto.trade.enums.OpenCloseType.fromCode(openCloseFilter.toLowerCase());
                    log.debug("成功转换openCloseFilter: {} -> {}", openCloseFilter, openCloseTypeEnum);
                } catch (Exception e) {
                    log.warn("无法转换openCloseFilter: {}, 将忽略此筛选条件", openCloseFilter);
                }
            }

            // 按动作和/或开平仓筛选(使用TradeAction JOIN查询)
            records = llmCallRecordRepository.findByApiKeyIdAndFiltersFromTradeAction(
                    apiKeyId, actionFilter, openCloseTypeEnum, pageRequest);
        } else if (modelFilter != null && !modelFilter.isEmpty() && callSourceFilter != null && !callSourceFilter.isEmpty()) {
            // 同时按模型和来源筛选
            records = llmCallRecordRepository.findByApiKeyIdAndModelNameAndCallSource(
                    apiKeyId, modelFilter, callSourceFilter, pageRequest);
        } else if (modelFilter != null && !modelFilter.isEmpty()) {
            // 只按模型筛选
            records = llmCallRecordRepository.findByApiKeyIdAndModelName(
                    apiKeyId, modelFilter, pageRequest);
        } else if (callSourceFilter != null && !callSourceFilter.isEmpty()) {
            // 只按来源筛选
            records = llmCallRecordRepository.findByApiKeyIdAndCallSource(
                    apiKeyId, callSourceFilter, pageRequest);
        } else {
            // 无筛选条件
            records = llmCallRecordRepository.findByApiKeyIdOrderByCallStartTimeDesc(apiKeyId, pageRequest);
        }

        log.debug("查询到 {} 条LLM调用历史记录", records.size());
        return records;
    }

    /**
     * 根据会话ID查询所有轮次
     *
     * @param sessionId 会话ID(Long类型)
     * @return 调用记录列表（按时间升序）
     */
    public List<LlmCallRecord> getConversationHistory(Long sessionId) {
        log.debug("查询会话历史 - sessionId: {}", sessionId);

        List<LlmCallRecord> records = llmCallRecordRepository
                .findBySessionIdOrderByCallStartTimeAsc(sessionId);

        log.debug("查询到 {} 条会话历史记录", records.size());
        return records;
    }

    /**
     * 根据ID查询记录
     *
     * @param recordId 记录ID
     * @return 记录实体
     */
    public Optional<LlmCallRecord> getCallRecord(Long recordId) {
        return llmCallRecordRepository.findById(recordId);
    }

    /**
     * 查询指定API Key的最新记录
     *
     * @param apiKeyId API密钥ID
     * @return 最新记录
     */
    public Optional<LlmCallRecord> getLatestRecord(Long apiKeyId) {
        return llmCallRecordRepository.findLatestByApiKeyId(apiKeyId);
    }

    // ==================== 多轮对话相关方法 ====================

    /**
     * 获取会话的轮次数
     *
     * @param sessionId 会话ID(Long类型)
     * @return 轮次数
     */
    public int getConversationRoundCount(Long sessionId) {
        List<LlmCallRecord> records = llmCallRecordRepository.findBySessionIdOrderByCallStartTimeAsc(sessionId);
        return records.size();
    }

    /**
     * 获取会话的最后一轮记录
     *
     * @param sessionId 会话ID(Long类型)
     * @return 最后一轮记录
     */
    public Optional<LlmCallRecord> getLastTurnRecord(Long sessionId) {
        List<LlmCallRecord> records = llmCallRecordRepository.findBySessionIdOrderByCallStartTimeAsc(sessionId);
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(records.size() - 1));
    }

    /**
     * 更新会话状态为已完成
     *
     * @param sessionId 会话ID(Long类型)
     */
    public void markConversationAsCompleted(Long sessionId) {
        List<LlmCallRecord> records = llmCallRecordRepository.findBySessionIdOrderByCallStartTimeAsc(sessionId);
        for (LlmCallRecord record : records) {
            record.setConversationState("COMPLETED");
            llmCallRecordRepository.save(record);
        }
        log.debug("会话已标记为完成 - sessionId: {}, 轮次数: {}", sessionId, records.size());
    }

    /**
     * 更新会话状态为已终止
     *
     * @param sessionId 会话ID(Long类型)
     * @param reason    终止原因
     */
    public void markConversationAsTerminated(Long sessionId, String reason) {
        List<LlmCallRecord> records = llmCallRecordRepository.findBySessionIdOrderByCallStartTimeAsc(sessionId);
        for (LlmCallRecord record : records) {
            record.setConversationState("TERMINATED");
            record.setStatus("FAILED");
            llmCallRecordRepository.save(record);
        }
        log.debug("会话已标记为终止 - sessionId: {}, 原因: {}, 轮次数: {}",
                sessionId, reason, records.size());
    }

    /**
     * [新增] 标记会话中所有PROCESSING状态的记录为TERMINATED
     * <p>
     * 用于多轮对话中断时,确保所有记录状态正确,不会出现悬空的PROCESSING记录。
     * </p>
     *
     * @param sessionId 会话ID(Long类型)
     * @param reason    终止原因
     */
    public void markAllProcessingAsTerminated(Long sessionId, String reason) {
        try {
            List<LlmCallRecord> allRecords = llmCallRecordRepository
                    .findBySessionIdOrderByCallStartTimeAsc(sessionId);

            int markedCount = 0;
            for (LlmCallRecord record : allRecords) {
                if ("PROCESSING".equals(record.getStatus())
                        || "PROCESSING".equals(record.getConversationState())) {
                    record.setConversationState("TERMINATED");
                    record.setStatus("FAILED");

                    // 保留原有错误信息,追加终止原因
                    String originalError = record.getErrorMessage();
                    if (null != originalError && !originalError.trim().isEmpty()) {
                        record.setErrorMessage(originalError + "; 会话终止: " + reason);
                    } else {
                        record.setErrorMessage("会话终止: " + reason);
                    }

                    llmCallRecordRepository.save(record);
                    markedCount++;
                }
            }

            log.debug("标记PROCESSING记录为TERMINATED - sessionId: {}, 标记数量: {}, 总记录数: {}, 原因: {}",
                    sessionId, markedCount, allRecords.size(), reason);

        } catch (Exception e) {
            log.error("标记PROCESSING记录失败 - sessionId: {}, reason: {}", sessionId, reason, e);
            // 不抛出异常,确保主流程不受影响
        }
    }

    /**
     * 查询超时的处理中记录
     *
     * @param timeoutMinutes 超时分钟数
     * @return 超时记录列表
     */
    public List<LlmCallRecord> getTimeoutRecords(Integer timeoutMinutes) {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        return llmCallRecordRepository.findProcessingRecords(timeoutThreshold);
    }

    /**
     * 获取会话链路
     * 根据记录ID递归查询完整的会话链路(从最顶层父级到所有后代记录)
     *
     * @param recordId 当前记录ID
     * @return 按时间升序排列的完整会话链路
     */
    public List<LlmCallRecord> getConversationChain(Long recordId) {
        log.debug("开始获取完整会话链路 - recordId: {}", recordId);

        if (null == recordId) {
            log.warn("记录ID为空");
            return new ArrayList<>();
        }

        // 步骤1: 向上查找最顶层父级
        Long rootId = findRootRecord(recordId);
        log.debug("找到最顶层父级 - rootId: {}", rootId);

        // 步骤2: 从root到recordId的链路
        List<LlmCallRecord> chainToRecord = getChainFromRoot(rootId, recordId);
        log.debug("父级链路长度: {}", chainToRecord.size());

        // 步骤3: 查询recordId的所有后代记录
        List<LlmCallRecord> descendants = new ArrayList<>();
        findAllDescendants(recordId, descendants);
        log.debug("后代记录数量: {}", descendants.size());

        // 步骤4: 合并并按时间排序
        List<LlmCallRecord> completeChain = new ArrayList<>();
        completeChain.addAll(chainToRecord);
        completeChain.addAll(descendants);

        // 按调用时间升序排序
        completeChain.sort(Comparator.comparing(LlmCallRecord::getCallStartTime));

        log.debug("获取完整会话链路成功 - recordId: {}, rootId: {}, 总记录数: {}",
                recordId, rootId, completeChain.size());
        return completeChain;
    }

    /**
     * 查找最顶层父级记录ID
     * 递归向上追溯,直到parentId为null
     *
     * @param recordId 起始记录ID
     * @return 最顶层父级记录ID
     */
    private Long findRootRecord(Long recordId) {
        Long currentId = recordId;
        Set<Long> visited = new HashSet<>(); // 防止循环引用

        while (currentId != null) {
            // 检查循环引用
            if (visited.contains(currentId)) {
                log.warn("检测到循环引用 - recordId: {}", currentId);
                break;
            }
            visited.add(currentId);

            // 查询当前记录
            LlmCallRecord current = llmCallRecordRepository.findById(currentId)
                    .orElse(null);

            if (null == current) {
                log.warn("记录不存在 - recordId: {}", currentId);
                break;
            }

            // 如果没有父级,当前就是最顶层
            if (current.getParentId() == null) {
                return currentId;
            }

            // 继续向上追溯
            currentId = current.getParentId();
        }

        return currentId;
    }

    /**
     * 查询从根记录到目标记录的链路
     *
     * @param rootId   根记录ID
     * @param targetId 目标记录ID
     * @return 从根到目标的记录列表
     */
    private List<LlmCallRecord> getChainFromRoot(Long rootId, Long targetId) {
        List<LlmCallRecord> chain = new ArrayList<>();

        // 从rootId开始,向下遍历到targetId
        Long currentId = rootId;
        Set<Long> visited = new HashSet<>();

        while (currentId != null && !visited.contains(currentId)) {
            visited.add(currentId);

            LlmCallRecord current = llmCallRecordRepository.findById(currentId)
                    .orElse(null);

            if (null == current) {
                break;
            }

            chain.add(current);

            // 到达目标记录
            if (currentId.equals(targetId)) {
                break;
            }

            // 查找当前记录的子记录中是否有targetId
            List<LlmCallRecord> children = llmCallRecordRepository
                    .findByParentIdOrderByCallStartTimeAsc(currentId);

            boolean found = false;
            for (LlmCallRecord child : children) {
                if (child.getId().equals(targetId) || isAncestorOf(child, targetId)) {
                    currentId = child.getId();
                    found = true;
                    break;
                }
            }

            if (!found) {
                break;
            }
        }

        return chain;
    }

    /**
     * 判断record是否是targetId的祖先
     *
     * @param record   记录
     * @param targetId 目标ID
     * @return 是否是祖先
     */
    private boolean isAncestorOf(LlmCallRecord record, Long targetId) {
        if (record.getId().equals(targetId)) {
            return true;
        }

        List<LlmCallRecord> children = llmCallRecordRepository
                .findByParentIdOrderByCallStartTimeAsc(record.getId());

        for (LlmCallRecord child : children) {
            if (isAncestorOf(child, targetId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 递归查找所有后代记录
     *
     * @param parentId 父记录ID
     * @param result   结果列表
     */
    private void findAllDescendants(Long parentId, List<LlmCallRecord> result) {
        // 查询直接子记录
        List<LlmCallRecord> children = llmCallRecordRepository
                .findByParentIdOrderByCallStartTimeAsc(parentId);

        for (LlmCallRecord child : children) {
            result.add(child);
            // 递归查找子记录的子记录
            findAllDescendants(child.getId(), result);
        }
    }

    /**
     * 获取下一个调用次数
     */
    private Integer getNextCallCount(Long apiKeyId) {
        Optional<LlmCallRecord> latest = llmCallRecordRepository.findLatestByApiKeyId(apiKeyId);
        return latest.map(record -> record.getCallCount() + 1).orElse(1);
    }

    /**
     * 生成会话ID（如果没有提供）
     *
     * @deprecated 此方法已废弃, 请使用createChatSession方法创建ChatSession并获取其ID
     */
    @Deprecated
    public String generateSessionId() {
        return "SESSION_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 创建新的ChatSession并返回其ID
     * 用于AI交易场景,建立LlmCallRecord与ChatSession的关联
     *
     * @param sessionName 会话名称(可选,为null时自动生成)
     * @param modelName   模型名称(可选)
     * @return ChatSession的ID
     */
    public Long createChatSession(String sessionName, String modelName) {
        // 如果没有提供会话名称,自动生成一个
        if (null == sessionName || sessionName.trim().isEmpty()) {
            sessionName = "AI交易会话_" + System.currentTimeMillis();
        }

        // 创建ChatSession实体
        ChatSession chatSession = ChatSession.builder()
                .sessionName(sessionName)
                .userId("ai_trading_bot") // AI交易机器人的会话
                .status("active")
                .modelName(modelName != null ? modelName : "")
                .build();

        // 保存到数据库
        ChatSession savedSession = chatSessionRepository.save(chatSession);

        log.debug("创建ChatSession成功 - sessionId: {}, sessionName: {}, modelName: {}",
                savedSession.getSessionId(), savedSession.getSessionName(), modelName);

        return savedSession.getSessionId();
    }

    /**
     * 根据记录ID获取调用来源
     *
     * @param recordId 记录ID
     * @return 调用来源, 如果记录不存在则返回"DIRECT"
     */
    public String getCallSourceById(Long recordId) {
        if (recordId == null) {
            return "DIRECT";
        }
        return llmCallRecordRepository.findById(recordId)
                .map(LlmCallRecord::getCallSource)
                .orElse("DIRECT");
    }
}

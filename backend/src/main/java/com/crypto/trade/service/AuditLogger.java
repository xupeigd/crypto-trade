package com.crypto.trade.service;

import com.crypto.trade.entity.LlmAuditLog;
import com.crypto.trade.repository.LlmAuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * AuditLogger
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class AuditLogger {

    /**
     * 专用审计日志记录器
     */
    private static final org.slf4j.Logger AUDIT_LOG = org.slf4j.LoggerFactory.getLogger("com.crypto.trade.audit");
    @Autowired
    private LlmAuditLogRepository auditLogRepository;

    /**
     * 记录成功的LLM调用审计信息
     *
     * @param sessionId        会话ID
     * @param callStatsId      调用统计ID
     * @param apiKeyId         API密钥ID
     * @param modelName        模型名称
     * @param promptContent    完整的prompt内容
     * @param aiResponse       AI原始响应内容
     * @param processingTimeMs 处理时间（毫秒）
     */
    public LlmAuditLog logSuccessCall(String sessionId, Long callStatsId, Long apiKeyId,
                                      String modelName, String promptContent, String aiResponse,
                                      Long processingTimeMs) {
        try {
            // 1. 记录完整内容到审计日志文件
            logFullContentToFile(sessionId, "TRADE_DECISION", apiKeyId, modelName, promptContent, aiResponse, processingTimeMs);

            // 2. 创建只包含元数据的审计日志实体
            String promptMetadata = createMetadataInfo(promptContent, null != promptContent ? promptContent.length() : 0);
            String responseMetadata = createMetadataInfo(aiResponse, null != aiResponse ? aiResponse.length() : 0);

            LlmAuditLog auditLog = LlmAuditLog.builder()
                    .sessionId(sessionId)
                    .callStatsId(callStatsId)
                    .apiKeyId(null == apiKeyId ? -1L : apiKeyId)
                    .modelName(modelName)
                    .promptContent(promptMetadata)
                    .aiResponse(responseMetadata)
                    .processingTimeMs(processingTimeMs)
                    .callStatus(LlmAuditLog.CallStatus.SUCCESS)
                    .build();

            // 保存元数据到数据库
            LlmAuditLog savedLog = auditLogRepository.save(auditLog);

            // 3. 记录元数据到审计日志文件（用于数据库查询和监控）
            logAuditToFile(savedLog);

            log.debug("成功记录LLM调用审计信息（分离方式） - sessionId: {}, callStatsId: {}, processingTime: {}ms",
                    sessionId, callStatsId, processingTimeMs);

            return savedLog;

        } catch (Exception e) {
            log.error("记录LLM调用审计信息失败 - sessionId: {}, callStatsId: {}", sessionId, callStatsId, e);
            // 即使数据库保存失败，也要记录到文件日志
            logFullContentToFile(sessionId, "TRADE_DECISION", apiKeyId, modelName, promptContent, aiResponse, processingTimeMs);
            return null;
        }
    }

    /**
     * 记录失败的LLM调用审计信息
     *
     * @param sessionId        会话ID
     * @param callStatsId      调用统计ID
     * @param apiKeyId         API密钥ID
     * @param modelName        模型名称
     * @param promptContent    完整的prompt内容
     * @param processingTimeMs 处理时间（毫秒）
     * @param errorMessage     错误信息
     */
    public LlmAuditLog logFailedCall(String sessionId, Long callStatsId, Long apiKeyId,
                                     String modelName, String promptContent, Long processingTimeMs,
                                     String errorMessage) {
        try {
            // 1. 记录完整内容到审计日志文件
            logFullContentToFile(sessionId, "TRADE_DECISION", apiKeyId, modelName, promptContent, null, processingTimeMs);

            // 2. 创建只包含元数据的审计日志实体
            String promptMetadata = createMetadataInfo(promptContent, null != promptContent ? promptContent.length() : 0);

            LlmAuditLog auditLog = LlmAuditLog.builder()
                    .sessionId(sessionId)
                    .callStatsId(null == callStatsId ? 0L : callStatsId)
                    .apiKeyId(null == apiKeyId ? 0L : apiKeyId)
                    .modelName(modelName)
                    .promptContent(promptMetadata)
                    .aiResponse("null")
                    .processingTimeMs(processingTimeMs)
                    .callStatus(LlmAuditLog.CallStatus.FAILED)
                    .errorMessage(errorMessage)
                    .build();

            // 保存元数据到数据库
            LlmAuditLog savedLog = auditLogRepository.save(auditLog);

            // 3. 记录元数据到审计日志文件（用于数据库查询和监控）
            logAuditToFile(savedLog);

            log.debug("成功记录LLM调用失败审计信息（分离方式） - sessionId: {}, callStatsId: {}, error: {}",
                    sessionId, callStatsId, errorMessage);

            return savedLog;

        } catch (Exception e) {
            log.error("记录LLM调用失败审计信息失败 - sessionId: {}, callStatsId: {}", sessionId, callStatsId, e);
            // 即使数据库保存失败，也要记录到文件日志
            logFullContentToFile(sessionId, "TRADE_DECISION", apiKeyId, modelName, promptContent, null, processingTimeMs);
            return null;
        }
    }

    /**
     * 生成唯一的会话ID
     *
     * @return 会话ID
     */
    public String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 记录完整的prompt和response内容到审计日志文件
     *
     * @param sessionId        会话ID
     * @param callType         调用类型（TRADE_DECISION/CHAT）
     * @param apiKeyId         API密钥ID
     * @param modelName        模型名称
     * @param promptContent    完整的prompt内容
     * @param aiResponse       完整的AI响应内容
     * @param processingTimeMs 处理时间（毫秒）
     */
    public void logFullContentToFile(String sessionId, String callType, Long apiKeyId,
                                     String modelName, String promptContent, String aiResponse,
                                     Long processingTimeMs) {
        try {
            // 创建完整内容的JSON日志
            StringBuilder logMessage = new StringBuilder();
            logMessage.append("{");
            logMessage.append("\"type\":\"FULL_CONTENT\",");
            logMessage.append("\"sessionId\":\"").append(escapeJson(sessionId)).append("\",");
            logMessage.append("\"timestamp\":\"").append(java.time.LocalDateTime.now()).append("\",");
            logMessage.append("\"callType\":\"").append(escapeJson(callType)).append("\",");
            logMessage.append("\"apiKeyId\":").append(apiKeyId).append(",");
            logMessage.append("\"modelName\":\"").append(escapeJson(modelName)).append("\",");
            logMessage.append("\"processingTimeMs\":").append(processingTimeMs).append(",");

            // 记录完整prompt信息
            if (null != promptContent) {
                String promptHash = calculateHash(promptContent);
                logMessage.append("\"prompt\":{");
                logMessage.append("\"length\":").append(promptContent.length()).append(",");
                logMessage.append("\"content\":\"").append(escapeJson(promptContent)).append("\",");
                logMessage.append("\"hash\":\"").append(promptHash).append("\"");
                logMessage.append("},");
            } else {
                logMessage.append("\"prompt\":null,");
            }

            // 记录完整response信息
            if (null != aiResponse) {
                String responseHash = calculateHash(aiResponse);
                logMessage.append("\"response\":{");
                logMessage.append("\"length\":").append(aiResponse.length()).append(",");
                logMessage.append("\"content\":\"").append(escapeJson(aiResponse)).append("\",");
                logMessage.append("\"hash\":\"").append(responseHash).append("\"");
                logMessage.append("}");
            } else {
                logMessage.append("\"response\":null");
            }

            logMessage.append("}");

            AUDIT_LOG.info("LLM_FULL_CONTENT {}", logMessage);
            log.debug("成功记录完整内容到审计日志文件 - sessionId: {}, promptLength: {}, responseLength: {}",
                    sessionId, null != promptContent ? promptContent.length() : 0, null != aiResponse ? aiResponse.length() : 0);

        } catch (Exception e) {
            log.error("记录完整内容到审计日志文件失败 - sessionId: {}", sessionId, e);
        }
    }

    /**
     * 计算字符串的MD5哈希值
     *
     * @param content 输入内容
     * @return MD5哈希值
     */
    private String calculateHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("计算哈希值失败", e);
            return "hash_failed";
        }
    }

    /**
     * 创建元数据信息字符串（用于数据库存储）
     *
     * @param content 内容
     * @param length  长度
     * @return 元数据字符串
     */
    private String createMetadataInfo(String content, int length) {
        if (null == content) {
            return "null";
        }
        String hash = calculateHash(content);
        return String.format("%d chars, hash:%s", length, hash);
    }

    /**
     * 记录审计日志到文件
     *
     * @param auditLog 审计日志实体
     */
    private void logAuditToFile(LlmAuditLog auditLog) {
        try {
            // 创建结构化JSON格式的日志
            StringBuilder logMessage = new StringBuilder();
            logMessage.append("{");
            logMessage.append("\"id\":").append(auditLog.getId()).append(",");
            logMessage.append("\"sessionId\":\"").append(escapeJson(auditLog.getSessionId())).append("\",");
            logMessage.append("\"callStatsId\":").append(auditLog.getCallStatsId()).append(",");
            logMessage.append("\"apiKeyId\":").append(auditLog.getApiKeyId()).append(",");
            logMessage.append("\"modelName\":\"").append(escapeJson(auditLog.getModelName())).append("\",");
            logMessage.append("\"callStatus\":\"").append(auditLog.getCallStatus().name()).append("\",");
            logMessage.append("\"processingTimeMs\":").append(auditLog.getProcessingTimeMs()).append(",");
            logMessage.append("\"promptLength\":").append(auditLog.getPromptContent() != null ? auditLog.getPromptContent().length() : 0).append(",");
            logMessage.append("\"responseLength\":").append(auditLog.getAiResponse() != null ? auditLog.getAiResponse().length() : 0).append(",");

            if (auditLog.getErrorMessage() != null) {
                logMessage.append("\"errorMessage\":\"").append(escapeJson(auditLog.getErrorMessage())).append("\",");
            }

            logMessage.append("\"createdTime\":\"").append(auditLog.getCreatedTime()).append("\"");
            logMessage.append("}");

            if (auditLog.getCallStatus() == LlmAuditLog.CallStatus.SUCCESS) {
                AUDIT_LOG.info("LLM_CALL_SUCCESS {}", logMessage);
            } else {
                AUDIT_LOG.error("LLM_CALL_FAILED {}", logMessage);
            }

        } catch (Exception e) {
            log.error("记录审计日志到文件失败", e);
        }
    }

    /**
     * 记录审计错误信息到文件（数据库保存失败时的降级方案）
     */
    private void logAuditErrorToFile(String sessionId, Long callStatsId, Long apiKeyId,
                                     String modelName, String promptContent, Long processingTimeMs,
                                     String errorMessage) {
        try {
            StringBuilder logMessage = new StringBuilder();
            logMessage.append("{");
            logMessage.append("\"sessionId\":\"").append(escapeJson(sessionId)).append("\",");
            logMessage.append("\"callStatsId\":").append(callStatsId).append(",");
            logMessage.append("\"apiKeyId\":").append(apiKeyId).append(",");
            logMessage.append("\"modelName\":\"").append(escapeJson(modelName)).append("\",");
            logMessage.append("\"callStatus\":\"FAILED\",");
            logMessage.append("\"processingTimeMs\":").append(processingTimeMs).append(",");
            logMessage.append("\"promptLength\":").append(null != promptContent ? promptContent.length() : 0).append(",");
            logMessage.append("\"errorMessage\":\"").append(escapeJson(errorMessage)).append("\",");
            logMessage.append("\"createdTime\":\"").append(java.time.LocalDateTime.now()).append("\"");
            logMessage.append("}");

            AUDIT_LOG.error("LLM_CALL_DB_SAVE_FAILED {}", logMessage);

        } catch (Exception e) {
            log.error("记录审计错误日志到文件失败", e);
        }
    }

    /**
     * 转义JSON字符串中的特殊字符
     *
     * @param str 原始字符串
     * @return 转义后的字符串
     */
    private String escapeJson(String str) {
        if (null == str) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
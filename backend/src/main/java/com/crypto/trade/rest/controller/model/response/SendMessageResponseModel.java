package com.crypto.trade.rest.controller.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SendMessageResponseModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SendMessageResponseModel {

    /**
     * 操作是否成功
     */
    Boolean success;

    /**
     * 响应消息
     */
    String message;

    /**
     * 会话ID
     */
    Long sessionId;

    /**
     * 消息ID
     */
    Long messageId;

    /**
     * 助手回复内容（如果已生成）
     */
    String assistantReply;

    /**
     * 使用的token数量
     */
    Integer tokensUsed;

    /**
     * 处理时间（毫秒）
     */
    Long processingTimeMs;

    /**
     * 创建成功响应
     *
     * @param sessionId        会话ID
     * @param messageId        消息ID
     * @param assistantReply   助手回复
     * @param tokensUsed       使用的token数量
     * @param processingTimeMs 处理时间
     * @return SendMessageResponseModel
     */
    public static SendMessageResponseModel success(Long sessionId, Long messageId,
                                                   String assistantReply, Integer tokensUsed, Long processingTimeMs) {
        return SendMessageResponseModel.builder()
                .success(true)
                .message("消息发送成功")
                .sessionId(sessionId)
                .messageId(messageId)
                .assistantReply(assistantReply)
                .tokensUsed(tokensUsed)
                .processingTimeMs(processingTimeMs)
                .build();
    }

    /**
     * 创建失败响应
     *
     * @param message 错误消息
     * @return SendMessageResponseModel
     */
    public static SendMessageResponseModel failure(String message) {
        return SendMessageResponseModel.builder()
                .success(false)
                .message(message)
                .build();
    }

    /**
     * 创建处理中响应
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     * @return SendMessageResponseModel
     */
    public static SendMessageResponseModel processing(Long sessionId, Long messageId) {
        return SendMessageResponseModel.builder()
                .success(true)
                .message("消息正在处理中")
                .sessionId(sessionId)
                .messageId(messageId)
                .build();
    }

    /**
     * 从ChatMessage创建响应
     *
     * @param message 聊天消息实体
     * @return SendMessageResponseModel
     */
    public static SendMessageResponseModel fromMessage(com.crypto.trade.entity.ChatMessage message) {
        if (null == message) {
            return failure("消息不存在");
        }
        return SendMessageResponseModel.builder()
                .success(true)
                .message("消息处理完成")
                .sessionId(message.getSession() != null ? message.getSession().getSessionId() : null)
                .messageId(message.getMessageId())
                .assistantReply("assistant".equals(message.getRole()) ? message.getContent() : null)
                .tokensUsed(message.getTokensUsed())
                .processingTimeMs(message.getProcessingTimeMs())
                .build();
    }

    /**
     * 判断是否成功
     *
     * @return true如果操作成功
     */
    public boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }

    /**
     * 判断是否有助手回复
     *
     * @return true如果有助手回复内容
     */
    public boolean hasAssistantReply() {
        return null != assistantReply && !assistantReply.trim().isEmpty();
    }

    /**
     * 获取处理时间描述
     *
     * @return 处理时间描述
     */
    public String getProcessingTimeText() {
        if (null == processingTimeMs || processingTimeMs == 0) {
            return null;
        }
        if (processingTimeMs < 1000) {
            return processingTimeMs + "ms";
        } else {
            return String.format("%.2fs", processingTimeMs / 1000.0);
        }
    }

    /**
     * 获取token使用情况描述
     *
     * @return token使用描述
     */
    public String getTokenUsageText() {
        if (null == tokensUsed || tokensUsed == 0) {
            return null;
        }
        return tokensUsed + " tokens";
    }
}
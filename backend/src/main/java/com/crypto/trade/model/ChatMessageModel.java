package com.crypto.trade.model;

import com.crypto.trade.entity.ChatMessage;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ChatMessageModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessageModel {

    /**
     * 消息ID
     */
    Long messageId;

    /**
     * 会话ID
     */
    Long sessionId;

    /**
     * 用户ID
     */
    String userId;

    /**
     * 消息角色：user/assistant/system
     */
    String role;

    /**
     * 消息内容
     */
    String content;

    /**
     * 使用的token数量
     */
    Integer tokensUsed;

    /**
     * 处理时间（毫秒）
     */
    Long processingTimeMs;


    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime createdTime;

    /**
     * 从Entity转换为Model
     *
     * @param entity ChatMessage实体
     * @return ChatMessageModel模型
     */
    public static ChatMessageModel fromEntity(ChatMessage entity) {
        if (null == entity) {
            return null;
        }

        return ChatMessageModel.builder()
                .messageId(entity.getMessageId())
                .sessionId(entity.getSession() != null ? entity.getSession().getSessionId() : null)
                .userId(entity.getSession() != null ? entity.getSession().getUserId() : "default")
                .role(entity.getRole())
                .content(entity.getContent())
                .tokensUsed(entity.getTokensUsed())
                .processingTimeMs(entity.getProcessingTimeMs())
                .createdTime(entity.getCreatedTime())
                .build();
    }

    /**
     * 判断是否为用户消息
     *
     * @return true如果角色为user
     */
    public boolean isUserMessage() {
        return "user".equals(role);
    }

    /**
     * 判断是否为助手消息
     *
     * @return true如果角色为assistant
     */
    public boolean isAssistantMessage() {
        return "assistant".equals(role);
    }

    /**
     * 判断是否为系统消息
     *
     * @return true如果角色为system
     */
    public boolean isSystemMessage() {
        return "system".equals(role);
    }


    /**
     * 获取角色显示名称
     *
     * @return 角色的中文描述
     */
    public String getRoleDisplayName() {
        if (null == role) {
            return "未知";
        }
        switch (role) {
            case "user":
                return "用户";
            case "assistant":
                return "助手";
            case "system":
                return "系统";
            default:
                return role;
        }
    }


    /**
     * 获取token使用情况描述
     *
     * @return token使用描述
     */
    public String getTokenUsageText() {
        if (null == tokensUsed || tokensUsed == 0) {
            return "无token消耗";
        }
        return "使用 " + tokensUsed + " tokens";
    }

    /**
     * 获取处理时间描述
     *
     * @return 处理时间描述
     */
    public String getProcessingTimeText() {
        if (null == processingTimeMs || processingTimeMs == 0) {
            return "处理时间未知";
        }
        if (processingTimeMs < 1000) {
            return processingTimeMs + "ms";
        } else {
            return String.format("%.2fs", processingTimeMs / 1000.0);
        }
    }

}
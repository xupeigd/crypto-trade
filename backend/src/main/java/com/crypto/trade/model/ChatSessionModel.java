package com.crypto.trade.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZoneId;

/**
 * ChatSessionModel
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
public class ChatSessionModel {

    /**
     * 会话ID
     */
    Long sessionId;

    /**
     * 会话名称
     */
    String sessionName;

    /**
     * 用户ID
     */
    String userId;

    /**
     * 会话状态：active/inactive
     */
    String status;

    /**
     * 使用的模型名称
     */
    String modelName;

    /**
     * 智能体ID（如果有）
     */
    Long agentId;


    /**
     * 创建时间
     */
    Long createdTime;

    /**
     * 更新时间
     */
    Long updatedTime;

    /**
     * 从Entity转换为Model
     *
     * @param entity ChatSession实体
     * @return ChatSessionModel模型
     */
    public static ChatSessionModel fromEntity(com.crypto.trade.entity.ChatSession entity) {
        if (null == entity) {
            return null;
        }

        // 安全转换时间字段,处理null值情况避免NPE
        Long createdTime = null;
        Long updatedTime = null;

        if (null != entity.getCreatedTime()) {
            createdTime = entity.getCreatedTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }

        if (null != entity.getUpdatedTime()) {
            updatedTime = entity.getUpdatedTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }

        return ChatSessionModel.builder()
                .sessionId(entity.getSessionId())
                .sessionName(entity.getSessionName())
                .userId(entity.getUserId())
                .status(entity.getStatus())
                .modelName(entity.getModelName())
                .agentId(entity.getAgentId())
                .createdTime(createdTime)
                .updatedTime(updatedTime)
                .build();
    }

    /**
     * 判断会话是否激活
     *
     * @return true如果状态为active
     */
    public boolean isActive() {
        return "active".equals(status);
    }

    /**
     * 获取状态显示名称
     *
     * @return 状态的中文描述
     */
    public String getStatusDisplayName() {
        if (null == status) {
            return "未知";
        }
        switch (status) {
            case "active":
                return "活跃";
            case "inactive":
                return "未激活";
            default:
                return status;
        }
    }

    /**
     * 获取显示用的会话名称
     *
     * @return 会话名称，如果为空则返回默认名称
     */
    public String getDisplayName() {
        if (null != sessionName && !sessionName.trim().isEmpty()) {
            return sessionName;
        }
        return "新会话 #" + sessionId;
    }

}
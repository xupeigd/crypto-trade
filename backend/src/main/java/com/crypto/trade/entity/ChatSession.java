package com.crypto.trade.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ChatSession
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_chat_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ChatSession {

    /**
     * 会话状态常量
     */
    public interface SessionStatus {
        String PRE_ACTIVE = "pre_active";
        String ACTIVE = "active";
        String ARCHIVED = "archived";
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "session_name", length = 100)
    private String sessionName;

    @Column(name = "user_id", length = 50)
    private String userId = "default";

    @Column(name = "status", length = 20)
    private String status = "active"; // active, archived

    @Column(name = "model_name", length = 50)
    private String modelName = "";

    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "created_time")
    private LocalDateTime createdTime;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    /**
     * JPA生命周期钩子 - 创建前自动设置时间戳
     * 确保新创建的实体始终有时间戳
     */
    @PrePersist
    protected void onCreate() {
        if (null == createdTime) {
            createdTime = LocalDateTime.now();
        }
        if (null == updatedTime) {
            updatedTime = LocalDateTime.now();
        }
    }

    /**
     * JPA生命周期钩子 - 更新前自动设置updated_time
     * 确保更新时updated_time始终被设置
     */
    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
    }
}
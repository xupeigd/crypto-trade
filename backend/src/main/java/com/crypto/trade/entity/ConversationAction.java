package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ConversationAction
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_conversation_actions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "decision_id", nullable = false)
    private String decisionId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType; // k_line, position_info, balance_info等

    @Column(name = "action_parameters", columnDefinition = "LONGTEXT")
    private String actionParameters; // JSON格式的参数

    @Column(name = "action_result", columnDefinition = "LONGTEXT")
    private String actionResult; // JSON格式的执行结果

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING"; // PENDING/SUCCESS/FAILED

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();

    @Column(name = "updated_time")
    private LocalDateTime updatedTime = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedTime = LocalDateTime.now();
    }
}
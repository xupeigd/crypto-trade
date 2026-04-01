package com.crypto.trade.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ChatMessage
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({
        "hibernateLazyInitializer",
        "handler",
        "session.hibernateLazyInitializer",
        "session.handler"
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false,
            foreignKey = @ForeignKey(name = "none", foreignKeyDefinition = ""))
    private ChatSession session;

    @Column(name = "role", length = 20, nullable = false)
    private String role; // user, assistant

    @Column(name = "content", columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "tool_call_id", length = 100)
    private String toolCallId; // tool调用ID

    @Column(name = "function_name", length = 100)
    private String functionName; // 调用的函数名

    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (null == createdTime) {
            createdTime = LocalDateTime.now();
        }
    }
}
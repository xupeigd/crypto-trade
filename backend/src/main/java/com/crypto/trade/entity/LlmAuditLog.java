package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * LlmAuditLog
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_llm_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 会话ID，用于关联同一次业务调用
     */
    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    /**
     * LLM调用统计ID，关联t_llm_call_stats表
     */
    @Column(name = "call_stats_id", nullable = false)
    private Long callStatsId;

    /**
     * API密钥ID，关联t_cex_api_keys表
     */
    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    /**
     * 使用的AI模型名称
     */
    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    /**
     * 完整的prompt内容
     */
    @Column(name = "prompt_content", nullable = false, columnDefinition = "LONGTEXT")
    private String promptContent;

    /**
     * AI原始响应内容
     */
    @Column(name = "ai_response", columnDefinition = "LONGTEXT")
    private String aiResponse;

    /**
     * 处理时间（毫秒）
     */
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    /**
     * 调用状态：SUCCESS/FAILED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "call_status", nullable = false, length = 10)
    private CallStatus callStatus;

    /**
     * 错误信息（仅在失败时有值）
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    /**
     * 调用状态枚举
     */
    public enum CallStatus {
        SUCCESS("成功"),
        FAILED("失败");

        private final String description;

        CallStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
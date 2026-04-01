package com.crypto.trade.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * AgentConfig
 * 智能体配置实体类
 *
 * @author page
 * @date 2026-03-09
 */

@Entity
@Table(name = "t_agent_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AgentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    /**
     * 绑定的工具列表（JSON数组格式，如 ["k_line"]）
     */
    @Column(name = "tools", columnDefinition = "TEXT")
    private String tools;

    /**
     * 绑定的技能ID列表（JSON数组格式，如 [1, 2, 3]）
     */
    @Column(name = "skills", columnDefinition = "TEXT")
    private String skills;

    /**
     * 绑定的模型配置ID（关联t_ai_model_configs的configId）
     */
    @Column(name = "model_config_id")
    private Long modelConfigId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 执行模式: LIVE/DRY_RUN, NULL表示使用上级配置
     */
    @Column(name = "execution_mode", length = 20)
    @Enumerated(EnumType.STRING)
    private ExecutionMode executionMode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreationTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

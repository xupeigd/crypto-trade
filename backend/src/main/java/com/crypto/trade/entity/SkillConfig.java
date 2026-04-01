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
 * SkillConfig
 * 技能配置实体类
 *
 * @author page
 * @date 2026-03-16
 */

@Entity
@Table(name = "t_skill_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SkillConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Prompt模板，指导LLM如何执行这个技能
     */
    @Column(name = "skill_prompt", columnDefinition = "TEXT")
    private String skillPrompt;

    /**
     * 输出格式要求(JSON Schema格式)
     */
    @Column(name = "output_format", columnDefinition = "TEXT")
    private String outputFormat;

    /**
     * 依赖的工具列表（JSON数组格式，如 ["k_line", "position_info"]）
     */
    @Column(name = "required_tools", columnDefinition = "TEXT")
    private String requiredTools;

    /**
     * 执行流程提示
     */
    @Column(name = "execution_hint", columnDefinition = "TEXT")
    private String executionHint;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

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

package com.crypto.trade.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * StrategyConfig
 * 策略配置实体类
 *
 * @author page
 * @date 2026-03-23
 */

@Entity
@Table(name = "t_strategy_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class StrategyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 标题
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * 策略名称
     */
    @Column(name = "strategy_name", nullable = false, length = 100)
    private String strategyName;

    /**
     * 版本
     */
    @Column(name = "version", length = 50)
    private String version;

    /**
     * Prompt提示词
     */
    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;

    /**
     * 策略代码
     */
    @Column(name = "strategy_code", columnDefinition = "TEXT")
    private String strategyCode;

    /**
     * 描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 是否启用
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

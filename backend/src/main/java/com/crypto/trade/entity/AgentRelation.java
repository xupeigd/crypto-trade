package com.crypto.trade.entity;

import com.crypto.trade.enums.AgentRelationType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AgentRelation
 * 智能体关系实体类
 *
 * @author page
 * @date 2026-03-22
 */

@Entity
@Table(name = "t_agent_relation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AgentRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 主Agent ID
     */
    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    /**
     * 子Agent ID
     */
    @Column(name = "sub_agent_id", nullable = false)
    private Long subAgentId;

    /**
     * 关系类型：MASTER_SLAVE/COLLABORATIVE/HIERARCHICAL
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 20)
    private AgentRelationType relationType;

    /**
     * 调用优先级（数字越小越优先）
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    /**
     * 委托说明
     */
    @Column(name = "delegation_prompt", length = 1000)
    private String delegationPrompt;

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

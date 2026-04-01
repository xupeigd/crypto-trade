package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AttentionQueue
 * ATTENTION队列实体类
 *
 * @author page
 * @date 2026-03-02
 */
@Entity
@Table(name = "t_attention_queue", indexes = {
        @Index(name = "idx_record_id", columnList = "record_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_expected_trigger_time", columnList = "expected_trigger_time")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttentionQueue {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 触发的LlmCallRecord的ID
     */
    @Column(name = "record_id", nullable = false)
    private Long recordId;

    /**
     * API密钥ID
     */
    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    /**
     * 合约代码（如BTC-USDT-SWAP）
     */
    @Column(name = "inst_id", length = 50)
    private String instId;

    /**
     * 优先级
     */
    @Column(name = "priority")
    private Integer priority;

    /**
     * 时间周期 (1m, 5m, 1H, etc.)
     */
    @Column(name = "timeframe", length = 20)
    private String timeframe;

    /**
     * 查询条数限制
     */
    @Column(name = "query_limit")
    private Integer queryLimit;

    /**
     * 预期触发时间
     */
    @Column(name = "expected_trigger_time", nullable = false)
    private LocalDateTime expectedTriggerTime;

    /**
     * 实际触发时间
     */
    @Column(name = "actual_trigger_time")
    private LocalDateTime actualTriggerTime;

    /**
     * 状态：PENDING/EXECUTED/EXPIRED
     */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING";

    /**
     * 创建时间
     */
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 备注
     */
    @Column(name = "remark", length = 500)
    private String remark;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
        if (updateTime == null) {
            updateTime = LocalDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }
}

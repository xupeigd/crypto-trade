package com.crypto.trade.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * FreqtradeInstance
 * Freqtrade运行实例实体类
 *
 * @author page
 * @date 2026-03-23
 */

@Entity
@Table(name = "t_freqtrade_instance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class FreqtradeInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 关联API Key
     */
    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    /**
     * 关联FreqtradeConfig
     */
    @Column(name = "freqtrade_config_id", nullable = false)
    private Long freqtradeConfigId;

    /**
     * 关联StrategyConfig
     */
    @Column(name = "strategy_config_id", nullable = false)
    private Long strategyConfigId;

    /**
     * 实例ID（md5前8位）
     */
    @Column(name = "instance_id", nullable = false, length = 20, unique = true)
    private String instanceId;

    /**
     * 实例名称（容器名）
     */
    @Column(name = "instance_name", nullable = false, length = 100, unique = true)
    private String instanceName;

    /**
     * 实例目录路径
     */
    @Column(name = "instance_dir", length = 500)
    private String instanceDir;

    /**
     * Docker容器ID
     */
    @Column(name = "container_id", length = 100)
    private String containerId;

    /**
     * 动态分配的API端口
     */
    @Column(name = "api_port", nullable = false)
    private Integer apiPort;

    /**
     * 状态: RUNNING/STOPPED/ERROR/STARTING
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "STOPPED";

    /**
     * 进程ID（备用）
     */
    @Column(name = "pid", length = 50)
    private String pid;

    /**
     * 启动时间
     */
    @Column(name = "started_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    /**
     * 停止时间
     */
    @Column(name = "stopped_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime stoppedAt;

    /**
     * 最后错误信息
     */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /**
     * 盈亏比例
     */
    @Column(name = "profit_ratio", precision = 10, scale = 4)
    private BigDecimal profitRatio;

    /**
     * 总交易次数
     */
    @Column(name = "total_trades")
    @Builder.Default
    private Integer totalTrades = 0;

    /**
     * 盈利交易次数
     */
    @Column(name = "winning_trades")
    @Builder.Default
    private Integer winningTrades = 0;

    /**
     * 是否Dry Run模式
     */
    @Column(name = "is_dry_run")
    @Builder.Default
    private Boolean isDryRun = true;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
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

    /**
     * 实例状态枚举
     */
    public static class Status {
        public static final String STARTING = "STARTING";
        public static final String RUNNING = "RUNNING";
        public static final String STOPPED = "STOPPED";
        public static final String ERROR = "ERROR";
    }
}
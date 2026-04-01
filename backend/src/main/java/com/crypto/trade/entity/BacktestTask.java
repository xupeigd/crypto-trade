package com.crypto.trade.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_backtest_task")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class BacktestTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "task_name", nullable = false, length = 100)
    private String taskName;

    @Column(name = "freqtrade_config_id", nullable = false)
    private Long freqtradeConfigId;

    @Column(name = "strategy_config_id", nullable = false)
    private Long strategyConfigId;

    @Column(name = "time_range", length = 50)
    private String timeRange;

    @Column(name = "timeframe", length = 20)
    private String timeframe;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING, RUNNING, SUCCESS, FAILED

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    @Column(name = "pid", length = 50)
    private String pid;

    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;

    @Column(name = "user_data_dir", length = 500)
    private String userDataDir;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

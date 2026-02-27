package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * TaskExecution
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Entity
@Table(name = "t_task_executions", indexes = {
        @Index(name = "idx_task_execution_lookup", columnList = "task_id, parent_task_name, execution_status, created_time"),
        @Index(name = "idx_execution_status_created", columnList = "execution_status, created_time")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "execution_id")
    private Long executionId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType; // cron, parent, manual

    @Column(name = "parent_task_name", nullable = false, length = 100)
    private String parentTaskName; // cron触发时为"st"

    @Column(name = "trigger_time", nullable = false)
    private LocalDateTime triggerTime;

    @Column(name = "actual_execute_time")
    private LocalDateTime actualExecuteTime;

    @Column(name = "finish_time")
    private LocalDateTime finishTime;

    @Column(name = "execution_status", nullable = false, length = 20)
    private String executionStatus; // pending, running, success, failed, timeout, skipped

    @Column(name = "execution_result", columnDefinition = "TEXT")
    private String executionResult;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();
}
package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ScheduledTask
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Entity
@Table(name = "t_scheduled_tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "task_name", nullable = false, unique = true, length = 100)
    private String taskName;

    @Column(name = "task_type", nullable = false, length = 20)
    private String taskType; // data_fetch, data_calculation

    @Column(name = "cron_expression", length = 50)
    private String cronExpression;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds = 300; // 默认5分钟

    @Column(name = "status", length = 20)
    private String status = "active"; // active, inactive

    @Column(name = "parent_task_id")
    private Long parentTaskId;

    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters; // JSON格式存储额外参数

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();

    @Column(name = "updated_time")
    private LocalDateTime updatedTime = LocalDateTime.now();
}
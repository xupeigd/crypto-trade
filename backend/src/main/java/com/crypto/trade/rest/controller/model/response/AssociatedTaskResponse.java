package com.crypto.trade.rest.controller.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AssociatedTaskResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AssociatedTaskResponse {

    /**
     * 任务ID
     */
    Long taskId;

    /**
     * 任务名称
     */
    String taskName;

    /**
     * 任务类型
     */
    String taskType;

    /**
     * Cron表达式
     */
    String cronExpression;

    /**
     * 超时时间（秒）
     */
    Integer timeoutSeconds;

    /**
     * 任务状态
     */
    String status;

    /**
     * 父任务ID
     */
    Long parentTaskId;

    /**
     * 任务参数（JSON格式）
     */
    String parameters;

    /**
     * 任务描述
     */
    String description;

    /**
     * 创建时间
     */
    LocalDateTime createdTime;

    /**
     * 更新时间
     */
    LocalDateTime updatedTime;
}
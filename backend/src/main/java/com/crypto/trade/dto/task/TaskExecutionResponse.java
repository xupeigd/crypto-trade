package com.crypto.trade.dto.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * TaskExecutionResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskExecutionResponse {

    /**
     * 执行ID
     */
    Long executionId;

    /**
     * 任务ID
     */
    Long taskId;

    /**
     * 触发类型
     */
    String triggerType;

    /**
     * 父任务名称
     */
    String parentTaskName;

    /**
     * 触发时间
     */
    LocalDateTime triggerTime;

    /**
     * 实际执行时间
     */
    LocalDateTime actualExecuteTime;

    /**
     * 完成时间
     */
    LocalDateTime finishTime;

    /**
     * 执行状态
     */
    String executionStatus;

    /**
     * 执行结果
     */
    String executionResult;

    /**
     * 错误信息
     */
    String errorMessage;

    /**
     * 执行时长（毫秒）
     */
    Long durationMillis;
}
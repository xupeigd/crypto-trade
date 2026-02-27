package com.crypto.trade.rest.controller.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CreateTaskReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateTaskReq {

    /**
     * 任务名称
     */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 100, message = "任务名称长度不能超过100个字符")
    String taskName;

    /**
     * 任务类型
     */
    @NotBlank(message = "任务类型不能为空")
    @Pattern(regexp = "^(data_fetch|data_calculation|system_maintenance)$",
            message = "任务类型必须是data_fetch、data_calculation或system_maintenance")
    String taskType;

    /**
     * Cron表达式
     */
    @Size(max = 50, message = "Cron表达式长度不能超过50个字符")
    String cronExpression;

    /**
     * 超时时间（秒）
     */
    Integer timeoutSeconds;

    /**
     * 任务状态
     */
    @Pattern(regexp = "^(active|inactive)$", message = "任务状态必须是active或inactive")
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
    @Size(max = 500, message = "任务描述长度不能超过500个字符")
    String description;
}
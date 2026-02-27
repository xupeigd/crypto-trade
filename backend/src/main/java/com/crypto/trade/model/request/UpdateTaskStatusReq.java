package com.crypto.trade.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新任务状态请求DTO
 * 用于替换Map<String, String>参数，提供类型安全和参数验证
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTaskStatusReq {

    /**
     * 任务状态
     * active - 激活任务
     * inactive - 停用任务
     */
    @NotBlank(message = "任务状态不能为空")
    @Pattern(regexp = "^(active|inactive)$", message = "任务状态必须是active或inactive")
    String status;

}
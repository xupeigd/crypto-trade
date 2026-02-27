package com.crypto.trade.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ApiKeyStatusReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiKeyStatusReq {

    /**
     * 状态值
     */
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "^(active|inactive)$", message = "状态只能是active或inactive")
    private String status;

    /**
     * 状态变更原因
     */
    private String changeReason;

    /**
     * 验证状态是否有效
     *
     * @return true如果状态有效
     */
    public boolean isValid() {
        return ("active".equals(status) || "inactive".equals(status));
    }
}
package com.crypto.trade.rest.controller.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SetRiskModeRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetRiskModeRequest {

    /**
     * 风控模式
     * AUTO - 自动风控模式
     * MANUAL - 手动风控模式
     */
    private String mode;

    /**
     * 变更原因
     * 默认为"手动设置"
     */
    private String reason;
}

package com.crypto.trade.rest.controller.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ResetRiskModeRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetRiskModeRequest {

    /**
     * 重置原因
     * 默认为"重置为默认模式"
     */
    private String reason;
}

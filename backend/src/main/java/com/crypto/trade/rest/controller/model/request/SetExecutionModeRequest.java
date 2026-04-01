package com.crypto.trade.rest.controller.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SetExecutionModeRequest
 * 设置执行模式请求对象
 *
 * @author page
 * @date 2026-03-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetExecutionModeRequest {

    /**
     * 执行模式
     * LIVE - 实盘模式，真实下单
     * DRY_RUN - 模拟模式，不实际下单
     */
    private String mode;

    /**
     * 变更原因
     */
    private String reason;
}

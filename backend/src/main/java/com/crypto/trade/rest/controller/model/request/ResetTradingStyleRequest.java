package com.crypto.trade.rest.controller.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ResetTradingStyleRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetTradingStyleRequest {

    /**
     * 重置原因
     * 默认为"重置为默认交易风格"
     */
    private String reason;
}

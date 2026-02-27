package com.crypto.trade.rest.controller.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SetTradingStyleRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetTradingStyleRequest {

    /**
     * 交易风格
     * C1_CONSERVATIVE - 保守型
     * C2_CAUTIOUS - 谨慎型
     * C3_MODERATE - 温和型
     * C4_ACTIVE - 活跃型
     * C5_AGGRESSIVE - 激进型
     */
    private String style;

    /**
     * 变更原因
     * 默认为"手动设置交易风格"
     */
    private String reason;
}

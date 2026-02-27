package com.crypto.trade.rest.controller.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TotalStopLossCancelReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TotalStopLossCancelReq {

    /**
     * API Key ID
     */
    Integer apiKeyId;

    /**
     * 算法订单ID
     */
    String algoId;

    /**
     * 保证金模式: cross(全仓) / isolated(逐仓)
     */
    String mgnMode;
}
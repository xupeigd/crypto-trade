package com.crypto.trade.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TotalStopLossAmendReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TotalStopLossAmendReq {

    /**
     * API Key ID
     */
    Integer apiKeyId;

    /**
     * 合约品种
     */
    String instId;

    /**
     * 算法订单ID
     */
    String algoId;

    /**
     * 止盈触发价格
     */
    String tpTriggerPx;

    /**
     * 止损触发价格
     */
    String slTriggerPx;

    /**
     * 止盈触发价格类型
     */
    String tpTriggerPxType;

    /**
     * 止损触发价格类型
     */
    String slTriggerPxType;

    /**
     * 保证金模式: cross(全仓) / isolated(逐仓)
     */
    String mgnMode;
}
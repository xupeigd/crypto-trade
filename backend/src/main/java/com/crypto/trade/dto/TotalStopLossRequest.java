package com.crypto.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TotalStopLossRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TotalStopLossRequest {

    /**
     * API Key ID
     */
    Integer apiKeyId;

    /**
     * 算法订单ID
     */
    String algoId;

    /**
     * 合约品种
     */
    String instId;

    /**
     * 持仓方向 (long/short)
     */
    String posSide;

    /**
     * 订单方向 (buy/sell)
     */
    String side;

    /**
     * 订单数量
     */
    String sz;

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
}
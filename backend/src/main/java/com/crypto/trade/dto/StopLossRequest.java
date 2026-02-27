package com.crypto.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * StopLossRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StopLossRequest {

    /**
     * API Key ID
     */
    Long apiKeyId;

    /**
     * 合约品种
     */
    String instId;

    /**
     * 订单方向 (buy/sell)
     */
    String side;

    /**
     * 持仓方向 (long/short)
     */
    String posSide;

    /**
     * 委托数量
     */
    BigDecimal sz;

    /**
     * 止盈触发价格
     */
    String tpTriggerPx;

    /**
     * 止盈触发类型 (1=价格, 2=百分比)
     */
    String tpTriggerPxType;

    /**
     * 止盈委托类型 (limit, market)
     */
    String tpOrderType;

    /**
     * 止盈触发模式 (1=仓位模式, 2=全仓模式)
     */
    String tpTriggerPxMode;

    /**
     * 止损触发价格
     */
    String slTriggerPx;

    /**
     * 止损触发类型 (1=价格, 2=百分比)
     */
    String slTriggerPxType;

    /**
     * 止损委托类型 (limit, market)
     */
    String slOrderType;

    /**
     * 止损触发模式 (1=仓位模式, 2=全仓模式)
     */
    String slTriggerPxMode;
}
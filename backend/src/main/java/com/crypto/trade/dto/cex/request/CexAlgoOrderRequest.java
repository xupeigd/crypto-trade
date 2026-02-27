package com.crypto.trade.dto.cex.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CexAlgoOrderRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CexAlgoOrderRequest {

    // ==================== 基础字段 ====================

    /**
     * 交易对/合约ID (如 BTC-USDT-SWAP)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String symbol;

    /**
     * 交易模式 (cross/isolated/cash)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String tradeMode;

    /**
     * 币种 (保证金币种)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String currency;

    /**
     * 买卖方向 (buy/sell)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String side;

    /**
     * 持仓方向 (long/short/net)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String positionSide;

    /**
     * 订单类型 (market/limit/oco/conditional等)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String orderType;

    /**
     * 委托数量
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String quantity;

    /**
     * 订单标签
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String tag;

    /**
     * 目标币种
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String targetCurrency;

    /**
     * 客户端自定义算法订单ID
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String clientAlgoOrderId;

    /**
     * 平仓比例 (OCO订单用, 0-1)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String closeFraction;

    /**
     * 交易计价币种
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String tradeQuoteCurrency;

    /**
     * 平仓后撤单 (true/false)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean cancelOnClosePosition;

    /**
     * 只减仓 (true/false)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean reduceOnly;

    // ==================== 止盈相关 ====================

    /**
     * 止盈触发价格
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String takeProfitTriggerPrice;

    /**
     * 止盈委托价格 (-1表示市价)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String takeProfitOrderPrice;

    /**
     * 止盈触发价格类型 (last/mark/index)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String takeProfitTriggerPriceType;

    // ==================== 止损相关 ====================

    /**
     * 止损触发价格
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String stopLossTriggerPrice;

    /**
     * 止损委托价格 (-1表示市价)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String stopLossOrderPrice;

    /**
     * 止损触发价格类型 (last/mark/index)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String stopLossTriggerPriceType;
}

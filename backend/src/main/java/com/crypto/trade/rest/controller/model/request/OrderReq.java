package com.crypto.trade.rest.controller.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * OrderReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReq {

    public Long apiKeyId;        // API Key ID

    public String instId;        // 合约品种

    public String side;          // 订单方向 (buy/sell)

    public String orderType;     // 订单类型 (market/limit)

    public BigDecimal amount;    // 成本金额

    public BigDecimal lever;      // 杠杆倍数

    public BigDecimal px;         // 委托价格（限价单时必填）

    public BigDecimal takeProfitPrice; // 止盈价格

    public BigDecimal stopLossPrice;    // 止损价格

    public String posSide;              // 持仓方向：long/short
}

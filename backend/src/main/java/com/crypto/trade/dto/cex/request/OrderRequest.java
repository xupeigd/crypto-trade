package com.crypto.trade.dto.cex.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * OrderRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    /**
     * 合约品种
     */
    public String instId;

    /**
     * 交易模式
     */
    public String tdMode;

    /**
     * 保证金币种
     */
    public String ccy;

    /**
     * 订单方向
     */
    public String side;

    /**
     * 订单类型
     */
    public String orderType;

    /**
     * 委托数量
     */
    public BigDecimal sz;

    /**
     * 委托价格（市价单为空）
     */
    public BigDecimal px;

    /**
     * 杠杆倍数
     */
    public BigDecimal lever;

    /**
     * 持仓方向：long/short
     */
    public String posSide;

    /**
     * 止盈触发价格
     */
    public BigDecimal tpTriggerPx;

    /**
     * 止盈价格 -1 市价
     */
    public BigDecimal tpOrdPx;

    /**
     * 止盈价格
     */
    public BigDecimal takeProfitPrice;

    /**
     * 止损价格
     */
    public BigDecimal stopLossPrice;
}

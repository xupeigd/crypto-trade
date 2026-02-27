package com.crypto.trade.dto.cex.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * CexPlaceOrderRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CexPlaceOrderRequest {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String symbol; // 交易对/合约

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String tradeMode; // 交易模式

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String currency; // 币种

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String clientOrderId; // 客户自定义订单ID

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String side; // buy/sell

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String orderType; // limit/market等

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String quantity; // 数量

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String positionSide; // long/short

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String price; // 价格

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String leverage; // 杠杆

    /**
     * 止盈价格
     * <p>
     * 当市场价格达到此价格时触发止盈平仓
     * </p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal takeProfitPrice;

    /**
     * 止损价格
     * <p>
     * 当市场价格达到此价格时触发止损平仓
     * </p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal stopLossPrice;
}

package com.crypto.trade.dto.cex.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CexAmendAlgoOrderRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CexAmendAlgoOrderRequest {

    /**
     * 算法订单ID (与clientAlgoOrderId二选一)
     */

    private String algoId;

    /**
     * 客户端自定义算法订单ID (与algoId二选一)
     */
    private String clientAlgoOrderId;

    /**
     * 失败撤单 (true/false)
     */
    private Boolean cancelOnFail;

    /**
     * 请求ID (自定义)
     */
    private String requestId;

    /**
     * 新数量
     */
    private String newQuantity;

    /**
     * 新止盈触发价格
     */
    private String newTakeProfitTriggerPrice;

    /**
     * 新止盈委托价格
     */
    private String newTakeProfitOrderPrice;

    /**
     * 新止损触发价格
     */
    private String newStopLossTriggerPrice;

    /**
     * 新止损委托价格
     */
    private String newStopLossOrderPrice;

    /**
     * 新止盈触发价格类型 (last/mark/index)
     */
    private String newTakeProfitTriggerPriceType;

    /**
     * 新止损触发价格类型 (last/mark/index)
     */
    private String newStopLossTriggerPriceType;

    /**
     * 新订单类型
     */
    private String newOrderType;

    private String symbol;

}

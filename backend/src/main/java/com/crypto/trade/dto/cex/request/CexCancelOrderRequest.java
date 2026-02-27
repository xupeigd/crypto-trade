package com.crypto.trade.dto.cex.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CexCancelOrderRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CexCancelOrderRequest {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String symbol; // 交易对/合约

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String orderId; // 订单ID

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String clientOrderId; // 客户自定义订单ID
}

package com.crypto.trade.dto.cex.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CexCancelAlgoOrderRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CexCancelAlgoOrderRequest {

    /**
     * 算法订单ID
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String algoId;

    /**
     * 交易对/合约ID
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String symbol;

    /**
     * 客户端自定义算法订单ID
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String clientAlgoOrderId;
}

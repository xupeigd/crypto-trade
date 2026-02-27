package com.crypto.trade.dto.cex.response;

import com.crypto.trade.dto.cex.model.CexAlgoOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * CexAlgoOrderResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CexAlgoOrderResponse {

    /**
     * 算法订单结果
     */
    private List<CexAlgoOrder> algoOrders;

    /**
     * 原始响应数据
     */
    private String rawData;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 时间戳
     */
    private Long timestamp;
}

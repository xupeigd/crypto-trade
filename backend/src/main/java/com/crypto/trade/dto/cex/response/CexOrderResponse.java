package com.crypto.trade.dto.cex.response;

import com.crypto.trade.dto.cex.model.CexOrder;
import lombok.Data;

import java.util.List;

/**
 * CEX订单响应(通用)
 * <p>
 * 统一不同交易所的订单响应格式
 * </p>
 *
 * @author Page
 * @since 2025-01-21
 */
@Data
public class CexOrderResponse {

    /**
     * 订单结果
     */
    private List<CexOrder> orders;

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

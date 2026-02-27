package com.crypto.trade.dto.cex.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CexAlgoOrderOperationResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CexAlgoOrderOperationResponse {

    /**
     * 算法订单ID
     */
    private String algoId;

    /**
     * 客户端自定义算法订单ID
     */
    private String clientAlgoOrderId;

    /**
     * 关联的普通订单ID
     */
    private String orderId;

    /**
     * 请求ID
     */
    private String requestId;

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
     * 原始响应数据 (JSON字符串)
     */
    private String rawData;

    /**
     * 时间戳 (毫秒)
     */
    private Long timestamp;

    /**
     * 判断操作是否成功
     */
    public boolean isSuccess() {
        return success != null && success;
    }

    /**
     * 获取错误信息 (如果失败)
     */
    public String getErrorMessage() {
        if (isSuccess()) {
            return null;
        }
        return errorMessage;
    }
}

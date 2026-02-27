package com.crypto.trade.dto.cex.response;

import lombok.Data;

/**
 * CexOperationResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
public class CexOperationResponse {

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

    /**
     * 创建成功响应
     */
    public static CexOperationResponse success(String requestId) {
        CexOperationResponse response = new CexOperationResponse();
        response.setSuccess(true);
        response.setRequestId(requestId);
        response.setTimestamp(System.currentTimeMillis());
        return response;
    }

    /**
     * 创建失败响应
     */
    public static CexOperationResponse failure(String errorCode, String errorMessage) {
        CexOperationResponse response = new CexOperationResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setErrorMessage(errorMessage);
        response.setTimestamp(System.currentTimeMillis());
        return response;
    }
}

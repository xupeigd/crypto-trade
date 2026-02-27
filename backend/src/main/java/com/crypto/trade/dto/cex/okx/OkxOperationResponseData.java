package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OkxOperationResponseData
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OkxOperationResponseData {

    /**
     * 请求返回结果的代码
     * "0"表示成功
     */
    @JsonProperty("sCode")
    private String sCode;

    /**
     * 请求返回结果的信息
     */
    @JsonProperty("sMsg")
    private String sMsg;

    /**
     * 请求返回结果的代码
     */
    @JsonProperty("code")
    private String code;

    /**
     * 请求返回结果的信息
     */
    @JsonProperty("msg")
    private String msg;

    // === 便利方法 ===

    /**
     * 判断操作是否成功
     */
    public boolean isSuccess() {
        return "0".equals(sCode) || "0".equals(code);
    }

    /**
     * 判断操作是否失败
     */
    public boolean isFailure() {
        return !isSuccess();
    }

    /**
     * 获取错误代码
     */
    public String getErrorCode() {
        if (isFailure()) {
            return null != sCode ? sCode : code;
        }
        return null;
    }

    /**
     * 获取错误消息
     */
    public String getErrorMessage() {
        if (isFailure()) {
            return null != sMsg ? sMsg : msg;
        }
        return null;
    }

    /**
     * 获取成功消息
     */
    public String getSuccessMessage() {
        if (isSuccess()) {
            return null != sMsg ? sMsg : msg;
        }
        return null;
    }
}
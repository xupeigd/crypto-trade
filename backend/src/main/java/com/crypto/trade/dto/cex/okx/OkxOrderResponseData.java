package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OkxOrderResponseData
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
public class OkxOrderResponseData {

    /**
     * 订单ID
     */
    @JsonProperty("ordId")
    private String ordId;

    /**
     * 客户自定义订单ID
     */
    @JsonProperty("clOrdId")
    private String clOrdId;

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

    // === 便利方法 ===

    /**
     * 判断操作是否成功
     */
    public boolean isSuccess() {
        return "0".equals(sCode);
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
        return isFailure() ? sCode : null;
    }

    /**
     * 获取错误消息
     */
    public String getErrorMessage() {
        return isFailure() ? sMsg : null;
    }

    /**
     * 获取成功消息
     */
    public String getSuccessMessage() {
        return isSuccess() ? sMsg : null;
    }
}
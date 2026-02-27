package com.crypto.trade.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BotStatusResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BotStatusResponse {

    /**
     * 调用次数
     */
    @JsonProperty("callCount")
    private Long callCount;
    /**
     * 上次调用时间（毫秒时间戳）
     */
    @JsonProperty("lastCallTime")
    private Long lastCallTime;
    /**
     * 调用模型名称
     */
    @JsonProperty("modelName")
    private String modelName;
    /**
     * 当前API Key ID
     */
    @JsonProperty("apiKeyId")
    private Long apiKeyId;
    /**
     * API Key描述
     */
    @JsonProperty("apiKeyDescription")
    private String apiKeyDescription;
    /**
     * 风控模式
     */
    @JsonProperty("riskControlMode")
    private String riskControlMode;
    /**
     * 交易风格
     */
    @JsonProperty("tradingStyle")
    private String tradingStyle;
    /**
     * 自动作业开关状态
     */
    @JsonProperty("automaticTradeEnabled")
    private Boolean automaticTradeEnabled;
    /**
     * 系统状态
     */
    @JsonProperty("systemStatus")
    private String systemStatus;
    /**
     * 上次处理时间（毫秒）
     */
    @JsonProperty("lastProcessingTime")
    private Long lastProcessingTime;

    /**
     * API Key信息内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiKeyInfo {
        /**
         * API Key ID
         */
        @JsonProperty("keyId")
        private Long keyId;

        /**
         * Key名称
         */
        @JsonProperty("keyName")
        private String keyName;

        /**
         * 供应商
         */
        @JsonProperty("vendor")
        private String vendor;

        /**
         * 状态
         */
        @JsonProperty("status")
        private String status;

        /**
         * 是否实盘交易
         */
        @JsonProperty("isLiveTrading")
        private Boolean isLiveTrading;
    }
}
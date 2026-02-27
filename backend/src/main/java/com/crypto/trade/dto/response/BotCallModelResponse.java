package com.crypto.trade.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BotCallModelResponse
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
public class BotCallModelResponse {

    /**
     * 任务ID
     */
    @JsonProperty("taskId")
    private String taskId;

    /**
     * API Key ID
     */
    @JsonProperty("apiKeyId")
    private Long apiKeyId;

    /**
     * 调用状态
     */
    @JsonProperty("status")
    private String status;

    /**
     * 状态描述
     */
    @JsonProperty("message")
    private String message;

    /**
     * AI响应内容
     */
    @JsonProperty("aiResponse")
    private String aiResponse;

    /**
     * 解析后的交易决策
     */
    @JsonProperty("tradeDecision")
    private Object tradeDecision;

    /**
     * 调用时间（毫秒时间戳）
     */
    @JsonProperty("callTime")
    private Long callTime;

    /**
     * 处理时间（毫秒）
     */
    @JsonProperty("processingTimeMs")
    private Long processingTimeMs;

    /**
     * 是否成功
     */
    @JsonProperty("success")
    private Boolean success;

    /**
     * 错误信息
     */
    @JsonProperty("errorMessage")
    private String errorMessage;

    /**
     * 模型名称
     */
    @JsonProperty("modelName")
    private String modelName;

    /**
     * 调用统计ID
     */
    @JsonProperty("callStatsId")
    private Long callStatsId;

    /**
     * 决策ID
     */
    @JsonProperty("decisionId")
    private String decisionId;
}
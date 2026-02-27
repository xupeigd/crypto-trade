package com.crypto.trade.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BotTriggerResponse
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
public class BotTriggerResponse {

    /**
     * 任务ID（用于查询执行状态）
     */
    @JsonProperty("taskId")
    private String taskId;

    /**
     * API Key ID
     */
    @JsonProperty("apiKeyId")
    private Long apiKeyId;

    /**
     * 触发状态
     */
    @JsonProperty("status")
    private String status;

    /**
     * 状态描述
     */
    @JsonProperty("message")
    private String message;

    /**
     * 触发时间（毫秒时间戳）
     */
    @JsonProperty("triggerTime")
    private Long triggerTime;

    /**
     * 预估执行时间（秒）
     */
    @JsonProperty("estimatedDuration")
    private Integer estimatedDuration;

    /**
     * 是否成功提交到异步队列
     */
    @JsonProperty("queued")
    private Boolean queued;

    /**
     * Prompt是否已生成
     */
    @JsonProperty("promptGenerated")
    private Boolean promptGenerated;

    /**
     * 预估Token数量
     */
    @JsonProperty("estimatedTokens")
    private Integer estimatedTokens;
}
package com.crypto.trade.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BotTaskStatusResponse
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
public class BotTaskStatusResponse {

    /**
     * 任务ID
     */
    @JsonProperty("taskId")
    private String taskId;

    /**
     * 任务状态 (SUBMITTED/PROCESSING/COMPLETED/ERROR/TIMEOUT)
     */
    @JsonProperty("status")
    private String status;

    /**
     * 任务提交时间（毫秒时间戳）
     */
    @JsonProperty("submittedTime")
    private Long submittedTime;

    /**
     * 任务开始处理时间（毫秒时间戳）
     */
    @JsonProperty("startedTime")
    private Long startedTime;

    /**
     * 处理时间（毫秒）
     */
    @JsonProperty("processingTimeMs")
    private Long processingTimeMs;

    /**
     * 错误信息
     */
    @JsonProperty("errorMessage")
    private String errorMessage;

    /**
     * 完整的AI响应
     */
    @JsonProperty("fullResponse")
    private String fullResponse;
}
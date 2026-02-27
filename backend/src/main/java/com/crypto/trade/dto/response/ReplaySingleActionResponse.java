package com.crypto.trade.dto.response;

import com.crypto.trade.entity.TradeAction;
import com.crypto.trade.service.conversation.TradeExecutionResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ReplaySingleActionResponse
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
public class ReplaySingleActionResponse {

    /**
     * 是否成功
     */
    @JsonProperty("success")
    private Boolean success;

    /**
     * 响应消息
     */
    @JsonProperty("message")
    private String message;

    /**
     * 历史记录ID
     */
    @JsonProperty("recordId")
    private Long recordId;

    /**
     * 动作ID
     */
    @JsonProperty("actionId")
    private Long actionId;

    /**
     * 原始AI模型名称
     */
    @JsonProperty("originalModelName")
    private String originalModelName;

    /**
     * 动作类型
     */
    @JsonProperty("actionType")
    private String actionType;

    /**
     * 合约代码
     */
    @JsonProperty("instId")
    private String instId;

    /**
     * 执行结果
     */
    @JsonProperty("executionResult")
    private TradeExecutionResult executionResult;

    /**
     * 重放动作列表
     */
    @JsonProperty("replayActions")
    private List<TradeAction> replayActions;

    /**
     * 执行时间
     */
    @JsonProperty("executedAt")
    private LocalDateTime executedAt;
}

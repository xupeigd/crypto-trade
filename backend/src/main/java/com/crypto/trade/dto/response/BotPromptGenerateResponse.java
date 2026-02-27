package com.crypto.trade.dto.response;

import com.crypto.trade.model.PositionModel;
import com.crypto.trade.model.SegmentModel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * BotPromptGenerateResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotPromptGenerateResponse {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<PositionModel> positions;
    /**
     * 任务ID（用于追踪）
     */
    @JsonProperty("taskId")
    private String taskId;
    /**
     * API Key ID
     */
    @JsonProperty("apiKeyId")
    private Long apiKeyId;
    /**
     * 生成状态
     */
    @JsonProperty("status")
    private String status;
    /**
     * 状态描述
     */
    @JsonProperty("message")
    private String message;
    /**
     * 生成的prompt内容
     */
    @JsonProperty("promptContent")
    private String promptContent;
    /**
     * 账户状态数据
     */
    @JsonProperty("accountData")
    private String accountData;
    /**
     * 持仓信息
     */
    @JsonProperty("positionDetails")
    private String positionDetails;
    /**
     * 生成时间（毫秒时间戳）
     */
    @JsonProperty("generateTime")
    private Long generateTime;
    /**
     * 预估token数量
     */
    @JsonProperty("estimatedTokens")
    private Integer estimatedTokens;
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
     * 结构化的Prompt段落数据
     */
    @JsonIgnore
    @JsonProperty("segments")
    private List<SegmentModel> segments;
    /**
     * Prompt生成耗时（毫秒）
     * 用于监控和展示生成性能
     */
    @JsonProperty("processingTimeMs")
    private Long processingTimeMs;

    /**
     * 账户余额快照ID
     * 用于关联生成prompt时的TradeBalanceSnapshot记录
     */
    @JsonProperty("balanceSnapshotId")
    private Long balanceSnapshotId;

}
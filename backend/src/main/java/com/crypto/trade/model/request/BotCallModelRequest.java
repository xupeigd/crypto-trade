package com.crypto.trade.model.request;

import com.crypto.trade.dto.AttentionInfo;
import com.crypto.trade.model.PositionModel;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * BotCallModelRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotCallModelRequest {

    /**
     * 持仓信息（可选）
     */
    List<PositionModel> positions;
    /**
     * API Key ID
     */
    @NotNull(message = "API Key ID不能为空")
    @JsonProperty("apiKeyId")
    private Long apiKeyId;
    /**
     * 用户自定义的prompt内容
     */
    @NotBlank(message = "Prompt内容不能为空")
    @JsonProperty("promptContent")
    private String promptContent;
    /**
     * 任务ID（可选，用于追踪）
     */
    @JsonProperty("taskId")
    private String taskId;
    /**
     * 是否记录到历史（默认true）
     */
    @JsonProperty("saveToHistory")
    @Builder.Default
    private Boolean saveToHistory = true;
    /**
     * 模型名称（可选，默认使用deepseek-r1:14b）
     */
    @JsonProperty("modelName")
    private String modelName;
    /**
     * 自定义参数（可选）
     */
    @JsonProperty("customParams")
    private Object customParams;
    /**
     * 账户余额快照ID（可选）
     * 用于关联生成prompt时的TradeBalanceSnapshot记录
     */
    @JsonProperty("balanceSnapshotId")
    private Long balanceSnapshotId;
    /**
     * ATTENTION信息列表（用于Attention触发时传递）
     */
    @JsonProperty("attentions")
    private List<AttentionInfo> attentions;

}
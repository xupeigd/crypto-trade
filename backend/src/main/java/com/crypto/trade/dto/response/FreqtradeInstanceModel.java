package com.crypto.trade.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * FreqtradeInstanceModel
 * 策略执行实例响应模型
 *
 * @author page
 * @date 2026-03-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FreqtradeInstanceModel {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("apiKeyId")
    private Long apiKeyId;

    @JsonProperty("freqtradeConfigId")
    private Long freqtradeConfigId;

    @JsonProperty("strategyConfigId")
    private Long strategyConfigId;

    @JsonProperty("instanceId")
    private String instanceId;

    @JsonProperty("instanceName")
    private String instanceName;

    @JsonProperty("instanceDir")
    private String instanceDir;

    @JsonProperty("containerId")
    private String containerId;

    @JsonProperty("apiPort")
    private Integer apiPort;

    @JsonProperty("status")
    private String status;

    @JsonProperty("pid")
    private String pid;

    @JsonProperty("startedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @JsonProperty("stoppedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime stoppedAt;

    @JsonProperty("lastError")
    private String lastError;

    @JsonProperty("profitRatio")
    private BigDecimal profitRatio;

    @JsonProperty("totalTrades")
    private Integer totalTrades;

    @JsonProperty("winningTrades")
    private Integer winningTrades;

    @JsonProperty("isDryRun")
    private Boolean isDryRun;

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 从实体转换为Model
     */
    public static FreqtradeInstanceModel fromEntity(com.crypto.trade.entity.FreqtradeInstance entity) {
        if (entity == null) {
            return null;
        }
        return FreqtradeInstanceModel.builder()
                .id(entity.getId())
                .apiKeyId(entity.getApiKeyId())
                .freqtradeConfigId(entity.getFreqtradeConfigId())
                .strategyConfigId(entity.getStrategyConfigId())
                .instanceId(entity.getInstanceId())
                .instanceName(entity.getInstanceName())
                .instanceDir(entity.getInstanceDir())
                .containerId(entity.getContainerId())
                .apiPort(entity.getApiPort())
                .status(entity.getStatus())
                .pid(entity.getPid())
                .startedAt(entity.getStartedAt())
                .stoppedAt(entity.getStoppedAt())
                .lastError(entity.getLastError())
                .profitRatio(entity.getProfitRatio())
                .totalTrades(entity.getTotalTrades())
                .winningTrades(entity.getWinningTrades())
                .isDryRun(entity.getIsDryRun())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

package com.crypto.trade.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * TradeBalanceSnapshotResponse
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
public class TradeBalanceSnapshotResponse {

    /**
     * 快照ID
     */
    @JsonProperty("snapshotId")
    private Long snapshotId;

    /**
     * API Key ID
     */
    @JsonProperty("apiKeyId")
    private Long apiKeyId;

    /**
     * 交易所名称
     */
    @JsonProperty("cexName")
    private String cexName;

    /**
     * 账户总权益(USDT)
     */
    @JsonProperty("totalEquityUsdt")
    private BigDecimal totalEquityUsdt;

    /**
     * 可用权益(USDT)
     */
    @JsonProperty("availableEquityUsdt")
    private BigDecimal availableEquityUsdt;

    /**
     * 已使用保证金(USDT)
     */
    @JsonProperty("usedMarginUsdt")
    private BigDecimal usedMarginUsdt;

    /**
     * 未实现盈亏(USDT)
     */
    @JsonProperty("unrealizedPnlUsdt")
    private BigDecimal unrealizedPnlUsdt;

    /**
     * 保证金使用率(百分比)
     */
    @JsonProperty("marginRatio")
    private BigDecimal marginRatio;

    /**
     * 最大可用金额(AI交易资金限制)
     */
    @JsonProperty("maxAvailableAmount")
    private BigDecimal maxAvailableAmount;

    /**
     * 快照来源(INITIAL/REPLY)
     */
    @JsonProperty("source")
    private String source;

    /**
     * 快照时间(毫秒时间戳)
     */
    @JsonProperty("snapshotTime")
    private Long snapshotTime;

    /**
     * 关联的AI调用记录ID
     */
    @JsonProperty("recordId")
    private Long recordId;
}

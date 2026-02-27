package com.crypto.trade.rest.controller.model.response;

import com.crypto.trade.entity.TradingStyle;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TradingStyleResponse
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
public class TradingStyleResponse {

    /**
     * 交易风格枚举
     */
    private TradingStyle style;

    /**
     * 交易风格描述
     */
    private String description;

    /**
     * 最大亏损率
     * 单位:百分比
     */
    private Double maxLossRate;

    /**
     * 目标盈利率
     * 单位:百分比
     */
    private Double targetProfitRate;
}

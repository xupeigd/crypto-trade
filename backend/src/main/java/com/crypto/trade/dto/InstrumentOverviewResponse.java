package com.crypto.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * InstrumentOverviewResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentOverviewResponse {

    /**
     * 合约ID
     */
    private String instId;

    /**
     * 资金费率
     */
    private BigDecimal fundingRate;

    /**
     * 下次资金费率
     */
    private BigDecimal nextFundingRate;

    /**
     * 资金费时间
     */
    private String fundingTime;

    /**
     * 下次资金费时间
     */
    private String nextFundingTime;

    /**
     * 各周期波动率数据
     * key: 周期 (1D/4H/1H/5m)
     * value: 周期波动率数据
     */
    private Map<String, PeriodVolatilityData> volatilityData;

    /**
     * 周期波动率数据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodVolatilityData {

        /**
         * 平均波动率 (%)
         */
        private Double averageVolatility;

        /**
         * 周期内最低价
         */
        private BigDecimal minPrice;

        /**
         * 周期内最高价
         */
        private BigDecimal maxPrice;

        /**
         * 数据点数量
         */
        private Integer dataPoints;
    }
}
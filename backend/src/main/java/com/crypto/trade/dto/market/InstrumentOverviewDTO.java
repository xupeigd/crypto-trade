package com.crypto.trade.dto.market;

import com.crypto.trade.dto.InstrumentOverviewResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * InstrumentOverviewDTO
 * 数据传输对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstrumentOverviewDTO {

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
     * 利率
     */
    private BigDecimal interestRate;

    /**
     * 溢价
     */
    private BigDecimal premium;

    /**
     * 已结算资金费率
     */
    private BigDecimal settledFundingRate;

    /**
     * 资金费结算时间（毫秒时间戳字符串）
     */
    private String fundingTime;

    /**
     * 下次资金费结算时间（毫秒时间戳字符串）
     */
    private String nextFundingTime;

    /**
     * 波动率数据
     * key: 时间周期（如 "1D", "4H", "1H", "5m"）
     * value: 该周期的波动率数据
     */
    private Map<String, InstrumentOverviewResponse.PeriodVolatilityData> volatilityData;
}

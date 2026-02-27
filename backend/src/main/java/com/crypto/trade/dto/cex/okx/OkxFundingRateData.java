package com.crypto.trade.dto.cex.okx;

import com.crypto.trade.dto.cex.utils.StringToBigDecimalDeserializer;
import com.crypto.trade.dto.cex.utils.StringToLongDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * OkxFundingRateData
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OkxFundingRateData {

    /**
     * 资金费率计算公式类型：withRate / withoutRate
     */
    String formulaType;

    /**
     * 当前资金费率（实时）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal fundingRate;

    /**
     * 本次资金费结算时间（毫秒）
     */
    @JsonDeserialize(using = StringToLongDeserializer.class)
    Long fundingTime;

    /**
     * 影响价格的名义价值（用于计算市场平均价格）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal impactValue;

    /**
     * 合约代码
     */
    String instId;

    /**
     * 合约类型：SWAP / FUTURES
     */
    String instType;

    /**
     * 利息率（固定）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal interestRate;

    /**
     * 资金费率上限
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal maxFundingRate;

    /**
     * 计算方式：current_period / next_period
     */
    String method;

    /**
     * 资金费率下限
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal minFundingRate;

    /**
     * 下一期预估资金费率（接口返回 "" 时为空）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal nextFundingRate;

    /**
     * 下一资金费结算时间（毫秒）
     */
    @JsonDeserialize(using = StringToLongDeserializer.class)
    Long nextFundingTime;

    /**
     * 溢价（Premium Index）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal premium;

    /**
     * 已结算资金费率（上一期实际费率）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal settFundingRate;

    /**
     * 结算状态：settled / unsettled
     */
    String settState;

    /**
     * 数据更新时间（毫秒）
     */
    @JsonDeserialize(using = StringToLongDeserializer.class)
    Long ts;

}
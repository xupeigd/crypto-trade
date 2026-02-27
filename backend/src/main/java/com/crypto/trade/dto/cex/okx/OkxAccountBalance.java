package com.crypto.trade.dto.cex.okx;

import com.crypto.trade.dto.cex.utils.StringToBigDecimalDeserializer;
import com.crypto.trade.dto.cex.utils.StringToLongDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * OkxAccountBalance
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OkxAccountBalance {

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal adjEq;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal availEq;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal borrowFroz;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal delta;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal deltaLever;

    String deltaNeutralStatus;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal imr;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal isoEq;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal mgnRatio;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal mmr;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal notionalUsd;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal notionalUsdForBorrow;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal notionalUsdForFutures;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal notionalUsdForOption;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal notionalUsdForSwap;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal ordFroz;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal totalEq;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal upl;

    @JsonDeserialize(using = StringToLongDeserializer.class)
    long uTime;

    @JsonProperty("details")
    List<OkxAccountBalanceDetail> details;

}
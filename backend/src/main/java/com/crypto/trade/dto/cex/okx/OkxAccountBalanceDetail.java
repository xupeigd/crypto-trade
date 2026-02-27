package com.crypto.trade.dto.cex.okx;

import com.crypto.trade.dto.cex.utils.StringToBigDecimalDeserializer;
import com.crypto.trade.dto.cex.utils.StringToLongDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * OkxAccountBalanceDetail
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OkxAccountBalanceDetail {

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal accAvgPx;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal autoLendAmt;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal autoLendMtAmt;

    String autoLendStatus;
    String autoStakingStatus;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal availBal;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal availEq;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal borrowFroz;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal cashBal;

    String ccy;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal clSpotInUseAmt;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal colBorrAutoConversion;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal colRes;

    boolean collateralEnabled;
    boolean collateralRestrict;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal crossLiab;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal disEq;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal eq;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal eqUsd;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal fixedBal;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal frozenBal;

    String frpType;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal imr;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal interest;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal isoEq;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal isoLiab;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal isoUpl;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal liab;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal maxLoan;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal maxSpotInUse;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal mgnRatio;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal mmr;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal notionalLever;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal openAvgPx;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal ordFrozen;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal rewardBal;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal smtSyncEq;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal spotBal;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal spotCopyTradingEq;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal spotInUseAmt;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal spotIsoBal;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal spotUpl;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal spotUplRatio;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal stgyEq;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal totalPnl;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal totalPnlRatio;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal twap;

    @JsonDeserialize(using = StringToLongDeserializer.class)
    long uTime;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal upl;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal uplLiab;
}

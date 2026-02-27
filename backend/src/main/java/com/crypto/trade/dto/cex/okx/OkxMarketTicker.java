package com.crypto.trade.dto.cex.okx;

import com.crypto.trade.dto.cex.utils.StringToBigDecimalDeserializer;
import com.crypto.trade.dto.cex.utils.StringToLongDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * OkxMarketTicker
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OkxMarketTicker {

    String instType;

    String instId;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal last;

    @JsonDeserialize(using = StringToLongDeserializer.class)
    Long lastSz;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal askPx;

    @JsonDeserialize(using = StringToLongDeserializer.class)
    Long askSz;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal bidPx;

    @JsonDeserialize(using = StringToLongDeserializer.class)
    Long bidSz;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal open24h;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal high24h;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal low24h;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal volCcy24h;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal vol24h;

    @JsonDeserialize(using = StringToLongDeserializer.class)
    Long ts;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal sodUtc0;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal sodUtc8;

}

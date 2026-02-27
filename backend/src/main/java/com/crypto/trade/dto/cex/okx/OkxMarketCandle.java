package com.crypto.trade.dto.cex.okx;

import com.crypto.trade.dto.cex.utils.StringToBigDecimalDeserializer;
import com.crypto.trade.dto.cex.utils.StringToLongDeserializer;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * OkxMarketCandle
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OkxMarketCandle {

    @JsonProperty(index = 0)
    @JsonDeserialize(using = StringToLongDeserializer.class)
    Long timestamp;

    @JsonProperty(index = 1)
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal open;

    @JsonProperty(index = 2)
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal high;

    @JsonProperty(index = 3)
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal low;

    @JsonProperty(index = 4)
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal close;

    /**
     * 交易量，数值为交易货币的数量。
     */
    @JsonProperty(index = 5)
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal volume;

    /**
     * 交易量，数值为计价货币的数量。
     */
    @JsonProperty(index = 6)
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal volumeCcy;

    /**
     * 交易量，以计价货币为单位
     */
    @JsonProperty(index = 7)
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal volCcyQuote;

    /**
     * K线状态
     * 0 代表 K 线未完结，1 代表 K 线已完结。
     */
    @JsonProperty(index = 8)
    Integer confirm;

    public static OkxMarketCandle from(List<String> array) {
        OkxMarketCandle okxMarketCandle = new OkxMarketCandle();
        okxMarketCandle.setTimestamp(Long.parseLong(array.get(0)));
        okxMarketCandle.setOpen(new BigDecimal(array.get(1)));
        okxMarketCandle.setHigh(new BigDecimal(array.get(2)));
        okxMarketCandle.setLow(new BigDecimal(array.get(3)));
        okxMarketCandle.setClose(new BigDecimal(array.get(4)));
        okxMarketCandle.setVolume(new BigDecimal(array.get(5)));
        okxMarketCandle.setVolumeCcy(new BigDecimal(array.get(6)));
        okxMarketCandle.setVolCcyQuote(new BigDecimal(array.get(7)));
        okxMarketCandle.setConfirm(Integer.parseInt(array.get(8)));
        return okxMarketCandle;
    }

}

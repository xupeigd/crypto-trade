package com.crypto.trade.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * MarketCandleModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MarketCandleModel {

    Long timestamp;

    BigDecimal open;

    BigDecimal high;

    BigDecimal low;

    BigDecimal close;

    /**
     * 交易量，数值为交易货币的数量。
     */
    BigDecimal volume;

    /**
     * 交易量，数值为计价货币的数量。
     */
    BigDecimal volumeCcy;

    /**
     * 交易量，以计价货币为单位
     */
    BigDecimal volCcyQuote;

    /**
     * K线状态
     * 0 代表 K 线未完结，1 代表 K 线已完结。
     */
    Integer confirm;

}

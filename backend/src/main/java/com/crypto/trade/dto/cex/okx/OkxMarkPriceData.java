package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * OkxMarkPriceData
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
public class OkxMarkPriceData {

    /**
     * 合约ID
     */
    @JsonProperty("instId")
    private String instId;

    /**
     * 标记价格
     */
    @JsonProperty("markPx")
    private String markPx;

    /**
     * 最新成交价
     */
    @JsonProperty("last")
    private String last;

    /**
     * 最高买价
     */
    @JsonProperty("bidPx")
    private String bidPx;

    /**
     * 最低卖价
     */
    @JsonProperty("askPx")
    private String askPx;

    /**
     * 24小时最高价
     */
    @JsonProperty("high24h")
    private String high24h;

    /**
     * 24小时最低价
     */
    @JsonProperty("low24h")
    private String low24h;

    /**
     * 24小时成交量，以张为单位
     */
    @JsonProperty("vol24h")
    private String vol24h;

    /**
     * 24小时成交量，以币为单位
     */
    @JsonProperty("volCcy24h")
    private String volCcy24h;

    /**
     * 24小时开盘价
     */
    @JsonProperty("open24h")
    private String open24h;

    /**
     * 时间戳
     */
    @JsonProperty("ts")
    private String ts;

    // === 便利方法 ===

    /**
     * 获取标记价格（BigDecimal）
     */
    public BigDecimal getMarkPriceAsBigDecimal() {
        try {
            return null != markPx && !markPx.isEmpty() ? new BigDecimal(markPx) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取最新价格（BigDecimal）
     */
    public BigDecimal getLastPriceAsBigDecimal() {
        try {
            return null != last && !last.isEmpty() ? new BigDecimal(last) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取买价（BigDecimal）
     */
    public BigDecimal getBidPriceAsBigDecimal() {
        try {
            return null != bidPx && !bidPx.isEmpty() ? new BigDecimal(bidPx) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取卖价（BigDecimal）
     */
    public BigDecimal getAskPriceAsBigDecimal() {
        try {
            return null != askPx && !askPx.isEmpty() ? new BigDecimal(askPx) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取24小时最高价（BigDecimal）
     */
    public BigDecimal getHigh24hAsBigDecimal() {
        try {
            return null != high24h && !high24h.isEmpty() ? new BigDecimal(high24h) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取24小时最低价（BigDecimal）
     */
    public BigDecimal getLow24hAsBigDecimal() {
        try {
            return null != low24h && !low24h.isEmpty() ? new BigDecimal(low24h) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取24小时开盘价（BigDecimal）
     */
    public BigDecimal getOpen24hAsBigDecimal() {
        try {
            return null != open24h && !open24h.isEmpty() ? new BigDecimal(open24h) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取24小时成交量（BigDecimal，以张为单位）
     */
    public BigDecimal getVolume24hAsBigDecimal() {
        try {
            return null != vol24h && !vol24h.isEmpty() ? new BigDecimal(vol24h) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取24小时成交量（BigDecimal，以币为单位）
     */
    public BigDecimal getVolCcy24hAsBigDecimal() {
        try {
            return null != volCcy24h && !volCcy24h.isEmpty() ? new BigDecimal(volCcy24h) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 计算24小时涨跌幅（百分比）
     */
    public BigDecimal calculateChangePercent24h() {
        BigDecimal openPrice = getOpen24hAsBigDecimal();
        BigDecimal lastPrice = getLastPriceAsBigDecimal();

        if (openPrice.compareTo(BigDecimal.ZERO) > 0) {
            return lastPrice.subtract(openPrice)
                    .divide(openPrice, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        return BigDecimal.ZERO;
    }
}
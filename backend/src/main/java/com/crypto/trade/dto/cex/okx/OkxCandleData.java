package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * OkxCandleData
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
public class OkxCandleData {

    /**
     * 时间戳，毫秒
     */
    @JsonProperty("ts")
    private String ts;

    /**
     * 开盘价
     */
    @JsonProperty("o")
    private String o;

    /**
     * 最高价
     */
    @JsonProperty("h")
    private String h;

    /**
     * 最低价
     */
    @JsonProperty("l")
    private String l;

    /**
     * 收盘价
     */
    @JsonProperty("c")
    private String c;

    /**
     * 成交量，以张为单位
     */
    @JsonProperty("vol")
    private String vol;

    /**
     * 成交量，以币为单位
     */
    @JsonProperty("volCcy")
    private String volCcy;

    /**
     * 成交量，以计价币为单位
     */
    @JsonProperty("volCcyQuote")
    private String volCcyQuote;

    /**
     * 确认状态
     * "0"：未确认，"1"：已确认
     */
    @JsonProperty("confirm")
    private String confirm;

    // === 便利方法 ===

    /**
     * 获取开盘价（BigDecimal）
     */
    public BigDecimal getOpenPrice() {
        try {
            return null != o && !o.isEmpty() ? new BigDecimal(o) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取最高价（BigDecimal）
     */
    public BigDecimal getHighPrice() {
        try {
            return null != h && !h.isEmpty() ? new BigDecimal(h) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取最低价（BigDecimal）
     */
    public BigDecimal getLowPrice() {
        try {
            return null != l && !l.isEmpty() ? new BigDecimal(l) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取收盘价（BigDecimal）
     */
    public BigDecimal getClosePrice() {
        try {
            return null != c && !c.isEmpty() ? new BigDecimal(c) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取成交量（BigDecimal，以张为单位）
     */
    public BigDecimal getVolume() {
        try {
            return null != vol && !vol.isEmpty() ? new BigDecimal(vol) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取成交量（BigDecimal，以币为单位）
     */
    public BigDecimal getVolumeInCurrency() {
        try {
            return null != volCcy && !volCcy.isEmpty() ? new BigDecimal(volCcy) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取成交量（BigDecimal，以计价币为单位）
     */
    public BigDecimal getVolumeInQuoteCurrency() {
        try {
            return null != volCcyQuote && !volCcyQuote.isEmpty() ? new BigDecimal(volCcyQuote) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取时间戳（Instant）
     */
    public Instant getTimestampInstant() {
        try {
            return null != ts && !ts.isEmpty() ? Instant.ofEpochMilli(Long.parseLong(ts)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取时间戳字符串
     */
    public String getTimestamp() {
        return ts;
    }

    // 别名方法，以保持向后兼容性
    public BigDecimal getOpenAsBigDecimal() {
        return getOpenPrice();
    }

    public BigDecimal getHighAsBigDecimal() {
        return getHighPrice();
    }

    public BigDecimal getLowAsBigDecimal() {
        return getLowPrice();
    }

    public BigDecimal getCloseAsBigDecimal() {
        return getClosePrice();
    }

    public BigDecimal getVolumeAsBigDecimal() {
        return getVolume();
    }

    public BigDecimal getCurrencyVolumeAsBigDecimal() {
        return getVolumeInCurrency();
    }

    /**
     * 判断是否已确认
     */
    public boolean isConfirmed() {
        return "1".equals(confirm);
    }

    /**
     * 计算价格变化
     */
    public BigDecimal getPriceChange() {
        BigDecimal open = getOpenPrice();
        BigDecimal close = getClosePrice();

        if (open.compareTo(BigDecimal.ZERO) > 0) {
            return close.subtract(open);
        }
        return BigDecimal.ZERO;
    }

    /**
     * 计算价格变化百分比
     */
    public BigDecimal getPriceChangePercent() {
        BigDecimal open = getOpenPrice();
        BigDecimal close = getClosePrice();

        if (open.compareTo(BigDecimal.ZERO) > 0) {
            return close.subtract(open)
                    .divide(open, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        return BigDecimal.ZERO;
    }
}
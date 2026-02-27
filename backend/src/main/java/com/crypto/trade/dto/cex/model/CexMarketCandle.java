package com.crypto.trade.dto.cex.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * CexMarketCandle
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public abstract class CexMarketCandle {

    // ==================== OHLCV ====================

    /**
     * 获取时间戳(毫秒)
     */
    public abstract Long getTimestamp();

    /**
     * 获取开盘价
     */
    public abstract BigDecimal getOpen();

    /**
     * 获取最高价
     */
    public abstract BigDecimal getHigh();

    /**
     * 获取最低价
     */
    public abstract BigDecimal getLow();

    /**
     * 获取收盘价
     */
    public abstract BigDecimal getClose();

    /**
     * 获取交易量(基础币种)
     */
    public abstract BigDecimal getVolume();

    /**
     * 获取成交额(计价币种)
     */
    public abstract BigDecimal getVolumeCcy();

    // ==================== K线状态 ====================

    /**
     * K线是否已完结
     * true-已完结, false-进行中
     */
    public abstract Boolean isConfirmed();

    /**
     * 获取K线时间(本地时间)
     */
    public LocalDateTime getDateTime() {
        Long timestamp = getTimestamp();
        if (null == timestamp) {
            return null;
        }
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
        );
    }

    // ==================== 便捷方法 ====================

    /**
     * 判断是否为阳线(收盘价 > 开盘价)
     */
    public boolean isBullish() {
        BigDecimal open = getOpen();
        BigDecimal close = getClose();
        if (null == open || null == close) {
            return false;
        }
        return close.compareTo(open) > 0;
    }

    /**
     * 判断是否为阴线(收盘价 < 开盘价)
     */
    public boolean isBearish() {
        BigDecimal open = getOpen();
        BigDecimal close = getClose();
        if (null == open || null == close) {
            return false;
        }
        return close.compareTo(open) < 0;
    }

    /**
     * 计算K线实体长度(收盘价 - 开盘价的绝对值)
     */
    public BigDecimal getBodySize() {
        BigDecimal open = getOpen();
        BigDecimal close = getClose();
        if (null == open || null == close) {
            return BigDecimal.ZERO;
        }
        return close.subtract(open).abs();
    }

    /**
     * 计算上影线长度(最高价 - max(开盘价,收盘价))
     */
    public BigDecimal getUpperShadow() {
        BigDecimal high = getHigh();
        BigDecimal open = getOpen();
        BigDecimal close = getClose();
        if (null == high || null == open || null == close) {
            return BigDecimal.ZERO;
        }
        BigDecimal maxOpenClose = open.max(close);
        return high.subtract(maxOpenClose).max(BigDecimal.ZERO);
    }

    /**
     * 计算下影线长度(min(开盘价,收盘价) - 最低价)
     */
    public BigDecimal getLowerShadow() {
        BigDecimal low = getLow();
        BigDecimal open = getOpen();
        BigDecimal close = getClose();
        if (null == low || null == open || null == close) {
            return BigDecimal.ZERO;
        }
        BigDecimal minOpenClose = open.min(close);
        return minOpenClose.subtract(low).max(BigDecimal.ZERO);
    }

    /**
     * 计算价格涨跌幅(百分比)
     */
    public BigDecimal getChangePercent() {
        BigDecimal open = getOpen();
        BigDecimal close = getClose();
        if (null == open || null == close || open.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return close.subtract(open)
                .divide(open, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * 计算价格振幅((最高价 - 最低价) / 开盘价 * 100%)
     */
    public BigDecimal getAmplitudePercent() {
        BigDecimal high = getHigh();
        BigDecimal low = getLow();
        BigDecimal open = getOpen();
        if (null == high || null == low || null == open || open.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return high.subtract(low)
                .divide(open, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * 判断是否为十字星(收盘价 ≈ 开盘价,涨跌幅 < 0.1%)
     */
    public boolean isDoji() {
        BigDecimal changePercent = getChangePercent();
        if (null == changePercent) {
            return false;
        }
        return changePercent.abs().compareTo(BigDecimal.valueOf(0.1)) < 0;
    }

    /**
     * 判断是否为光头阳线(最低价 = 开盘价,收盘价 > 开盘价)
     */
    public boolean isBullishMarubozu() {
        if (!isBullish()) {
            return false;
        }
        BigDecimal low = getLow();
        BigDecimal open = getOpen();
        if (null == low || null == open) {
            return false;
        }
        return low.compareTo(open) == 0;
    }

    /**
     * 判断是否为光脚阴线(最高价 = 开盘价,收盘价 < 开盘价)
     */
    public boolean isBearishMarubozu() {
        if (!isBearish()) {
            return false;
        }
        BigDecimal high = getHigh();
        BigDecimal open = getOpen();
        if (null == high || null == open) {
            return false;
        }
        return high.compareTo(open) == 0;
    }

    // ==================== 兼容方法 ====================

    /**
     * 获取成交额(兼容旧代码)
     * <p>
     * 返回计价币种的成交额
     * </p>
     *
     * @return 成交额
     */
    public BigDecimal getQuoteVolume() {
        return getVolumeCcy();
    }

    /**
     * 获取成交额(兼容旧代码,另一种命名)
     * <p>
     * 返回计价币种的成交额
     * </p>
     *
     * @return 成交额
     */
    public BigDecimal getVolCcyQuote() {
        return getVolumeCcy();
    }

    /**
     * 获取确认状态(兼容旧代码)
     * <p>
     * K线已完结返回1,否则返回0
     * </p>
     *
     * @return 1=已完结, 0=进行中
     */
    public Integer getConfirm() {
        return isConfirmed() ? 1 : 0;
    }
}

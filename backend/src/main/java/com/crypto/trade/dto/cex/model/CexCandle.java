package com.crypto.trade.dto.cex.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CexCandle
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public abstract class CexCandle {

    /**
     * 获取时间戳
     *
     * @return K线时间戳
     */
    public abstract LocalDateTime getTimestamp();

    /**
     * 获取开盘价
     *
     * @return 开盘价
     */
    public abstract BigDecimal getOpen();

    /**
     * 获取最高价
     *
     * @return 最高价
     */
    public abstract BigDecimal getHigh();

    /**
     * 获取最低价
     *
     * @return 最低价
     */
    public abstract BigDecimal getLow();

    /**
     * 获取收盘价
     *
     * @return 收盘价
     */
    public abstract BigDecimal getClose();

    /**
     * 获取成交量(张)
     *
     * @return 成交量
     */
    public abstract BigDecimal getVolume();

    /**
     * 获取成交额(计价币)
     *
     * @return 成交额
     */
    public abstract BigDecimal getVolumeCcy();

    /**
     * 获取成交额(USD)
     *
     * @return 成交额(USD)
     */
    public abstract BigDecimal getVolumeCcyQuote();

    /**
     * 判断K线是否已完结
     * <p>
     * true=已完结(可确认), false=未完结(实时更新中)
     * </p>
     *
     * @return true=已完结, false=未完结
     */
    public abstract boolean isConfirmed();

    // ==================== 便捷计算方法 ====================

    /**
     * 计算K线涨跌金额
     *
     * @return 收盘价 - 开盘价
     */
    public BigDecimal getChangeAmount() {
        BigDecimal close = getClose();
        BigDecimal open = getOpen();
        if (null == close || null == open) {
            return BigDecimal.ZERO;
        }
        return close.subtract(open);
    }

    /**
     * 计算K线涨跌幅
     *
     * @return (收盘价 - 开盘价) / 开盘价 * 100
     */
    public BigDecimal getChangePercent() {
        BigDecimal close = getClose();
        BigDecimal open = getOpen();
        if (null == close || null == open || BigDecimal.ZERO.compareTo(open) == 0) {
            return BigDecimal.ZERO;
        }
        return close.subtract(open)
                .divide(open, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * 计算K线实体高度
     *
     * @return |收盘价 - 开盘价|
     */
    public abstract BigDecimal getBodySize();

    /**
     * 计算上影线长度
     *
     * @return 最高价 - max(开盘价, 收盘价)
     */
    public BigDecimal getUpperShadow() {
        BigDecimal high = getHigh();
        BigDecimal open = getOpen();
        BigDecimal close = getClose();
        if (null == high || null == open || null == close) {
            return BigDecimal.ZERO;
        }
        BigDecimal max = open.max(close);
        return high.subtract(max);
    }

    /**
     * 计算下影线长度
     *
     * @return min(开盘价, 收盘价) - 最低价
     */
    public BigDecimal getLowerShadow() {
        BigDecimal low = getLow();
        BigDecimal open = getOpen();
        BigDecimal close = getClose();
        if (null == low || null == open || null == close) {
            return BigDecimal.ZERO;
        }
        BigDecimal min = open.min(close);
        return min.subtract(low);
    }

    /**
     * 判断是否为阳线(收盘价 > 开盘价)
     *
     * @return true=阳线, false=阴线或平盘
     */
    public boolean isBullish() {
        return getClose().compareTo(getOpen()) > 0;
    }

    /**
     * 判断是否为阴线(收盘价 < 开盘价)
     *
     * @return true=阴线, false=阳线或平盘
     */
    public boolean isBearish() {
        return getClose().compareTo(getOpen()) < 0;
    }

    /**
     * 判断是否为十字星(收盘价 ≈ 开盘价)
     *
     * @return true=十字星, false=非十字星
     */
    public boolean isDoji() {
        return getClose().compareTo(getOpen()) == 0;
    }
}

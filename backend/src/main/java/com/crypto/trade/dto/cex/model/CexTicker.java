package com.crypto.trade.dto.cex.model;

import java.math.BigDecimal;

/**
 * CexTicker
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public abstract class CexTicker {

    /**
     * 获取交易对/合约代码
     *
     * @return 交易对标识
     */
    public abstract String getSymbol();

    /**
     * 获取最新成交价
     *
     * @return 最新价格
     */
    public abstract BigDecimal getLastPrice();

    /**
     * 获取买一价
     *
     * @return 买一价格
     */
    public abstract BigDecimal getBidPrice();

    /**
     * 获取卖一价
     *
     * @return 卖一价格
     */
    public abstract BigDecimal getAskPrice();

    /**
     * 获取买一量
     *
     * @return 买一数量
     */
    public abstract BigDecimal getBidSize();

    /**
     * 获取卖一量
     *
     * @return 卖一数量
     */
    public abstract BigDecimal getAskSize();

    /**
     * 获取24小时开盘价
     *
     * @return 24h开盘价
     */
    public abstract BigDecimal getOpen24h();

    /**
     * 获取24小时最高价
     *
     * @return 24h最高价
     */
    public abstract BigDecimal getHigh24h();

    /**
     * 获取24小时最低价
     *
     * @return 24h最低价
     */
    public abstract BigDecimal getLow24h();

    /**
     * 获取24小时成交量(张)
     *
     * @return 24h成交量
     */
    public abstract BigDecimal getVolume24h();

    /**
     * 获取24小时成交额(计价币)
     *
     * @return 24h成交额
     */
    public abstract BigDecimal getVolumeCcy24h();

    /**
     * 获取时间戳
     *
     * @return 数据时间戳
     */
    public abstract Long getTimestamp();

    // ==================== 便捷计算方法 ====================

    /**
     * 计算买卖价差
     *
     * @return 卖一价 - 买一价
     */
    public BigDecimal getSpread() {
        BigDecimal ask = getAskPrice();
        BigDecimal bid = getBidPrice();
        if (null == ask || null == bid) {
            return BigDecimal.ZERO;
        }
        return ask.subtract(bid);
    }

    /**
     * 计算价差百分比
     *
     * @return (卖一价 - 买一价) / 买一价 * 100
     */
    public BigDecimal getSpreadPercent() {
        BigDecimal spread = getSpread();
        BigDecimal bid = getBidPrice();
        if (null == bid || BigDecimal.ZERO.compareTo(bid) == 0) {
            return BigDecimal.ZERO;
        }
        return spread.divide(bid, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * 计算中间价
     *
     * @return (买一价 + 卖一价) / 2
     */
    public BigDecimal getMidPrice() {
        BigDecimal bid = getBidPrice();
        BigDecimal ask = getAskPrice();
        if (null == bid || null == ask) {
            return getLastPrice();
        }
        return bid.add(ask).divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 计算24h涨跌幅
     *
     * @return (最新价 - 开盘价) / 开盘价 * 100
     */
    public BigDecimal getChange24hPercent() {
        BigDecimal last = getLastPrice();
        BigDecimal open = getOpen24h();
        if (null == last || null == open || BigDecimal.ZERO.compareTo(open) == 0) {
            return BigDecimal.ZERO;
        }
        return last.subtract(open)
                .divide(open, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * 计算24h涨跌金额
     *
     * @return 最新价 - 开盘价
     */
    public BigDecimal getChange24hAmount() {
        BigDecimal last = getLastPrice();
        BigDecimal open = getOpen24h();
        if (null == last || null == open) {
            return BigDecimal.ZERO;
        }
        return last.subtract(open);
    }

    /**
     * 判断24h是否上涨
     *
     * @return true=上涨, false=下跌或持平
     */
    public boolean is24hUp() {
        return getChange24hAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 判断24h是否下跌
     *
     * @return true=下跌, false=上涨或持平
     */
    public boolean is24hDown() {
        return getChange24hAmount().compareTo(BigDecimal.ZERO) < 0;
    }
}

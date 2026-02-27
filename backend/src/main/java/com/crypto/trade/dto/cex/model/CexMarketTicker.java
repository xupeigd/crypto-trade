package com.crypto.trade.dto.cex.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CexMarketTicker
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public abstract class CexMarketTicker {

    // ==================== 基础标识 ====================

    /**
     * 获取交易对/合约ID (如 BTC-USDT-SWAP)
     */
    public abstract String getSymbol();

    /**
     * 获取合约类型 (SPOT/SWAP/FUTURES/OPTION)
     */
    public abstract String getInstrumentType();

    // ==================== 实时价格 ====================

    /**
     * 获取最新成交价
     */
    public abstract BigDecimal getLastPrice();

    /**
     * 获取买一价
     */
    public abstract BigDecimal getBidPrice();

    /**
     * 获取卖一价
     */
    public abstract BigDecimal getAskPrice();

    // ==================== 盘口数量 ====================

    /**
     * 获取买一量
     */
    public abstract BigDecimal getBidSize();

    /**
     * 获取卖一量
     */
    public abstract BigDecimal getAskSize();

    /**
     * 获取最新成交量
     */
    public abstract BigDecimal getLastSize();

    // ==================== 24小时行情 ====================

    /**
     * 获取24小时开盘价
     */
    public abstract BigDecimal getOpen24h();

    /**
     * 获取24小时最高价
     */
    public abstract BigDecimal getHigh24h();

    /**
     * 获取24小时最低价
     */
    public abstract BigDecimal getLow24h();

    /**
     * 获取24小时成交量(基础币种)
     */
    public abstract BigDecimal getVolume24h();

    /**
     * 获取24小时成交额(计价币种)
     */
    public abstract BigDecimal getVolumeCcy24h();

    // ==================== 其他 ====================

    /**
     * 获取数据时间戳
     */
    public abstract Long getTimestamp();

    /**
     * 获取数据时间(本地时间)
     */
    public abstract LocalDateTime getUpdateTime();

    // ==================== 便捷方法 ====================

    /**
     * 判断是否为上涨行情(最新价 > 24h开盘价)
     */
    public boolean isBullish() {
        BigDecimal last = getLastPrice();
        BigDecimal open24h = getOpen24h();
        if (null == last || null == open24h) {
            return false;
        }
        return last.compareTo(open24h) > 0;
    }

    /**
     * 判断是否为下跌行情(最新价 < 24h开盘价)
     */
    public boolean isBearish() {
        BigDecimal last = getLastPrice();
        BigDecimal open24h = getOpen24h();
        if (null == last || null == open24h) {
            return false;
        }
        return last.compareTo(open24h) < 0;
    }

    /**
     * 计算24h价格涨跌幅(百分比)
     */
    public BigDecimal getChange24hPercent() {
        BigDecimal last = getLastPrice();
        BigDecimal open24h = getOpen24h();
        if (null == last || null == open24h || open24h.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return last.subtract(open24h)
                .divide(open24h, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * 获取买卖价差(Ask - Bid)
     */
    public BigDecimal getSpread() {
        BigDecimal ask = getAskPrice();
        BigDecimal bid = getBidPrice();
        if (null == ask || null == bid) {
            return BigDecimal.ZERO;
        }
        return ask.subtract(bid);
    }

    // ==================== 兼容方法 ====================

    /**
     * 获取成交量(兼容旧代码)
     * <p>
     * 返回24小时成交量
     * </p>
     *
     * @return 24小时成交量
     */
    public BigDecimal getVolume() {
        return getVolume24h();
    }

    /**
     * 获取买一量(兼容旧代码)
     * <p>
     * 返回买一盘口数量
     * </p>
     *
     * @return 买一量
     */
    public BigDecimal getBidQuantity() {
        return getBidSize();
    }

    /**
     * 获取卖一量(兼容旧代码)
     * <p>
     * 返回卖一盘口数量
     * </p>
     *
     * @return 卖一量
     */
    public BigDecimal getAskQuantity() {
        return getAskSize();
    }
}

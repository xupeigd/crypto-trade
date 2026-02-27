package com.crypto.trade.dto.cex.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CexFundingRate
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public abstract class CexFundingRate {

    /**
     * 获取交易对/合约代码
     *
     * @return 交易对标识
     */
    public abstract String getSymbol();

    /**
     * 获取当前资金费率
     *
     * @return 当前费率(如 0.0001 表示 0.01 %)
     */
    public abstract BigDecimal getFundingRate();

    /**
     * 获取下一期预估资金费率
     *
     * @return 下一期费率
     */
    public abstract BigDecimal getNextFundingRate();

    /**
     * 获取费率结算时间
     *
     * @return 下次结算时间
     */
    public abstract LocalDateTime getFundingTime();

    /**
     * 获取下一期费率结算时间
     *
     * @return 下下期结算时间
     */
    public abstract LocalDateTime getNextFundingTime();

    /**
     * 获取利息率
     *
     * @return 利息率
     */
    public abstract BigDecimal getInterestRate();

    /**
     * 获取溢价指数
     *
     * @return 溢价指数
     */
    public abstract BigDecimal getPremium();

    /**
     * 获取已结算资金费率
     *
     * @return 上次已结算费率
     */
    public abstract BigDecimal getSettledFundingRate();

    // ==================== 便捷方法 ====================

    /**
     * 计算资金费率百分比
     *
     * @return 费率 * 100
     */
    public BigDecimal getFundingRatePercent() {
        BigDecimal rate = getFundingRate();
        if (null == rate) {
            return BigDecimal.ZERO;
        }
        return rate.multiply(BigDecimal.valueOf(100));
    }

    /**
     * 判断当前费率是否为正(多头付费给空头)
     *
     * @return true=正费率, false=负费率
     */
    public boolean isPositiveRate() {
        BigDecimal rate = getFundingRate();
        return null != rate && BigDecimal.ZERO.compareTo(rate) < 0;
    }

    /**
     * 判断当前费率是否为负(空头付费给多头)
     *
     * @return true=负费率, false=正费率
     */
    public boolean isNegativeRate() {
        BigDecimal rate = getFundingRate();
        return null != rate && BigDecimal.ZERO.compareTo(rate) > 0;
    }

    /**
     * 计算距离下次结算的毫秒数
     *
     * @return 距离结算的毫秒数
     */
    public long getMillisToNextFunding() {
        LocalDateTime fundingTime = getFundingTime();
        if (null == fundingTime) {
            return 0;
        }
        return java.time.Duration.between(
                java.time.LocalDateTime.now(),
                fundingTime
        ).toMillis();
    }
}

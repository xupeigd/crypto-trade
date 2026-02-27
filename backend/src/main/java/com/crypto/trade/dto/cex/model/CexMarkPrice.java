package com.crypto.trade.dto.cex.model;

import java.math.BigDecimal;

/**
 * CexMarkPrice
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public abstract class CexMarkPrice {

    /**
     * 获取交易对/合约代码
     *
     * @return 交易对标识
     */
    public abstract String getSymbol();

    /**
     * 获取标记价格
     *
     * @return 标记价格
     */
    public abstract BigDecimal getMarkPrice();

    /**
     * 获取时间戳
     *
     * @return 数据时间戳
     */
    public abstract Long getTimestamp();

    // ==================== 便捷方法 ====================

    /**
     * 判断标记价格是否有效
     *
     * @return true=有效, false=无效
     */
    public boolean isValid() {
        BigDecimal price = getMarkPrice();
        return null != price && BigDecimal.ZERO.compareTo(price) < 0;
    }
}

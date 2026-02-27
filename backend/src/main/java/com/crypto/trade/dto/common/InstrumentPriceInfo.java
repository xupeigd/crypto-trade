package com.crypto.trade.dto.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * InstrumentPriceInfo
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InstrumentPriceInfo {

    /**
     * 合约ID
     */
    private String instId;

    /**
     * 最新价格
     */
    private BigDecimal lastPrice;

    /**
     * 涨跌幅
     */
    private BigDecimal changePercent;

    /**
     * 1小时涨跌幅
     */
    private BigDecimal changePercent1H;

    /**
     * 4小时涨跌幅
     */
    private BigDecimal changePercent4H;

    /**
     * 更新时间（毫秒时间戳）
     */
    private Long updateTime;

    // OKX API 扩展字段

    /**
     * 标记价格
     */
    private BigDecimal markPrice;

    /**
     * 买一价
     */
    private BigDecimal bidPrice;

    /**
     * 卖一价
     */
    private BigDecimal askPrice;

    /**
     * 24小时最高价
     */
    private BigDecimal high24h;

    /**
     * 24小时最低价
     */
    private BigDecimal low24h;

    /**
     * 24小时成交量
     */
    private BigDecimal volume24h;

    /**
     * 24小时交易额(币数量)
     */
    private BigDecimal volumeCcy24h;

    /**
     * 24小时开盘价
     */
    private BigDecimal open24h;

    /**
     * OKX API时间戳
     */
    private String ts;

    // 便利构造方法

    /**
     * 创建基础价格信息
     */
    public InstrumentPriceInfo(String instId, BigDecimal lastPrice, Long updateTime) {
        this.instId = instId;
        this.lastPrice = lastPrice;
        this.updateTime = updateTime;
    }

    /**
     * 创建完整的价格信息
     */
    public InstrumentPriceInfo(String instId, BigDecimal lastPrice, BigDecimal markPrice, Long updateTime) {
        this.instId = instId;
        this.lastPrice = lastPrice;
        this.markPrice = markPrice;
        this.updateTime = updateTime;
    }

    // 便利方法

    /**
     * 检查是否有有效价格
     */
    public boolean hasValidPrice() {
        return lastPrice != null || markPrice != null;
    }

    /**
     * 获取主要价格（优先使用lastPrice，否则使用markPrice）
     */
    public BigDecimal getPrimaryPrice() {
        return lastPrice != null ? lastPrice : markPrice;
    }

    /**
     * 设置价格兼容性（如果lastPrice为空，使用markPrice填充）
     */
    public void ensurePriceCompatibility() {
        if (lastPrice == null && markPrice != null) {
            lastPrice = markPrice;
        }
        // 如果updateTime为空但ts不为空，尝试解析
        if (updateTime == null && ts != null && !ts.isEmpty()) {
            try {
                updateTime = Long.parseLong(ts);
            } catch (NumberFormatException e) {
                updateTime = System.currentTimeMillis();
            }
        }
    }

    /**
     * 检查数据是否过期（指定毫秒数）
     */
    public boolean isExpired(long maxAgeMillis) {
        if (updateTime == null) {
            return true;
        }
        return System.currentTimeMillis() - updateTime > maxAgeMillis;
    }

    /**
     * 获取24小时涨跌幅（基于open24h和lastPrice计算）
     */
    public BigDecimal get24hChangePercent() {
        if (open24h != null && open24h.compareTo(BigDecimal.ZERO) != 0 && lastPrice != null) {
            return lastPrice.subtract(open24h)
                    .divide(open24h, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        return changePercent;
    }

    /**
     * 计算价格变化百分比（相对于基准价格）
     */
    public BigDecimal calculateChangePercent(BigDecimal basePrice) {
        if (basePrice != null && basePrice.compareTo(BigDecimal.ZERO) != 0 && lastPrice != null) {
            return lastPrice.subtract(basePrice)
                    .divide(basePrice, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        return BigDecimal.ZERO;
    }
}
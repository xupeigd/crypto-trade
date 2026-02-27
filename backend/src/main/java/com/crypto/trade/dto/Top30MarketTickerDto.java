package com.crypto.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Top30MarketTickerDto
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Top30MarketTickerDto {

    /**
     * 供应商
     */
    private String vendor;

    /**
     * 合约类型
     */
    private String instType;

    /**
     * 时间字符串(yyyy-MM-dd HH格式)
     */
    private String tsHourStr;

    /**
     * Top30合约列表
     */
    private List<MarketTickerDto> top30Tickers;

    /**
     * 数据生成时间
     */
    private LocalDateTime generatedAt;

    /**
     * 总24小时USDT计价交易额
     */
    private BigDecimal totalVolume24hUsdt;

    /**
     * 平均24小时涨跌幅
     */
    private BigDecimal averageChange24hPercent;

    /**
     * 最高交易额合约
     */
    private MarketTickerDto highestVolumeTicker;

    /**
     * 涨幅最大合约
     */
    private MarketTickerDto highestGainerTicker;

    /**
     * 跌幅最大合约
     */
    private MarketTickerDto highestLoserTicker;

    /**
     * 计算统计信息
     */
    public void calculateStatistics() {
        if (top30Tickers == null || top30Tickers.isEmpty()) {
            return;
        }

        // 计算总交易额
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal totalChange = BigDecimal.ZERO;
        int validChangeCount = 0;

        MarketTickerDto maxVolumeTicker = null;
        MarketTickerDto maxGainerTicker = null;
        MarketTickerDto maxLoserTicker = null;

        for (MarketTickerDto ticker : top30Tickers) {
            // 总交易额
            if (ticker.getVolume24hUsdt() != null) {
                totalVolume = totalVolume.add(ticker.getVolume24hUsdt());

                // 最高交易额合约
                if (maxVolumeTicker == null ||
                        ticker.getVolume24hUsdt().compareTo(maxVolumeTicker.getVolume24hUsdt()) > 0) {
                    maxVolumeTicker = ticker;
                }
            }

            // 涨跌幅统计
            if (ticker.getChange24hPercent() != null) {
                totalChange = totalChange.add(ticker.getChange24hPercent());
                validChangeCount++;

                // 最高涨幅合约
                if (maxGainerTicker == null ||
                        ticker.getChange24hPercent().compareTo(maxGainerTicker.getChange24hPercent()) > 0) {
                    maxGainerTicker = ticker;
                }

                // 最大跌幅合约
                if (maxLoserTicker == null ||
                        ticker.getChange24hPercent().compareTo(maxLoserTicker.getChange24hPercent()) < 0) {
                    maxLoserTicker = ticker;
                }
            }
        }

        this.totalVolume24hUsdt = totalVolume;
        this.highestVolumeTicker = maxVolumeTicker;
        this.highestGainerTicker = maxGainerTicker;
        this.highestLoserTicker = maxLoserTicker;

        // 计算平均涨跌幅
        if (validChangeCount > 0) {
            this.averageChange24hPercent = totalChange.divide(
                    new BigDecimal(validChangeCount), 4, RoundingMode.HALF_UP);
        }
    }
}
package com.crypto.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * MarketTickerDto
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketTickerDto {

    /**
     * 供应商
     */
    private String vendor;

    /**
     * 合约类型
     */
    private String instType;

    /**
     * 合约ID
     */
    private String instId;

    /**
     * 基础货币 (从instId解析, 例如 BTC-USDT-SWAP -> BTC)
     */
    private String baseAsset;

    /**
     * 报价货币 (从instId解析, 例如 BTC-USDT-SWAP -> USDT)
     */
    private String quoteAsset;

    /**
     * 排名
     */
    private Integer rank;

    /**
     * 最新价格
     */
    private BigDecimal last;

    /**
     * 最新成交量
     */
    private BigDecimal lastSz;

    /**
     * 买一价格
     */
    private BigDecimal bidPrice;

    /**
     * 买一数量
     */
    private BigDecimal bidSz;

    /**
     * 卖一价格
     */
    private BigDecimal askPrice;

    /**
     * 卖一数量
     */
    private BigDecimal askSz;

    /**
     * 24小时开盘价
     */
    private BigDecimal open24h;

    /**
     * 24小时最高价
     */
    private BigDecimal high24h;

    /**
     * 24小时最低价
     */
    private BigDecimal low24h;

    /**
     * 24小时基础货币成交量
     */
    private BigDecimal volCcy24h;

    /**
     * 24小时报价货币成交量
     */
    private BigDecimal vol24h;

    /**
     * 24小时USDT计价交易额
     */
    private BigDecimal volume24hUsdt;

    /**
     * 24小时涨跌幅(%)
     */
    private BigDecimal change24hPercent;

    /**
     * 4小时涨跌幅(%)
     */
    private BigDecimal change4hPercent;

    /**
     * 合约面值(最小交易单位)
     */
    private BigDecimal ctVal;

    /**
     * 涨跌概率(%) - 基于最近100根4H K线计算
     */
    private BigDecimal riseProbability;

    /**
     * 最大涨幅(%) - 最近100根4H K线中的最大单根涨幅
     */
    private BigDecimal maxRise;

    /**
     * 最大跌幅(%) - 最近100根4H K线中的最大单根跌幅
     */
    private BigDecimal maxFall;

    /**
     * 时间戳
     */
    private Long ts;

    /**
     * 时间字符串(yyyy-MM-dd HH格式)
     */
    private String tsHourStr;

    /**
     * 数据摄入时间
     */
    private LocalDateTime dataIngestionTime;

    /**
     * 交易额排名
     */
    private Integer volumeRank;

    /**
     * 计算24小时USDT计价交易额
     */
    public void calculateVolume24hUsdt() {
        if (last != null && volCcy24h != null) {
            this.volume24hUsdt = last.multiply(volCcy24h);
        }
    }

    /**
     * 计算24小时涨跌幅
     */
    public void calculateChange24hPercent() {
        if (open24h != null && last != null && open24h.compareTo(BigDecimal.ZERO) > 0) {
            this.change24hPercent = last.subtract(open24h)
                    .divide(open24h, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
    }

    /**
     * 从instId解析baseAsset和quoteAsset
     * 例如: BTC-USDT-SWAP -> baseAsset=BTC, quoteAsset=USDT
     */
    public void parseAssetsFromInstId() {
        if (instId != null && instId.contains("-")) {
            String[] parts = instId.split("-");
            if (parts.length >= 2) {
                this.baseAsset = parts[0];
                this.quoteAsset = parts[1];
            }
        }
    }
}
package com.crypto.trade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MarketContext
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketContext {

    private String userId;
    private LocalDateTime timestamp;
    private MarketCondition marketCondition;
    private List<String> availableSymbols;
    private Map<String, BigDecimal> currentPrices;
    private Map<String, BigDecimal> volume24h;
    private Map<String, BigDecimal> priceChange24h;
    private List<TechnicalIndicator> technicalIndicators;
    private MarketSentiment sentiment;
    private MarketVolatility volatility;

    /**
     * 获取指定合约的当前价格
     */
    public BigDecimal getCurrentPrice(String symbol) {
        return currentPrices != null ? currentPrices.getOrDefault(symbol, BigDecimal.ZERO) : BigDecimal.ZERO;
    }

    /**
     * 获取指定合约的24小时交易量
     */
    public BigDecimal getVolume24h(String symbol) {
        return volume24h != null ? volume24h.getOrDefault(symbol, BigDecimal.ZERO) : BigDecimal.ZERO;
    }

    /**
     * 获取指定合约的24小时价格变化
     */
    public BigDecimal getPriceChange24h(String symbol) {
        return priceChange24h != null ? priceChange24h.getOrDefault(symbol, BigDecimal.ZERO) : BigDecimal.ZERO;
    }

    /**
     * 市场状况枚举
     */
    public enum MarketCondition {
        BULL_MARKET("牛市"),
        BEAR_MARKET("熊市"),
        SIDEWAYS("震荡市"),
        UNCERTAIN("不确定");

        private final String description;

        MarketCondition(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 市场情绪枚举
     */
    public enum MarketSentiment {
        EXTREME_FEAR("极度恐惧"),
        FEAR("恐惧"),
        NEUTRAL("中性"),
        GREED("贪婪"),
        EXTREME_GREED("极度贪婪");

        private final String description;

        MarketSentiment(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 市场波动性枚举
     */
    public enum MarketVolatility {
        LOW("低波动"),
        MEDIUM("中等波动"),
        HIGH("高波动"),
        EXTREME("极端波动");

        private final String description;

        MarketVolatility(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
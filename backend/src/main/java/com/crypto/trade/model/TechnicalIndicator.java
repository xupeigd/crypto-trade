package com.crypto.trade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * TechnicalIndicator
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnicalIndicator {

    private String symbol;
    private String indicatorName;
    private String timeframe;
    private BigDecimal value;
    private String signal; // BUY/SELL/HOLD/NEUTRAL
    private BigDecimal strength; // 信号强度 0-1
    private LocalDateTime timestamp;
    private String description;

    /**
     * RSI指标
     */
    public static TechnicalIndicator rsi(String symbol, BigDecimal value, String timeframe) {
        String signal = "NEUTRAL";
        BigDecimal strength = BigDecimal.ZERO;

        if (value.compareTo(BigDecimal.valueOf(30)) < 0) {
            signal = "BUY";
            strength = BigDecimal.valueOf(70).subtract(value).divide(BigDecimal.valueOf(40), 2, RoundingMode.HALF_UP);
        } else if (value.compareTo(BigDecimal.valueOf(70)) > 0) {
            signal = "SELL";
            strength = value.subtract(BigDecimal.valueOf(70)).divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
        }

        return TechnicalIndicator.builder()
                .symbol(symbol)
                .indicatorName("RSI")
                .timeframe(timeframe)
                .value(value)
                .signal(signal)
                .strength(strength)
                .timestamp(LocalDateTime.now())
                .description("RSI相对强弱指标")
                .build();
    }

    /**
     * MACD指标
     */
    public static TechnicalIndicator macd(String symbol, BigDecimal macdLine, BigDecimal signalLine, String timeframe) {
        String signal;
        BigDecimal strength;

        if (macdLine.compareTo(signalLine) > 0) {
            signal = "BUY";
            strength = macdLine.subtract(signalLine).abs().divide(BigDecimal.valueOf(0.01), 2, RoundingMode.HALF_UP);
        } else {
            signal = "SELL";
            strength = signalLine.subtract(macdLine).abs().divide(BigDecimal.valueOf(0.01), 2, RoundingMode.HALF_UP);
        }

        // 限制强度在0-1之间
        strength = strength.min(BigDecimal.ONE);

        return TechnicalIndicator.builder()
                .symbol(symbol)
                .indicatorName("MACD")
                .timeframe(timeframe)
                .value(macdLine)
                .signal(signal)
                .strength(strength)
                .timestamp(LocalDateTime.now())
                .description("MACD移动平均收敛散度")
                .build();
    }

    /**
     * 布林带指标
     */
    public static TechnicalIndicator bollingerBands(String symbol, BigDecimal currentPrice,
                                                    BigDecimal upperBand, BigDecimal lowerBand, String timeframe) {
        String signal = "HOLD";
        BigDecimal strength = BigDecimal.ZERO;

        if (currentPrice.compareTo(lowerBand) <= 0) {
            signal = "BUY";
            strength = lowerBand.subtract(currentPrice).divide(lowerBand, 2, RoundingMode.HALF_UP);
        } else if (currentPrice.compareTo(upperBand) >= 0) {
            signal = "SELL";
            strength = currentPrice.subtract(upperBand).divide(upperBand, 2, RoundingMode.HALF_UP);
        }

        // 限制强度在0-1之间
        strength = strength.min(BigDecimal.ONE);

        return TechnicalIndicator.builder()
                .symbol(symbol)
                .indicatorName("BOLLINGER_BANDS")
                .timeframe(timeframe)
                .value(currentPrice)
                .signal(signal)
                .strength(strength)
                .timestamp(LocalDateTime.now())
                .description("布林带指标")
                .build();
    }

    /**
     * 移动平均线指标
     */
    public static TechnicalIndicator movingAverage(String symbol, BigDecimal currentPrice,
                                                   BigDecimal maPrice, int period, String timeframe) {
        String signal;
        BigDecimal strength;

        if (currentPrice.compareTo(maPrice) > 0) {
            signal = "BUY";
            strength = currentPrice.subtract(maPrice).divide(maPrice, 4, RoundingMode.HALF_UP);
        } else {
            signal = "SELL";
            strength = maPrice.subtract(currentPrice).divide(maPrice, 4, RoundingMode.HALF_UP);
        }

        // 限制强度在0-1之间
        strength = strength.min(BigDecimal.ONE).max(BigDecimal.ZERO);

        return TechnicalIndicator.builder()
                .symbol(symbol)
                .indicatorName("MA_" + period)
                .timeframe(timeframe)
                .value(maPrice)
                .signal(signal)
                .strength(strength)
                .timestamp(LocalDateTime.now())
                .description(period + "期移动平均线")
                .build();
    }

    /**
     * 是否为买入信号
     */
    public boolean isBuySignal() {
        return "BUY".equalsIgnoreCase(signal);
    }

    /**
     * 是否为卖出信号
     */
    public boolean isSellSignal() {
        return "SELL".equalsIgnoreCase(signal);
    }

    /**
     * 是否为中性信号
     */
    public boolean isNeutralSignal() {
        return "NEUTRAL".equalsIgnoreCase(signal) || "HOLD".equalsIgnoreCase(signal);
    }

    /**
     * 信号强度是否足够强
     */
    public boolean hasStrongSignal() {
        return strength != null && strength.compareTo(BigDecimal.valueOf(0.7)) >= 0;
    }
}
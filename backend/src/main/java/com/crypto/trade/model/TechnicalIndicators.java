package com.crypto.trade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * TechnicalIndicators
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TechnicalIndicators {

    /**
     * 合约ID
     */
    private String instId;

    /**
     * 时间周期
     */
    private String timeframe;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 当前价格
     */
    private BigDecimal currentPrice;

    /**
     * RSI指标数据
     */
    private RsiData rsiData;

    /**
     * BOLL指标数据
     */
    private BollData bollData;

    /**
     * ADX指标数据
     */
    private AdxData adxData;

    /**
     * MACD指标数据
     */
    private MacdData macdData;

    /**
     * MACD历史数据（DIF）
     */
    private List<Double> macdHistory;

    /**
     * DEA历史数据（信号线）
     */
    private List<Double> deaHistory;

    /**
     * 柱状图历史数据
     */
    private List<Double> histogramHistory;

    /**
     * OHLC历史数据
     */
    private List<OhlcData> ohlcHistory;

    /**
     * RSI历史序列值
     */
    private List<Double> rsi5History;
    private List<Double> rsi20History;
    private List<Double> rsi30History;

    /**
     * EMA历史序列值
     */
    private List<Double> ema5History;
    private List<Double> ema20History;
    private List<Double> ema30History;

    /**
     * BOLL历史序列值
     */
    private List<BollPeriod> boll5History;
    private List<BollPeriod> boll15History;
    private List<BollPeriod> boll20History;
    private List<BollPeriod> boll25History;

    /**
     * ADX历史序列值
     */
    private List<Double> adx14History;

    /**
     * 获取RSI信号状态
     */
    public static String getRsiSignal(BigDecimal rsiValue) {
        if (rsiValue == null) {
            return "数据不足";
        }

        if (rsiValue.compareTo(new BigDecimal("80")) >= 0) {
            return "严重超买";
        } else if (rsiValue.compareTo(new BigDecimal("70")) >= 0) {
            return "超买";
        } else if (rsiValue.compareTo(new BigDecimal("60")) >= 0) {
            return "中性偏强";
        } else if (rsiValue.compareTo(new BigDecimal("40")) >= 0) {
            return "中性";
        } else if (rsiValue.compareTo(new BigDecimal("30")) >= 0) {
            return "中性偏弱";
        } else if (rsiValue.compareTo(new BigDecimal("20")) >= 0) {
            return "超卖";
        } else {
            return "严重超卖";
        }
    }

    /**
     * 获取BOLL价格位置
     */
    public static String getBollPricePosition(BigDecimal currentPrice, BigDecimal upperBand,
                                              BigDecimal middleBand, BigDecimal lowerBand) {
        if (currentPrice == null || upperBand == null || middleBand == null || lowerBand == null) {
            return "数据不足";
        }

        if (currentPrice.compareTo(upperBand) > 0) {
            return "上轨上方";
        } else if (currentPrice.compareTo(upperBand) >= 0 && currentPrice.compareTo(middleBand) > 0) {
            return "上轨附近";
        } else if (currentPrice.compareTo(middleBand) >= 0 && currentPrice.compareTo(lowerBand) > 0) {
            return "中轨附近";
        } else if (currentPrice.compareTo(lowerBand) >= 0) {
            return "下轨附近";
        } else {
            return "下轨下方";
        }
    }

    /**
     * 获取BOLL信号
     */
    public static String getBollSignal(String pricePosition) {
        switch (pricePosition) {
            case "上轨上方":
                return "强势突破，关注回调风险";
            case "上轨附近":
                return "强势区域，注意阻力";
            case "中轨附近":
                return "震荡区域，观察方向";
            case "下轨附近":
                return "弱势区域，关注支撑";
            case "下轨下方":
                return "强势下跌，可能有反弹";
            default:
                return "信号不明确";
        }
    }

    /**
     * 计算总体RSI信号
     */
    public static String calculateOverallRsiSignal(BigDecimal rsi5, BigDecimal rsi15, BigDecimal rsi25) {
        String signal5 = getRsiSignal(rsi5);
        String signal15 = getRsiSignal(rsi15);
        String signal25 = getRsiSignal(rsi25);

        // 如果所有周期都显示超买或超卖
        if (signal5.contains("超买") && signal15.contains("超买") && signal25.contains("超买")) {
            return "强烈看跌回调";
        } else if (signal5.contains("超卖") && signal15.contains("超卖") && signal25.contains("超卖")) {
            return "强烈看涨反弹";
        }
        // 如果短期超买/超卖但长期中性
        else if (signal5.contains("超买") && signal25.contains("中性")) {
            return "短期回调可能";
        } else if (signal5.contains("超卖") && signal25.contains("中性")) {
            return "短期反弹可能";
        }
        // 如果趋势一致
        else if (signal5.contains("偏强") && signal15.contains("偏强") && signal25.contains("偏强")) {
            return "上涨趋势";
        } else if (signal5.contains("偏弱") && signal15.contains("偏弱") && signal25.contains("偏弱")) {
            return "下跌趋势";
        }

        return "趋势不明确";
    }

    /**
     * 获取ADX信号状态
     *
     * @param adxValue ADX值
     * @return 信号状态
     */
    public static String getAdxSignal(BigDecimal adxValue) {
        if (adxValue == null) {
            return "数据不足";
        }

        if (adxValue.compareTo(new BigDecimal("50")) >= 0) {
            return "强趋势";
        } else if (adxValue.compareTo(new BigDecimal("25")) >= 0) {
            return "趋势";
        } else if (adxValue.compareTo(new BigDecimal("20")) >= 0) {
            return "弱趋势";
        } else {
            return "无趋势";
        }
    }

    /**
     * 获取ADX趋势方向
     *
     * @param plusDI  +DI值
     * @param minusDI -DI值
     * @return 趋势方向
     */
    public static String getAdxTrendDirection(BigDecimal plusDI, BigDecimal minusDI) {
        if (plusDI == null || minusDI == null) {
            return "数据不足";
        }

        if (plusDI.compareTo(minusDI) > 0) {
            return "上涨";
        } else if (plusDI.compareTo(minusDI) < 0) {
            return "下跌";
        } else {
            return "横盘";
        }
    }

    /**
     * 计算总体ADX信号
     */
    public static String calculateOverallAdxSignal(BigDecimal adx14) {
        String strength = getAdxSignal(adx14);

        if (strength.equals("无趋势")) {
            return "市场无明确趋势，建议观望";
        } else if (strength.equals("弱趋势")) {
            return "趋势较弱，可能反转";
        } else if (strength.equals("趋势")) {
            return "趋势存在，可考虑顺势交易";
        } else if (strength.equals("强趋势")) {
            return "趋势强劲，适合趋势跟踪策略";
        }

        return "信号不明确";
    }

    /**
     * 计算总体BOLL信号
     */
    public static String calculateOverallBollSignal(String position5, String position15, String position25) {
        // 添加空值检查，防止空指针异常
        if (position5 == null) {
            return "数据不足";
        }

        // 如果只有5周期数据，进行基于单周期的分析
        if (position15 == null && position25 == null) {
            if (position5.contains("上轨上") || position5.contains("上轨附近")) {
                return "强势突破，注意回调";
            } else if (position5.contains("下轨下") || position5.contains("下轨附近")) {
                return "弱势下跌，关注反弹";
            } else if (position5.contains("中轨")) {
                return "震荡整理，等待方向";
            } else {
                return "位置不明确";
            }
        }

        // 如果15周期数据为null，使用安全检查
        if (position15 == null) {
            position15 = "";
        }
        if (position25 == null) {
            position25 = "";
        }

        // 如果价格在多个周期的上轨上方或附近
        if ((position5.contains("上轨上") || position5.contains("上轨附近")) &&
                (position15.contains("上轨上") || position15.contains("上轨附近"))) {
            return "强势突破，注意回调";
        }
        // 如果价格在多个周期的下轨下方或附近
        else if ((position5.contains("下轨下") || position5.contains("下轨附近")) &&
                (position15.contains("下轨下") || position15.contains("下轨附近"))) {
            return "弱势下跌，关注反弹";
        }
        // 如果在中轨附近震荡
        else if (position5.contains("中轨") && position15.contains("中轨")) {
            return "震荡整理，等待方向";
        }
        // 从下轨向上突破
        else if (position5.contains("中轨") && (position15.contains("下轨附近") || position25.contains("下轨附近"))) {
            return "反弹迹象";
        }
        // 从上轨向下回调
        else if (position5.contains("中轨") && (position15.contains("上轨附近") || position25.contains("上轨附近"))) {
            return "回调迹象";
        }

        return "位置不明确";
    }

    /**
     * 格式化技术指标为详细表格格式
     */
    public static String formatTechnicalIndicatorsAsTable(TechnicalIndicators indicators) {
        if (indicators == null) {
            return "技术指标数据不可用";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n## ").append(indicators.getInstId()).append(" 技术指标详细数据 (").append(indicators.getTimeframe()).append(")\n\n");

        // 当前价格信息
        sb.append("**当前价格**: ").append(indicators.getCurrentPrice()).append("\n\n");

        // 1. OHLC数据表格（横向显示，时间从近到远）
        if (indicators.getOhlcHistory() != null && !indicators.getOhlcHistory().isEmpty()) {
            sb.append("### OHLC数据 (最近120条，横向时间轴)\n\n");

            // 表头：时间行
            sb.append("| 指标 ");
            int ohlcSize = indicators.getOhlcHistory().size();
            int ohlcStartIdx = Math.max(0, ohlcSize - 48); // 显示最近48条数据

            // 时间行表头
            for (int i = ohlcSize - 1; i >= ohlcStartIdx; i--) {
                OhlcData ohlc = indicators.getOhlcHistory().get(i);
                sb.append("| ").append(ohlc.getTime()).append(" ");
            }
            sb.append("|\n");

            // 分隔行
            sb.append("|------");
            for (int i = ohlcSize - 1; i >= ohlcStartIdx; i--) {
                sb.append("|----------");
            }
            sb.append("|\n");

            // 开盘价行
            sb.append("| 开盘价 ");
            for (int i = ohlcSize - 1; i >= ohlcStartIdx; i--) {
                OhlcData ohlc = indicators.getOhlcHistory().get(i);
                sb.append("| ").append(ohlc.getOpen()).append(" ");
            }
            sb.append("|\n");

            // 最高价行
            sb.append("| 最高价 ");
            for (int i = ohlcSize - 1; i >= ohlcStartIdx; i--) {
                OhlcData ohlc = indicators.getOhlcHistory().get(i);
                sb.append("| ").append(ohlc.getHigh()).append(" ");
            }
            sb.append("|\n");

            // 最低价行
            sb.append("| 最低价 ");
            for (int i = ohlcSize - 1; i >= ohlcStartIdx; i--) {
                OhlcData ohlc = indicators.getOhlcHistory().get(i);
                sb.append("| ").append(ohlc.getLow()).append(" ");
            }
            sb.append("|\n");

            // 收盘价行
            sb.append("| 收盘价 ");
            for (int i = ohlcSize - 1; i >= ohlcStartIdx; i--) {
                OhlcData ohlc = indicators.getOhlcHistory().get(i);
                sb.append("| ").append(ohlc.getClose()).append(" ");
            }
            sb.append("|\n");

            // 成交量行
            sb.append("| 成交量 ");
            for (int i = ohlcSize - 1; i >= ohlcStartIdx; i--) {
                OhlcData ohlc = indicators.getOhlcHistory().get(i);
                sb.append("| ").append(ohlc.getVolume()).append(" ");
            }
            sb.append("|\n\n");
        }

        // 2. RSI指标详细表格
        if (indicators.getRsiData() != null) {
            sb.append("### RSI指标详情\n\n");
            sb.append("| 周期 | 当前数值 | 信号状态 |\n");
            sb.append("|------|----------|----------|\n");
            sb.append("| RSI-5  | ").append(indicators.getRsiData().getRsi5()).append(" | ").append(indicators.getRsiData().getRsi5Signal()).append(" |\n");
            sb.append("| RSI-20 | ").append(indicators.getRsiData().getRsi20()).append(" | ").append(indicators.getRsiData().getRsi20Signal()).append(" |\n");
            sb.append("| RSI-30 | ").append(indicators.getRsiData().getRsi30()).append(" | ").append(indicators.getRsiData().getRsi30Signal()).append(" |\n");
            sb.append("\n**总体信号**: ").append(indicators.getRsiData().getOverallSignal()).append("\n\n");

            // RSI历史序列（横向显示，时间从近到远）
            sb.append("#### RSI历史序列 (最近120个值，横向时间轴)\n\n");

            // 计算RSI数据的最小尺寸
            int rsi5Size = indicators.getRsi5History() != null ? indicators.getRsi5History().size() : 0;
            int rsi20Size = indicators.getRsi20History() != null ? indicators.getRsi20History().size() : 0;
            int rsi30Size = indicators.getRsi30History() != null ? indicators.getRsi30History().size() : 0;
            int minRsiSize = Math.min(Math.min(rsi5Size, rsi20Size), rsi30Size);
            int rsiStartIdx = Math.max(0, minRsiSize - 48); // 显示最近48条数据

            // 时间行表头
            sb.append("| 时间 ");
            int ohlcSize = indicators.getOhlcHistory().size();
            int rsiOffset = ohlcSize - minRsiSize; // RSI数据相对于OHLC的偏移量
            for (int i = minRsiSize - 1; i >= rsiStartIdx; i--) {
                int ohlcIdx = rsiOffset + i; // 计算对应的OHLC索引
                if (ohlcIdx >= 0 && ohlcIdx < ohlcSize) {
                    String time = indicators.getOhlcHistory().get(ohlcIdx).getTime();
                    sb.append("| ").append(time).append(" ");
                }
            }
            sb.append("|\n");

            // 分隔行
            sb.append("|------");
            for (int i = minRsiSize - 1; i >= rsiStartIdx; i--) {
                sb.append("|-------");
            }
            sb.append("|\n");

            // RSI-5行
            sb.append("| RSI-5 ");
            for (int i = minRsiSize - 1; i >= rsiStartIdx; i--) {
                BigDecimal rsi5 = indicators.getRsi5History() != null && i < indicators.getRsi5History().size() ?
                        BigDecimal.valueOf(indicators.getRsi5History().get(i)).setScale(8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                sb.append("| ").append(rsi5).append(" ");
            }
            sb.append("|\n");

            // RSI-20行
            sb.append("| RSI-20 ");
            for (int i = minRsiSize - 1; i >= rsiStartIdx; i--) {
                BigDecimal rsi20 = indicators.getRsi20History() != null && i < indicators.getRsi20History().size() ?
                        BigDecimal.valueOf(indicators.getRsi20History().get(i)).setScale(8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                sb.append("| ").append(rsi20).append(" ");
            }
            sb.append("|\n");

            // RSI-30行
            sb.append("| RSI-30 ");
            for (int i = minRsiSize - 1; i >= rsiStartIdx; i--) {
                BigDecimal rsi30 = indicators.getRsi30History() != null && i < indicators.getRsi30History().size() ?
                        BigDecimal.valueOf(indicators.getRsi30History().get(i)).setScale(8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                sb.append("| ").append(rsi30).append(" ");
            }
            sb.append("|\n\n");
        }

        // 3. BOLL指标详细表格
        if (indicators.getBollData() != null) {
            sb.append("### BOLL指标详情\n\n");
            sb.append("| 周期 | 上轨 | 中轨 | 下轨 | 当前价格位置 | 信号 |\n");
            sb.append("|------|------|------|------|--------------|------|\n");

            BollPeriod boll5 = indicators.getBollData().getBoll5();
            BollPeriod boll15 = indicators.getBollData().getBoll15();
            BollPeriod boll25 = indicators.getBollData().getBoll25();

            if (boll5 != null) {
                sb.append("| BOLL-5  | ").append(boll5.getUpperBand())
                        .append(" | ").append(boll5.getMiddleBand())
                        .append(" | ").append(boll5.getLowerBand())
                        .append(" | ").append(boll5.getPricePosition())
                        .append(" | ").append(boll5.getSignal()).append(" |\n");
            }
            if (boll15 != null) {
                sb.append("| BOLL-15 | ").append(boll15.getUpperBand())
                        .append(" | ").append(boll15.getMiddleBand())
                        .append(" | ").append(boll15.getLowerBand())
                        .append(" | ").append(boll15.getPricePosition())
                        .append(" | ").append(boll15.getSignal()).append(" |\n");
            }
            if (boll25 != null) {
                sb.append("| BOLL-25 | ").append(boll25.getUpperBand())
                        .append(" | ").append(boll25.getMiddleBand())
                        .append(" | ").append(boll25.getLowerBand())
                        .append(" | ").append(boll25.getPricePosition())
                        .append(" | ").append(boll25.getSignal()).append(" |\n");
            }
            sb.append("\n**总体BOLL信号**: ").append(indicators.getBollData().getOverallSignal()).append("\n\n");

            // BOLL详细分析表格
            sb.append("#### BOLL详细分析\n\n");
            sb.append("| 周期 | 带宽 | 距上轨% | 距下轨% |\n");
            sb.append("|------|------|---------|---------|\n");

            if (boll5 != null) {
                sb.append("| BOLL-5  | ").append(boll5.getBandwidth())
                        .append(" | ").append(boll5.getDistanceToUpperPercent()).append("%")
                        .append(" | ").append(boll5.getDistanceToLowerPercent()).append("%").append(" |\n");
            }
            if (boll15 != null) {
                sb.append("| BOLL-15 | ").append(boll15.getBandwidth())
                        .append(" | ").append(boll15.getDistanceToUpperPercent()).append("%")
                        .append(" | ").append(boll15.getDistanceToLowerPercent()).append("%").append(" |\n");
            }
            if (boll25 != null) {
                sb.append("| BOLL-25 | ").append(boll25.getBandwidth())
                        .append(" | ").append(boll25.getDistanceToUpperPercent()).append("%")
                        .append(" | ").append(boll25.getDistanceToLowerPercent()).append("%").append(" |\n");
            }

            // BOLL历史序列表格（横向显示，时间从近到远）
            sb.append("\n#### BOLL历史序列 (最近120个值，横向时间轴)\n\n");

            // BOLL-5上轨横向表格
            sb.append("##### BOLL-5上轨\n\n");
            // 时间行表头
            sb.append("| 时间 ");
            int boll5Size = indicators.getBoll5History() != null ? indicators.getBoll5History().size() : 0;
            int boll15Size = indicators.getBoll15History() != null ? indicators.getBoll15History().size() : 0;
            int boll25Size = indicators.getBoll25History() != null ? indicators.getBoll25History().size() : 0;
            int minBollSize = Math.min(Math.min(boll5Size, boll15Size), boll25Size);
            int bollStartIdx = Math.max(0, minBollSize - 48); // 显示最近48条数据

            int ohlcSize4Boll = indicators.getOhlcHistory().size();
            int bollOffset = ohlcSize4Boll - minBollSize; // BOLL数据相对于OHLC的偏移量
            for (int i = minBollSize - 1; i >= bollStartIdx; i--) {
                int ohlcIdx = bollOffset + i; // 计算对应的OHLC索引
                if (ohlcIdx >= 0 && ohlcIdx < ohlcSize4Boll) {
                    String time = indicators.getOhlcHistory().get(ohlcIdx).getTime();
                    sb.append("| ").append(time).append(" ");
                }
            }
            sb.append("|\n");

            // 分隔行
            sb.append("|------");
            for (int i = minBollSize - 1; i >= bollStartIdx; i--) {
                sb.append("|-----------");
            }
            sb.append("|\n");

            // BOLL-5上轨数据行
            sb.append("| 上轨 ");
            for (int i = minBollSize - 1; i >= bollStartIdx; i--) {
                TechnicalIndicators.BollPeriod boll5Hist = indicators.getBoll5History() != null && i < indicators.getBoll5History().size() ?
                        indicators.getBoll5History().get(i) : null;
                sb.append("| ").append(boll5Hist != null ? boll5Hist.getUpperBand() : "N/A").append(" ");
            }
            sb.append("|\n");

            // BOLL-5中轨数据行
            sb.append("| 中轨 ");
            for (int i = minBollSize - 1; i >= bollStartIdx; i--) {
                TechnicalIndicators.BollPeriod boll5Hist = indicators.getBoll5History() != null && i < indicators.getBoll5History().size() ?
                        indicators.getBoll5History().get(i) : null;
                sb.append("| ").append(boll5Hist != null ? boll5Hist.getMiddleBand() : "N/A").append(" ");
            }
            sb.append("|\n");

            // BOLL-5下轨数据行
            sb.append("| 下轨 ");
            for (int i = minBollSize - 1; i >= bollStartIdx; i--) {
                TechnicalIndicators.BollPeriod boll5Hist = indicators.getBoll5History() != null && i < indicators.getBoll5History().size() ?
                        indicators.getBoll5History().get(i) : null;
                sb.append("| ").append(boll5Hist != null ? boll5Hist.getLowerBand() : "N/A").append(" ");
            }
            sb.append("|\n\n");

            // BOLL-15上轨横向表格
            sb.append("##### BOLL-15上轨\n\n");
            // 时间行表头
            sb.append("| 时间 ");
            for (int i = minBollSize - 1; i >= bollStartIdx; i--) {
                int ohlcIdx = bollOffset + i; // 计算对应的OHLC索引
                if (ohlcIdx >= 0 && ohlcIdx < ohlcSize4Boll) {
                    String time = indicators.getOhlcHistory().get(ohlcIdx).getTime();
                    sb.append("| ").append(time).append(" ");
                }
            }
            sb.append("|\n");

            // 分隔行
            sb.append("|------");
            for (int i = minBollSize - 1; i >= bollStartIdx; i--) {
                sb.append("|-----------");
            }
            sb.append("|\n");

            // BOLL-15上轨数据行
            sb.append("| 上轨 ");
            for (int i = minBollSize - 1; i >= bollStartIdx; i--) {
                TechnicalIndicators.BollPeriod boll15Hist = indicators.getBoll15History() != null && i < indicators.getBoll15History().size() ?
                        indicators.getBoll15History().get(i) : null;
                sb.append("| ").append(boll15Hist != null ? boll15Hist.getUpperBand() : "N/A").append(" ");
            }
            sb.append("|\n");

            // BOLL-15中轨数据行
            sb.append("| 中轨 ");
            for (int i = minBollSize - 1; i >= bollStartIdx; i--) {
                TechnicalIndicators.BollPeriod boll15Hist = indicators.getBoll15History() != null && i < indicators.getBoll15History().size() ?
                        indicators.getBoll15History().get(i) : null;
                sb.append("| ").append(boll15Hist != null ? boll15Hist.getMiddleBand() : "N/A").append(" ");
            }
            sb.append("|\n");

            // BOLL-15下轨数据行
            sb.append("| 下轨 ");
            for (int i = minBollSize - 1; i >= bollStartIdx; i--) {
                TechnicalIndicators.BollPeriod boll15Hist = indicators.getBoll15History() != null && i < indicators.getBoll15History().size() ?
                        indicators.getBoll15History().get(i) : null;
                sb.append("| ").append(boll15Hist != null ? boll15Hist.getLowerBand() : "N/A").append(" ");
            }
            sb.append("|\n\n");

            // BOLL-25上轨横向表格
            sb.append("##### BOLL-25上轨\n\n");
            // 时间行表头
            sb.append("| 时间 ");
            for (int i = minBollSize - 1; i >= bollStartIdx; i--) {
                int ohlcIdx = bollOffset + i; // 计算对应的OHLC索引
                if (ohlcIdx >= 0 && ohlcIdx < ohlcSize4Boll) {
                    String time = indicators.getOhlcHistory().get(ohlcIdx).getTime();
                    sb.append("| ").append(time).append(" ");
                }
            }
            sb.append("|\n");

            // 分隔行
            sb.append("|------");
            for (int i = minBollSize - 1; i >= bollStartIdx; i--) {
                sb.append("|-----------");
            }
            sb.append("|\n");

            // BOLL-25上轨数据行
            sb.append("| 上轨 ");
            for (int i = minBollSize - 1; i >= bollStartIdx; i--) {
                TechnicalIndicators.BollPeriod boll25Hist = indicators.getBoll25History() != null && i < indicators.getBoll25History().size() ?
                        indicators.getBoll25History().get(i) : null;
                sb.append("| ").append(boll25Hist != null ? boll25Hist.getUpperBand() : "N/A").append(" ");
            }
            sb.append("|\n");

            // BOLL-25中轨数据行
            sb.append("| 中轨 ");
            for (int i = minBollSize - 1; i >= bollStartIdx; i--) {
                TechnicalIndicators.BollPeriod boll25Hist = indicators.getBoll25History() != null && i < indicators.getBoll25History().size() ?
                        indicators.getBoll25History().get(i) : null;
                sb.append("| ").append(boll25Hist != null ? boll25Hist.getMiddleBand() : "N/A").append(" ");
            }
            sb.append("|\n");

            // BOLL-25下轨数据行
            sb.append("| 下轨 ");
            for (int i = minBollSize - 1; i >= bollStartIdx; i--) {
                TechnicalIndicators.BollPeriod boll25Hist = indicators.getBoll25History() != null && i < indicators.getBoll25History().size() ?
                        indicators.getBoll25History().get(i) : null;
                sb.append("| ").append(boll25Hist != null ? boll25Hist.getLowerBand() : "N/A").append(" ");
            }
            sb.append("|\n\n");
        }

        return sb.toString();
    }

    /**
     * 获取MACD信号
     */
    public static String getMacdSignal(BigDecimal diff, BigDecimal dea) {
        if (diff == null || dea == null) return "数据不足";

        if (diff.compareTo(dea) > 0) {
            return "金叉看涨";
        } else {
            return "死叉看跌";
        }
    }

    /**
     * 计算总体MACD信号
     */
    public static String calculateOverallMacdSignal(MacdPeriod macdPeriod) {
        if (macdPeriod == null) return "数据不足";

        String trendSignal = macdPeriod.getTrendSignal();
        if (trendSignal != null) {
            return trendSignal;
        }

        return getMacdSignal(macdPeriod.getDiff(), macdPeriod.getDea());
    }

    /**
     * OHLC数据结构
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OhlcData {
        /**
         * 时间戳
         */
        private Long timestamp;

        /**
         * 时间字符串 (格式: HH:mm)
         */
        private String time;

        /**
         * 开盘价
         */
        private BigDecimal open;

        /**
         * 最高价
         */
        private BigDecimal high;

        /**
         * 最低价
         */
        private BigDecimal low;

        /**
         * 收盘价
         */
        private BigDecimal close;

        /**
         * 成交量
         */
        private BigDecimal volume;

        /**
         * 成交额
         */
        private BigDecimal volumeCcy;
    }

    /**
     * RSI指标数据
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RsiData {
        /**
         * RSI 5周期数值
         */
        private BigDecimal rsi5;

        /**
         * RSI 20周期数值
         */
        private BigDecimal rsi20;

        /**
         * RSI 30周期数值
         */
        private BigDecimal rsi30;

        /**
         * RSI 5周期信号状态
         */
        private String rsi5Signal;

        /**
         * RSI 20周期信号状态
         */
        private String rsi20Signal;

        /**
         * RSI 30周期信号状态
         */
        private String rsi30Signal;

        /**
         * 总体RSI信号
         */
        private String overallSignal;
    }

    /**
     * BOLL指标数据
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BollData {
        /**
         * BOLL 5周期数据
         */
        private BollPeriod boll5;

        /**
         * BOLL 15周期数据
         */
        private BollPeriod boll15;

        /**
         * BOLL 20周期数据
         */
        private BollPeriod boll20;

        /**
         * BOLL 25周期数据
         */
        private BollPeriod boll25;

        /**
         * 总体BOLL信号
         */
        private String overallSignal;
    }

    /**
     * BOLL单周期数据
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BollPeriod {
        /**
         * 周期
         */
        private Integer period;

        /**
         * 上轨价格
         */
        private BigDecimal upperBand;

        /**
         * 中轨价格
         */
        private BigDecimal middleBand;

        /**
         * 下轨价格
         */
        private BigDecimal lowerBand;

        /**
         * 当前价格相对于BOLL的位置
         */
        private String pricePosition;

        /**
         * 信号状态
         */
        private String signal;

        /**
         * BOLL宽度（上轨-下轨）
         */
        private BigDecimal bandwidth;

        /**
         * 价格距离上轨的百分比
         */
        private BigDecimal distanceToUpperPercent;

        /**
         * 价格距离下轨的百分比
         */
        private BigDecimal distanceToLowerPercent;
    }

    /**
     * ADX指标数据
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AdxData {
        /**
         * ADX 14周期数据
         */
        private AdxPeriod adx14;

        /**
         * 总体ADX信号
         */
        private String overallSignal;
    }

    /**
     * ADX单周期数据
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AdxPeriod {
        /**
         * 周期
         */
        private Integer period;

        /**
         * ADX值
         */
        private BigDecimal adx;

        /**
         * +DI值 (Positive Directional Indicator)
         */
        private BigDecimal plusDI;

        /**
         * -DI值 (Negative Directional Indicator)
         */
        private BigDecimal minusDI;

        /**
         * 趋势强度
         */
        private String trendStrength;

        /**
         * 趋势方向
         */
        private String trendDirection;
    }

    /**
     * MACD指标数据
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MacdData {
        /**
         * MACD 12,26,9周期数据
         */
        private MacdPeriod macd;

        /**
         * 总体MACD信号
         */
        private String overallSignal;
    }

    /**
     * MACD单周期数据
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MacdPeriod {
        /**
         * 快线周期
         */
        private Integer fastPeriod;

        /**
         * 慢线周期
         */
        private Integer slowPeriod;

        /**
         * 信号线周期
         */
        private Integer signalPeriod;

        /**
         * MACD值（DIF：快线EMA - 慢线EMA）
         */
        private BigDecimal diff;

        /**
         * DEA值（信号线：DIF的EMA）
         */
        private BigDecimal dea;

        /**
         * MACD柱状图值（2 * (DIF - DEA)）
         */
        private BigDecimal macd;

        /**
         * 趋势信号
         */
        private String trendSignal;
    }
}
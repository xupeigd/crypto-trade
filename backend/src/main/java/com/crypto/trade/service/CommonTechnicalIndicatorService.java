package com.crypto.trade.service;

import com.crypto.trade.dto.indicator.IndicatorCalculationRequest;
import com.crypto.trade.dto.indicator.IndicatorCalculationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CommonTechnicalIndicatorService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Slf4j
@Service
public class CommonTechnicalIndicatorService {

    private static final int SCALE = 8;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    public IndicatorCalculationResponse calculate(IndicatorCalculationRequest request) {
        if (request == null || request.getOhlcData() == null || request.getOhlcData().isEmpty()) {
            return IndicatorCalculationResponse.builder().results(Collections.emptyMap()).build();
        }

        // Sort OHLC data by timestamp
        List<IndicatorCalculationRequest.OhlcItem> sortedOhlc = request.getOhlcData().stream()
                .sorted(Comparator.comparing(IndicatorCalculationRequest.OhlcItem::getTimestamp))
                .collect(Collectors.toList());

        Map<String, List<IndicatorCalculationResponse.IndicatorResult>> results = new HashMap<>();

        if (request.getIndicators() != null) {
            for (IndicatorCalculationRequest.IndicatorConfig config : request.getIndicators()) {
                String name = config.getName().toUpperCase();
                List<IndicatorCalculationResponse.IndicatorResult> indicatorResults = new ArrayList<>();

                switch (name) {
                    case "SMA":
                        for (Integer period : config.getPeriods()) {
                            indicatorResults.add(calculateSMA(sortedOhlc, period));
                        }
                        break;
                    case "EMA":
                        for (Integer period : config.getPeriods()) {
                            indicatorResults.add(calculateEMA(sortedOhlc, period));
                        }
                        break;
                    case "WMA":
                        for (Integer period : config.getPeriods()) {
                            indicatorResults.add(calculateWMA(sortedOhlc, period));
                        }
                        break;
                    case "RSI":
                        for (Integer period : config.getPeriods()) {
                            indicatorResults.add(calculateRSI(sortedOhlc, period));
                        }
                        break;
                    case "BOLL":
                        // Assuming period list contains "length" (default stdDev=2) or just use convention
                        // If user passes multiple periods, we calculate multiple BOLLs
                        for (Integer period : config.getPeriods()) {
                            indicatorResults.add(calculateBOLL(sortedOhlc, period, 2));
                        }
                        break;
                    case "MACD":
                        // MACD expects [fast, slow, signal]. We treat the list as one config if size is 3.
                        if (config.getPeriods() != null && config.getPeriods().size() == 3) {
                            indicatorResults.add(calculateMACD(sortedOhlc, config.getPeriods().get(0), config.getPeriods().get(1), config.getPeriods().get(2)));
                        } else {
                            // Default MACD 12, 26, 9
                            indicatorResults.add(calculateMACD(sortedOhlc, 12, 26, 9));
                        }
                        break;
                    case "ATR":
                        for (Integer period : config.getPeriods()) {
                            indicatorResults.add(calculateATR(sortedOhlc, period));
                        }
                        break;
                    case "CCI":
                        for (Integer period : config.getPeriods()) {
                            indicatorResults.add(calculateCCI(sortedOhlc, period));
                        }
                        break;
                    case "OBV":
                        for (Integer period : config.getPeriods()) {
                            indicatorResults.add(calculateOBV(sortedOhlc, period));
                        }
                        break;
                    case "KDJ":
                        for (Integer period : config.getPeriods()) {
                            indicatorResults.add(calculateKDJ(sortedOhlc, period));
                        }
                        break;
                    case "ADX":
                        // ADX expects [period]. Default is 14
                        int adxPeriod = config.getPeriods() != null && !config.getPeriods().isEmpty()
                                ? config.getPeriods().get(0) : 14;
                        indicatorResults.add(calculateADX(sortedOhlc, adxPeriod));
                        break;
                    default:
                        log.warn("Unsupported indicator: {}", name);
                }

                if (!indicatorResults.isEmpty()) {
                    results.computeIfAbsent(name, k -> new ArrayList<>()).addAll(indicatorResults);
                }
            }
        }

        return IndicatorCalculationResponse.builder().results(results).build();
    }

    private IndicatorCalculationResponse.IndicatorResult calculateSMA(List<IndicatorCalculationRequest.OhlcItem> data, int period) {
        // 确保OHLC数据按时间戳升序排序，无论传入什么顺序都能正确计算
        List<IndicatorCalculationRequest.OhlcItem> sortedData = data.stream()
                .sorted(Comparator.comparing(IndicatorCalculationRequest.OhlcItem::getTimestamp))
                .toList();

        List<IndicatorCalculationResponse.SingleValue> values = new ArrayList<>(Collections.nCopies(sortedData.size(), null));

        if (sortedData.size() >= period) {
            for (int i = period - 1; i < sortedData.size(); i++) {
                BigDecimal sum = BigDecimal.ZERO;
                for (int j = 0; j < period; j++) {
                    sum = sum.add(sortedData.get(i - j).getClose());
                }
                values.set(i, IndicatorCalculationResponse.SingleValue.builder()
                        .timestamp(sortedData.get(i).getTimestamp())
                        .value(sum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE))
                        .build());
            }
        }

        return IndicatorCalculationResponse.IndicatorResult.builder()
                .period(String.valueOf(period))
                .values(values)
                .build();
    }

    private IndicatorCalculationResponse.IndicatorResult calculateEMA(List<IndicatorCalculationRequest.OhlcItem> data, int period) {
        // 确保OHLC数据按时间戳升序排序，无论传入什么顺序都能正确计算
        List<IndicatorCalculationRequest.OhlcItem> sortedData = data.stream()
                .sorted(Comparator.comparing(IndicatorCalculationRequest.OhlcItem::getTimestamp))
                .toList();

        List<IndicatorCalculationResponse.SingleValue> values = new ArrayList<>(Collections.nCopies(sortedData.size(), null));

        if (sortedData.size() >= period) {
            BigDecimal k = BigDecimal.valueOf(2.0 / (period + 1));

            // Initialize with SMA
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = 0; i < period; i++) {
                sum = sum.add(sortedData.get(i).getClose());
            }
            BigDecimal sma = sum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);
            values.set(period - 1, IndicatorCalculationResponse.SingleValue.builder()
                    .timestamp(sortedData.get(period - 1).getTimestamp())
                    .value(sma)
                    .build());

            BigDecimal prevEma = sma;
            for (int i = period; i < sortedData.size(); i++) {
                BigDecimal close = sortedData.get(i).getClose();
                // EMA = Close * k + PrevEMA * (1-k)
                BigDecimal ema = close.multiply(k).add(prevEma.multiply(BigDecimal.ONE.subtract(k)));
                ema = ema.setScale(SCALE, ROUNDING_MODE);
                values.set(i, IndicatorCalculationResponse.SingleValue.builder()
                        .timestamp(sortedData.get(i).getTimestamp())
                        .value(ema)
                        .build());
                prevEma = ema;
            }
        }

        return IndicatorCalculationResponse.IndicatorResult.builder()
                .period(String.valueOf(period))
                .values(values)
                .build();
    }

    private IndicatorCalculationResponse.IndicatorResult calculateWMA(List<IndicatorCalculationRequest.OhlcItem> data, int period) {
        // 确保OHLC数据按时间戳升序排序，无论传入什么顺序都能正确计算
        List<IndicatorCalculationRequest.OhlcItem> sortedData = data.stream()
                .sorted(Comparator.comparing(IndicatorCalculationRequest.OhlcItem::getTimestamp))
                .toList();

        List<IndicatorCalculationResponse.SingleValue> values = new ArrayList<>(Collections.nCopies(sortedData.size(), null));

        if (sortedData.size() >= period) {
            BigDecimal denominator = BigDecimal.valueOf((long) period * (period + 1) / 2);

            for (int i = period - 1; i < sortedData.size(); i++) {
                BigDecimal numerator = BigDecimal.ZERO;
                for (int j = 0; j < period; j++) {
                    int weight = period - j;
                    BigDecimal price = sortedData.get(i - j).getClose();
                    numerator = numerator.add(price.multiply(BigDecimal.valueOf(weight)));
                }
                values.set(i, IndicatorCalculationResponse.SingleValue.builder()
                        .timestamp(sortedData.get(i).getTimestamp())
                        .value(numerator.divide(denominator, SCALE, ROUNDING_MODE))
                        .build());
            }
        }

        return IndicatorCalculationResponse.IndicatorResult.builder()
                .period(String.valueOf(period))
                .values(values)
                .build();
    }

    private IndicatorCalculationResponse.IndicatorResult calculateRSI(List<IndicatorCalculationRequest.OhlcItem> data, int period) {
        // 确保OHLC数据按时间戳升序排序，无论传入什么顺序都能正确计算
        List<IndicatorCalculationRequest.OhlcItem> sortedData = data.stream()
                .sorted(Comparator.comparing(IndicatorCalculationRequest.OhlcItem::getTimestamp))
                .toList();

        List<IndicatorCalculationResponse.SingleValue> values = new ArrayList<>(Collections.nCopies(sortedData.size(), null));

        if (sortedData.size() >= period + 1) {
            double avgGain = 0.0;
            double avgLoss = 0.0;

            // First average gain/loss (SMA)
            for (int i = 1; i <= period; i++) {
                double change = sortedData.get(i).getClose().subtract(sortedData.get(i - 1).getClose()).doubleValue();
                if (change > 0) avgGain += change;
                else avgLoss += Math.abs(change);
            }
            avgGain /= period;
            avgLoss /= period;

            // First RSI
            double rs = (avgLoss == 0) ? 100.0 : avgGain / avgLoss;
            double rsi = 100.0 - (100.0 / (1.0 + rs));
            values.set(period, IndicatorCalculationResponse.SingleValue.builder()
                    .timestamp(sortedData.get(period).getTimestamp())
                    .value(BigDecimal.valueOf(rsi).setScale(SCALE, ROUNDING_MODE))
                    .build());

            // Wilder's Smoothing
            for (int i = period + 1; i < sortedData.size(); i++) {
                double change = sortedData.get(i).getClose().subtract(sortedData.get(i - 1).getClose()).doubleValue();
                double gain = (change > 0) ? change : 0.0;
                double loss = (change < 0) ? Math.abs(change) : 0.0;

                avgGain = (avgGain * (period - 1) + gain) / period;
                avgLoss = (avgLoss * (period - 1) + loss) / period;

                rs = (avgLoss == 0) ? 100.0 : avgGain / avgLoss;
                rsi = 100.0 - (100.0 / (1.0 + rs));
                values.set(i, IndicatorCalculationResponse.SingleValue.builder()
                        .timestamp(sortedData.get(i).getTimestamp())
                        .value(BigDecimal.valueOf(rsi).setScale(SCALE, ROUNDING_MODE))
                        .build());
            }
        }

        return IndicatorCalculationResponse.IndicatorResult.builder()
                .period(String.valueOf(period))
                .values(values)
                .build();
    }

    private IndicatorCalculationResponse.IndicatorResult calculateBOLL(List<IndicatorCalculationRequest.OhlcItem> data, int period, int stdDevMultiplier) {
        // 确保OHLC数据按时间戳升序排序，无论传入什么顺序都能正确计算
        List<IndicatorCalculationRequest.OhlcItem> sortedData = data.stream()
                .sorted(Comparator.comparing(IndicatorCalculationRequest.OhlcItem::getTimestamp))
                .toList();

        List<IndicatorCalculationResponse.BollValue> values = new ArrayList<>(Collections.nCopies(sortedData.size(), null));

        if (sortedData.size() >= period) {
            for (int i = period - 1; i < sortedData.size(); i++) {
                // Calculate SMA (Middle Band)
                BigDecimal sum = BigDecimal.ZERO;
                for (int j = 0; j < period; j++) {
                    sum = sum.add(sortedData.get(i - j).getClose());
                }
                BigDecimal mb = sum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);

                // Calculate StdDev
                double sumSqDiff = 0.0;
                for (int j = 0; j < period; j++) {
                    double diff = sortedData.get(i - j).getClose().subtract(mb).doubleValue();
                    sumSqDiff += diff * diff;
                }
                double stdDev = Math.sqrt(sumSqDiff / period);
                BigDecimal bandwidth = BigDecimal.valueOf(stdDev * stdDevMultiplier);

                BigDecimal ub = mb.add(bandwidth).setScale(SCALE, ROUNDING_MODE);
                BigDecimal lb = mb.subtract(bandwidth).setScale(SCALE, ROUNDING_MODE);

                values.set(i, IndicatorCalculationResponse.BollValue.builder()
                        .timestamp(sortedData.get(i).getTimestamp())
                        .upper(ub)
                        .middle(mb)
                        .lower(lb)
                        .build());
            }
        }

        return IndicatorCalculationResponse.IndicatorResult.builder()
                .period(period + "," + stdDevMultiplier)
                .bollValues(values)
                .build();
    }

    private IndicatorCalculationResponse.IndicatorResult calculateMACD(List<IndicatorCalculationRequest.OhlcItem> data, int fastPeriod, int slowPeriod, int signalPeriod) {
        // 确保OHLC数据按时间戳升序排序，无论传入什么顺序都能正确计算
        List<IndicatorCalculationRequest.OhlcItem> sortedData = data.stream()
                .sorted(Comparator.comparing(IndicatorCalculationRequest.OhlcItem::getTimestamp))
                .toList();

        List<IndicatorCalculationResponse.MacdValue> values = new ArrayList<>(Collections.nCopies(sortedData.size(), null));

        if (sortedData.size() >= slowPeriod) {
            // Calculate Fast EMA
            List<BigDecimal> fastEma = calculateEmaList(sortedData, fastPeriod);
            // Calculate Slow EMA
            List<BigDecimal> slowEma = calculateEmaList(sortedData, slowPeriod);

            // Calculate DIF (Fast - Slow)
            List<BigDecimal> difList = new ArrayList<>(Collections.nCopies(sortedData.size(), null));
            for (int i = 0; i < sortedData.size(); i++) {
                if (fastEma.get(i) != null && slowEma.get(i) != null) {
                    difList.set(i, fastEma.get(i).subtract(slowEma.get(i)));
                }
            }

            // Calculate DEA (EMA of DIF)
            // Need to handle nulls in DIF list
            // Find first non-null index
            int startIdx = slowPeriod - 1;
            if (startIdx < sortedData.size()) {
                List<BigDecimal> deaList = calculateEmaOfValues(difList, signalPeriod, startIdx);

                for (int i = 0; i < sortedData.size(); i++) {
                    BigDecimal dif = difList.get(i);
                    BigDecimal dea = deaList.get(i);
                    if (dif != null && dea != null) {
                        BigDecimal macd = dif.subtract(dea).multiply(BigDecimal.valueOf(2)).setScale(SCALE, ROUNDING_MODE);
                        values.set(i, IndicatorCalculationResponse.MacdValue.builder()
                                .timestamp(sortedData.get(i).getTimestamp())
                                .diff(dif.setScale(SCALE, ROUNDING_MODE))
                                .dea(dea.setScale(SCALE, ROUNDING_MODE))
                                .macd(macd)
                                .build());
                    }
                }
            }
        }

        return IndicatorCalculationResponse.IndicatorResult.builder()
                .period(fastPeriod + "," + slowPeriod + "," + signalPeriod)
                .macdValues(values)
                .build();
    }

    // Helper for MACD internal EMA
    private List<BigDecimal> calculateEmaList(List<IndicatorCalculationRequest.OhlcItem> data, int period) {
        // 确保OHLC数据按时间戳升序排序，无论传入什么顺序都能正确计算
        List<IndicatorCalculationRequest.OhlcItem> sortedData = data.stream()
                .sorted(Comparator.comparing(IndicatorCalculationRequest.OhlcItem::getTimestamp))
                .toList();

        List<BigDecimal> values = new ArrayList<>(Collections.nCopies(sortedData.size(), null));
        if (sortedData.size() < period) return values;

        BigDecimal k = BigDecimal.valueOf(2.0 / (period + 1));

        // SMA for initial
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(sortedData.get(i).getClose());
        }
        BigDecimal prevEma = sum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);
        values.set(period - 1, prevEma);

        for (int i = period; i < sortedData.size(); i++) {
            BigDecimal close = sortedData.get(i).getClose();
            BigDecimal ema = close.multiply(k).add(prevEma.multiply(BigDecimal.ONE.subtract(k)));
            values.set(i, ema);
            prevEma = ema;
        }
        return values;
    }

    // Helper for DEA (EMA of a value list)
    private List<BigDecimal> calculateEmaOfValues(List<BigDecimal> inputValues, int period, int startIndex) {
        List<BigDecimal> values = new ArrayList<>(Collections.nCopies(inputValues.size(), null));

        // Check if we have enough data from startIndex
        if (inputValues.size() - startIndex < period) return values;

        BigDecimal k = BigDecimal.valueOf(2.0 / (period + 1));

        // Initial SMA
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(inputValues.get(startIndex + i));
        }
        BigDecimal prevEma = sum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);
        values.set(startIndex + period - 1, prevEma);

        for (int i = startIndex + period; i < inputValues.size(); i++) {
            BigDecimal val = inputValues.get(i);
            BigDecimal ema = val.multiply(k).add(prevEma.multiply(BigDecimal.ONE.subtract(k)));
            values.set(i, ema);
            prevEma = ema;
        }
        return values;
    }

    private IndicatorCalculationResponse.IndicatorResult calculateATR(List<IndicatorCalculationRequest.OhlcItem> data, int period) {
        List<IndicatorCalculationRequest.OhlcItem> sortedData = data.stream()
                .sorted(Comparator.comparing(IndicatorCalculationRequest.OhlcItem::getTimestamp))
                .toList();

        List<IndicatorCalculationResponse.SingleValue> values = new ArrayList<>(Collections.nCopies(sortedData.size(), null));
        if (period <= 0 || sortedData.size() < period) {
            return IndicatorCalculationResponse.IndicatorResult.builder()
                    .period(String.valueOf(period))
                    .values(values)
                    .build();
        }

        List<BigDecimal> tr = new ArrayList<>(Collections.nCopies(sortedData.size(), BigDecimal.ZERO));
        for (int i = 0; i < sortedData.size(); i++) {
            BigDecimal high = sortedData.get(i).getHigh();
            BigDecimal low = sortedData.get(i).getLow();
            BigDecimal prevClose = i > 0 ? sortedData.get(i - 1).getClose() : sortedData.get(i).getClose();

            BigDecimal tr1 = high.subtract(low);
            BigDecimal tr2 = high.subtract(prevClose).abs();
            BigDecimal tr3 = low.subtract(prevClose).abs();
            tr.set(i, tr1.max(tr2).max(tr3));
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < sortedData.size(); i++) {
            sum = sum.add(tr.get(i));
            if (i < period - 1) continue;

            if (i == period - 1) {
                BigDecimal first = sum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);
                values.set(i, IndicatorCalculationResponse.SingleValue.builder()
                        .timestamp(sortedData.get(i).getTimestamp())
                        .value(first)
                        .build());
            } else {
                IndicatorCalculationResponse.SingleValue prevSv = values.get(i - 1);
                if (prevSv != null) {
                    BigDecimal prevAtr = prevSv.getValue();
                    BigDecimal atr = prevAtr.multiply(BigDecimal.valueOf(period - 1))
                            .add(tr.get(i))
                            .divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);
                    values.set(i, IndicatorCalculationResponse.SingleValue.builder()
                            .timestamp(sortedData.get(i).getTimestamp())
                            .value(atr)
                            .build());
                }
            }
        }

        return IndicatorCalculationResponse.IndicatorResult.builder()
                .period(String.valueOf(period))
                .values(values)
                .build();
    }

    private IndicatorCalculationResponse.IndicatorResult calculateCCI(List<IndicatorCalculationRequest.OhlcItem> data, int period) {
        List<IndicatorCalculationRequest.OhlcItem> sortedData = data.stream()
                .sorted(Comparator.comparing(IndicatorCalculationRequest.OhlcItem::getTimestamp))
                .toList();

        List<IndicatorCalculationResponse.SingleValue> values = new ArrayList<>(Collections.nCopies(sortedData.size(), null));
        if (period <= 0 || sortedData.size() < period) {
            return IndicatorCalculationResponse.IndicatorResult.builder()
                    .period(String.valueOf(period))
                    .values(values)
                    .build();
        }

        List<BigDecimal> tp = new ArrayList<>(sortedData.size());
        for (IndicatorCalculationRequest.OhlcItem item : sortedData) {
            BigDecimal t = item.getHigh().add(item.getLow()).add(item.getClose())
                    .divide(BigDecimal.valueOf(3), SCALE, ROUNDING_MODE);
            tp.add(t);
        }

        for (int i = period - 1; i < sortedData.size(); i++) {
            BigDecimal sumTp = BigDecimal.ZERO;
            for (int j = i - period + 1; j <= i; j++) {
                sumTp = sumTp.add(tp.get(j));
            }
            BigDecimal smaTp = sumTp.divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);

            BigDecimal mdSum = BigDecimal.ZERO;
            for (int j = i - period + 1; j <= i; j++) {
                mdSum = mdSum.add(tp.get(j).subtract(smaTp).abs());
            }
            BigDecimal md = mdSum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);

            BigDecimal cci;
            if (md.compareTo(BigDecimal.ZERO) == 0) {
                cci = BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
            } else {
                cci = tp.get(i).subtract(smaTp)
                        .divide(md.multiply(BigDecimal.valueOf(0.015)), SCALE, ROUNDING_MODE);
            }

            values.set(i, IndicatorCalculationResponse.SingleValue.builder()
                    .timestamp(sortedData.get(i).getTimestamp())
                    .value(cci)
                    .build());
        }

        return IndicatorCalculationResponse.IndicatorResult.builder()
                .period(String.valueOf(period))
                .values(values)
                .build();
    }

    private IndicatorCalculationResponse.IndicatorResult calculateOBV(List<IndicatorCalculationRequest.OhlcItem> data, int period) {
        List<IndicatorCalculationRequest.OhlcItem> sortedData = data.stream()
                .sorted(Comparator.comparing(IndicatorCalculationRequest.OhlcItem::getTimestamp))
                .toList();

        List<IndicatorCalculationResponse.SingleValue> values = new ArrayList<>(Collections.nCopies(sortedData.size(), null));
        if (period <= 0 || sortedData.isEmpty()) {
            return IndicatorCalculationResponse.IndicatorResult.builder()
                    .period(String.valueOf(period))
                    .values(values)
                    .build();
        }

        List<BigDecimal> raw = new ArrayList<>(Collections.nCopies(sortedData.size(), BigDecimal.ZERO));
        BigDecimal obv = BigDecimal.ZERO;
        for (int i = 1; i < sortedData.size(); i++) {
            BigDecimal prevClose = sortedData.get(i - 1).getClose();
            BigDecimal close = sortedData.get(i).getClose();
            BigDecimal vol = sortedData.get(i).getVolume() != null ? sortedData.get(i).getVolume() : BigDecimal.ZERO;
            int cmp = close.compareTo(prevClose);
            if (cmp > 0) obv = obv.add(vol);
            else if (cmp < 0) obv = obv.subtract(vol);
            raw.set(i, obv);
        }

        if (sortedData.size() < period) {
            return IndicatorCalculationResponse.IndicatorResult.builder()
                    .period(String.valueOf(period))
                    .values(values)
                    .build();
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < raw.size(); i++) {
            sum = sum.add(raw.get(i));
            if (i >= period) {
                sum = sum.subtract(raw.get(i - period));
            }
            if (i >= period - 1) {
                BigDecimal sma = sum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);
                values.set(i, IndicatorCalculationResponse.SingleValue.builder()
                        .timestamp(sortedData.get(i).getTimestamp())
                        .value(sma)
                        .build());
            }
        }

        return IndicatorCalculationResponse.IndicatorResult.builder()
                .period(String.valueOf(period))
                .values(values)
                .build();
    }

    private IndicatorCalculationResponse.IndicatorResult calculateKDJ(List<IndicatorCalculationRequest.OhlcItem> data, int period) {
        List<IndicatorCalculationRequest.OhlcItem> sortedData = data.stream()
                .sorted(Comparator.comparing(IndicatorCalculationRequest.OhlcItem::getTimestamp))
                .toList();

        List<IndicatorCalculationResponse.SingleValue> values = new ArrayList<>(Collections.nCopies(sortedData.size(), null));
        List<IndicatorCalculationResponse.KdjValue> kdjValues = new ArrayList<>(Collections.nCopies(sortedData.size(), null));
        if (period <= 0 || sortedData.size() < period) {
            return IndicatorCalculationResponse.IndicatorResult.builder()
                    .period(String.valueOf(period))
                    .values(values)
                    .kdjValues(kdjValues)
                    .build();
        }

        BigDecimal k = BigDecimal.valueOf(50).setScale(SCALE, ROUNDING_MODE);
        BigDecimal d = BigDecimal.valueOf(50).setScale(SCALE, ROUNDING_MODE);
        BigDecimal twoThirds = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(3), SCALE, ROUNDING_MODE);
        BigDecimal oneThird = BigDecimal.ONE.divide(BigDecimal.valueOf(3), SCALE, ROUNDING_MODE);

        for (int i = 0; i < sortedData.size(); i++) {
            if (i < period - 1) continue;

            int start = i - period + 1;
            BigDecimal highest = sortedData.get(start).getHigh();
            BigDecimal lowest = sortedData.get(start).getLow();
            for (int j = start + 1; j <= i; j++) {
                highest = highest.max(sortedData.get(j).getHigh());
                lowest = lowest.min(sortedData.get(j).getLow());
            }

            BigDecimal denom = highest.subtract(lowest);
            BigDecimal rsv;
            if (denom.compareTo(BigDecimal.ZERO) == 0) {
                rsv = BigDecimal.valueOf(50).setScale(SCALE, ROUNDING_MODE);
            } else {
                rsv = sortedData.get(i).getClose().subtract(lowest)
                        .divide(denom, SCALE, ROUNDING_MODE)
                        .multiply(BigDecimal.valueOf(100));
            }

            k = twoThirds.multiply(k).add(oneThird.multiply(rsv)).setScale(SCALE, ROUNDING_MODE);
            d = twoThirds.multiply(d).add(oneThird.multiply(k)).setScale(SCALE, ROUNDING_MODE);
            BigDecimal j = k.multiply(BigDecimal.valueOf(3)).subtract(d.multiply(BigDecimal.valueOf(2))).setScale(SCALE, ROUNDING_MODE);

            kdjValues.set(i, IndicatorCalculationResponse.KdjValue.builder()
                    .timestamp(sortedData.get(i).getTimestamp())
                    .k(k)
                    .d(d)
                    .j(j)
                    .build());

            values.set(i, IndicatorCalculationResponse.SingleValue.builder()
                    .timestamp(sortedData.get(i).getTimestamp())
                    .value(k)
                    .build());
        }

        return IndicatorCalculationResponse.IndicatorResult.builder()
                .period(String.valueOf(period))
                .values(values)
                .kdjValues(kdjValues)
                .build();
    }

    /**
     * 计算ADX (Average Directional Index) 平均方向指数
     * ADX用于衡量趋势的强度，范围从0到100
     *
     * @param data   OHLC数据
     * @param period ADX周期，通常为14
     * @return ADX计算结果
     */
    private IndicatorCalculationResponse.IndicatorResult calculateADX(List<IndicatorCalculationRequest.OhlcItem> data, int period) {
        List<IndicatorCalculationRequest.OhlcItem> sortedData = data.stream()
                .sorted(Comparator.comparing(IndicatorCalculationRequest.OhlcItem::getTimestamp))
                .toList();

        List<IndicatorCalculationResponse.SingleValue> values = new ArrayList<>(Collections.nCopies(sortedData.size(), null));

        if (sortedData.size() < period * 2) {
            return IndicatorCalculationResponse.IndicatorResult.builder()
                    .period(String.valueOf(period))
                    .values(values)
                    .build();
        }

        // 计算TR (True Range)
        // 注意：TR从索引1开始计算（需要前一个数据），所以需要在前面填充0以保持索引对齐
        List<BigDecimal> trList = new ArrayList<>(Collections.nCopies(sortedData.size(), BigDecimal.ZERO));
        for (int i = 1; i < sortedData.size(); i++) {
            BigDecimal high = sortedData.get(i).getHigh();
            BigDecimal low = sortedData.get(i).getLow();
            BigDecimal prevClose = sortedData.get(i - 1).getClose();

            BigDecimal tr1 = high.subtract(low);
            BigDecimal tr2 = high.subtract(prevClose).abs();
            BigDecimal tr3 = low.subtract(prevClose).abs();

            BigDecimal tr = tr1.max(tr2).max(tr3);
            trList.set(i, tr);
        }

        // 计算+DM和-DM (Directional Movement)
        // 同样在前面填充0以保持索引对齐
        List<BigDecimal> plusDM = new ArrayList<>(Collections.nCopies(sortedData.size(), BigDecimal.ZERO));
        List<BigDecimal> minusDM = new ArrayList<>(Collections.nCopies(sortedData.size(), BigDecimal.ZERO));
        for (int i = 1; i < sortedData.size(); i++) {
            BigDecimal high = sortedData.get(i).getHigh();
            BigDecimal low = sortedData.get(i).getLow();
            BigDecimal prevHigh = sortedData.get(i - 1).getHigh();
            BigDecimal prevLow = sortedData.get(i - 1).getLow();

            BigDecimal upMove = high.subtract(prevHigh);
            BigDecimal downMove = prevLow.subtract(low);

            BigDecimal plusDm = (upMove.compareTo(downMove) > 0 && upMove.compareTo(BigDecimal.ZERO) > 0) ? upMove : BigDecimal.ZERO;
            BigDecimal minusDm = (downMove.compareTo(upMove) > 0 && downMove.compareTo(BigDecimal.ZERO) > 0) ? downMove : BigDecimal.ZERO;

            plusDM.set(i, plusDm);
            minusDM.set(i, minusDm);
        }

        // 计算平滑的TR、+DM、-DM (使用Wilders Smoothing = EMA)
        List<BigDecimal> smoothedTR = calculateEmaListFromIndex(trList, period, 0, sortedData.size());
        List<BigDecimal> smoothedPlusDM = calculateEmaListFromIndex(plusDM, period, 0, sortedData.size());
        List<BigDecimal> smoothedMinusDM = calculateEmaListFromIndex(minusDM, period, 0, sortedData.size());

        // 计算+DI和-DI
        List<BigDecimal> plusDI = new ArrayList<>();
        List<BigDecimal> minusDI = new ArrayList<>();
        for (int i = 0; i < smoothedTR.size(); i++) {
            if (smoothedTR.get(i) != null && smoothedTR.get(i).compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal pdi = smoothedPlusDM.get(i).divide(smoothedTR.get(i), SCALE, ROUNDING_MODE)
                        .multiply(BigDecimal.valueOf(100));
                BigDecimal mdi = smoothedMinusDM.get(i).divide(smoothedTR.get(i), SCALE, ROUNDING_MODE)
                        .multiply(BigDecimal.valueOf(100));
                plusDI.add(pdi);
                minusDI.add(mdi);
            } else {
                plusDI.add(null);
                minusDI.add(null);
            }
        }

        // 计算DX (Directional Index)
        List<BigDecimal> dx = new ArrayList<>();
        for (int i = 0; i < plusDI.size(); i++) {
            if (plusDI.get(i) != null && minusDI.get(i) != null) {
                BigDecimal diSum = plusDI.get(i).add(minusDI.get(i));
                if (diSum.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal diDiff = plusDI.get(i).subtract(minusDI.get(i)).abs();
                    BigDecimal dxi = diDiff.divide(diSum, SCALE, ROUNDING_MODE).multiply(BigDecimal.valueOf(100));
                    dx.add(dxi);
                } else {
                    dx.add(null);
                }
            } else {
                dx.add(null);
            }
        }

        // 计算ADX (DX的EMA)
        // DX列表的大小是 sortedData.size() - 1（因为TR从索引1开始）
        // DX有效值从索引period开始
        // ADX需要从DX的索引period开始计算（因为DX本身也需要period个数据点）
        int dxStartIndex = period; // DX从索引period开始有值
        int adxStartIndexInDx = period - 1; // ADX在DX列表中的起始索引

        // 检查dx列表中是否有足够的非null值用于计算ADX
        long nonNullDxCount = dx.stream().filter(Objects::nonNull).count();
        log.debug("ADX计算前检查: dx.size()={}, 非null值数量={}, 期望最小值={}",
                dx.size(), nonNullDxCount, period * 2);

        List<BigDecimal> adx = calculateEmaListFromIndex(dx, period, adxStartIndexInDx, sortedData.size());

        // 对齐结果到原始数据
        // adx列表的大小与sortedData相同，索引一一对应
        // 只需要从startIndex开始填充values即可
        int startIndex = period * 2 - 1; // ADX在原始数据中的起始索引

        // 统计非null的ADX值数量
        long nonNullAdxCount = adx.stream().filter(Objects::nonNull).count();
        log.debug("ADX计算完成: sortedData.size()={}, adx.size()={}, 非nullADX值数量={}, startIndex={}",
                sortedData.size(), adx.size(), nonNullAdxCount, startIndex);

        // 直接使用adx[i]，因为adx和sortedData的索引已经对齐
        for (int i = startIndex; i < sortedData.size(); i++) {
            BigDecimal adxValue = adx.get(i);
            if (adxValue != null) {
                values.set(i, IndicatorCalculationResponse.SingleValue.builder()
                        .timestamp(sortedData.get(i).getTimestamp())
                        .value(adxValue)
                        .build());
            }
        }

        // 统计结果中非null值的数量
        long nonNullValuesCount = values.stream().filter(Objects::nonNull).count();
        log.debug("ADX对齐完成: values.size()={}, 非null值数量={}", values.size(), nonNullValuesCount);

        return IndicatorCalculationResponse.IndicatorResult.builder()
                .period(String.valueOf(period))
                .values(values)
                .build();
    }

    /**
     * 从指定索引开始计算EMA
     *
     * @param data       输入数据列表
     * @param period     EMA周期
     * @param startIndex 开始计算的位置
     * @param targetSize 返回列表的目标大小
     * @return EMA计算结果列表
     */
    private List<BigDecimal> calculateEmaListFromIndex(List<BigDecimal> data, int period, int startIndex, int targetSize) {
        List<BigDecimal> result = new ArrayList<>(Collections.nCopies(targetSize, null));

        if (data.size() - startIndex < period) {
            return result;
        }

        BigDecimal k = BigDecimal.valueOf(2.0 / (period + 1));

        // 初始SMA
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(data.get(startIndex + i));
        }
        BigDecimal prevEma = sum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);

        // 注意：result的索引需要与原始数据对齐
        // data的索引对应result的索引
        result.set(startIndex + period - 1, prevEma);

        // EMA
        for (int i = startIndex + period; i < data.size(); i++) {
            BigDecimal val = data.get(i);
            if (val != null) {
                BigDecimal ema = val.multiply(k).add(prevEma.multiply(BigDecimal.ONE.subtract(k)));
                result.set(i, ema);
                prevEma = ema;
            }
        }

        return result;
    }
}

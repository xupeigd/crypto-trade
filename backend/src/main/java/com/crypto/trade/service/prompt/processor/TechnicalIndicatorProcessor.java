package com.crypto.trade.service.prompt.processor;

import com.crypto.trade.dto.cex.model.CexMarketCandle;
import com.crypto.trade.dto.cex.model.CexPosition;
import com.crypto.trade.dto.indicator.IndicatorCalculationRequest;
import com.crypto.trade.dto.indicator.IndicatorCalculationResponse;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.model.TechnicalIndicators;
import com.crypto.trade.service.CommonTechnicalIndicatorService;
import com.crypto.trade.service.TechnicalIndicatorService;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import com.crypto.trade.service.prompt.AbstractPromptProcessor;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.service.prompt.PromptProcessException;
import com.crypto.trade.service.unified.UnifiedPositionService;
import com.crypto.trade.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * TechnicalIndicatorProcessor
 * 处理器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TechnicalIndicatorProcessor
        extends AbstractPromptProcessor {

    // 技术指标周期上限常量
    private static final int MAX_INDICATOR_PERIOD = 60;
    // 历史数据展示数量常量（表格中显示的最近数据点数量）
    private static final int HISTORY_DATA_COUNT = 30;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final UnifiedPositionService unifiedPositionService;
    private final CommonTechnicalIndicatorService commonTechnicalIndicatorService;
    private final UnifiedCexApiService unifiedCexApiService;
    private final ApiKeyService apiKeyService;

    @Override
    public String getName() {
        return "TechnicalIndicatorProcessor";
    }

    @Override
    public int getPriority() {
        return 30; // 中等优先级
    }

    @Override
    public boolean shouldExecute(PromptContext context) {
        // 总是尝试执行，让处理器自己决定是否需要获取数据
        return getBooleanParameter("enableTechnicalIndicators", true);
    }

    @Override
    public SegmentModel process(PromptContext context) throws PromptProcessException {
        return safeProcess(context, () -> {
            // 检查是否启用技术指标
            boolean enableTechnicalIndicators = getBooleanParameter("enableTechnicalIndicators", true);
            if (!enableTechnicalIndicators) {
                return SegmentModel.builder()
                        .content("")
                        .showTitle(false)
                        .build();
            }

            // 获取多时间周期参数
            String timeframesStr = getStringParameter("timeframes", "4H,1H,5m");
            String[] timeframes = timeframesStr.split(",");
            int dataCount = getIntParameter("dataCount", 30);
            String outputFormat = getStringParameter("outputFormat", "USER_TABLE"); // SUMMARY, DETAILED, TABLE, MULTI_TIMEFRAME, USER_TABLE

            // 获取持仓数据
            List<CexPosition> positions = getPositionData(context);
            if (positions == null || positions.isEmpty()) {
                return SegmentModel.builder()
                        .title("技术指标分析")
                        .content("\n无持仓数据，无法计算技术指标")
                        .category(SegmentModel.Category.ANALYSIS)
                        .priority(SegmentModel.Priority.MEDIUM)
                        .showTitle(true)
                        .addMetadata("processor", getName())
                        .addMetadata("processorPriority", getPriority())
                        .addMetadata("timeframes", timeframesStr)
                        .addMetadata("dataCount", dataCount)
                        .addMetadata("outputFormat", outputFormat)
                        .addMetadata("enableTechnicalIndicators", enableTechnicalIndicators)
                        .addMetadata("dataStatus", "UNAVAILABLE")
                        .build();
            }

            // 获取多时间周期技术指标数据，传入context支持去重
            Map<String, Map<String, TechnicalIndicators>> multiTimeframeIndicators =
                    getMultiTimeframeIndicators(positions, timeframes, dataCount, context.getApiKeyId(), context);

            if (multiTimeframeIndicators == null || multiTimeframeIndicators.isEmpty()) {
                return SegmentModel.builder()
                        .title("技术指标分析")
                        .content("无法获取技术指标数据")
                        .category(SegmentModel.Category.ANALYSIS)
                        .priority(SegmentModel.Priority.MEDIUM)
                        .showTitle(true)
                        .addMetadata("processor", getName())
                        .addMetadata("processorPriority", getPriority())
                        .addMetadata("timeframes", timeframesStr)
                        .addMetadata("dataCount", dataCount)
                        .addMetadata("outputFormat", outputFormat)
                        .addMetadata("enableTechnicalIndicators", true)
                        .addMetadata("dataStatus", "UNAVAILABLE")
                        .build();
            }

            // 根据输出格式生成内容
            String content = switch (outputFormat.toUpperCase()) {
                case "MULTI_TIMEFRAME" -> formatMultiTimeframeIndicators(multiTimeframeIndicators, timeframes);
                case "DETAILED" -> formatDetailedMultiTimeframeIndicators(multiTimeframeIndicators, timeframes);
                case "TABLE" -> formatMultiTimeframeIndicatorsAsTable(multiTimeframeIndicators, timeframes);
                case "USER_TABLE" -> formatMultiTimeframeIndicatorsAsUserTable(multiTimeframeIndicators, timeframes);
                default -> formatMultiTimeframeIndicatorsAsSummary(multiTimeframeIndicators, timeframes);
            };

            return SegmentModel.builder()
                    .title("技术指标分析")
                    .content(content)
                    .category(SegmentModel.Category.ANALYSIS)
                    .priority(SegmentModel.Priority.MEDIUM)
                    .showTitle(true)
                    .addMetadata("processor", getName())
                    .addMetadata("processorPriority", getPriority())
                    .addMetadata("timeframes", timeframesStr)
                    .addMetadata("dataCount", dataCount)
                    .addMetadata("outputFormat", outputFormat)
                    .addMetadata("enableTechnicalIndicators", enableTechnicalIndicators)
                    .addMetadata("positionCount", positions.size())
                    .addMetadata("dataStatus", "SUCCESS")
                    .build();
        });
    }

    /**
     * 获取持仓数据
     */
    private List<CexPosition> getPositionData(PromptContext context) {
        try {
            log.debug("开始获取持仓数据");

            // 首先尝试从context获取持仓数据
            @SuppressWarnings("unchecked")
            List<CexPosition> positions = (List<CexPosition>) context.getCustomData().get("positions");

            // 如果context中没有持仓数据，主动获取
            if (positions == null || positions.isEmpty()) {
                positions = unifiedPositionService.getLatestPositionData(context.getApiKeyId(), false);
            }

            if (positions != null && !positions.isEmpty()) {
                log.debug("持仓数据获取成功，持仓数量: {}", positions.size());
                // 过滤有效持仓并去重
                return positions.stream()
                        .filter(Objects::nonNull)
                        .filter(p -> p.getSymbol() != null && !p.getSymbol().trim().isEmpty())
                        .filter(p -> p.getQuantity() != null && p.getQuantity().compareTo(BigDecimal.ZERO) != 0)
                        .collect(Collectors.toList());
            } else {
                log.debug("持仓数据为空");
                return null;
            }

        } catch (Exception e) {
            log.error("获取持仓数据失败", e);
            return null;
        }
    }

    /**
     * 获取多时间周期技术指标数据
     */
    private Map<String, Map<String, TechnicalIndicators>> getMultiTimeframeIndicators(List<CexPosition> positions, String[] timeframes,
                                                                                      int dataCount, Long apiKeyId, PromptContext context) {
        try {
            log.debug("开始获取多时间周期技术指标数据 - 时间周期: {}, 数据量: {}",
                    Arrays.toString(timeframes), dataCount);

            Map<String, Map<String, TechnicalIndicators>> result = new LinkedHashMap<>();

            // 获取唯一合约列表
            List<String> uniqueInstIds = positions.stream()
                    .map(CexPosition::getSymbol)
                    .distinct()
                    .collect(Collectors.toList());

            // 为每个时间周期并行计算技术指标

            // 等待所有计算完成
            CompletableFuture.allOf(Arrays.stream(timeframes)
                    .map(timeframe -> CompletableFuture.runAsync(() -> {
                        try {
                            log.debug("开始计算{}时间周期技术指标", timeframe);
                            Map<String, TechnicalIndicators> timeframeIndicators =
                                    calculateTimeframeIndicators(uniqueInstIds, timeframe, dataCount, apiKeyId, context);

                            if (timeframeIndicators != null && !timeframeIndicators.isEmpty()) {
                                synchronized (result) {
                                    result.put(timeframe, timeframeIndicators);
                                }
                            }
                        } catch (Exception e) {
                            log.error("计算{}时间周期技术指标失败", timeframe, e);
                        }
                    })).toArray(CompletableFuture[]::new)).join();

            if (!result.isEmpty()) {
                log.debug("多时间周期技术指标获取成功，时间周期数量: {}", result.size());
                return result;
            } else {
                log.warn("多时间周期技术指标数据为空");
                return null;
            }

        } catch (Exception e) {
            log.error("获取多时间周期技术指标失败", e);
            return null;
        }
    }

    /**
     * 计算单个时间周期的技术指标
     */
    private Map<String, TechnicalIndicators> calculateTimeframeIndicators(List<String> instIds, String timeframe, int dataCount,
                                                                          Long apiKeyId, PromptContext context) {
        try {
            // 获取技术指标周期参数
            List<Integer> rsiPeriods = getIntListParameter("rsiPeriods", Arrays.asList(5, 20, 30));
            List<Integer> emaPeriods = getIntListParameter("emaPeriods", Arrays.asList(5, 20, 30));
            List<Integer> bollPeriods = getIntListParameter("bollPeriods", List.of(20));
            List<Integer> adxPeriods = getIntListParameter("adxPeriods", List.of(14));
            List<Integer> macdPeriods = getIntListParameter("macdPeriods", Arrays.asList(12, 26, 9));

            // 技术指标周期上限验证：将超过60的周期截断为60
            rsiPeriods = rsiPeriods.stream()
                    .map(p -> Math.min(p, MAX_INDICATOR_PERIOD))
                    .distinct()
                    .collect(Collectors.toList());
            emaPeriods = emaPeriods.stream()
                    .map(p -> Math.min(p, MAX_INDICATOR_PERIOD))
                    .distinct()
                    .collect(Collectors.toList());
            bollPeriods = bollPeriods.stream()
                    .map(p -> Math.min(p, MAX_INDICATOR_PERIOD))
                    .distinct()
                    .collect(Collectors.toList());
            adxPeriods = adxPeriods.stream()
                    .map(p -> Math.min(p, MAX_INDICATOR_PERIOD))
                    .distinct()
                    .collect(Collectors.toList());
            macdPeriods = macdPeriods.stream()
                    .map(p -> Math.min(p, MAX_INDICATOR_PERIOD))
                    .distinct()
                    .collect(Collectors.toList());

            log.debug("应用技术指标周期上限验证（最大{}），RSI{} EMA{} BOLL{} ADX{} MACD{}",
                    MAX_INDICATOR_PERIOD, rsiPeriods, emaPeriods, bollPeriods, adxPeriods, macdPeriods);

            // 计算所需的数据获取量，确保技术指标计算准确性
            int requiredDataCount = calculateOptimalDataCount(rsiPeriods, emaPeriods, bollPeriods, adxPeriods, macdPeriods, dataCount);
            log.debug("时间周期{}的技术指标数据需求：RSI{} EMA{} BOLL{} ADX{} MACD{}，计算后获取{}条原始数据",
                    timeframe, rsiPeriods, emaPeriods, bollPeriods, adxPeriods, macdPeriods, requiredDataCount);

            Map<String, TechnicalIndicators> timeframeIndicators = new LinkedHashMap<>();

            // 获取ApiKey对象
            var apiKeykey = apiKeyService.getDecryptedKey(apiKeyId);

            for (String instId : instIds) {
                try {
                    // 去重检查：如果context不为null，检查是否已处理过该instId#timeframe
                    if (context != null && !context.checkAndMarkProcessed(instId, timeframe)) {
                        log.debug("持仓技术指标已返回过，跳过 - instId: {}, timeframe: {}", instId, timeframe);
                        continue;  // 已处理过，跳过
                    }

                    // 使用通用CEX方法获取K线数据
                    var candles = unifiedCexApiService.getMarketCandles(apiKeykey, instId, timeframe, requiredDataCount);
                    if (candles == null || candles.isEmpty()) {
                        log.warn("合约{} {}周期K线数据为空", instId, timeframe);
                        continue;
                    }

                    log.debug("合约{} {}周期获取到{}条K线数据", instId, timeframe, candles.size());

                    // 转换为OhlcItem列表
                    List<IndicatorCalculationRequest.OhlcItem> ohlcItems = candles.stream()
                            .map(c -> IndicatorCalculationRequest.OhlcItem.builder()
                                    .timestamp(c.getTimestamp())
                                    .open(c.getOpen())
                                    .high(c.getHigh())
                                    .low(c.getLow())
                                    .close(c.getClose())
                                    .volume(c.getVolume())
                                    .build())
                            .sorted(Comparator.comparing(IndicatorCalculationRequest.OhlcItem::getTimestamp))
                            .collect(Collectors.toList());

                    log.debug("合约{} {}周期OHLC数据转换完成 - 第1条: timestamp={}, open={}, high={}, low={}, close={}, volume={}",
                            instId, timeframe,
                            ohlcItems.isEmpty() ? null : ohlcItems.get(0).getTimestamp(),
                            ohlcItems.isEmpty() ? null : ohlcItems.get(0).getOpen(),
                            ohlcItems.isEmpty() ? null : ohlcItems.get(0).getHigh(),
                            ohlcItems.isEmpty() ? null : ohlcItems.get(0).getLow(),
                            ohlcItems.isEmpty() ? null : ohlcItems.get(0).getClose(),
                            ohlcItems.isEmpty() ? null : ohlcItems.get(0).getVolume());

                    // 构建指标计算请求
                    List<IndicatorCalculationRequest.IndicatorConfig> indicators = new ArrayList<>();

                    // 添加RSI指标
                    if (!rsiPeriods.isEmpty()) {
                        indicators.add(IndicatorCalculationRequest.IndicatorConfig.builder()
                                .name("RSI")
                                .periods(rsiPeriods)
                                .build());
                    }

                    // 添加EMA指标
                    if (!emaPeriods.isEmpty()) {
                        indicators.add(IndicatorCalculationRequest.IndicatorConfig.builder()
                                .name("EMA")
                                .periods(emaPeriods)
                                .build());
                    }

                    // 添加BOLL指标
                    if (!bollPeriods.isEmpty()) {
                        indicators.add(IndicatorCalculationRequest.IndicatorConfig.builder()
                                .name("BOLL")
                                .periods(bollPeriods)
                                .build());
                    }

                    // 添加ADX指标
                    if (!adxPeriods.isEmpty()) {
                        indicators.add(IndicatorCalculationRequest.IndicatorConfig.builder()
                                .name("ADX")
                                .periods(adxPeriods)
                                .build());
                    }

                    // 添加MACD指标
                    if (!macdPeriods.isEmpty() && macdPeriods.size() >= 3) {
                        indicators.add(IndicatorCalculationRequest.IndicatorConfig.builder()
                                .name("MACD")
                                .periods(macdPeriods)
                                .build());
                    }

                    IndicatorCalculationRequest request = IndicatorCalculationRequest.builder()
                            .ohlcData(ohlcItems)
                            .indicators(indicators)
                            .build();

                    // 计算技术指标
                    IndicatorCalculationResponse response = commonTechnicalIndicatorService.calculate(request);

                    // 转换结果
                    TechnicalIndicators technicalIndicators = convertResponseToTechnicalIndicators(instId, timeframe,
                            candles, response, rsiPeriods, emaPeriods, bollPeriods, adxPeriods, macdPeriods);

                    if (technicalIndicators != null) {
                        timeframeIndicators.put(instId, technicalIndicators);
                    }

                } catch (Exception e) {
                    log.error("计算合约{} {}周期技术指标失败", instId, timeframe, e);
                }
            }

            return timeframeIndicators;

        } catch (Exception e) {
            log.error("计算{}时间周期技术指标失败", timeframe, e);
            return null;
        }
    }

    /**
     * 转换指标计算响应为TechnicalIndicators对象
     */
    private TechnicalIndicators convertResponseToTechnicalIndicators(String instId, String timeframe, List<?> candles,
                                                                     IndicatorCalculationResponse response, List<Integer> rsiPeriods,
                                                                     List<Integer> emaPeriods, List<Integer> bollPeriods,
                                                                     List<Integer> adxPeriods, List<Integer> macdPeriods) {
        try {
            TechnicalIndicators indicators = TechnicalIndicators.builder()
                    .instId(instId)
                    .timeframe(timeframe)
                    .timestamp(System.currentTimeMillis())
                    .build();

            // 设置当前价格（从candles获取最新收盘价）
            if (candles != null && !candles.isEmpty()) {
                Object lastCandle = candles.get(candles.size() - 1);
                BigDecimal currentPrice = extractClosePrice(lastCandle);
                indicators.setCurrentPrice(currentPrice);
                // 构建OHLC历史数据
                List<TechnicalIndicators.OhlcData> ohlcHistory = buildOhlcHistory(candles);
                indicators.setOhlcHistory(ohlcHistory);
            }

            if (null != response && !CollectionUtils.isEmpty(response.getResults())) {
                Map<String, List<IndicatorCalculationResponse.IndicatorResult>> indicatorResults = response.getResults();
                // 处理RSI指标
                if (indicatorResults.containsKey("RSI")) {
                    processRsiIndicators(response, indicators, rsiPeriods);
                }
                // 处理EMA指标
                if (indicatorResults.containsKey("EMA")) {
                    processEmaIndicators(response, indicators, emaPeriods);
                }
                // 处理BOLL指标
                if (indicatorResults.containsKey("BOLL")) {
                    processBollIndicators(response, indicators, bollPeriods);
                }
                // 处理ADX指标
                if (indicatorResults.containsKey("ADX")) {
                    processAdxIndicators(response, indicators, adxPeriods);
                }
                // 处理MACD指标
                if (indicatorResults.containsKey("MACD")) {
                    processMacdIndicators(response, indicators, macdPeriods);
                }
            }

            return indicators;

        } catch (Exception e) {
            log.error("转换技术指标响应失败: {}", instId, e);
            return null;
        }
    }

    /**
     * 从candle对象提取收盘价
     */
    private BigDecimal extractClosePrice(Object candle) {
        try {
            if (candle instanceof Map) {
                // 处理Map类型的candle数据
                @SuppressWarnings("unchecked")
                Map<String, Object> candleMap = (Map<String, Object>) candle;
                Object close = candleMap.get("close");
                if (close instanceof BigDecimal) {
                    return (BigDecimal) close;
                } else if (close instanceof Number) {
                    return BigDecimal.valueOf(((Number) close).doubleValue());
                }
            } else if (candle instanceof CexMarketCandle) {
                // 处理CexMarketCandle对象 - 直接返回close价格
                return ((CexMarketCandle) candle).getClose();
            }
            return new BigDecimal("0");
        } catch (Exception e) {
            log.debug("提取收盘价失败", e);
            return new BigDecimal("0");
        }
    }

    /**
     * 构建OHLC历史数据
     */
    private List<TechnicalIndicators.OhlcData> buildOhlcHistory(List<?> candles) {
        List<TechnicalIndicators.OhlcData> ohlcHistory = new ArrayList<>();

        log.debug("开始构建OHLC历史数据 - 原始candles数量: {}", candles.size());

        // 只处理最近40条OHLC数据用于历史记录，保持内存控制
        // 这样既能为表格展示提供足够的30条数据，又能保留额外数据用于分析
        int maxOhlcHistory = Math.min(40, candles.size());
        List<?> recentCandles = candles.subList(0, maxOhlcHistory);

        log.debug("处理最近{}条OHLC数据", maxOhlcHistory);

        for (int i = 0; i < recentCandles.size(); i++) {
            Object candle = recentCandles.get(i);
            try {
                TechnicalIndicators.OhlcData ohlcData = null;

                if (candle instanceof Map) {
                    // 处理Map类型的candle数据
                    @SuppressWarnings("unchecked")
                    Map<String, Object> candleMap = (Map<String, Object>) candle;

                    log.debug("candle[{}] 是Map类型", i);

                    ohlcData = TechnicalIndicators.OhlcData.builder()
                            .timestamp(((Number) candleMap.get("timestamp")).longValue())
                            .time(formatTime(((Number) candleMap.get("timestamp")).longValue()))
                            .open(extractBigDecimal(candleMap.get("open")))
                            .high(extractBigDecimal(candleMap.get("high")))
                            .low(extractBigDecimal(candleMap.get("low")))
                            .close(extractBigDecimal(candleMap.get("close")))
                            .volume(extractBigDecimal(candleMap.get("volume")))
                            .build();

                } else if (candle instanceof CexMarketCandle cexCandle) {
                    // 处理CexMarketCandle对象 - 这是关键修复！

                    log.debug("candle[{}] 是CexMarketCandle类型 - timestamp={}, open={}, high={}, low={}, close={}, volume={}",
                            i, cexCandle.getTimestamp(), cexCandle.getOpen(),
                            cexCandle.getHigh(), cexCandle.getLow(),
                            cexCandle.getClose(), cexCandle.getVolume());

                    ohlcData = TechnicalIndicators.OhlcData.builder()
                            .timestamp(cexCandle.getTimestamp())
                            .time(formatTime(cexCandle.getTimestamp()))
                            .open(cexCandle.getOpen())
                            .high(cexCandle.getHigh())
                            .low(cexCandle.getLow())
                            .close(cexCandle.getClose())
                            .volume(cexCandle.getVolume())
                            .volumeCcy(cexCandle.getVolumeCcy())
                            .build();
                } else {
                    log.warn("candle[{}] 类型未知: {}", i, candle.getClass().getName());
                }

                if (ohlcData != null) {
                    ohlcHistory.add(ohlcData);
                    log.debug("成功添加OHLC数据[{}]: timestamp={}, open={}, high={}, low={}, close={}, volume={}",
                            i, ohlcData.getTimestamp(), ohlcData.getOpen(),
                            ohlcData.getHigh(), ohlcData.getLow(),
                            ohlcData.getClose(), ohlcData.getVolume());
                }
            } catch (Exception e) {
                log.error("构建OHLC数据[{}]失败", i, e);
            }
        }

        log.debug("OHLC历史数据构建完成 - 成功构建{}条数据", ohlcHistory.size());

        return ohlcHistory;
    }

    /**
     * 处理RSI指标
     */
    private void processRsiIndicators(IndicatorCalculationResponse response, TechnicalIndicators indicators, List<Integer> rsiPeriods) {
        try {
            List<IndicatorCalculationResponse.IndicatorResult> rsiResults = response.getResults().get("RSI");
            if (rsiResults == null || rsiResults.isEmpty()) return;

            TechnicalIndicators.RsiData.RsiDataBuilder rsiBuilder = TechnicalIndicators.RsiData.builder();
            List<Double> rsi5History = new ArrayList<>();
            List<Double> rsi20History = new ArrayList<>();
            List<Double> rsi30History = new ArrayList<>();

            for (IndicatorCalculationResponse.IndicatorResult result : rsiResults) {
                String period = result.getPeriod();
                List<IndicatorCalculationResponse.SingleValue> values = result.getValues();
                if (CollectionUtils.isEmpty(values)) {
                    continue;
                }
                values = new ArrayList<>(values.stream().filter(Objects::nonNull).toList());
                values.sort(Comparator.comparing(IndicatorCalculationResponse.SingleValue::getTimestamp));
                Collections.reverse(values);
//                List<BigDecimal> values = result.getValues().stream()
//                        .map(IndicatorCalculationResponse.SingleValue::getValue)
//                        .collect(Collectors.toList());
//                if (values.isEmpty()) continue;

                // 获取最新值
                BigDecimal latestValue = values.get(0).getValue();
                String signal = TechnicalIndicators.getRsiSignal(latestValue);

                // 提取最近10个历史值
                List<Double> history = extractLast10Values(values);
                Collections.reverse(history);
                switch (period) {
                    case "5":
                        rsiBuilder.rsi5(latestValue).rsi5Signal(signal);
                        rsi5History.addAll(history);
                        break;
                    case "20":
                        rsiBuilder.rsi20(latestValue).rsi20Signal(signal);
                        rsi20History.addAll(history);
                        break;
                    case "30":
                        rsiBuilder.rsi30(latestValue).rsi30Signal(signal);
                        rsi30History.addAll(history);
                        break;
                }
            }

            // 计算总体信号
            String overallSignal = calculateRsiOverallSignal(rsiBuilder.build().getRsi5(), rsiBuilder.build().getRsi20(),
                    rsiBuilder.build().getRsi30());
            rsiBuilder.overallSignal(overallSignal);

            indicators.setRsiData(rsiBuilder.build());
            indicators.setRsi5History(rsi5History);
            indicators.setRsi20History(rsi20History);
            indicators.setRsi30History(rsi30History);
        } catch (Exception e) {
            log.error("处理RSI指标失败", e);
        }
    }

    /**
     * 处理EMA指标
     */
    private void processEmaIndicators(IndicatorCalculationResponse response, TechnicalIndicators indicators, List<Integer> emaPeriods) {
        try {
            List<IndicatorCalculationResponse.IndicatorResult> emaResults = response.getResults().get("EMA");
            if (emaResults == null || emaResults.isEmpty()) return;

            List<Double> ema5History = new ArrayList<>();
            List<Double> ema20History = new ArrayList<>();
            List<Double> ema30History = new ArrayList<>();

            for (IndicatorCalculationResponse.IndicatorResult result : emaResults) {
                String period = result.getPeriod();
                List<IndicatorCalculationResponse.SingleValue> values = result.getValues();
                if (CollectionUtils.isEmpty(values)) continue;
                // 提取最近10个历史值
                values = new ArrayList<>(values.stream().filter(Objects::nonNull).toList());
                List<Double> history = extractLast10Values(values);
                Collections.reverse(history);
                switch (period) {
                    case "5":
                        ema5History.addAll(history);
                        break;
                    case "20":
                        ema20History.addAll(history);
                        break;
                    case "30":
                        ema30History.addAll(history);
                        break;
                }
            }
            indicators.setEma5History(ema5History);
            indicators.setEma20History(ema20History);
            indicators.setEma30History(ema30History);

        } catch (Exception e) {
            log.error("处理EMA指标失败", e);
        }
    }

    /**
     * 处理BOLL指标
     */
    private void processBollIndicators(IndicatorCalculationResponse response, TechnicalIndicators indicators, List<Integer> bollPeriods) {
        try {
            List<IndicatorCalculationResponse.IndicatorResult> bollResults = response.getResults().get("BOLL");
            if (bollResults == null || bollResults.isEmpty()) {
                log.debug("BOLL指标结果为空或未找到BOLL数据");
                return;
            }

            TechnicalIndicators.BollData.BollDataBuilder bollBuilder = TechnicalIndicators.BollData.builder();
            List<TechnicalIndicators.BollPeriod> boll5History = new ArrayList<>();
            List<TechnicalIndicators.BollPeriod> boll15History = new ArrayList<>();
            List<TechnicalIndicators.BollPeriod> boll20History = new ArrayList<>();
            List<TechnicalIndicators.BollPeriod> boll25History = new ArrayList<>();

            // 获取当前价格
            BigDecimal currentPrice = indicators.getCurrentPrice();
            if (currentPrice == null) {
                log.warn("当前价格为空，使用0作为默认值进行BOLL计算");
                currentPrice = BigDecimal.ZERO;
            } else {
                log.debug("使用当前价格进行BOLL计算: {}", currentPrice);
            }

            // 将currentPrice声明为final，以便在lambda中使用
            final BigDecimal finalCurrentPrice = currentPrice;

            for (IndicatorCalculationResponse.IndicatorResult result : bollResults) {
                String period = result.getPeriod();
                List<IndicatorCalculationResponse.BollValue> bollValues = result.getBollValues();

                if (CollectionUtils.isEmpty(bollValues)) continue;

                bollValues = new ArrayList<>(bollValues.stream().filter(Objects::nonNull).toList());

                // 获取最新值
                IndicatorCalculationResponse.BollValue latestBoll = bollValues.get(bollValues.size() - 1);

                // 提取最近10个历史值
                List<IndicatorCalculationResponse.BollValue> history = extractLast10BollValues(bollValues);
                List<TechnicalIndicators.BollPeriod> bollPeriodHistory = history.stream()
                        .map(bollValue -> convertToBollPeriod(bollValue, finalCurrentPrice))
                        .toList();

                switch (period) {
                    case "5,2":
                        TechnicalIndicators.BollPeriod boll5 = convertToBollPeriod(latestBoll, currentPrice);
                        bollBuilder.boll5(boll5);
                        boll5History.addAll(bollPeriodHistory);
                        break;
                    case "15,2":
                        TechnicalIndicators.BollPeriod boll15 = convertToBollPeriod(latestBoll, currentPrice);
                        bollBuilder.boll15(boll15);
                        boll15History.addAll(bollPeriodHistory);
                        break;
                    case "20,2":
                        TechnicalIndicators.BollPeriod boll20 = convertToBollPeriod(latestBoll, currentPrice);
                        bollBuilder.boll20(boll20);
                        boll20History.addAll(bollPeriodHistory);
                        break;
                    case "25,2":
                        TechnicalIndicators.BollPeriod boll25 = convertToBollPeriod(latestBoll, currentPrice);
                        bollBuilder.boll25(boll25);
                        boll25History.addAll(bollPeriodHistory);
                        break;
                }
            }

            // 计算总体信号
            String overallSignal = calculateBollOverallSignal(
                    bollBuilder.build().getBoll5());
            bollBuilder.overallSignal(overallSignal);

            indicators.setBollData(bollBuilder.build());
            indicators.setBoll5History(boll5History);
            indicators.setBoll15History(boll15History);
            indicators.setBoll20History(boll20History);
            indicators.setBoll25History(boll25History);

        } catch (Exception e) {
            log.error("处理BOLL指标失败", e);
        }
    }

    /**
     * 处理ADX指标
     */
    private void processAdxIndicators(IndicatorCalculationResponse response, TechnicalIndicators indicators, List<Integer> adxPeriods) {
        try {
            List<IndicatorCalculationResponse.IndicatorResult> adxResults = response.getResults().get("ADX");
            if (adxResults == null || adxResults.isEmpty()) {
                log.debug("ADX指标结果为空或未找到ADX数据");
                return;
            }

            TechnicalIndicators.AdxData.AdxDataBuilder adxBuilder = TechnicalIndicators.AdxData.builder();
            List<Double> adx14History = new ArrayList<>();

            for (IndicatorCalculationResponse.IndicatorResult result : adxResults) {
                String period = result.getPeriod();
                List<IndicatorCalculationResponse.SingleValue> values = result.getValues();
                if (CollectionUtils.isEmpty(values)) {
                    continue;
                }

                log.debug("ADX指标处理: period={}, values.size()={}", period, values.size());

                // 过滤掉null的SingleValue对象（防止排序时NullPointerException）
                // 但不处理value为null的情况
                values = new ArrayList<>(values.stream()
                        .filter(Objects::nonNull)
                        .toList());

                // 按timestamp排序（升序）
                values.sort(Comparator.comparing(IndicatorCalculationResponse.SingleValue::getTimestamp));

                // 反转（降序，最新的在前）
                Collections.reverse(values);

                // 获取最新值（第一个value非null的值）
                BigDecimal latestAdx = null;
                for (IndicatorCalculationResponse.SingleValue sv : values) {
                    if (sv.getValue() != null) {
                        latestAdx = sv.getValue();
                        break;
                    }
                }

                if (latestAdx == null) {
                    log.warn("ADX指标: period={}没有有效值", period);
                    continue;
                }

                String trendStrength = TechnicalIndicators.getAdxSignal(latestAdx);
                String overallSignal = TechnicalIndicators.calculateOverallAdxSignal(latestAdx);

                // 提取最近30个历史值（保持value为null，降序，最新的在前）
                List<Double> history = extractLast10ValuesWithNulls(values);
                // 不要再次反转，保持降序（最新的在前），与OHLC一致
                log.debug("ADX历史值: period={}, history.size()={}", period, history.size());

                switch (period) {
                    case "14":
                        adxBuilder.adx14(TechnicalIndicators.AdxPeriod.builder()
                                .period(14)
                                .adx(latestAdx)
                                .plusDI(BigDecimal.ZERO) // TODO: 需要从响应中获取+DI和-DI值
                                .minusDI(BigDecimal.ZERO)
                                .trendStrength(trendStrength)
                                .trendDirection("横盘") // TODO: 根据+DI和-DI计算趋势方向
                                .build());
                        adxBuilder.overallSignal(overallSignal);
                        adx14History.addAll(history);
                        break;
                }
            }

            indicators.setAdxData(adxBuilder.build());
            indicators.setAdx14History(adx14History);

        } catch (Exception e) {
            log.error("处理ADX指标失败", e);
        }
    }

    /**
     * 处理MACD指标
     */
    private void processMacdIndicators(IndicatorCalculationResponse response, TechnicalIndicators indicators, List<Integer> macdPeriods) {
        try {
            List<IndicatorCalculationResponse.IndicatorResult> macdResults = response.getResults().get("MACD");
            if (macdResults == null || macdResults.isEmpty()) {
                log.debug("MACD指标结果为空或未找到MACD数据");
                return;
            }

            TechnicalIndicators.MacdData.MacdDataBuilder macdBuilder = TechnicalIndicators.MacdData.builder();
            List<Double> macdHistory = new ArrayList<>();
            List<Double> deaHistory = new ArrayList<>();
            List<Double> histogramHistory = new ArrayList<>();

            for (IndicatorCalculationResponse.IndicatorResult result : macdResults) {
                String period = result.getPeriod();
                List<IndicatorCalculationResponse.MacdValue> macdValues = result.getMacdValues();
                if (CollectionUtils.isEmpty(macdValues)) {
                    continue;
                }

                log.debug("MACD指标处理: period={}, macdValues.size()={}", period, macdValues.size());

                // 过滤掉null的MacdValue对象（防止排序时NullPointerException）
                macdValues = new ArrayList<>(macdValues.stream()
                        .filter(Objects::nonNull)
                        .toList());

                // 按timestamp排序（升序）
                macdValues.sort(Comparator.comparing(IndicatorCalculationResponse.MacdValue::getTimestamp));

                // 反转（降序，最新的在前）
                Collections.reverse(macdValues);

                // 获取最新值（第一个所有字段都非null的值）
                IndicatorCalculationResponse.MacdValue latestMacd = null;
                for (IndicatorCalculationResponse.MacdValue mv : macdValues) {
                    if (mv != null && mv.getDiff() != null && mv.getDea() != null && mv.getMacd() != null) {
                        latestMacd = mv;
                        break;
                    }
                }

                if (latestMacd == null) {
                    log.warn("MACD指标: period={}没有有效值", period);
                    continue;
                }

                String trendSignal = TechnicalIndicators.getMacdSignal(latestMacd.getDiff(), latestMacd.getDea());
                String overallSignal = TechnicalIndicators.calculateOverallMacdSignal(null);

                // 解析period字符串（例如"12,26,9"）
                String[] periods = period.split(",");
                int fastPeriod = 12;
                int slowPeriod = 26;
                int signalPeriod = 9;
                if (periods.length == 3) {
                    fastPeriod = Integer.parseInt(periods[0].trim());
                    slowPeriod = Integer.parseInt(periods[1].trim());
                    signalPeriod = Integer.parseInt(periods[2].trim());
                }

                // 提取最近30个历史值（降序，最新的在前）
                // 注意：macdValues已经是降序（最新的在前），前33个是null
                // 我们需要提取前30个非null的值
                List<Double> diffHist = extractLast30NonNullValues(macdValues, IndicatorCalculationResponse.MacdValue::getDiff);
                List<Double> deaHist = extractLast30NonNullValues(macdValues, IndicatorCalculationResponse.MacdValue::getDea);
                List<Double> macdHist = extractLast30NonNullValues(macdValues, IndicatorCalculationResponse.MacdValue::getMacd);

                log.debug("MACD历史值: period={}, diffHist.size()={}, deaHist.size()={}, macdHist.size()={}",
                        period, diffHist.size(), deaHist.size(), macdHist.size());

                macdBuilder.macd(TechnicalIndicators.MacdPeriod.builder()
                        .fastPeriod(fastPeriod)
                        .slowPeriod(slowPeriod)
                        .signalPeriod(signalPeriod)
                        .diff(latestMacd.getDiff())
                        .dea(latestMacd.getDea())
                        .macd(latestMacd.getMacd())
                        .trendSignal(trendSignal)
                        .build());
                macdBuilder.overallSignal(overallSignal);

                macdHistory.addAll(diffHist);
                deaHistory.addAll(deaHist);
                histogramHistory.addAll(macdHist);
            }

            indicators.setMacdData(macdBuilder.build());
            indicators.setMacdHistory(macdHistory);
            indicators.setDeaHistory(deaHistory);
            indicators.setHistogramHistory(histogramHistory);

        } catch (Exception e) {
            log.error("处理MACD指标失败", e);
        }
    }

    /**
     * 提取最近30个MACD数值
     */
    private List<Double> extractLast10MacdValues(List<IndicatorCalculationResponse.MacdValue> values,
                                                 java.util.function.Function<IndicatorCalculationResponse.MacdValue, BigDecimal> getter) {
        if (CollectionUtils.isEmpty(values)) return Collections.emptyList();

        // 判断values的顺序
        if (values.get(0).getTimestamp() < values.get(values.size() - 1).getTimestamp()) {
            // 如果是升序，反转列表（最新的在前面）
            Collections.reverse(values);
        }

        // 取实际数量和HISTORY_DATA_COUNT中的较小值，避免IndexOutOfBoundsException
        int count = Math.min(values.size(), HISTORY_DATA_COUNT);

        return values.subList(0, count)
                .stream()
                .map(getter)
                .map(v -> v == null ? null : v.doubleValue())
                .collect(Collectors.toList());
    }

    /**
     * 提取最近30个数值
     */
    private List<Double> extractLast10Values(List<IndicatorCalculationResponse.SingleValue> values) {
        if (CollectionUtils.isEmpty(values)) return Collections.emptyList();

        // 判断values的顺序
        if (values.get(0).getTimestamp() < values.get(values.size() - 1).getTimestamp()) {
            // 如果是升序，反转列表（最新的在前面）
            Collections.reverse(values);
        }

        // 取实际数量和HISTORY_DATA_COUNT中的较小值，避免IndexOutOfBoundsException
        int count = Math.min(values.size(), HISTORY_DATA_COUNT);

        return values.subList(0, count)
                .stream()
                .map(IndicatorCalculationResponse.SingleValue::getValue)
                .map(v -> null == v ? 0D : v.doubleValue())
                .collect(Collectors.toList());
    }

    /**
     * 提取最近30个数值（保留null值）
     */
    private List<Double> extractLast10ValuesWithNulls(List<IndicatorCalculationResponse.SingleValue> values) {
        if (CollectionUtils.isEmpty(values)) return Collections.emptyList();

        // 判断values的顺序
        if (values.get(0).getTimestamp() < values.get(values.size() - 1).getTimestamp()) {
            // 如果是升序，反转列表（最新的在前面）
            Collections.reverse(values);
        }

        // 取实际数量和HISTORY_DATA_COUNT中的较小值，避免IndexOutOfBoundsException
        int count = Math.min(values.size(), HISTORY_DATA_COUNT);

        return values.subList(0, count)
                .stream()
                .map(IndicatorCalculationResponse.SingleValue::getValue)
                .map(v -> v == null ? null : v.doubleValue())
                .collect(Collectors.toList());
    }

    /**
     * 提取前30个非null值（降序，最新的在前）
     * 用于MACD等指标，前面有null值的情况
     */
    private <T> List<Double> extractLast30NonNullValues(List<T> values, java.util.function.Function<T, BigDecimal> getter) {
        if (CollectionUtils.isEmpty(values)) return Collections.emptyList();

        List<Double> result = new ArrayList<>();
        int count = 0;

        // 从前向后遍历（已经是降序，最新的在前），收集前30个非null值
        for (T item : values) {
            BigDecimal value = getter.apply(item);
            if (value != null) {
                result.add(value.doubleValue());
                count++;
                if (count >= HISTORY_DATA_COUNT) {
                    break;
                }
            }
        }

        log.debug("extractLast30NonNullValues: 输入大小={}, 提取到{}个非null值", values.size(), result.size());

        return result;
    }

    /**
     * 提取最近10个BOLL值
     */
    private List<IndicatorCalculationResponse.BollValue> extractLast10BollValues(
            List<IndicatorCalculationResponse.BollValue> bollValues) {
        if (bollValues == null || bollValues.isEmpty()) return new ArrayList<>();

        int startIndex = Math.max(0, bollValues.size() - HISTORY_DATA_COUNT);
        return bollValues.subList(startIndex, bollValues.size());
    }

    /**
     * 转换BOLL值
     */
    private TechnicalIndicators.BollPeriod convertToBollPeriod(IndicatorCalculationResponse.BollValue bollValue, BigDecimal currentPrice) {
        if (bollValue == null) return null;

        if (currentPrice == null) currentPrice = BigDecimal.ZERO;

        BigDecimal bandwidth = bollValue.getUpper().subtract(bollValue.getLower());

        BigDecimal distanceToUpperPercent = BigDecimal.ZERO;
        BigDecimal distanceToLowerPercent = BigDecimal.ZERO;

        if (bollValue.getUpper().compareTo(bollValue.getLower()) > 0) {
            distanceToUpperPercent = bollValue.getUpper().subtract(currentPrice)
                    .divide(bollValue.getUpper(), 8, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

            distanceToLowerPercent = currentPrice.subtract(bollValue.getLower())
                    .divide(bollValue.getLower(), 8, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        return TechnicalIndicators.BollPeriod.builder()
                .upperBand(bollValue.getUpper())
                .middleBand(bollValue.getMiddle())
                .lowerBand(bollValue.getLower())
                .pricePosition(TechnicalIndicators.getBollPricePosition(
                        currentPrice, bollValue.getUpper(), bollValue.getMiddle(), bollValue.getLower()))
                .signal(TechnicalIndicators.getBollSignal(
                        TechnicalIndicators.getBollPricePosition(
                                currentPrice, bollValue.getUpper(), bollValue.getMiddle(), bollValue.getLower())))
                .bandwidth(bandwidth)
                .distanceToUpperPercent(distanceToUpperPercent)
                .distanceToLowerPercent(distanceToLowerPercent)
                .build();
    }

    /**
     * 提取BigDecimal值
     */
    private BigDecimal extractBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        return new BigDecimal(value.toString());
    }

    /**
     * 格式化时间
     */
    private String formatTime(Long timestamp) {
        try {
            LocalDateTime dateTime = LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.ofHours(8));
            return dateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 计算RSI总体信号
     */
    private String calculateRsiOverallSignal(BigDecimal rsi5, BigDecimal rsi20, BigDecimal rsi30) {
        return TechnicalIndicators.calculateOverallRsiSignal(rsi5, rsi20, rsi30);
    }

    /**
     * 计算BOLL总体信号
     */
    private String calculateBollOverallSignal(TechnicalIndicators.BollPeriod boll5) {
        if (boll5 == null) return "信号不明确";
        String position = boll5.getPricePosition();
        return TechnicalIndicators.getBollSignal(position);
    }

    /**
     * 获取整数列表参数
     */
    private List<Integer> getIntListParameter(String paramName, List<Integer> defaultValue) {
        try {
            String paramStr = getStringParameter(paramName, null);
            if (paramStr == null || paramStr.trim().isEmpty()) {
                return defaultValue;
            }

            return Arrays.stream(paramStr.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("解析整数列表参数失败: {}, 使用默认值", paramName, e);
            return defaultValue;
        }
    }

    /**
     * 格式化为汇总形式
     */
    private String formatIndicatorsAsSummary(Map<String, TechnicalIndicators> indicators, String timeframe) {
        StringBuilder summaryContent = new StringBuilder();
        summaryContent.append("### 持仓合约技术指标汇总 (").append(timeframe).append(")\n\n");

        // 创建汇总表格
        summaryContent.append("| 合约代码 | 当前价格 | RSI-5 | RSI信号 | BOLL位置 | BOLL信号 | 总体信号 |\n");
        summaryContent.append("|---------|----------|-------|---------|----------|----------|----------|\n");

        for (Map.Entry<String, TechnicalIndicators> entry : indicators.entrySet()) {
            String instId = entry.getKey();
            TechnicalIndicators indicator = entry.getValue();

            String row = formatIndicatorAsSummaryRow(instId, indicator);
            summaryContent.append(row).append("\n");
        }

        return summaryContent.toString();
    }

    /**
     * 格式化为详细形式
     */
    private String formatDetailedIndicators(Map<String, TechnicalIndicators> indicators, String timeframe) {
        StringBuilder detailedContent = new StringBuilder();
        detailedContent.append("### 持仓合约技术指标详细分析 (").append(timeframe).append(")\n\n");

        for (Map.Entry<String, TechnicalIndicators> entry : indicators.entrySet()) {
            TechnicalIndicators indicator = entry.getValue();
            // 使用TechnicalIndicators自带的格式化方法
            String formattedIndicator = TechnicalIndicators.formatTechnicalIndicatorsAsTable(indicator);
            detailedContent.append(formattedIndicator).append("\n---\n\n");
        }

        return detailedContent.toString();
    }

    /**
     * 格式化为表格形式
     */
    private String formatIndicatorsAsTable(Map<String, TechnicalIndicators> indicators, String timeframe) {
        StringBuilder tableContent = new StringBuilder();
        tableContent.append("### 持仓合约技术指标 (").append(timeframe).append(")\n\n");

        // RSI指标表格
        tableContent.append("#### RSI指标\n\n");
        tableContent.append("| 合约代码 | RSI-5 | RSI-15 | RSI-25 | 5分钟信号 | 15分钟信号 | 25分钟信号 | 总体信号 |\n");
        tableContent.append("|---------|-------|--------|--------|----------|-----------|-----------|----------|\n");

        for (Map.Entry<String, TechnicalIndicators> entry : indicators.entrySet()) {
            String row = formatRsiAsTableRow(entry.getKey(), entry.getValue());
            tableContent.append(row).append("\n");
        }

        // BOLL指标表格
        tableContent.append("\n#### BOLL指标\n\n");
        tableContent.append("| 合约代码 | 5分钟位置 | 5分钟信号 | 15分钟位置 | 15分钟信号 | 25分钟位置 | 25分钟信号 | 总体信号 |\n");
        tableContent.append("|---------|-----------|----------|-------------|-----------|--------------|--------------|----------|\n");

        for (Map.Entry<String, TechnicalIndicators> entry : indicators.entrySet()) {
            String row = formatBollAsTableRow(entry.getKey(), entry.getValue());
            tableContent.append(row).append("\n");
        }

        return tableContent.toString();
    }

    /**
     * 将技术指标格式化为汇总表格行
     */
    private String formatIndicatorAsSummaryRow(String instId, TechnicalIndicators indicator) {
        try {
            String currentPrice = indicator.getCurrentPrice() != null ?
                    indicator.getCurrentPrice().setScale(8, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() : "-";

            String rsi5 = indicator.getRsiData() != null && indicator.getRsiData().getRsi5() != null ?
                    indicator.getRsiData().getRsi5().setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() : "-";

            String rsiSignal = indicator.getRsiData() != null && indicator.getRsiData().getRsi5Signal() != null ?
                    indicator.getRsiData().getRsi5Signal() : "-";

            String bollPosition = indicator.getBollData() != null && indicator.getBollData().getBoll5() != null && indicator.getBollData().getBoll5().getPricePosition() != null ?
                    indicator.getBollData().getBoll5().getPricePosition() : "-";

            String bollSignal = indicator.getBollData() != null && indicator.getBollData().getBoll5() != null && indicator.getBollData().getBoll5().getSignal() != null ?
                    indicator.getBollData().getBoll5().getSignal() : "-";

            // 计算总体信号
            String overallSignal = "信号不明确";
            if (indicator.getRsiData() != null && indicator.getBollData() != null) {
                String rsiOverall = indicator.getRsiData().getOverallSignal();
                String bollOverall = indicator.getBollData().getOverallSignal();

                if (rsiOverall.contains("看涨") && bollOverall.contains("反弹")) {
                    overallSignal = "看涨";
                } else if (rsiOverall.contains("看跌") && bollOverall.contains("回调")) {
                    overallSignal = "看跌";
                } else if (rsiOverall.contains("趋势") || bollOverall.contains("整理")) {
                    overallSignal = "震荡";
                }
            }

            return String.format("| %s | %s | %s | %s | %s | %s | %s |",
                    instId, currentPrice, rsi5, rsiSignal, bollPosition, bollSignal, overallSignal);

        } catch (Exception e) {
            log.debug("格式化技术指标汇总行失败: {}", instId, e);
            return "| - | - | - | - | - | - | - |";
        }
    }

    /**
     * 将RSI指标格式化为表格行
     */
    private String formatRsiAsTableRow(String instId, TechnicalIndicators indicator) {
        try {
            String rsi5 = indicator.getRsiData() != null && indicator.getRsiData().getRsi5() != null ?
                    indicator.getRsiData().getRsi5().setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() : "-";

            String rsi20 = indicator.getRsiData() != null && indicator.getRsiData().getRsi20() != null ?
                    indicator.getRsiData().getRsi20().setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() : "-";

            String rsi30 = indicator.getRsiData() != null && indicator.getRsiData().getRsi30() != null ?
                    indicator.getRsiData().getRsi30().setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() : "-";

            String rsi5Signal = indicator.getRsiData() != null && indicator.getRsiData().getRsi5Signal() != null ?
                    indicator.getRsiData().getRsi5Signal() : "-";

            String rsi20Signal = indicator.getRsiData() != null && indicator.getRsiData().getRsi20Signal() != null ?
                    indicator.getRsiData().getRsi20Signal() : "-";

            String rsi30Signal = indicator.getRsiData() != null && indicator.getRsiData().getRsi30Signal() != null ?
                    indicator.getRsiData().getRsi30Signal() : "-";

            String overallSignal = indicator.getRsiData() != null ? indicator.getRsiData().getOverallSignal() : "-";

            return String.format("| %s | %s | %s | %s | %s | %s | %s | %s |",
                    instId, rsi5, rsi20, rsi30, rsi5Signal, rsi20Signal, rsi30Signal, overallSignal);

        } catch (Exception e) {
            log.debug("格式化RSI指标行失败: {}", instId, e);
            return "| - | - | - | - | - | - | - | - |";
        }
    }

    /**
     * 将BOLL指标格式化为表格行
     */
    private String formatBollAsTableRow(String instId, TechnicalIndicators indicator) {
        try {
            String position5 = indicator.getBollData() != null && indicator.getBollData().getBoll5() != null && indicator.getBollData().getBoll5().getPricePosition() != null ?
                    indicator.getBollData().getBoll5().getPricePosition() : "-";

            String signal5 = indicator.getBollData() != null && indicator.getBollData().getBoll5() != null && indicator.getBollData().getBoll5().getSignal() != null ?
                    indicator.getBollData().getBoll5().getSignal() : "-";

            String position15 = indicator.getBollData() != null && indicator.getBollData().getBoll15() != null && indicator.getBollData().getBoll15().getPricePosition() != null ?
                    indicator.getBollData().getBoll15().getPricePosition() : "-";

            String signal15 = indicator.getBollData() != null && indicator.getBollData().getBoll15() != null && indicator.getBollData().getBoll15().getSignal() != null ?
                    indicator.getBollData().getBoll15().getSignal() : "-";

            String position25 = indicator.getBollData() != null && indicator.getBollData().getBoll25() != null && indicator.getBollData().getBoll25().getPricePosition() != null ?
                    indicator.getBollData().getBoll25().getPricePosition() : "-";

            String signal25 = indicator.getBollData() != null && indicator.getBollData().getBoll25() != null && indicator.getBollData().getBoll25().getSignal() != null ?
                    indicator.getBollData().getBoll25().getSignal() : "-";

            String overallSignal = indicator.getBollData() != null ? indicator.getBollData().getOverallSignal() : "-";

            return String.format("| %s | %s | %s | %s | %s | %s | %s | %s |",
                    instId, position5, signal5, position15, signal15, position25, signal25, overallSignal);

        } catch (Exception e) {
            log.debug("格式化BOLL指标行失败: {}", instId, e);
            return "| - | - | - | - | - | - | - | - |";
        }
    }

    /**
     * 格式化多时间周期技术指标 (主要格式)
     */
    private String formatMultiTimeframeIndicators(Map<String, Map<String, TechnicalIndicators>> multiTimeframeIndicators,
                                                  String[] timeframes) {
        StringBuilder content = new StringBuilder();
        content.append("### 技术指标分析\n\n");

        for (String timeframe : timeframes) {
            Map<String, TechnicalIndicators> timeframeIndicators = multiTimeframeIndicators.get(timeframe);
            if (timeframeIndicators == null || timeframeIndicators.isEmpty()) {
                continue;
            }

            String timeframeName = getTimeframeName(timeframe);
            content.append("#### ").append(timeframeName).append("技术指标\n\n");

            // 获取技术指标数据
            Map<String, Object> indicatorData = extractIndicatorData(timeframeIndicators);

            // 显示RSI指标
            content.append("**RSI相对强弱指数**\n\n");
            content.append("| 指标 | 最新值 | 信号 |\n");
            content.append("|------|--------|------|\n");

            @SuppressWarnings("unchecked")
            Map<String, Object> rsiData = (Map<String, Object>) indicatorData.get("RSI");
            if (rsiData != null) {
                for (String period : Arrays.asList("5", "20", "30")) {
                    Object rsiValue = rsiData.get("RSI_" + period);
                    Object rsiSignal = rsiData.get("RSI_" + period + "_SIGNAL");
                    content.append(String.format("| RSI-%s | %s | %s |\n",
                            period,
                            formatIndicatorValue(rsiValue),
                            formatIndicatorSignal(rsiSignal)));
                }
            }
            content.append("\n");

            // 显示EMA指标
            content.append("**EMA简单移动平均**\n\n");
            content.append("| 指标 | 最新值 | 与价格关系 |\n");
            content.append("|------|--------|------------|\n");

            @SuppressWarnings("unchecked")
            Map<String, Object> emaData = (Map<String, Object>) indicatorData.get("EMA");
            if (emaData != null) {
                for (String period : Arrays.asList("5", "20", "30")) {
                    Object emaValue = emaData.get("EMA_" + period);
                    Object priceRelation = emaData.get("EMA_" + period + "_RELATION");
                    content.append(String.format("| EMA-%s | %s | %s |\n",
                            period,
                            formatIndicatorValue(emaValue),
                            formatPriceRelation(priceRelation)));
                }
            }
            content.append("\n");

            // 显示BOLL指标
            content.append("**BOLL布林带**\n\n");
            content.append("| 指标 | 上轨 | 中轨 | 下轨 | 价格位置 | 信号 |\n");
            content.append("|------|------|------|------|----------|------|\n");

            @SuppressWarnings("unchecked")
            Map<String, Object> bollData = (Map<String, Object>) indicatorData.get("BOLL");
            if (bollData != null) {
                Object bollUpper = bollData.get("BOLL_5_UPPER");
                Object bollMiddle = bollData.get("BOLL_5_MIDDLE");
                Object bollLower = bollData.get("BOLL_5_LOWER");
                Object pricePosition = bollData.get("BOLL_5_POSITION");
                Object bollSignal = bollData.get("BOLL_5_SIGNAL");

                content.append(String.format("| BOLL-5 | %s | %s | %s | %s | %s |\n",
                        formatIndicatorValue(bollUpper),
                        formatIndicatorValue(bollMiddle),
                        formatIndicatorValue(bollLower),
                        formatPricePosition(pricePosition),
                        formatIndicatorSignal(bollSignal)));
            }
            content.append("\n---\n\n");
        }

        return content.toString();
    }

    /**
     * 格式化多时间周期技术指标为汇总格式
     */
    private String formatMultiTimeframeIndicatorsAsSummary(Map<String, Map<String, TechnicalIndicators>> multiTimeframeIndicators,
                                                           String[] timeframes) {
        StringBuilder content = new StringBuilder();
        content.append("### 多时间周期技术指标汇总（最近30个值）\n\n");

        // 为每个时间周期创建单独的章节
        for (String timeframe : timeframes) {
            Map<String, TechnicalIndicators> timeframeIndicators = multiTimeframeIndicators.get(timeframe);
            if (timeframeIndicators == null || timeframeIndicators.isEmpty()) {
                continue;
            }

            String timeframeName = getTimeframeName(timeframe);
            content.append("#### ").append(timeframeName).append("技术指标\n\n");

            // RSI指标表格
            content.append("**RSI指标（最近30个值）**\n\n");
            content.append("| 合约代码 | RSI-5 | RSI-20 | RSI-30 | 信号状态 |\n");
            content.append("|---------|-------|--------|--------|----------|\n");

            for (Map.Entry<String, TechnicalIndicators> entry : timeframeIndicators.entrySet()) {
                String row = formatRsiHistoryAsTableRow(entry.getKey(), entry.getValue());
                content.append(row).append("\n");
            }

            // EMA指标表格
            content.append("\n**EMA指标（最近30个值）**\n\n");
            content.append("| 合约代码 | EMA-5 | EMA-20 | EMA-30 | 趋势状态 |\n");
            content.append("|---------|-------|---------|---------|----------|\n");

            for (Map.Entry<String, TechnicalIndicators> entry : timeframeIndicators.entrySet()) {
                String row = formatEmaHistoryAsTableRow(entry.getKey(), entry.getValue());
                content.append(row).append("\n");
            }

            // BOLL指标表格
            content.append("\n**BOLL指标（最近30个值）**\n\n");
            content.append("| 合约代码 | BOLL-5位置 | BOLL-5信号 | 总体信号 |\n");
            content.append("|---------|------------|------------|----------|\n");

            for (Map.Entry<String, TechnicalIndicators> entry : timeframeIndicators.entrySet()) {
                String row = formatBollHistoryAsTableRow(entry.getKey(), entry.getValue());
                content.append(row).append("\n");
            }

            content.append("\n---\n\n");
        }

        return content.toString();
    }

    /**
     * 格式化多时间周期技术指标为详细格式
     */
    private String formatDetailedMultiTimeframeIndicators(Map<String, Map<String, TechnicalIndicators>> multiTimeframeIndicators,
                                                          String[] timeframes) {
        StringBuilder content = new StringBuilder();
        content.append("### 多时间周期技术指标详细分析\n\n");

        for (String timeframe : timeframes) {
            Map<String, TechnicalIndicators> timeframeIndicators = multiTimeframeIndicators.get(timeframe);
            if (timeframeIndicators == null || timeframeIndicators.isEmpty()) {
                continue;
            }

            String timeframeName = getTimeframeName(timeframe);
            content.append("## ").append(timeframeName).append("详细技术指标\n\n");

            for (Map.Entry<String, TechnicalIndicators> entry : timeframeIndicators.entrySet()) {
                String instId = entry.getKey();
                TechnicalIndicators indicator = entry.getValue();

                content.append("### ").append(instId).append("\n\n");

                // 显示最近30个技术指标值
                content.append("**最近30个数据点的技术指标值：**\n\n");

                // 这里需要从TechnicalIndicators对象中获取历史数据
                // 由于当前实现限制，先显示基本信息
                content.append("- 当前价格: ").append(formatIndicatorValue(indicator.getCurrentPrice())).append("\n");
                content.append("- 时间周期: ").append(timeframe).append("\n");
                content.append("- 数据更新时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
            }

            content.append("---\n\n");
        }

        return content.toString();
    }

    /**
     * 格式化多时间周期技术指标为表格格式
     */
    private String formatMultiTimeframeIndicatorsAsTable(Map<String, Map<String, TechnicalIndicators>> multiTimeframeIndicators,
                                                         String[] timeframes) {
        StringBuilder content = new StringBuilder();
        content.append("### 多时间周期技术指标表格\n\n");

        for (String timeframe : timeframes) {
            Map<String, TechnicalIndicators> timeframeIndicators = multiTimeframeIndicators.get(timeframe);
            if (timeframeIndicators == null || timeframeIndicators.isEmpty()) {
                continue;
            }

            String timeframeName = getTimeframeName(timeframe);
            content.append("#### ").append(timeframeName).append("技术指标\n\n");

            // RSI表格
            content.append("**RSI指标**\n\n");
            content.append("| 合约代码 | RSI-5 | RSI-20 | RSI-30 | 信号 |\n");
            content.append("|---------|-------|--------|--------|------|\n");

            for (Map.Entry<String, TechnicalIndicators> entry : timeframeIndicators.entrySet()) {
                String instId = entry.getKey();
                TechnicalIndicators indicator = entry.getValue();
                String row = formatMultiTimeframeRsiRow(instId, indicator);
                content.append(row).append("\n");
            }

            content.append("\n");

            // EMA表格
            content.append("**EMA指标**\n\n");
            content.append("| 合约代码 | EMA-5 | EMA-20 | EMA-30 | 价格关系 |\n");
            content.append("|---------|-------|--------|--------|----------|\n");

            for (Map.Entry<String, TechnicalIndicators> entry : timeframeIndicators.entrySet()) {
                String instId = entry.getKey();
                TechnicalIndicators indicator = entry.getValue();
                String row = formatMultiTimeframeEmaRow(instId, indicator);
                content.append(row).append("\n");
            }

            content.append("\n---\n\n");
        }

        return content.toString();
    }

    /**
     * 提取技术指标数据
     */
    private Map<String, Object> extractIndicatorData(Map<String, TechnicalIndicators> indicators) {
        Map<String, Object> data = new HashMap<>();

        try {
            // 从所有合约中聚合数据
            List<Double> allRsi5History = new ArrayList<>();
            List<Double> allRsi20History = new ArrayList<>();
            List<Double> allRsi30History = new ArrayList<>();
            List<Double> allEma5History = new ArrayList<>();
            List<Double> allEma20History = new ArrayList<>();
            List<Double> allEma30History = new ArrayList<>();
            List<TechnicalIndicators.BollPeriod> allBoll5History = new ArrayList<>();

            // 聚合所有合约的指标历史数据
            for (TechnicalIndicators indicator : indicators.values()) {
                if (indicator.getRsi5History() != null) {
                    allRsi5History.addAll(indicator.getRsi5History());
                }
                if (indicator.getRsi20History() != null) {
                    allRsi20History.addAll(indicator.getRsi20History());
                }
                if (indicator.getRsi30History() != null) {
                    allRsi30History.addAll(indicator.getRsi30History());
                }
                if (indicator.getEma5History() != null) {
                    allEma5History.addAll(indicator.getEma5History());
                }
                if (indicator.getEma20History() != null) {
                    allEma20History.addAll(indicator.getEma20History());
                }
                if (indicator.getEma30History() != null) {
                    allEma30History.addAll(indicator.getEma30History());
                }
                if (indicator.getBoll5History() != null) {
                    allBoll5History.addAll(indicator.getBoll5History());
                }
            }

            // RSI数据 - 返回最近10个值
            Map<String, Object> rsiData = new HashMap<>();
            rsiData.put("RSI_5", formatHistoryValues(getLast10Values(allRsi5History)));
            rsiData.put("RSI_5_SIGNAL", getRsiLatestSignal(indicators, 5));
            rsiData.put("RSI_20", formatHistoryValues(getLast10Values(allRsi20History)));
            rsiData.put("RSI_20_SIGNAL", getRsiLatestSignal(indicators, 20));
            rsiData.put("RSI_30", formatHistoryValues(getLast10Values(allRsi30History)));
            rsiData.put("RSI_30_SIGNAL", getRsiLatestSignal(indicators, 30));
            data.put("RSI", rsiData);

            // EMA数据 - 返回最近10个值
            Map<String, Object> emaData = new HashMap<>();
            emaData.put("EMA_5", formatHistoryValues(getLast10Values(allEma5History)));
            emaData.put("EMA_5_RELATION", "价格相对均线位置");
            emaData.put("EMA_20", formatHistoryValues(getLast10Values(allEma20History)));
            emaData.put("EMA_20_RELATION", "价格相对均线位置");
            emaData.put("EMA_30", formatHistoryValues(getLast10Values(allEma30History)));
            emaData.put("EMA_30_RELATION", "价格相对均线位置");
            data.put("EMA", emaData);

            // BOLL数据 - 返回最近10组值
            Map<String, Object> bollData = new HashMap<>();
            List<TechnicalIndicators.BollPeriod> last10Boll = getLast10BollValues(allBoll5History);
            if (!last10Boll.isEmpty()) {
                List<String> upperBands = new ArrayList<>();
                List<String> middleBands = new ArrayList<>();
                List<String> lowerBands = new ArrayList<>();
                List<String> positions = new ArrayList<>();
                List<String> signals = new ArrayList<>();

                for (TechnicalIndicators.BollPeriod boll : last10Boll) {
                    upperBands.add(formatBollValue(boll.getUpperBand()));
                    middleBands.add(formatBollValue(boll.getMiddleBand()));
                    lowerBands.add(formatBollValue(boll.getLowerBand()));
                    positions.add(boll.getPricePosition() != null ? boll.getPricePosition() : "数据不足");
                    signals.add(boll.getSignal() != null ? boll.getSignal() : "信号不明确");
                }

                bollData.put("BOLL_5_UPPER", upperBands);
                bollData.put("BOLL_5_MIDDLE", middleBands);
                bollData.put("BOLL_5_LOWER", lowerBands);
                bollData.put("BOLL_5_POSITION", positions);
                bollData.put("BOLL_5_SIGNAL", signals);
            } else {
                // 如果没有BOLL数据，提供默认值
                bollData.put("BOLL_5_UPPER", List.of("数据不足"));
                bollData.put("BOLL_5_MIDDLE", List.of("数据不足"));
                bollData.put("BOLL_5_LOWER", List.of("数据不足"));
                bollData.put("BOLL_5_POSITION", List.of("数据不足"));
                bollData.put("BOLL_5_SIGNAL", List.of("信号不明确"));
            }
            data.put("BOLL", bollData);

        } catch (Exception e) {
            log.error("提取技术指标数据失败", e);
            // 返回默认数据结构
            return createDefaultIndicatorData();
        }

        return data;
    }

    /**
     * 获取最近10个数值
     */
    private List<Double> getLast10Values(List<Double> values) {
        if (values == null || values.isEmpty()) {
            // 返回30个默认值50.0
            List<Double> defaults = new ArrayList<>();
            for (int i = 0; i < HISTORY_DATA_COUNT; i++) {
                defaults.add(50.0);
            }
            return defaults;
        }

        int startIndex = Math.max(0, values.size() - HISTORY_DATA_COUNT);
        return values.subList(startIndex, values.size());
    }

    /**
     * 获取最近10个BOLL值
     */
    private List<TechnicalIndicators.BollPeriod> getLast10BollValues(List<TechnicalIndicators.BollPeriod> values) {
        if (values == null || values.isEmpty()) return new ArrayList<>();

        int startIndex = Math.max(0, values.size() - HISTORY_DATA_COUNT);
        return values.subList(startIndex, values.size());
    }

    /**
     * 格式化历史值为字符串列表
     */
    private List<String> formatHistoryValues(List<Double> values) {
        return values.stream()
                .map(value -> String.format("%.2f", value))
                .collect(Collectors.toList());
    }

    /**
     * 格式化BOLL值（支持8位小数）
     */
    private String formatBollValue(BigDecimal value) {
        if (value == null) return "数据不足";
        DecimalFormat df = new DecimalFormat("#,##0.00######");
        return df.format(value);
    }

    /**
     * 获取RSI最新信号
     */
    private String getRsiLatestSignal(Map<String, TechnicalIndicators> indicators, int period) {
        try {
            for (TechnicalIndicators indicator : indicators.values()) {
                if (indicator.getRsiData() != null) {
                    switch (period) {
                        case 5:
                            if (indicator.getRsiData().getRsi5Signal() != null) {
                                return indicator.getRsiData().getRsi5Signal();
                            }
                            break;
                        case 20:
                            if (indicator.getRsiData().getRsi20Signal() != null) {
                                return indicator.getRsiData().getRsi20Signal();
                            }
                            break;
                        case 30:
                            if (indicator.getRsiData().getRsi30Signal() != null) {
                                return indicator.getRsiData().getRsi30Signal();
                            }
                            break;
                    }
                }
            }
            return "数据不足";
        } catch (Exception e) {
            log.debug("获取RSI信号失败", e);
            return "信号不明确";
        }
    }

    /**
     * 创建默认指标数据结构
     */
    private Map<String, Object> createDefaultIndicatorData() {
        Map<String, Object> data = new HashMap<>();

        // 默认RSI数据
        Map<String, Object> rsiData = new HashMap<>();
        rsiData.put("RSI_5", Arrays.asList("50.00", "50.00", "50.00", "50.00", "50.00", "50.00", "50.00", "50.00", "50.00", "50.00"));
        rsiData.put("RSI_5_SIGNAL", "中性");
        rsiData.put("RSI_20", Arrays.asList("50.00", "50.00", "50.00", "50.00", "50.00", "50.00", "50.00", "50.00", "50.00", "50.00"));
        rsiData.put("RSI_20_SIGNAL", "中性");
        rsiData.put("RSI_30", Arrays.asList("50.00", "50.00", "50.00", "50.00", "50.00", "50.00", "50.00", "50.00", "50.00", "50.00"));
        rsiData.put("RSI_30_SIGNAL", "中性");
        data.put("RSI", rsiData);

        // 默认EMA数据
        Map<String, Object> emaData = new HashMap<>();
        emaData.put("EMA_5", Arrays.asList("50000.00", "50000.00", "50000.00", "50000.00", "50000.00", "50000.00", "50000.00", "50000.00", "50000.00", "50000.00"));
        emaData.put("EMA_5_RELATION", "价格相对均线位置");
        emaData.put("EMA_20", Arrays.asList("48000.00", "48000.00", "48000.00", "48000.00", "48000.00", "48000.00", "48000.00", "48000.00", "48000.00", "48000.00"));
        emaData.put("EMA_20_RELATION", "价格相对均线位置");
        emaData.put("EMA_30", Arrays.asList("46000.00", "46000.00", "46000.00", "46000.00", "46000.00", "46000.00", "46000.00", "46000.00", "46000.00", "46000.00"));
        emaData.put("EMA_30_RELATION", "价格相对均线位置");
        data.put("EMA", emaData);

        // 默认BOLL数据
        Map<String, Object> bollData = new HashMap<>();
        bollData.put("BOLL_5_UPPER", Arrays.asList("数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足"));
        bollData.put("BOLL_5_MIDDLE", Arrays.asList("数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足"));
        bollData.put("BOLL_5_LOWER", Arrays.asList("数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足"));
        bollData.put("BOLL_5_POSITION", Arrays.asList("数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足", "数据不足"));
        bollData.put("BOLL_5_SIGNAL", Arrays.asList("信号不明确", "信号不明确", "信号不明确", "信号不明确", "信号不明确", "信号不明确", "信号不明确", "信号不明确", "信号不明确", "信号不明确"));
        data.put("BOLL", bollData);

        return data;
    }

    /**
     * 格式化多时间周期汇总行
     */
    private String formatMultiTimeframeSummaryRow(String instId, String timeframe, TechnicalIndicators indicator) {
        try {
            String rsi5 = "50.00"; // 从indicator获取
            String ema20 = "50000.00"; // 从indicator获取
            String bollPosition = "中轨"; // 从indicator获取
            String overallSignal = "中性"; // 从indicator获取

            return String.format("| %s | %s | %s | %s | %s | %s |",
                    instId, getTimeframeName(timeframe), rsi5, ema20, bollPosition, overallSignal);

        } catch (Exception e) {
            log.debug("格式化多时间周期汇总行失败: {} {}", instId, timeframe, e);
            return "| - | - | - | - | - | - |";
        }
    }

    /**
     * 格式化多时间周期RSI行
     */
    private String formatMultiTimeframeRsiRow(String instId, TechnicalIndicators indicator) {
        try {
            String rsi5 = "50.00"; // 从indicator获取
            String rsi20 = "50.00"; // 从indicator获取
            String rsi30 = "50.00"; // 从indicator获取
            String signal = "中性"; // 从indicator获取

            return String.format("| %s | %s | %s | %s | %s |",
                    instId, rsi5, rsi20, rsi30, signal);

        } catch (Exception e) {
            log.debug("格式化多时间周期RSI行失败: {}", instId, e);
            return "| - | - | - | - | - |";
        }
    }

    /**
     * 格式化多时间周期EMA行
     */
    private String formatMultiTimeframeEmaRow(String instId, TechnicalIndicators indicator) {
        try {
            String ema5 = "50000.00"; // 从indicator获取
            String ema20 = "48000.00"; // 从indicator获取
            String ema30 = "46000.00"; // 从indicator获取
            String priceRelation = "价格位于均线上方"; // 从indicator获取

            return String.format("| %s | %s | %s | %s | %s |",
                    instId, ema5, ema20, ema30, priceRelation);

        } catch (Exception e) {
            log.debug("格式化多时间周期EMA行失败: {}", instId, e);
            return "| - | - | - | - | - |";
        }
    }

    /**
     * 获取时间周期中文名称
     */
    private String getTimeframeName(String timeframe) {
        return switch (timeframe.toLowerCase()) {
            case "4h" -> "4小时";
            case "1h" -> "1小时";
            case "5m" -> "5分钟";
            default -> timeframe;
        };
    }

    /**
     * 格式化指标数值
     */
    private String formatIndicatorValue(Object value) {
        if (value == null) return "-";
        if (value instanceof BigDecimal decimal) {
            DecimalFormat df = new DecimalFormat("#,##0.00######");
            df.setRoundingMode(RoundingMode.HALF_UP);
            return df.format(decimal);
        }
        return value.toString();
    }

    /**
     * 格式化指标信号
     */
    private String formatIndicatorSignal(Object signal) {
        if (signal == null) return "-";
        return signal.toString();
    }

    /**
     * 格式化价格关系
     */
    private String formatPriceRelation(Object relation) {
        if (relation == null) return "-";
        return relation.toString();
    }

    /**
     * 格式化价格位置
     */
    private String formatPricePosition(Object position) {
        if (position == null) return "-";
        return position.toString();
    }

    /**
     * 将RSI历史数据格式化为表格行
     */
    private String formatRsiHistoryAsTableRow(String instId, TechnicalIndicators indicator) {
        try {
            String rsi5History = formatHistorySeries(indicator.getRsi5History());
            String rsi20History = formatHistorySeries(indicator.getRsi20History());
            String rsi30History = formatHistorySeries(indicator.getRsi30History());

            String signalStatus = getRsiSignalStatus(indicator);

            return String.format("| %s | %s | %s | %s | %s |",
                    instId, rsi5History, rsi20History, rsi30History, signalStatus);

        } catch (Exception e) {
            log.debug("格式化RSI历史数据行失败: {}", instId, e);
            return "| - | - | - | - | - |";
        }
    }

    /**
     * 将EMA历史数据格式化为表格行
     */
    private String formatEmaHistoryAsTableRow(String instId, TechnicalIndicators indicator) {
        try {
            String ema5History = formatHistorySeries(indicator.getEma5History());
            String ema20History = formatHistorySeries(indicator.getEma20History());
            String ema30History = formatHistorySeries(indicator.getEma30History());

            String trendStatus = getEmaTrendStatus(indicator);

            return String.format("| %s | %s | %s | %s | %s |",
                    instId, ema5History, ema20History, ema30History, trendStatus);

        } catch (Exception e) {
            log.debug("格式化EMA历史数据行失败: {}", instId, e);
            return "| - | - | - | - | - |";
        }
    }

    /**
     * 将BOLL历史数据格式化为表格行
     */
    private String formatBollHistoryAsTableRow(String instId, TechnicalIndicators indicator) {
        try {
            String boll5Position = getLatestBollPosition(indicator);
            String boll5Signal = getLatestBollSignal(indicator);
            String overallSignal = indicator.getBollData() != null ?
                    indicator.getBollData().getOverallSignal() : "-";

            return String.format("| %s | %s | %s | %s |",
                    instId, boll5Position, boll5Signal, overallSignal);

        } catch (Exception e) {
            log.debug("格式化BOLL历史数据行失败: {}", instId, e);
            return "| - | - | - | - |";
        }
    }

    /**
     * 格式化历史序列为简洁显示
     */
    private String formatHistorySeries(List<Double> history) {
        if (history == null || history.isEmpty()) {
            return "数据不足";
        }

        if (history.size() <= 3) {
            return history.stream()
                    .map(v -> String.format("%.1f", v))
                    .collect(Collectors.joining(", "));
        }

        // 显示前3个值、省略号、后3个值
        List<String> formatted = history.stream()
                .map(v -> String.format("%.1f", v))
                .toList();

        List<String> result = new ArrayList<>(formatted.subList(0, 3));
        result.add("...");
        result.addAll(formatted.subList(formatted.size() - 3, formatted.size()));
        return String.join(", ", result);
    }

    /**
     * 获取RSI信号状态
     */
    private String getRsiSignalStatus(TechnicalIndicators indicator) {
        try {
            if (indicator.getRsiData() == null) return "数据不足";

            List<Double> rsi5History = indicator.getRsi5History();
            if (rsi5History == null || rsi5History.size() < 2) return "数据不足";

            double current = rsi5History.get(rsi5History.size() - 1);
            double previous = rsi5History.get(rsi5History.size() - 2);

            if (current > 70) return "超买区域";
            if (current < 30) return "超卖区域";
            if (current > previous && current > 50) return "上涨趋势";
            if (current < previous && current < 50) return "下跌趋势";
            return "震荡整理";

        } catch (Exception e) {
            return "计算错误";
        }
    }

    /**
     * 获取EMA趋势状态
     */
    private String getEmaTrendStatus(TechnicalIndicators indicator) {
        try {
            if (indicator.getEma5History() == null || indicator.getEma20History() == null ||
                    indicator.getEma5History().size() < 2 || indicator.getEma20History().size() < 2) {
                return "数据不足";
            }

            double ema5Current = indicator.getEma5History().get(indicator.getEma5History().size() - 1);
            double ema5Previous = indicator.getEma5History().get(indicator.getEma5History().size() - 2);
            double ema20Current = indicator.getEma20History().get(indicator.getEma20History().size() - 1);

            if (ema5Current > ema20Current && ema5Current > ema5Previous) {
                return "多头排列";
            } else if (ema5Current < ema20Current && ema5Current < ema5Previous) {
                return "空头排列";
            } else if (ema5Current > ema5Previous) {
                return "上涨整理";
            } else if (ema5Current < ema5Previous) {
                return "下跌整理";
            } else {
                return "横盘整理";
            }

        } catch (Exception e) {
            return "计算错误";
        }
    }

    /**
     * 获取最新BOLL位置
     */
    private String getLatestBollPosition(TechnicalIndicators indicator) {
        try {
            if (indicator.getBollData() == null || indicator.getBollData().getBoll5() == null) {
                return "数据不足";
            }

            TechnicalIndicators.BollPeriod boll5 = indicator.getBollData().getBoll5();
            if (boll5.getPricePosition() != null) {
                return boll5.getPricePosition();
            }

            return "未知";

        } catch (Exception e) {
            return "计算错误";
        }
    }

    /**
     * 获取最新BOLL信号
     */
    private String getLatestBollSignal(TechnicalIndicators indicator) {
        try {
            if (indicator.getBollData() == null || indicator.getBollData().getBoll5() == null) {
                return "数据不足";
            }

            TechnicalIndicators.BollPeriod boll5 = indicator.getBollData().getBoll5();
            if (boll5.getSignal() != null) {
                return boll5.getSignal();
            }

            return "无信号";

        } catch (Exception e) {
            return "计算错误";
        }
    }

    /**
     * 格式化多时间周期技术指标为用户要求的表格格式
     * 每个时间周期生成独立的表格
     */
    private String formatMultiTimeframeIndicatorsAsUserTable(Map<String, Map<String, TechnicalIndicators>> multiTimeframeIndicators,
                                                             String[] timeframes) {
        if (multiTimeframeIndicators == null || multiTimeframeIndicators.isEmpty()) {
            return "无法获取技术指标数据";
        }

        StringBuilder content = new StringBuilder();
        content.append("### 技术指标分析\n\n");

        // 为每个时间周期创建独立的表格
        for (String timeframe : timeframes) {
            Map<String, TechnicalIndicators> timeframeIndicators = multiTimeframeIndicators.get(timeframe);
            if (timeframeIndicators == null || timeframeIndicators.isEmpty()) {
                continue;
            }

            String timeframeName = getTimeframeDisplayName(timeframe);
            content.append("#### ").append(timeframeName).append("周期技术指标表格\n\n");

            // 遍历该时间周期下的所有持仓
            for (Map.Entry<String, TechnicalIndicators> entry : timeframeIndicators.entrySet()) {
                String instId = entry.getKey();
                TechnicalIndicators indicators = entry.getValue();
                if (null == indicators) {
                    continue;
                }

                // 为每个持仓生成技术指标表格
                content.append("##### ").append(instId).append("\n\n");

                String tableContent = buildUserTableForTimeframe(indicators, timeframe);
                content.append(tableContent);

                content.append("\n");
            }

            content.append("\n---\n\n");
        }

        return content.toString();
    }

    /**
     * 为单个时间周期构建用户要求的表格
     */
    private String buildUserTableForTimeframe(TechnicalIndicators indicators, String timeframeName) {
        StringBuilder table = new StringBuilder();

        List<String> timestamps = indicators.getOhlcHistory().subList(0, HISTORY_DATA_COUNT)
                .stream()
                .map(v -> Objects.requireNonNull(DateTimeUtils.fromTimestamp(v.getTimestamp()))
                        .format(DateTimeFormatter.ofPattern("MM/dd HH:mm")))
                .toList();
        // 构建表头
        table.append("| ").append(timeframeName).append(" | ");
        for (String timestamp : timestamps) {
            table.append(timestamp).append(" | ");
        }
        table.append("\n");
        // 构建分隔线
        table.append("|-----------| ");
        table.append("------ | ".repeat(timestamps.size()));
        table.append("\n");

        // 构建时间戳行（仅在 function calling 时返回）
        table.append("| 时间戳 | ");
        for (int i = 0; i < timestamps.size(); i++) {
            TechnicalIndicators.OhlcData ohlc = indicators.getOhlcHistory().get(i);
            table.append(ohlc.getTimestamp()).append(" | ");
        }
        table.append("\n");

        // 构建分隔线
//        table.append("|-----------| ");
//        table.append("------ | ".repeat(timestamps.size()));
//        table.append("\n");

        // 添加OHLC数据行
        addOhlcRowsToTable(table, indicators, timestamps.size());

        // 添加EMA指标行
        addEmaRowsToTable(table, indicators, timestamps.size());

        // 添加RSI指标行
        addRsiRowsToTable(table, indicators, timestamps.size());

        // 添加BOLL指标行
        addBollRowsToTable(table, indicators, timestamps.size());

        // 添加ADX指标行
        addAdxRowsToTable(table, indicators, timestamps.size());

        // 添加MACD指标行
        addMacdRowsToTable(table, indicators, timestamps.size());

        return table.toString();
    }

    /**
     * 添加EMA指标行到表格
     */
    private void addEmaRowsToTable(StringBuilder table, TechnicalIndicators indicators, int columnCount) {
        // EMA-5
        table.append("| EMA(5) | ");
        List<Double> ema5History = indicators.getEma5History();
        for (int i = 0; i < columnCount; i++) {
            String value = getHistoryValueAt(ema5History, i, columnCount);
            table.append(value).append(" | ");
        }
        table.append("\n");

        // EMA-20
        table.append("| EMA(20) | ");
        List<Double> ema20History = indicators.getEma20History();
        for (int i = 0; i < columnCount; i++) {
            String value = getHistoryValueAt(ema20History, i, columnCount);
            table.append(value).append(" | ");
        }
        table.append("\n");

        // EMA-30
        table.append("| EMA(30) | ");
        List<Double> ema30History = indicators.getEma30History();
        for (int i = 0; i < columnCount; i++) {
            String value = getHistoryValueAt(ema30History, i, columnCount);
            table.append(value).append(" | ");
        }
        table.append("\n");
    }

    /**
     * 添加RSI指标行到表格
     */
    private void addRsiRowsToTable(StringBuilder table, TechnicalIndicators indicators, int columnCount) {
        // RSI-5
        table.append("| RSI(5) | ");
        List<Double> rsi5History = indicators.getRsi5History();
        for (int i = 0; i < columnCount; i++) {
            String value = getHistoryValueAt(rsi5History, i, columnCount);
            table.append(value).append(" | ");
        }
        table.append("\n");

        // RSI-20
        table.append("| RSI(20) | ");
        List<Double> rsi20History = indicators.getRsi20History();
        for (int i = 0; i < columnCount; i++) {
            String value = getHistoryValueAt(rsi20History, i, columnCount);
            table.append(value).append(" | ");
        }
        table.append("\n");

        // RSI-30
        table.append("| RSI(30) | ");
        List<Double> rsi30History = indicators.getRsi30History();
        for (int i = 0; i < columnCount; i++) {
            String value = getHistoryValueAt(rsi30History, i, columnCount);
            table.append(value).append(" | ");
        }
        table.append("\n");
    }

    /**
     * 添加BOLL指标行到表格
     */
    private void addBollRowsToTable(StringBuilder table, TechnicalIndicators indicators, int columnCount) {
        // 支持所有BOLL周期：5, 15, 20, 25
//        int[] bollPeriods = {5, 15, 20, 25};
        List<Integer> bollPeriods = getIntListParameter("bollPeriods", List.of(20));
        for (int period : bollPeriods) {
            List<TechnicalIndicators.BollPeriod> bollHistory = getBollHistoryByPeriod(indicators, period);
            // 如果BOLL数据为空，添加空行
            if (bollHistory == null || bollHistory.isEmpty()) {
                addBollEmptyRows(table, period, columnCount);
                continue;
            }
            // 添加BOLL数据行
            addBollDataRows(table, period, bollHistory, columnCount);
        }
    }

    private List<TechnicalIndicators.BollPeriod> getBollHistoryByPeriod(TechnicalIndicators indicators, int period) {
        return switch (period) {
            case 5 -> indicators.getBoll5History();
            case 15 -> indicators.getBoll15History();
            case 20 -> indicators.getBoll20History();
            case 25 -> indicators.getBoll25History();
            default -> new ArrayList<>();
        };
    }

    private void addBollEmptyRows(StringBuilder table, int period, int columnCount) {
        String[] bollTypes = {"UB", "MB", "LB"};
        for (String bollType : bollTypes) {
            table.append("| BOLL(").append(period).append(")-").append(bollType).append(" | ");
            table.append("- | ".repeat(Math.max(0, columnCount)));
            table.append("\n");
        }
    }

    private void addBollDataRows(StringBuilder table, int period, List<TechnicalIndicators.BollPeriod> bollHistory, int columnCount) {
        String[] bollTypes = {"UB", "MB", "LB"};

        for (String bollType : bollTypes) {
            table.append("| BOLL(").append(period).append(")-").append(bollType).append(" | ");
            for (int i = 0; i < columnCount; i++) {
                TechnicalIndicators.BollPeriod boll = getBollPeriodAt(bollHistory, i, columnCount);
                String value = getBollValue(boll, bollType);
                table.append(value).append(" | ");
            }
            table.append("\n");
        }
    }

    private String getBollValue(TechnicalIndicators.BollPeriod boll, String bollType) {
        if (boll == null) {
            return "-";
        }

        return switch (bollType) {
            case "UB" -> boll.getUpperBand() != null ? formatPrice(boll.getUpperBand()) : "-";
            case "MB" -> boll.getMiddleBand() != null ? formatPrice(boll.getMiddleBand()) : "-";
            case "LB" -> boll.getLowerBand() != null ? formatPrice(boll.getLowerBand()) : "-";
            default -> "-";
        };
    }

    /**
     * 从OHLC历史数据中获取时间序列
     */
    private List<String> getTimeSeriesFromOhlcData(List<TechnicalIndicators.OhlcData> ohlcHistory, int count) {
        List<String> timestamps = new ArrayList<>();
        if (ohlcHistory == null || ohlcHistory.isEmpty()) {
            return timestamps;
        }

        // 取最近count条数据
        int startIndex = Math.max(0, ohlcHistory.size() - count);
        for (int i = ohlcHistory.size() - 1; i >= startIndex; i--) {
            TechnicalIndicators.OhlcData ohlc = ohlcHistory.get(i);
            if (ohlc != null && ohlc.getTimestamp() != null) {
                timestamps.add(formatTimestampForTable(ohlc.getTimestamp()));
            }
        }

        return timestamps;
    }

    /**
     * 为表格格式化时间戳
     */
    private String formatTimestampForTable(Long timestamp) {
        try {
            if (timestamp == null) return "";
            LocalDateTime dateTime = LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.ofHours(8));
            return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception e) {
            log.debug("格式化时间戳失败: {}", timestamp, e);
            return "";
        }
    }

    /**
     * 添加ADX指标行到表格
     */
    private void addAdxRowsToTable(StringBuilder table, TechnicalIndicators indicators, int columnCount) {
        if (indicators.getAdxData() == null) {
            return;
        }

        TechnicalIndicators.AdxData adxData = indicators.getAdxData();
        List<Double> adx14History = indicators.getAdx14History();

        // ADX-14
        table.append("| ADX(14) | ");
        for (int i = 0; i < columnCount; i++) {
            // 使用reverseIndex=true，与OHLC保持一致的显示顺序（降序，最新的在左）
            String value = getHistoryValueAt(adx14History, i, columnCount, true);
            table.append(value).append(" | ");
        }
        table.append("\n");
    }

    /**
     * 添加MACD指标行到表格
     */
    private void addMacdRowsToTable(StringBuilder table, TechnicalIndicators indicators, int columnCount) {
        if (indicators.getMacdData() == null) {
            return;
        }

        TechnicalIndicators.MacdData macdData = indicators.getMacdData();
        List<Double> macdHistory = indicators.getMacdHistory();
        List<Double> deaHistory = indicators.getDeaHistory();
        List<Double> histogramHistory = indicators.getHistogramHistory();

        // MACD (DIF)
        table.append("| MACD(12,26,9)-DIF | ");
        for (int i = 0; i < columnCount; i++) {
            String value = getHistoryValueAt(macdHistory, i, columnCount, true);
            table.append(value).append(" | ");
        }
        table.append("\n");

        // DEA (信号线)
        table.append("| MACD(12,26,9)-DEA | ");
        for (int i = 0; i < columnCount; i++) {
            String value = getHistoryValueAt(deaHistory, i, columnCount, true);
            table.append(value).append(" | ");
        }
        table.append("\n");

        // MACD柱状图
        table.append("| MACD(12,26,9)-BAR | ");
        for (int i = 0; i < columnCount; i++) {
            String value = getHistoryValueAt(histogramHistory, i, columnCount, true);
            table.append(value).append(" | ");
        }
        table.append("\n");
    }

    /**
     * 获取时间周期的显示名称
     */
    private String getTimeframeDisplayName(String timeframe) {
        return switch (timeframe) {
            case "4H", "4h" -> "4小时";
            case "1H", "1h" -> "1小时";
            case "5m" -> "5分钟";
            default -> timeframe;
        };
    }

    /**
     * 获取历史数据中指定位置的值（默认反转索引）
     */
    private String getHistoryValueAt(List<Double> history, int index, int totalCount) {
        return getHistoryValueAt(history, index, totalCount, true);
    }

    /**
     * 获取历史数据中指定位置的值
     *
     * @param history      历史数据列表
     * @param index        请求的索引
     * @param totalCount   总列数
     * @param reverseIndex 是否反转索引（true=从后往前，false=从前往后）
     */
    private String getHistoryValueAt(List<Double> history, int index, int totalCount, boolean reverseIndex) {
        if (history == null || history.isEmpty()) {
            return "";
        }

        int actualIndex;
        if (reverseIndex) {
            // 反转索引（从最新开始）
            actualIndex = history.size() - 1 - index;
        } else {
            // 不反转索引（从最旧开始，与OHLC保持一致）
            actualIndex = index;
        }

        if (actualIndex >= 0 && actualIndex < history.size()) {
            Double value = history.get(actualIndex);
            if (value != null) {
                return String.format("%.4f", value);
            } else {
                return ""; // null值显示为空
            }
        }

        return "";
    }

    /**
     * 获取BOLL历史数据中指定位置的值
     */
    private TechnicalIndicators.BollPeriod getBollPeriodAt(List<TechnicalIndicators.BollPeriod> bollHistory, int index, int totalCount) {
        if (bollHistory == null || bollHistory.isEmpty()) {
            return null;
        }

        // 计算实际索引（从最新开始）
        int actualIndex = bollHistory.size() - 1 - index;
        if (actualIndex >= 0 && actualIndex < bollHistory.size()) {
            return bollHistory.get(actualIndex);
        }

        return null;
    }

    /**
     * 等待OHLC数据可用
     */
    private List<String> waitForOhlcData(TechnicalIndicators indicators, String timeframeName, int requiredCount) {
        log.debug("开始等待{}时间周期的OHLC数据，需要{}条数据", timeframeName, requiredCount);

        long maxWaitTime = 3000; // 最多等待3秒
        long startTime = System.currentTimeMillis();
        int retryCount = 0;
        int maxRetries = 6; // 最多重试6次，每次500ms

        while (System.currentTimeMillis() - startTime < maxWaitTime && retryCount < maxRetries) {
            // 检查OHLC数据是否可用
            List<String> timestamps = getTimeSeriesFromOhlcData(indicators.getOhlcHistory(), requiredCount);

            if (!timestamps.isEmpty()) {
                log.debug("{}时间周期的OHLC数据已获取到，共{}条数据", timeframeName, timestamps.size());
                return timestamps;
            }

            retryCount++;
            log.debug("第{}次等待{}时间周期的OHLC数据", retryCount, timeframeName);

            try {
                //noinspection BusyWait
                Thread.sleep(500); // 等待500ms再重试
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.warn("{}时间周期OHLC数据等待超时，已等待{}ms，重试{}次",
                timeframeName, System.currentTimeMillis() - startTime, retryCount);
        return new ArrayList<>(); // 返回空列表表示等待失败
    }

    /**
     * 计算最优数据获取量，确保技术指标计算准确性
     */
    private int calculateOptimalDataCount(List<Integer> rsiPeriods, List<Integer> emaPeriods, List<Integer> bollPeriods,
                                          List<Integer> adxPeriods, List<Integer> macdPeriods, int defaultCount) {
        // 找出各指标的最大周期
        int maxRsiPeriod = rsiPeriods.isEmpty() ? 0 : rsiPeriods.stream().max(Integer::compareTo).orElse(0);
        int maxEmaPeriod = emaPeriods.isEmpty() ? 0 : emaPeriods.stream().max(Integer::compareTo).orElse(0);
        int maxBollPeriod = bollPeriods.isEmpty() ? 0 : bollPeriods.stream().max(Integer::compareTo).orElse(0);
        int maxAdxPeriod = adxPeriods.isEmpty() ? 0 : adxPeriods.stream().max(Integer::compareTo).orElse(0);

        // MACD需要获取快线、慢线、信号线周期
        int macdFastPeriod = 0;
        int macdSlowPeriod = 0;
        int macdSignalPeriod = 0;
        if (!macdPeriods.isEmpty() && macdPeriods.size() >= 3) {
            macdFastPeriod = macdPeriods.get(0);   // 12
            macdSlowPeriod = macdPeriods.get(1);   // 26
            macdSignalPeriod = macdPeriods.get(2); // 9
        } else if (!macdPeriods.isEmpty()) {
            macdSlowPeriod = macdPeriods.get(0);
        }

        // 计算各指标的实际数据需求
        // RSI需要period+1个数据点（用于计算第一个变化值）
        int rsiRequired = maxRsiPeriod > 0 ? maxRsiPeriod + 1 : 0;
        // EMA需要period个数据点
        // BOLL需要period个数据点
        // ADX需要period*2个数据点（需要计算TR、DM、DI、DX等多个中间值）
        int adxRequired = maxAdxPeriod > 0 ? maxAdxPeriod * 2 : 0;
        // MACD需要：(慢线周期-1) + 信号线周期 + 要显示的有效值(30)
        // 第一个有效MACD值出现在索引：(慢线周期-1) + (信号线周期-1)
        // 要得到30个有效值，总共需要：(慢线周期-1) + (信号线周期-1) + 30 + 1
        int macdRequired = 0;
        if (macdSlowPeriod > 0 && macdSignalPeriod > 0) {
            int firstMacdIndex = (macdSlowPeriod - 1) + (macdSignalPeriod - 1);
            macdRequired = firstMacdIndex + HISTORY_DATA_COUNT;
        }

        // 取最大需求，并添加缓冲区确保计算稳定性
        int maxRequired = Math.max(Math.max(Math.max(Math.max(rsiRequired, maxEmaPeriod), maxBollPeriod), adxRequired), macdRequired);
        int bufferedCount = maxRequired + defaultCount; // 添加10个数据点的缓冲区

        // 确保不小于默认值，同时设置合理的上限
        int optimalCount = Math.max(defaultCount, bufferedCount);

        // 设置最大获取量限制，防止过度请求
        final int MAX_DATA_COUNT = 100;
        optimalCount = Math.min(optimalCount, MAX_DATA_COUNT);

        log.debug("数据量计算：RSI最大周期{}(需要{}), EMA最大周期{}(需要{}), BOLL最大周期{}(需要{}), ADX最大周期{}(需要{}), MACD({},{},{})需要{}(首个MACD索引{}, 总共{}), " +
                        "最终获取{}条原始数据",
                maxRsiPeriod, rsiRequired, maxEmaPeriod, maxEmaPeriod, maxBollPeriod, maxBollPeriod,
                maxAdxPeriod, adxRequired, macdFastPeriod, macdSlowPeriod, macdSignalPeriod,
                macdRequired > 0 ? ((macdSlowPeriod - 1) + (macdSignalPeriod - 1)) : 0,
                macdRequired > 0 ? (macdSlowPeriod - 1) + (macdSignalPeriod - 1) : 0,
                macdRequired, optimalCount);

        return optimalCount;
    }

    /**
     * 生成默认时间戳，用于在OHLC数据不可用时仍然显示表格
     */
    private List<String> generateDefaultTimestamps(int count) {
        List<String> timestamps = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(8));

        // 生成由近到远的时间戳（从当前时间开始，往前推算）
        for (int i = 0; i < count; i++) {
            LocalDateTime time = now.minusHours(i); // 每个时间戳间隔1小时
            timestamps.add(time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        }

        log.debug("生成了{}个默认时间戳用于表格显示，顺序由近到远", count);
        return timestamps;
    }

    /**
     * 添加OHLC数据行到表格
     */
    private void addOhlcRowsToTable(StringBuilder table, TechnicalIndicators indicators, int columnCount) {
        List<TechnicalIndicators.OhlcData> ohlcHistory = indicators.getOhlcHistory();
        if (ohlcHistory == null || ohlcHistory.isEmpty()) {
            log.debug("OHLC历史数据为空，但仍显示OHLC行（使用占位符）");
            ohlcHistory = new ArrayList<>(); // 使用空列表，getOhlcValueAt会处理占位符
        }

        log.debug("添加OHLC数据到表格 - OHLC历史数据条数: {}, 表格列数: {}",
                ohlcHistory.size(), columnCount);

        // 添加OHLC各行数据
        String[] ohlcTypes = {"open", "high", "low", "close", "volume"};
        for (String ohlcType : ohlcTypes) {
            table.append("| ").append(ohlcType).append(" | ");
            for (int i = 0; i < columnCount; i++) {
                // 防止数组越界
                if (i < ohlcHistory.size()) {
                    TechnicalIndicators.OhlcData ohlcData = ohlcHistory.get(i);
                    if (ohlcType.equals("volume")) {
                        log.debug("OHLC数据[{}]: timestamp={}, open={}, high={}, low={}, close={}, volume={}",
                                i, ohlcData.getTimestamp(), ohlcData.getOpen(),
                                ohlcData.getHigh(), ohlcData.getLow(),
                                ohlcData.getClose(), ohlcData.getVolume());
                    }
                    String value = getOhlcValueAt(ohlcData, ohlcType);
                    table.append(value).append(" | ");
                } else {
                    table.append("- | ");
                }
            }
            table.append("\n");
        }
        log.debug("已添加OHLC数据到表格，共{}行", ohlcTypes.length);
    }

    /**
     * 获取OHLC值
     */
    private String getOhlcValueAt(TechnicalIndicators.OhlcData ohlcData, String ohlcType) {
        if (null == ohlcData) {
            return "-";
        }
//
//        // 从最新数据开始显示
//        int dataIndex = Math.max(0, ohlcHistory.size() - columnCount + index);
//        if (dataIndex >= ohlcHistory.size()) {
//            return "-";
//        }
//        if (ohlcData == null) {
//            return "-";
//        }

        return switch (ohlcType) {
            case "open" -> formatPrice(ohlcData.getOpen());
            case "high" -> formatPrice(ohlcData.getHigh());
            case "low" -> formatPrice(ohlcData.getLow());
            case "close" -> formatPrice(ohlcData.getClose());
            case "volume" -> ohlcData.getVolume() != null ? ohlcData.getVolume().toString() : "-";
            default -> "-";
        };
    }

    /**
     * 格式化价格（支持8位小数）
     */
    private String formatPrice(BigDecimal value) {
        if (value == null) return "";
        DecimalFormat df = new DecimalFormat("#,##0.00######");
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df.format(value);
    }

    // ==================== 多轮会话Query支持 ====================

    /**
     * 为多轮会话的Query操作处理技术指标
     * <p>
     * 此方法专门用于多轮会话中的工具调用(Query)，返回与第1次prompt相同格式的技术指标表格。
     * </p>
     * <p>
     * 处理流程：
     * 1. 获取K线数据（基于Query参数）
     * 2. 计算技术指标（RSI、EMA、BOLL）
     * 3. 格式化为用户表格（USER_TABLE格式）
     * 4. 返回格式化结果
     * </p>
     *
     * @param instId    合约代码（如：BTC-USDT-SWAP）
     * @param timeframe 时间周期（如：1H、4H、1D）
     * @param limit     数据条数
     * @param apiKeyId  API密钥ID
     * @return 格式化的技术指标表格字符串
     */
    public String processQuery(String instId, String timeframe, int limit, Long apiKeyId) {
        // 调用带context的方法，context为null时不进行去重检查
        return processQuery(instId, timeframe, limit, apiKeyId, null);
    }

    /**
     * 处理多轮会话中的QUERY工具调用（支持去重）
     * <p>
     * 此方法用于处理多轮会话中的QUERY工具调用，返回与第1次prompt相同格式的技术指标表格。
     * 支持通过PromptContext进行全局去重，防止相同的instId#timeframe重复返回技术指标。
     * </p>
     * <p>
     * 处理流程：
     * 1. 去重检查（如果context不为null）
     * 2. 获取K线数据（基于Query参数）
     * 3. 计算技术指标（RSI、EMA、BOLL）
     * 4. 格式化为用户表格（USER_TABLE格式）
     * 5. 返回格式化结果
     * </p>
     *
     * @param instId    合约代码（如：BTC-USDT-SWAP）
     * @param timeframe 时间周期（如：1H、4H、1D）
     * @param limit     数据条数
     * @param apiKeyId  API密钥ID
     * @param context   Prompt上下文（用于去重检查，可为null）
     * @return 格式化的技术指标表格字符串，如果已处理过则返回空字符串
     */
    public String processQuery(String instId, String timeframe, int limit, Long apiKeyId, PromptContext context) {
        try {
            log.debug("开始处理技术指标查询 - instId: {}, timeframe: {}, limit: {}", instId, timeframe, limit);

            // 去重检查：如果context不为null，检查是否已处理过该instId#timeframe
            if (context != null && !context.checkAndMarkProcessed(instId, timeframe)) {
                log.debug("技术指标已返回过，跳过 - instId: {}, timeframe: {}", instId, timeframe);
                return "";  // 静默跳过，返回空字符串
            }

            // 参数校验
            if (instId == null || instId.trim().isEmpty()) {
                return "错误：合约代码不能为空";
            }
            if (timeframe == null || timeframe.trim().isEmpty()) {
                return "错误：时间周期不能为空";
            }

            // 计算实际需要的数据量（考虑技术指标计算需求）
            int dataCount = Math.max(limit, 10);
            List<Integer> rsiPeriods = getIntListParameter("rsiPeriods", Arrays.asList(5, 20, 30));
            List<Integer> emaPeriods = getIntListParameter("emaPeriods", Arrays.asList(5, 20, 30));
            List<Integer> bollPeriods = getIntListParameter("bollPeriods", List.of(20));
            List<Integer> adxPeriods = getIntListParameter("adxPeriods", List.of(14));
            List<Integer> macdPeriods = getIntListParameter("macdPeriods", Arrays.asList(12, 26, 9));
            int requiredDataCount = calculateOptimalDataCount(rsiPeriods, emaPeriods, bollPeriods, adxPeriods, macdPeriods, dataCount);

            log.debug("Query参数优化：原始limit={}, 实际获取={}", limit, requiredDataCount);

            ApiKey apiKey = null == apiKeyId ? null : apiKeyService.getDecryptedKey(apiKeyId);

            // 使用通用CEX方法获取K线数据
            var candles = unifiedCexApiService.getMarketCandles(apiKey, instId, timeframe, requiredDataCount);
            if (candles == null || candles.isEmpty()) {
                return String.format("错误:无法获取%s合约%s周期的K线数据", instId, timeframe);
            }

            // 转换为OhlcItem列表
            List<IndicatorCalculationRequest.OhlcItem> ohlcItems = candles.stream()
                    .map(c -> IndicatorCalculationRequest.OhlcItem.builder()
                            .timestamp(c.getTimestamp())
                            .open(c.getOpen())
                            .high(c.getHigh())
                            .low(c.getLow())
                            .close(c.getClose())
                            .volume(c.getVolume())
                            .build())
                    .sorted(Comparator.comparing(IndicatorCalculationRequest.OhlcItem::getTimestamp))
                    .collect(Collectors.toList());

            // 构建指标计算请求
            List<IndicatorCalculationRequest.IndicatorConfig> indicators = new ArrayList<>();

            // 添加RSI指标
            if (!rsiPeriods.isEmpty()) {
                indicators.add(IndicatorCalculationRequest.IndicatorConfig.builder()
                        .name("RSI")
                        .periods(rsiPeriods)
                        .build());
            }

            // 添加EMA指标
            if (!emaPeriods.isEmpty()) {
                indicators.add(IndicatorCalculationRequest.IndicatorConfig.builder()
                        .name("EMA")
                        .periods(emaPeriods)
                        .build());
            }

            // 添加BOLL指标
            if (!bollPeriods.isEmpty()) {
                indicators.add(IndicatorCalculationRequest.IndicatorConfig.builder()
                        .name("BOLL")
                        .periods(bollPeriods)
                        .build());
            }

            // 添加ADX指标
            if (!adxPeriods.isEmpty()) {
                indicators.add(IndicatorCalculationRequest.IndicatorConfig.builder()
                        .name("ADX")
                        .periods(adxPeriods)
                        .build());
            }

            // 添加MACD指标
            if (!macdPeriods.isEmpty() && macdPeriods.size() >= 3) {
                indicators.add(IndicatorCalculationRequest.IndicatorConfig.builder()
                        .name("MACD")
                        .periods(macdPeriods)
                        .build());
            }

            // 构建请求
            IndicatorCalculationRequest request = IndicatorCalculationRequest.builder()
                    .ohlcData(ohlcItems)
                    .indicators(indicators)
                    .build();

            // 计算技术指标
            IndicatorCalculationResponse response = commonTechnicalIndicatorService.calculate(request);

            // 转换结果
            TechnicalIndicators technicalIndicators = convertResponseToTechnicalIndicators(
                    instId, timeframe, candles, response, rsiPeriods, emaPeriods, bollPeriods, adxPeriods, macdPeriods);

            if (technicalIndicators == null) {
                return String.format("错误：计算%s合约%s周期技术指标失败", instId, timeframe);
            }

            // 格式化为用户表格
            String timeframeName = getTimeframeDisplayName(timeframe);
            String tableContent = buildUserTableForTimeframe(technicalIndicators, timeframeName);

            // 构建完整响应
            StringBuilder result = new StringBuilder();
            result.append("### ").append(instId).append("-").append(timeframeName).append("技术指标表格\n\n");
            result.append(tableContent);

            log.debug("多轮会话Query处理成功 - instId: {}, timeframe: {}, 结果长度: {}",
                    instId, timeframe, result.length());

            return result.toString();

        } catch (Exception e) {
            log.error("处理多轮会话Query失败 - instId: {}, timeframe: {}",
                    instId, timeframe, e);
            return String.format("错误：处理Query失败 - %s", e.getMessage());
        }
    }
}
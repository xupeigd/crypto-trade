package com.crypto.trade.service.conversation;

import com.crypto.trade.service.prompt.processor.TechnicalIndicatorProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * KLineQueryExecutor
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class KLineQueryExecutor
        implements ToolExecutor {

//    @Autowired
//    CommonTechnicalIndicatorService commonTechnicalIndicatorService;
    /**
     * 技术指标处理器
     * 用于生成与第1次prompt相同格式的技术指标表格
     */
    @Autowired
    TechnicalIndicatorProcessor technicalIndicatorProcessor;

    @Override
    public ToolExecutionResult execute(ToolParameters parameters, Long apiKeyId) {
        long startTime = System.currentTimeMillis();

        try {
            KLineParameters klineParams = (KLineParameters) parameters;

            // 参数验证
            if (!validateParameters(klineParams)) {
                return ToolExecutionResult.failure("K线查询参数验证失败", getToolName());
            }

            // 调用TechnicalIndicatorProcessor处理Query
            // 返回与第1次prompt相同格式的技术指标表格
            String technicalIndicatorTable = technicalIndicatorProcessor.processQuery(klineParams.getInstId(),
                    klineParams.getTimeframe(), klineParams.getLimit(), apiKeyId);

            // 检查是否处理成功
            if (!StringUtils.hasText(technicalIndicatorTable)) {
                return ToolExecutionResult.failure("技术指标计算失败", getToolName());
            }

            // 检查是否为错误消息
            if (technicalIndicatorTable.startsWith("错误：")) {
                return ToolExecutionResult.failure(technicalIndicatorTable, getToolName());
            }

            long processingTime = System.currentTimeMillis() - startTime;

            log.info("多轮会话Query处理成功 - instId: {}, timeframe: {}, 耗时: {}ms",
                    klineParams.getInstId(), klineParams.getTimeframe(), processingTime);

            // 返回格式化的技术指标表格
            return ToolExecutionResult.success(technicalIndicatorTable, processingTime, getToolName());

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("K线数据查询失败", e);
            return ToolExecutionResult.failure("K线数据查询失败: " + e.getMessage(), processingTime, getToolName());
        }
    }

    @Override
    public String getToolName() {
        return "k_line";
    }

    @Override
    public boolean validateParameters(ToolParameters parameters) {
        if (!(parameters instanceof KLineParameters klineParams)) {
            return false;
        }

        // 验证必需参数
        if (klineParams.getInstId() == null || klineParams.getInstId().trim().isEmpty()) {
            return false;
        }

        if (klineParams.getTimeframe() == null || klineParams.getTimeframe().trim().isEmpty()) {
            return false;
        }

        // 验证limit范围
        return klineParams.getLimit() == null || (klineParams.getLimit() >= 1 && klineParams.getLimit() <= 500);
    }

    @Override
    public Class<? extends ToolParameters> getParameterType() {
        return KLineParameters.class;
    }

//    /**
//     * 处理K线数据,统一返回完整的JSON格式
//     * 不再区分分析类型,直接返回所有原始字段
//     */
//    private Object processCandlesData(List<OkxMarketCandle> candles, String analysisType) {
//        // 统一返回完整的K线数据JSON格式
//        log.info("处理K线数据,数据条数:{},分析类型(忽略):{}", candles.size(), analysisType);
//        return convertCandlesToCompleteJson(candles);
//    }

//    /**
//     * 转换K线数据为Map格式
//     */
//    private List<Map<String, Object>> convertCandlesToMap(List<OkxMarketCandle> candles) {
//        List<Map<String, Object>> result = new ArrayList<>();
//
//        for (OkxMarketCandle candle : candles) {
//            Map<String, Object> candleData = new HashMap<>();
//            candleData.put("timestamp", candle.getTimestamp());
//            candleData.put("open", candle.getOpen());
//            candleData.put("high", candle.getHigh());
//            candleData.put("low", candle.getLow());
//            candleData.put("close", candle.getClose());
//            candleData.put("volume", candle.getVolume());
//            candleData.put("volumeCcyQuote", candle.getVolCcyQuote());
//            candleData.put("volumeCcyBase", candle.getVolumeCcy());
//            candleData.put("confirm", candle.getConfirm());
//
//            result.add(candleData);
//        }
//
//        return result;
//    }

//    /**
//     * 转换为完整的K线数据JSON格式
//     * 包含所有原始字段,不做任何预处理
//     */
//    private Map<String, Object> convertCandlesToCompleteJson(List<OkxMarketCandle> candles) {
//        Map<String, Object> result = new HashMap<>();
//
//        // 元数据
//        result.put("count", candles.size());
//        result.put("data_type", "kline_full");
//
//        // 完整的K线数据
//        List<Map<String, Object>> candleList = new ArrayList<>();
//        for (OkxMarketCandle candle : candles) {
//            Map<String, Object> candleData = new LinkedHashMap<>();
//            // 保持字段顺序,便于大模型理解
//            candleData.put("timestamp", candle.getTimestamp());
//            candleData.put("open", candle.getOpen());
//            candleData.put("high", candle.getHigh());
//            candleData.put("low", candle.getLow());
//            candleData.put("close", candle.getClose());
//            candleData.put("volume", candle.getVolume());
//            candleData.put("volCcyQuote", candle.getVolCcyQuote());
//            candleData.put("volCcy", candle.getVolumeCcy());
//            candleData.put("confirm", candle.getConfirm());
//
//            candleList.add(candleData);
//        }
//        result.put("candles", candleList);
//
//        return result;
//    }

//    /**
//     * 增强技术分析信息
//     */
//    private Map<String, Object> enrichWithTechnicalAnalysis(List<OkxMarketCandle> candles) {
//        Map<String, Object> result = new HashMap<>();
//
//        // 原始数据
//        result.put("candles", convertCandlesToMap(candles));
//
//        // 技术指标
//        result.put("technical_indicators", calculateTechnicalIndicators(candles));
//
//        // 关键价格水平
//        result.put("price_levels", identifyPriceLevels(candles));
//
//        return result;
//    }

//    /**
//     * 趋势分析
//     */
//    private Map<String, Object> enrichWithTrendAnalysis(List<OkxMarketCandle> candles) {
//        Map<String, Object> result = new HashMap<>();
//
//        result.put("candles", convertCandlesToMap(candles));
//        result.put("trend_analysis", analyzeTrend(candles));
//        result.put("support_resistance", identifySupportResistance(candles));
//
//        return result;
//    }

//    /**
//     * 波动率分析
//     */
//    private Map<String, Object> enrichWithVolatilityAnalysis(List<OkxMarketCandle> candles) {
//        Map<String, Object> result = new HashMap<>();
//
//        result.put("candles", convertCandlesToMap(candles));
//        result.put("volatility_metrics", calculateVolatilityMetrics(candles));
//        result.put("price_range", calculatePriceRange(candles));
//
//        return result;
//    }

//    /**
//     * 计算技术指标
//     */
//    private Map<String, Object> calculateTechnicalIndicators(List<OkxMarketCandle> candles) {
//        if (candles.size() < 20) {
//            return Map.of("note", "数据不足，无法计算技术指标");
//        }
//
//        // Convert to OhlcItem
//        List<IndicatorCalculationRequest.OhlcItem> ohlcItems = candles.stream()
//                .map(c -> IndicatorCalculationRequest.OhlcItem.builder()
//                        .timestamp(c.getTimestamp())
//                        .open(c.getOpen())
//                        .high(c.getHigh())
//                        .low(c.getLow())
//                        .close(c.getClose())
//                        .volume(c.getVolume())
//                        .build())
//                .sorted(Comparator.comparing(IndicatorCalculationRequest.OhlcItem::getTimestamp))
//                .collect(Collectors.toList());
//
//        // Build Request
//        IndicatorCalculationRequest request = IndicatorCalculationRequest.builder()
//                .ohlcData(ohlcItems)
//                .indicators(Arrays.asList(
//                        IndicatorCalculationRequest.IndicatorConfig.builder().name("SMA").periods(Arrays.asList(20, 50)).build(),
//                        IndicatorCalculationRequest.IndicatorConfig.builder().name("RSI").periods(Collections.singletonList(14)).build()
//                ))
//                .build();
//
//        // Calculate
//        IndicatorCalculationResponse response = commonTechnicalIndicatorService.calculate(request);
//
//        // Extract results
//        BigDecimal sma20 = getLatestValue(response, "SMA", "20");
//        BigDecimal sma50 = getLatestValue(response, "SMA", "50");
//        BigDecimal rsi14 = getLatestValue(response, "RSI", "14");
//
//        if (sma20 == null) sma20 = BigDecimal.ZERO;
//        if (sma50 == null) sma50 = BigDecimal.ZERO;
//        if (rsi14 == null) rsi14 = BigDecimal.ZERO;
//
//        BigDecimal latestPrice = candles.get(candles.size() - 1).getClose();
//
//        BigDecimal priceVsSma20 = (sma20.compareTo(BigDecimal.ZERO) != 0)
//                ? latestPrice.subtract(sma20).divide(sma20, 4, java.math.RoundingMode.HALF_UP)
//                : BigDecimal.ZERO;
//
//        return Map.of(
//                "sma_20", sma20,
//                "sma_50", sma50,
//                "latest_price", latestPrice,
//                "price_vs_sma20", priceVsSma20,
//                "rsi", rsi14
//        );
//    }

//    private BigDecimal getLatestValue(IndicatorCalculationResponse response, String indicatorName, String period) {
//        if (response != null && response.getResults() != null && response.getResults().containsKey(indicatorName)) {
//            List<IndicatorCalculationResponse.IndicatorResult> results = response.getResults().get(indicatorName);
//            for (IndicatorCalculationResponse.IndicatorResult res : results) {
//                if (period.equals(res.getPeriod())) {
//                    List<IndicatorCalculationResponse.SingleValue> values = res.getValues();
//                    if (values != null && !values.isEmpty()) {
//                        IndicatorCalculationResponse.SingleValue lastValue = values.get(values.size() - 1);
//                        if (lastValue != null) {
//                            return lastValue.getValue();
//                        }
//                    }
//                }
//            }
//        }
//        return null;
//    }

//    /**
//     * 分析趋势
//     */
//    private Map<String, Object> analyzeTrend(List<OkxMarketCandle> candles) {
//        if (candles.size() < 2) {
//            return Map.of("trend", "unknown", "note", "数据不足");
//        }
//
//        BigDecimal firstPrice = candles.get(0).getClose();
//        BigDecimal lastPrice = candles.get(candles.size() - 1).getClose();
//        BigDecimal change = lastPrice.subtract(firstPrice);
//        BigDecimal changePercent = change.divide(firstPrice, 4, RoundingMode.HALF_UP)
//                .multiply(new BigDecimal("100"));
//
//        String trend = changePercent.compareTo(BigDecimal.ZERO) > 0 ? "upward" :
//                changePercent.compareTo(BigDecimal.ZERO) < 0 ? "downward" : "sideways";
//
//        return Map.of(
//                "trend", trend,
//                "change_percent", changePercent,
//                "total_change", change,
//                "period_candles", candles.size()
//        );
//    }

//    /**
//     * 计算波动率指标
//     */
//    private Map<String, Object> calculateVolatilityMetrics(List<OkxMarketCandle> candles) {
//        List<BigDecimal> closes = candles.stream()
//                .map(OkxMarketCandle::getClose)
//                .toList();
//
//        if (closes.size() < 2) {
//            return Map.of("note", "数据不足，无法计算波动率");
//        }
//
//        BigDecimal mean = closes.stream()
//                .reduce(BigDecimal.ZERO, BigDecimal::add)
//                .divide(new BigDecimal(closes.size()), 8, RoundingMode.HALF_UP);
//
//        BigDecimal variance = closes.stream()
//                .map(price -> price.subtract(mean).pow(2))
//                .reduce(BigDecimal.ZERO, BigDecimal::add)
//                .divide(new BigDecimal(closes.size()), 8, RoundingMode.HALF_UP);
//
//        BigDecimal standardDeviation = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
//        BigDecimal volatility = standardDeviation.divide(mean, 4, RoundingMode.HALF_UP)
//                .multiply(new BigDecimal("100"));
//
//        BigDecimal maxPrice = closes.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
//        BigDecimal minPrice = closes.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
//        BigDecimal priceRange = maxPrice.subtract(minPrice);
//
//        return Map.of(
//                "volatility_percent", volatility,
//                "standard_deviation", standardDeviation,
//                "price_range", priceRange,
//                "highest_price", maxPrice,
//                "lowest_price", minPrice
//        );
//    }

//    private Map<String, Object> identifyPriceLevels(List<OkxMarketCandle> candles) {
//        List<BigDecimal> highs = candles.stream().map(OkxMarketCandle::getHigh).toList();
//        List<BigDecimal> lows = candles.stream().map(OkxMarketCandle::getLow).toList();
//
//        BigDecimal highestHigh = highs.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
//        BigDecimal lowestLow = lows.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
//
//        return Map.of(
//                "highest_high", highestHigh,
//                "lowest_low", lowestLow,
//                "current_price", candles.get(candles.size() - 1).getClose()
//        );
//    }

//    private Map<String, Object> identifySupportResistance(List<OkxMarketCandle> candles) {
//        // 简化的支撑阻力位识别
//        List<BigDecimal> lows = candles.stream().map(OkxMarketCandle::getLow).toList();
//        List<BigDecimal> highs = candles.stream().map(OkxMarketCandle::getHigh).toList();
//
//        return Map.of(
//                "support_level", lows.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO),
//                "resistance_level", highs.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO)
//        );
//    }

//    private Map<String, Object> calculatePriceRange(List<OkxMarketCandle> candles) {
//        BigDecimal maxHigh = candles.stream()
//                .map(OkxMarketCandle::getHigh)
//                .max(BigDecimal::compareTo)
//                .orElse(BigDecimal.ZERO);
//
//        BigDecimal minLow = candles.stream()
//                .map(OkxMarketCandle::getLow)
//                .min(BigDecimal::compareTo)
//                .orElse(BigDecimal.ZERO);
//
//        return Map.of(
//                "range", maxHigh.subtract(minLow),
//                "max_high", maxHigh,
//                "min_low", minLow
//        );
//    }

}
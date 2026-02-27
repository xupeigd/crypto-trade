package com.crypto.trade.service;

import com.crypto.trade.dto.cex.model.CexMarketCandle;
import com.crypto.trade.dto.cex.model.CexPosition;
import com.crypto.trade.dto.indicator.IndicatorCalculationRequest;
import com.crypto.trade.dto.indicator.IndicatorCalculationResponse;
import com.crypto.trade.model.TechnicalIndicators;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * TechnicalIndicatorService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class TechnicalIndicatorService {

    // ✅ 优化：技术指标缓存，5分钟过期，容量增加到200
    private static final int INDICATOR_CACHE_MAX_SIZE = 200;
    // ✅ 优化：K线数据缓存，3分钟过期，容量增加到100
    private static final int CANDLE_CACHE_MAX_SIZE = 100;
    private final Cache<String, TechnicalIndicators> indicatorCache = Caffeine.newBuilder()
            .maximumSize(INDICATOR_CACHE_MAX_SIZE)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();
    private final Cache<String, List<CexMarketCandle>> candleCache = Caffeine.newBuilder()
            .maximumSize(CANDLE_CACHE_MAX_SIZE)
            .expireAfterWrite(Duration.ofMinutes(3))
            .build();
    // 线程池用于并发处理
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @Autowired
    private UnifiedCexApiService unifiedCexApiService;
    @Autowired
    private CommonTechnicalIndicatorService commonIndicatorService;

    @Value("${technical.indicator.decimal-scale:8}")
    private Integer decimalScale;

    /**
     * 获取持仓合约的技术指标（按instId去重计算）
     *
     * @param positions 持仓列表
     * @param apiKeyId  API密钥ID
     * @return 合约ID到技术指标的映射
     */
    public Map<String, TechnicalIndicators> getIndicatorsForPositions(List<CexPosition> positions, Long apiKeyId) {
        // 输入参数验证
        if (!validateInputParameters(positions, apiKeyId)) {
            return Collections.emptyMap();
        }

        Map<String, TechnicalIndicators> result = new HashMap<>();
        String timeframe = "5m";

        // 数据质量检查和过滤有效持仓
        List<CexPosition> validPositions = filterValidPositions(positions);

        if (validPositions.isEmpty()) {
            log.debug("没有找到有效持仓合约，跳过技术指标计算");
            return result;
        }

        // 按instId去重，避免重复计算相同合约的技术指标
        // 使用LinkedHashMap保持顺序，同时记录重复情况
        Map<String, CexPosition> uniquePositions = new LinkedHashMap<>();
        Set<String> duplicateInstIds = new HashSet<>();

        for (CexPosition position : validPositions) {
            String instId = position.getSymbol();
            if (uniquePositions.containsKey(instId)) {
                duplicateInstIds.add(instId);
                log.debug("发现重复合约instId: {}, 将复用技术指标计算结果", instId);
            } else {
                uniquePositions.put(instId, position);
            }
        }

        // 记录去重统计信息
        int originalCount = validPositions.size();
        int uniqueCount = uniquePositions.size();
        int duplicateCount = duplicateInstIds.size();

        log.info("技术指标计算去重统计 - 原始持仓数: {}, 去重后合约数: {}, 重复合约数: {}",
                originalCount, uniqueCount, duplicateCount);

        // 如果有重复合约，记录详细信息
        if (duplicateCount > 0) {
            log.info("重复合约清单: {}", String.join(", ", duplicateInstIds));
        }

        // 并发获取去重后合约的技术指标
        List<CompletableFuture<TechnicalIndicators>> futures = uniquePositions.values().stream()
                .map(position -> CompletableFuture.supplyAsync(() -> {
                    String instId = position.getSymbol();
                    try {
                        log.info("开始计算合约{}的技术指标", instId);
                        TechnicalIndicators indicators = getTechnicalIndicators(instId, timeframe, apiKeyId);
                        log.info("合约{}技术指标计算完成，结果: {}", instId,
                                indicators != null ? "成功" : "失败");
                        return indicators;
                    } catch (Exception e) {
                        log.error("计算合约{}的技术指标失败，异常类型: {}, 异常信息: {}",
                                instId, e.getClass().getSimpleName(), e.getMessage(), e);
                        return null;
                    }
                }, executorService))
                .collect(Collectors.toList());

        // 等待所有任务完成，收集结果
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 收集计算结果
        int successCount = 0;
        int index = 0;
        for (Map.Entry<String, CexPosition> entry : uniquePositions.entrySet()) {
            String instId = entry.getKey();
            TechnicalIndicators indicators = futures.get(index).join();
            if (indicators != null) {
                result.put(instId, indicators);
                successCount++;
                log.debug("合约{}技术指标计算成功", instId);
            } else {
                log.warn("合约{}技术指标计算失败", instId);
            }
            index++;
        }

        log.info("技术指标计算完成 - 输入持仓数: {}, 去重后合约数: {}, 成功计算数: {}, 节省重复计算数: {}",
                originalCount, uniqueCount, successCount, duplicateCount);

        return result;
    }

    /**
     * 获取单个合约的技术指标
     *
     * @param instId    合约ID
     * @param timeframe 时间周期
     * @param apiKeyId  API密钥ID
     * @return 技术指标数据
     */
    public TechnicalIndicators getTechnicalIndicators(String instId, String timeframe, Long apiKeyId) {
        // ✅ 优化：移除apiKeyId，改为instId_timeframe，提高缓存复用率
        String cacheKey = String.format("%s_%s", instId, timeframe);

        // 尝试从缓存获取
        TechnicalIndicators cached = indicatorCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("缓存命中 - 技术指标数据: {}, timeframe: {}", instId, timeframe);
            return cached;
        }

        try {
            log.info("开始计算合约{}的技术指标", instId);

            // 获取120条K线数据用于详细分析
            // 计算需要的最小数据量：取所有指标周期的最大值+1
            int minDataRequired = 31; // RSI-30需要31条数据（30+1）
            List<CexMarketCandle> candles = getCandlesData(instId, timeframe, 120, apiKeyId);
            if (candles == null || candles.size() < minDataRequired) {
                log.warn("合约{}的K线数据不足，无法计算技术指标，数据量: {}, 至少需要: {}",
                        instId, candles != null ? candles.size() : 0, minDataRequired);
                return null;
            }

            // 当前价格（最新收盘价）
            BigDecimal currentPrice = candles.get(candles.size() - 1).getClose();

            // 转换数据为计算请求格式
            List<IndicatorCalculationRequest.OhlcItem> ohlcItems = candles.stream()
                    .map(c -> IndicatorCalculationRequest.OhlcItem.builder()
                            .timestamp(c.getTimestamp())
                            .open(c.getOpen())
                            .high(c.getHigh())
                            .low(c.getLow())
                            .close(c.getClose())
                            .volume(c.getVolume())
                            .build())
                    .collect(Collectors.toList());

            // 构建计算请求
            IndicatorCalculationRequest request = IndicatorCalculationRequest.builder()
                    .ohlcData(ohlcItems)
                    .indicators(Arrays.asList(
                            IndicatorCalculationRequest.IndicatorConfig.builder()
                                    .name("RSI")
                                    .periods(Arrays.asList(5, 20, 30))
                                    .build(),
                            IndicatorCalculationRequest.IndicatorConfig.builder()
                                    .name("SMA")
                                    .periods(Arrays.asList(5, 20, 30))
                                    .build(),
                            IndicatorCalculationRequest.IndicatorConfig.builder()
                                    .name("BOLL")
                                    .periods(Arrays.asList(5, 20))
                                    .build()
                    ))
                    .build();

            // 执行计算
            IndicatorCalculationResponse response = commonIndicatorService.calculate(request);

            // 解析RSI结果
            List<Double> rsi5Values = getRsiValues(response, "5");
            List<Double> rsi20Values = getRsiValues(response, "20");
            List<Double> rsi30Values = getRsiValues(response, "30");

            TechnicalIndicators.RsiData rsiData = calculateRsiIndicators(rsi5Values, rsi20Values, rsi30Values);

            // 解析SMA结果
            List<Double> sma5Values = getSmaValues(response, "5");
            List<Double> sma20Values = getSmaValues(response, "20");
            List<Double> sma30Values = getSmaValues(response, "30");

            // 解析BOLL结果
            List<TechnicalIndicators.BollPeriod> boll5Values = getBollValues(response, "5", 2);
            List<TechnicalIndicators.BollPeriod> boll20Values = getBollValues(response, "20", 2);

            TechnicalIndicators.BollData bollData = calculateBollIndicators(boll5Values, boll20Values, currentPrice);

            // 转换K线数据为OHLC格式（保留最近120条）
            log.debug("构建OHLC数据，原始K线数量: {}", candles.size());
            List<TechnicalIndicators.OhlcData> ohlcHistory = candles.stream()
                    .skip(Math.max(0, candles.size() - 120))
                    .map(candle -> {
                        String timeStr = new SimpleDateFormat("HH:mm")
                                .format(new Date(candle.getTimestamp()));
                        return TechnicalIndicators.OhlcData.builder()
                                .timestamp(candle.getTimestamp())
                                .time(timeStr)
                                .open(candle.getOpen())
                                .high(candle.getHigh())
                                .low(candle.getLow())
                                .close(candle.getClose())
                                .volume(candle.getVolume())
                                .volumeCcy(candle.getVolumeCcy())
                                .build();
                    })
                    .collect(Collectors.toList());

            log.debug("OHLC数据构建完成，数据量: {}, BOLL5数据量: {}, BOLL20数据量: {}",
                    ohlcHistory.size(),
                    boll5Values != null ? boll5Values.size() : 0,
                    boll20Values != null ? boll20Values.size() : 0);

            // 构建技术指标对象
            TechnicalIndicators indicators = TechnicalIndicators.builder()
                    .instId(instId)
                    .timeframe(timeframe)
                    .currentPrice(currentPrice)
                    .rsiData(rsiData)
                    .bollData(bollData)
                    .ohlcHistory(ohlcHistory)
                    .rsi5History(rsi5Values)
                    .rsi20History(rsi20Values)
                    .rsi30History(rsi30Values)
                    .ema5History(sma5Values)
                    .ema20History(sma20Values)
                    .ema30History(sma30Values)
                    .boll5History(boll5Values)
                    .boll20History(boll20Values)
                    .timestamp(System.currentTimeMillis())
                    .build();

            // 缓存结果
            indicatorCache.put(cacheKey, indicators);

            log.info("技术指标计算完成 - 合约: {}, timeframe: {}, OHLC数据量: {}, 已缓存", instId, timeframe, ohlcHistory.size());
            return indicators;

        } catch (Exception e) {
            log.error("获取合约{}的技术指标失败", instId, e);
            return null;
        }
    }

    private List<Double> getRsiValues(IndicatorCalculationResponse response, String period) {
        if (response == null || response.getResults() == null || !response.getResults().containsKey("RSI")) {
            return new ArrayList<>();
        }
        return response.getResults().get("RSI").stream()
                .filter(r -> r.getPeriod().equals(period))
                .findFirst()
                .map(r -> r.getValues().stream()
                        .map(v -> (v != null && v.getValue() != null) ? v.getValue().doubleValue() : null)
                        .collect(Collectors.toList()))
                .orElse(new ArrayList<>());
    }

    private List<Double> getSmaValues(IndicatorCalculationResponse response, String period) {
        if (response == null || response.getResults() == null || !response.getResults().containsKey("SMA")) {
            return new ArrayList<>();
        }
        return response.getResults().get("SMA").stream()
                .filter(r -> r.getPeriod().equals(period))
                .findFirst()
                .map(r -> r.getValues().stream()
                        .map(v -> (v != null && v.getValue() != null) ? v.getValue().doubleValue() : null)
                        .collect(Collectors.toList()))
                .orElse(new ArrayList<>());
    }

    private List<TechnicalIndicators.BollPeriod> getBollValues(IndicatorCalculationResponse response, String period, int stdDev) {
        if (response == null || response.getResults() == null || !response.getResults().containsKey("BOLL")) {
            log.warn("BOLL响应为空或缺少BOLL数据，response={}", response);
            return new ArrayList<>();
        }
        String periodKey = period + "," + stdDev;
        log.debug("查找BOLL数据，periodKey={}", periodKey);
        List<IndicatorCalculationResponse.IndicatorResult> bollResults = response.getResults().get("BOLL");
        log.debug("BOLL结果数量: {}, 可用periods: {}",
                bollResults.size(),
                bollResults.stream().map(IndicatorCalculationResponse.IndicatorResult::getPeriod).collect(Collectors.toList()));

        return bollResults.stream()
                .filter(r -> r.getPeriod().equals(periodKey))
                .findFirst()
                .map(r -> {
                    List<IndicatorCalculationResponse.BollValue> bollValues = r.getBollValues();
                    log.debug("找到BOLL数据，periodKey={}, 数据量={}", periodKey, bollValues != null ? bollValues.size() : 0);
                    return bollValues.stream()
                            .map(v -> {
                                if (v == null || v.getUpper() == null || v.getMiddle() == null || v.getLower() == null) {
                                    log.warn("BOLL数据为空或缺少必要字段，跳过该数据点");
                                    return null;
                                }
                                return calculateBollPeriodWithData(v.getUpper(), v.getMiddle(), v.getLower(),
                                        v.getMiddle(), Integer.parseInt(period));
                            })
                            .collect(Collectors.toList());
                })
                .orElse(new ArrayList<>());
    }

    /**
     * 获取K线数据（带缓存）
     */
    private List<CexMarketCandle> getCandlesData(String instId, String timeframe, int limit, Long apiKeyId) {
        String cacheKey = String.format("candles_%s_%s_%d", instId, timeframe, limit);

        // 尝试从缓存获取
        List<CexMarketCandle> cached = candleCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("K线缓存命中 - 合约: {}, timeframe: {}, 数据量: {}", instId, timeframe, cached.size());
            return cached;
        }

        // 从API获取
        log.info("从API获取K线数据 - 合约: {}, timeframe: {}, 限制: {}", instId, timeframe, limit);
        List<CexMarketCandle> candles = unifiedCexApiService.getMarketCandles(null, instId, timeframe, limit);
        if (candles != null && !candles.isEmpty()) {
            // 按时间排序(最早的数据在前)
            candles.sort(Comparator.comparing(CexMarketCandle::getTimestamp));
            candleCache.put(cacheKey, candles);
            log.info("K线数据获取成功 - 合约: {}, 数据量: {}, 已缓存", instId, candles.size());
        } else {
            log.warn("K线数据获取失败或为空 - 合约: {}", instId);
        }

        return candles;
    }

    /**
     * 从预先计算的RSI序列构建RSI数据
     */
    private TechnicalIndicators.RsiData calculateRsiIndicators(List<Double> rsi5Values,
                                                               List<Double> rsi20Values,
                                                               List<Double> rsi30Values) {
        try {
            BigDecimal rsi5 = getLastValue(rsi5Values);
            BigDecimal rsi20 = getLastValue(rsi20Values);
            BigDecimal rsi30 = getLastValue(rsi30Values);

            return TechnicalIndicators.RsiData.builder()
                    .rsi5(rsi5)
                    .rsi20(rsi20)
                    .rsi30(rsi30)
                    .rsi5Signal(TechnicalIndicators.getRsiSignal(rsi5))
                    .rsi20Signal(TechnicalIndicators.getRsiSignal(rsi20))
                    .rsi30Signal(TechnicalIndicators.getRsiSignal(rsi30))
                    .overallSignal(TechnicalIndicators.calculateOverallRsiSignal(rsi5, rsi20, rsi30))
                    .build();

        } catch (Exception e) {
            log.error("RSI数据构建失败", e);
            return TechnicalIndicators.RsiData.builder().build();
        }
    }

    /**
     * 从预先计算的BOLL序列构建BOLL数据
     */
    private TechnicalIndicators.BollData calculateBollIndicators(List<TechnicalIndicators.BollPeriod> boll5Values,
                                                                 List<TechnicalIndicators.BollPeriod> boll20Values,
                                                                 BigDecimal currentPrice) {
        try {
            TechnicalIndicators.BollPeriod boll5 = getLastBollPeriod(boll5Values, 5, currentPrice);
            TechnicalIndicators.BollPeriod boll20 = getLastBollPeriod(boll20Values, 20, currentPrice);

            String position5 = boll5.getPricePosition();
            String position20 = boll20.getPricePosition();

            return TechnicalIndicators.BollData.builder()
                    .boll5(boll5)
                    .boll20(boll20)
                    .overallSignal(TechnicalIndicators.calculateOverallBollSignal(position5, position20, null))
                    .build();

        } catch (Exception e) {
            log.error("BOLL数据构建失败", e);
            return TechnicalIndicators.BollData.builder().build();
        }
    }

    /**
     * 获取列表中的最后一个值（使用配置的小数精度）
     */
    private BigDecimal getLastValue(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        int scale = decimalScale != null ? decimalScale : 8;
        return BigDecimal.valueOf(values.get(values.size() - 1))
                .setScale(scale, RoundingMode.HALF_UP);
    }

    /**
     * 获取BOLL列表中的最后一个值（补充完整数据）
     */
    private TechnicalIndicators.BollPeriod getLastBollPeriod(List<TechnicalIndicators.BollPeriod> bollValues, int period, BigDecimal currentPrice) {
        if (bollValues == null || bollValues.isEmpty()) {
            return TechnicalIndicators.BollPeriod.builder().period(period).build();
        }

        TechnicalIndicators.BollPeriod lastBoll = bollValues.get(bollValues.size() - 1);
        // 如果最后一个BOLL数据没有完整的距离计算，补充计算
        if (lastBoll.getDistanceToUpperPercent() == null || lastBoll.getDistanceToLowerPercent() == null) {
            return calculateBollPeriodWithData(lastBoll.getUpperBand(), lastBoll.getMiddleBand(),
                    lastBoll.getLowerBand(), currentPrice, period);
        }
        return lastBoll;
    }

    /**
     * 使用已计算的轨线值构建完整的BollPeriod
     */
    private TechnicalIndicators.BollPeriod calculateBollPeriodWithData(BigDecimal upperBand, BigDecimal middleBand,
                                                                       BigDecimal lowerBand, BigDecimal currentPrice, int period) {
        String pricePosition = TechnicalIndicators.getBollPricePosition(currentPrice, upperBand, middleBand, lowerBand);
        String signal = TechnicalIndicators.getBollSignal(pricePosition);

        // 计算BOLL宽度（上轨-下轨）
        BigDecimal bandwidth = upperBand.subtract(lowerBand);

        // 计算价格距离上下轨的百分比
        BigDecimal distanceToUpperPercent = BigDecimal.ZERO;
        BigDecimal distanceToLowerPercent = BigDecimal.ZERO;

        if (upperBand.compareTo(lowerBand) > 0) {
            // 距离上轨的百分比: (上轨 - 当前价格) / 上轨 * 100
            distanceToUpperPercent = upperBand.subtract(currentPrice)
                    .divide(upperBand, 8, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

            // 距离下轨的百分比: (当前价格 - 下轨) / 下轨 * 100
            distanceToLowerPercent = currentPrice.subtract(lowerBand)
                    .divide(lowerBand, 8, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        int scale = decimalScale != null ? decimalScale : 8;

        return TechnicalIndicators.BollPeriod.builder()
                .period(period)
                .upperBand(upperBand.setScale(scale, RoundingMode.HALF_UP))
                .middleBand(middleBand.setScale(scale, RoundingMode.HALF_UP))
                .lowerBand(lowerBand.setScale(scale, RoundingMode.HALF_UP))
                .pricePosition(pricePosition)
                .signal(signal)
                .bandwidth(bandwidth.setScale(scale, RoundingMode.HALF_UP))
                .distanceToUpperPercent(distanceToUpperPercent.setScale(scale, RoundingMode.HALF_UP))
                .distanceToLowerPercent(distanceToLowerPercent.setScale(scale, RoundingMode.HALF_UP))
                .build();
    }

    /**
     * 过滤有效的持仓合约（包含数据质量检查）
     */
    private List<CexPosition> filterValidPositions(List<CexPosition> positions) {
        List<CexPosition> validPositions = new ArrayList<>();
        int nullPositionCount = 0;
        int invalidInstIdCount = 0;
        int zeroPositionCount = 0;

        for (CexPosition position : positions) {
            if (position == null) {
                nullPositionCount++;
                continue;
            }

            String instId = position.getSymbol();
            if (instId == null || instId.trim().isEmpty()) {
                invalidInstIdCount++;
                log.debug("发现无效的instId: {}, 跳过该持仓", instId);
                continue;
            }

            if (position.getQuantity() == null || position.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                zeroPositionCount++;
                log.debug("持仓数量为零的合约: {}, 跳过计算", instId);
                continue;
            }

            validPositions.add(position);
        }

        // 记录数据质量统计
        int totalPositions = positions.size();
        log.info("持仓数据质量检查 - 总数: {}, 有效: {}, null持仓: {}, 无效instId: {}, 零持仓: {}",
                totalPositions, validPositions.size(), nullPositionCount, invalidInstIdCount, zeroPositionCount);

        if (nullPositionCount > 0 || invalidInstIdCount > 0) {
            log.warn("发现{}个null持仓和{}个无效instId的持仓，已过滤", nullPositionCount, invalidInstIdCount);
        }

        return validPositions;
    }

    /**
     * 验证输入参数
     */
    private boolean validateInputParameters(List<CexPosition> positions, Long apiKeyId) {
        if (positions == null) {
            log.debug("持仓列表为null，跳过技术指标计算");
            return false;
        }

        if (positions.isEmpty()) {
            log.debug("持仓列表为空，跳过技术指标计算");
            return false;
        }

        if (apiKeyId == null) {
            log.error("API密钥ID为null，无法进行技术指标计算");
            return false;
        }

        if (apiKeyId <= 0) {
            log.error("API密钥ID无效: {}", apiKeyId);
            return false;
        }

        log.debug("输入参数验证通过 - 持仓数量: {}, API密钥ID: {}", positions.size(), apiKeyId);
        return true;
    }

    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        var indicatorStats = indicatorCache.stats();
        var candleStats = candleCache.stats();

        return String.format(
                "技术指标缓存统计 - 命中率: %.2f%%, 未命中数: %d, 请求数: %d, 大小: %d/%d; " +
                        "K线缓存统计 - 命中率: %.2f%%, 未命中数: %d, 请求数: %d, 大小: %d/%d",
                indicatorStats.hitRate() * 100,
                indicatorStats.missCount(),
                indicatorStats.requestCount(),
                indicatorCache.estimatedSize(),
                INDICATOR_CACHE_MAX_SIZE,
                candleStats.hitRate() * 100,
                candleStats.missCount(),
                candleStats.requestCount(),
                candleCache.estimatedSize(),
                CANDLE_CACHE_MAX_SIZE
        );
    }

    /**
     * 清理缓存
     */
    public void clearCache() {
        long indicatorSize = indicatorCache.estimatedSize();
        long candleSize = candleCache.estimatedSize();

        indicatorCache.invalidateAll();
        candleCache.invalidateAll();

        log.info("技术指标缓存已清理 - 技术指标缓存条目: {}, K线缓存条目: {}", indicatorSize, candleSize);
    }

    /**
     * 失效指定合约的技术指标缓存
     *
     * @param instId 合约ID
     */
    public void invalidateCacheByInstId(String instId) {
        if (instId == null || instId.trim().isEmpty()) {
            log.warn("尝试失效缓存时instId为空，跳过");
            return;
        }

        long beforeSize = indicatorCache.estimatedSize();
        long candleBeforeSize = candleCache.estimatedSize();

        // 失效技术指标缓存（所有timeframe）
        indicatorCache.asMap().keySet().removeIf(key -> key.startsWith(instId + "_"));
        // 失效K线缓存（所有timeframe和limit）
        candleCache.asMap().keySet().removeIf(key -> key.startsWith("candles_" + instId + "_"));

        long afterSize = indicatorCache.estimatedSize();
        long candleAfterSize = candleCache.estimatedSize();

        log.info("已失效合约{}的缓存 - 技术指标缓存: {} -> {}, K线缓存: {} -> {}",
                instId, beforeSize, afterSize, candleBeforeSize, candleAfterSize);
    }

    /**
     * 失效所有缓存
     */
    public void invalidateAllCache() {
        long indicatorSize = indicatorCache.estimatedSize();
        long candleSize = candleCache.estimatedSize();

        indicatorCache.invalidateAll();
        candleCache.invalidateAll();

        log.info("已失效所有缓存 - 技术指标缓存条目: {}, K线缓存条目: {}", indicatorSize, candleSize);
    }

    /**
     * 关闭资源
     */
    @PreDestroy
    public void shutdown() {
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        clearCache();
    }
}
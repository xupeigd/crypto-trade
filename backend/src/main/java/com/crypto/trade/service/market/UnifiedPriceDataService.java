package com.crypto.trade.service.market;

import com.crypto.trade.dto.InstrumentOverviewResponse;
import com.crypto.trade.dto.cex.model.CexFundingRate;
import com.crypto.trade.dto.cex.model.CexMarkPrice;
import com.crypto.trade.dto.cex.model.CexMarketCandle;
import com.crypto.trade.dto.indicator.IndicatorCalculationRequest;
import com.crypto.trade.dto.indicator.IndicatorCalculationResponse;
import com.crypto.trade.dto.market.IndicatorsDataDTO;
import com.crypto.trade.dto.market.InstrumentOverviewDTO;
import com.crypto.trade.dto.market.UnifiedChartDataRequest;
import com.crypto.trade.dto.market.UnifiedChartDataResponse;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.KlineData;
import com.crypto.trade.event.InstrumentUpdateEvent;
import com.crypto.trade.model.MarketCandleModel;
import com.crypto.trade.repository.KlineDataRepository;
import com.crypto.trade.service.CommonTechnicalIndicatorService;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * UnifiedPriceDataService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service("marketPriceService")
public class UnifiedPriceDataService {

    private static final int MAX_KLINE_RECORDS = 500;
    private static final List<String> DEFAULT_TIMEFRAMES = Arrays.asList("5m", "1H", "4H", "1D");
    // 并发控制和API限流配置
    private static final int MAX_CONCURRENT_API_CALLS = 3; // 最大并发API调用数
    private static final long API_RATE_LIMIT_DELAY_MS = 200; // API调用间隔（毫秒）
    private static final int MAX_RETRY_ATTEMPTS = 3; // 最大重试次数
    private static final long RETRY_DELAY_MS = 1000; // 重试延迟（毫秒）
    // 并发控制
    private final Semaphore apiCallSemaphore = new Semaphore(MAX_CONCURRENT_API_CALLS);
    private final Map<String, Long> lastApiCallTime = new ConcurrentHashMap<>();
    // 统一图表数据缓存（第二层缓存）
    private final Cache<String, UnifiedChartDataResponse> unifiedChartCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .build();

    @Autowired
    UnifiedCexApiService unifiedCexApiService;
    @Autowired
    KlineDataRepository klineDataRepository;
    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    Executor taskExecutor;
    @Autowired
    CommonTechnicalIndicatorService commonTechnicalIndicatorService;

    private static List<Double> getDoubles(List<CexMarketCandle> candles) {
        List<Double> changeRates = new ArrayList<>();
        // 计算每个K线的涨跌幅: (high - low) / open * 100
        for (CexMarketCandle candle : candles) {
            BigDecimal open = candle.getOpen();
            BigDecimal high = candle.getHigh();
            BigDecimal low = candle.getLow();
            if (null != open && null != high && null != low && open.compareTo(BigDecimal.ZERO) > 0) {
                double changeRate = (high.doubleValue() - low.doubleValue()) / open.doubleValue() * 100;
                // 过滤掉0值和极端值
                changeRates.add(changeRate);
            }
        }
        return changeRates;
    }

    /**
     * 获取标记价格
     *
     * @param apiKey API密钥
     * @param instId 合约ID，如 "BTC-USDT-SWAP"
     * @return 标记价格
     */
    public BigDecimal getMarkPrice(ApiKey apiKey, String instId) {
        // 使用统一方法获取标记价格
        List<CexMarkPrice> markPriceDataList = unifiedCexApiService.getMarkPrice(apiKey, instId);
        if (!markPriceDataList.isEmpty()) {
            CexMarkPrice markPriceData = markPriceDataList.get(0);
            BigDecimal markPrice = markPriceData.getMarkPrice();
            if (markPrice.compareTo(BigDecimal.ZERO) > 0) {
                log.debug("成功获取标记价格 - 合约: {}, 价格: {}", instId, markPrice);
                return markPrice;
            }
        }
        return null;
    }

    /**
     * 获取标记价格K线数据
     *
     * @param apiKey API密钥
     * @param instId 合约ID
     * @param period 时间周期，如 "5m", "1H", "4H"
     * @param limit  数据条数限制，默认100
     * @return K线数据列表
     */
    public List<CexMarketCandle> getMarkPriceCandles(ApiKey apiKey, String instId, String period, int limit) {
        // 使用统一方法获取K线数据
        return unifiedCexApiService.getMarketCandles(apiKey, instId, period, limit);
    }

    /**
     * 获取资金费率
     *
     * @param apiKey API密钥
     * @param instId 合约ID
     * @return 资金费率信息
     */
    public CexFundingRate getFundingRate(ApiKey apiKey, String instId) {
        // 使用统一方法获取资金费率
        List<CexFundingRate> fundingRateDataList = unifiedCexApiService.getFundingRate(apiKey, instId);
        if (fundingRateDataList.isEmpty()) {
            log.warn("未找到资金费率数据 - 合约: {}, API响应数据为空", instId);
            return null;
        }
        CexFundingRate fundingData = fundingRateDataList.get(0);
        log.debug("成功获取资金费率 - 合约: {}, 费率: {}, 下次费率: {}", instId, fundingData.getFundingRate(), fundingData.getNextFundingRate());
        return fundingData;
    }

    /**
     * 计算平均波动率
     * 方法：取100个K线的OHLC数据，计算每个K线的涨跌幅 (high - low) / open * 100，
     * 去除最高最低的涨跌幅值后，除以98得出平均波动率
     *
     * @param candles K线数据列表
     * @return 平均波动率 (%)
     */
    public Double calculateAverageVolatility(List<CexMarketCandle> candles) {
        if (null == candles || candles.size() < 100) {
            log.warn("K线数据不足，无法计算波动率。当前数据量: {}", null != candles ? candles.size() : 0);
            return null;
        }

        try {
            List<Double> changeRates = getDoubles(candles);
            if (changeRates.size() < 10) {
                log.warn("有效涨跌幅数据不足，无法计算波动率。有效数据量: {}", changeRates.size());
                return null;
            }
            // 排序后去除最高和最低的涨跌幅值
            Collections.sort(changeRates);
            // 如果有足够数据，去除最高最低值
            List<Double> filteredRates;
            if (changeRates.size() > 2) {
                filteredRates = changeRates.subList(1, changeRates.size() - 1);
            } else {
                filteredRates = changeRates;
            }
            // 计算平均值
            double sum = filteredRates.stream().mapToDouble(Double::doubleValue).sum();
            double averageVolatility = sum / filteredRates.size();
            // 保留2位小数
            return Math.round(averageVolatility * 100.0) / 100.0;
        } catch (Exception e) {
            log.error("计算平均波动率失败", e);
            return null;
        }
    }

    /**
     * 获取指定周期的波动率数据
     *
     * @param apiKey API密钥
     * @param instId 合约ID
     * @param period 时间周期 (1D/4H/1H/5m)
     * @return 周期波动率数据
     */
    public InstrumentOverviewResponse.PeriodVolatilityData getPeriodVolatility(ApiKey apiKey, String instId, String period) {
        try {
            // 获取100个周期的K线数据
            List<CexMarketCandle> candles = getMarkPriceCandles(apiKey, instId, period, 100);
            if (candles.isEmpty()) {
                log.warn("未获取到K线数据 - 合约: {}, 周期: {}", instId, period);
                return null;
            }
            InstrumentOverviewResponse.PeriodVolatilityData result = new InstrumentOverviewResponse.PeriodVolatilityData();
            // 计算平均波动率
            Double averageVolatility = calculateAverageVolatility(candles);
            result.setAverageVolatility(averageVolatility);
            // 计算极值：从所有K线中找出最高价和最低价
            BigDecimal minPrice = null;
            BigDecimal maxPrice = null;
            for (CexMarketCandle candle : candles) {
                BigDecimal high = candle.getHigh();
                BigDecimal low = candle.getLow();
                if (null != high && null != low) {
                    if (null == minPrice || low.compareTo(minPrice) < 0) {
                        minPrice = low;
                    }
                    if (null == maxPrice || high.compareTo(maxPrice) > 0) {
                        maxPrice = high;
                    }
                }
            }
            result.setMinPrice(minPrice);
            result.setMaxPrice(maxPrice);
            result.setDataPoints(candles.size());
            log.debug("成功计算周期波动率 - 合约: {}, 周期: {}, 波动率: {}%, 数据点: {}, 最低价: {}, 最高价: {}",
                    instId, period, averageVolatility, candles.size(), minPrice, maxPrice);
            return result;
        } catch (Exception e) {
            log.error("获取周期波动率失败 - 合约: {}, 周期: {}", instId, period, e);
            return null;
        }
    }

    /**
     * 获取合约概况信息
     *
     * @param apiKey  API密钥
     * @param instId  合约ID
     * @param periods 时间周期列表
     * @return 合约概况信息
     */
    public InstrumentOverviewDTO getInstrumentOverview(ApiKey apiKey, String instId, List<String> periods) {
        try {
            // 验证参数
            if (null == periods || periods.isEmpty()) {
                log.warn("周期参数为空，使用默认周期");
                periods = List.of("1D", "4H", "1H", "5m");
            }
            // 验证周期是否支持
            List<String> validPeriods = periods.stream()
                    .filter(period -> "1D".equals(period) || "4H".equals(period) || "1H".equals(period) || "5m".equals(period))
                    .collect(Collectors.toList());

            if (validPeriods.isEmpty()) {
                log.warn("没有有效的时间周期，使用默认周期");
                validPeriods = List.of("1D", "4H", "1H", "5m");
            }

            // 构建DTO
            InstrumentOverviewDTO.InstrumentOverviewDTOBuilder builder = InstrumentOverviewDTO.builder()
                    .instId(instId);

            // 获取资金费率信息
            CexFundingRate fundingRateInfo = getFundingRate(apiKey, instId);
            if (null != fundingRateInfo) {
                // 手动构建字段，确保时间格式正确（LocalDateTime → 毫秒时间戳字符串）
                builder.fundingRate(fundingRateInfo.getFundingRate())
                        .nextFundingRate(fundingRateInfo.getNextFundingRate())
                        .interestRate(fundingRateInfo.getInterestRate())
                        .premium(fundingRateInfo.getPremium())
                        .settledFundingRate(fundingRateInfo.getSettledFundingRate());

                // 转换LocalDateTime为毫秒时间戳字符串（前端需要）
                LocalDateTime fundingTime = fundingRateInfo.getFundingTime();
                if (null != fundingTime) {
                    long fundingTimeMillis = fundingTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    builder.fundingTime(String.valueOf(fundingTimeMillis));
                }

                LocalDateTime nextFundingTime = fundingRateInfo.getNextFundingTime();
                if (null != nextFundingTime) {
                    long nextFundingTimeMillis = nextFundingTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    builder.nextFundingTime(String.valueOf(nextFundingTimeMillis));
                }
            }

            // 获取各周期的波动率数据
            Map<String, InstrumentOverviewResponse.PeriodVolatilityData> volatilityData = new HashMap<>();
            for (String period : validPeriods) {
                InstrumentOverviewResponse.PeriodVolatilityData periodData = getPeriodVolatility(apiKey, instId, period);
                if (null != periodData) {
                    volatilityData.put(period, periodData);
                }
            }
            builder.volatilityData(volatilityData);

            log.debug("成功获取合约概况 - 合约: {}, 资金费率: {}, 周期数: {}",
                    instId, null == fundingRateInfo ? "--" : fundingRateInfo.getFundingRate(), volatilityData.size());

            return builder.build();
        } catch (Exception e) {
            log.error("获取合约概况失败 - 合约: {}", instId, e);
            return InstrumentOverviewDTO.builder().instId(instId).build();
        }
    }

    // ==================== 并发控制和API限流方法 ====================

    /**
     * 带并发控制和重试机制的API调用
     *
     * @param apiCall API调用逻辑
     * @param apiName API名称（用于日志记录）
     * @param <T>     返回类型
     * @return API调用结果
     */
    private <T> T callApiWithRateLimit(Supplier<T> apiCall, String apiName) {
        // 获取信号量许可
        try {
            boolean acquired = apiCallSemaphore.tryAcquire(30, TimeUnit.SECONDS);
            if (!acquired) {
                log.error("获取API调用许可超时 - apiName: {}", apiName);
                throw new RuntimeException("API调用并发控制超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取API调用许可被中断 - apiName: {}", apiName);
            throw new RuntimeException("API调用并发控制被中断", e);
        }
        try {
            // API限流：检查上次调用时间
            enforceRateLimit(apiName);
            // 带重试的API调用
            return callApiWithRetry(apiCall, apiName);
        } finally {
            // 释放信号量许可
            apiCallSemaphore.release();
        }
    }

    /**
     * API限流控制
     */
    private void enforceRateLimit(String apiName) {
        long currentTime = System.currentTimeMillis();
        Long lastCallTime = lastApiCallTime.get(apiName);
        if (lastCallTime != null) {
            long timeSinceLastCall = currentTime - lastCallTime;
            if (timeSinceLastCall < API_RATE_LIMIT_DELAY_MS) {
                try {
                    long sleepTime = API_RATE_LIMIT_DELAY_MS - timeSinceLastCall;
                    log.debug("API限流延迟 - apiName: {}, 延迟: {}ms", apiName, sleepTime);
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("API限流延迟被中断 - apiName: {}", apiName);
                }
            }
        }
        lastApiCallTime.put(apiName, System.currentTimeMillis());
    }

    /**
     * 带重试机制的API调用
     */
    private <T> T callApiWithRetry(Supplier<T> apiCall, String apiName) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                log.debug("尝试API调用 - apiName: {}, 第{}次尝试", apiName, attempt);
                T result = apiCall.get();

                if (result != null) {
                    log.debug("API调用成功 - apiName: {}, 第{}次尝试", apiName, attempt);
                    return result;
                }

                log.warn("API调用返回null结果 - apiName: {}, 第{}次尝试", apiName, attempt);

            } catch (Exception e) {
                lastException = e;
                log.warn("API调用失败 - apiName: {}, 第{}次尝试, 错误: {}",
                        apiName, attempt, e.getMessage());

                // 如果不是最后一次尝试，等待后重试
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        long retryDelay = RETRY_DELAY_MS * attempt; // 指数退避
                        log.debug("等待重试 - apiName: {}, 延迟: {}ms", apiName, retryDelay);
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("重试等待被中断 - apiName: {}", apiName);
                        throw new RuntimeException("API调用重试被中断", ie);
                    }
                }
            }
        }

        log.error("API调用最终失败 - apiName: {}, 已重试{}次", apiName, MAX_RETRY_ATTEMPTS);
        if (lastException != null) {
            throw (RuntimeException) lastException;
        } else {
            throw new RuntimeException("API调用失败，已达到最大重试次数", lastException);
        }
    }

    /**
     * 将统一K线数据转换为KlineData实体
     */
    private KlineData convertToKlineData(String provider, String instId, String timeframe, CexMarketCandle candle) {
        try {
            // 解析基础资产和计价资产
            String[] assets = instId.split("-");
            String baseAsset = assets.length > 0 ? assets[0] : null;
            String quoteAsset = assets.length > 1 ? assets[1] : null;
            // 转换时间戳
            Long klineTime = candle.getTimestamp();
            return KlineData.builder()
                    .provider(provider)
                    .instId(instId)
                    .timeframe(timeframe)
                    .klineTime(klineTime)
                    .openPrice(candle.getOpen())
                    .highPrice(candle.getHigh())
                    .lowPrice(candle.getLow())
                    .closePrice(candle.getClose())
                    .volume(candle.getVolume())
                    .quoteVolume(candle.getQuoteVolume())
                    .quoteAsset(quoteAsset)
                    .baseAsset(baseAsset)
                    .confirm(candle.getConfirm())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("转换K线数据失败 - provider: {}, instId: {}, timeframe: {}, ts: {}",
                    provider, instId, timeframe, candle.getTimestamp(), e);
            return null;
        }
    }

    /**
     * 批量保存K线数据
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    public int saveKlineData(String provider, String instId, String timeframe, List<CexMarketCandle> candles) {
        if (null == candles || candles.isEmpty()) {
            log.warn("K线数据为空，跳过保存 - provider: {}, instId: {}, timeframe: {}",
                    provider, instId, timeframe);
            return 0;
        }

        try {
            // 转换数据
            List<KlineData> klineDataList = candles.stream()
                    .map(candle -> convertToKlineData(provider, instId, timeframe, candle))
                    .filter(Objects::nonNull)
                    .toList();
            if (klineDataList.isEmpty()) {
                log.warn("转换后的K线数据为空 - provider: {}, instId: {}, timeframe: {}",
                        provider, instId, timeframe);
                return 0;
            }
            // 批量保存或更新（使用ON DUPLICATE KEY UPDATE避免唯一键冲突）
            int savedCount = 0;
            int failedCount = 0;
            for (KlineData klineData : klineDataList) {
                int retryCount = 0;
                boolean saved = false;

                while (retryCount <= 2 && !saved) { // 最多重试2次
                    try {
                        int result = klineDataRepository.batchUpsert(klineData);
                        if (result > 0) {
                            savedCount++;
                            saved = true;
                            log.debug("K线数据保存成功 - provider: {}, instId: {}, timeframe: {}, klineTime: {}, retryCount: {}",
                                    provider, instId, timeframe, klineData.getKlineTime(), retryCount);
                        }
                    } catch (Exception e) {
                        retryCount++;

                        if (retryCount <= 2) {
                            // 短暂延迟后重试
                            try {
                                Thread.sleep(50L * retryCount); // 50ms, 100ms
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }

                            log.debug("K线数据保存失败，准备重试 - provider: {}, instId: {}, timeframe: {}, klineTime: {}, retryCount: {}, error: {}",
                                    provider, instId, timeframe, klineData.getKlineTime(), retryCount, e.getMessage());
                        } else {
                            // 重试次数用完，记录失败
                            failedCount++;

                            // 获取完整错误信息进行详细分析
                            String errorMsg = e.getMessage();
                            String exceptionClassName = e.getClass().getSimpleName();

                            // 判断错误类型并记录详细信息
                            if (errorMsg != null) {
                                // 数据库连接问题
                                if (errorMsg.contains("Communications link failure") ||
                                        errorMsg.contains("connection") ||
                                        errorMsg.contains("timeout") ||
                                        errorMsg.contains("closed")) {
                                    log.error("数据库连接问题，跳过当前批次 - provider: {}, instId: {}, timeframe: {}, error: {}, exception: {}",
                                            provider, instId, timeframe, errorMsg, exceptionClassName);
                                    // 数据库连接问题时，跳出整个批次处理
                                    return savedCount;
                                }
                                // JPA事务问题
                                else if (errorMsg.contains("Executing an update/delete query") ||
                                        errorMsg.contains("TransactionRequiredException") ||
                                        errorMsg.contains("IllegalStateException")) {
                                    log.error("JPA事务管理问题 - provider: {}, instId: {}, timeframe: {}, klineTime: {}, error: {}, exception: {}, " +
                                                    "建议检查事务配置和数据库连接状态",
                                            provider, instId, timeframe, klineData.getKlineTime(), errorMsg, exceptionClassName);
                                }
                                // 唯一约束冲突
                                else if (errorMsg.contains("Duplicate entry") ||
                                        errorMsg.contains("Unique constraint") ||
                                        errorMsg.contains("uk_provider_timeframe_kline_time")) {
                                    log.debug("数据重复，可能是并发操作 - provider: {}, instId: {}, timeframe: {}, klineTime: {}, error: {}",
                                            provider, instId, timeframe, klineData.getKlineTime(), errorMsg);
                                }
                                // 其他SQL错误
                                else {
                                    log.warn("单个K线数据保存失败，已达到最大重试次数 - provider: {}, instId: {}, timeframe: {}, klineTime: {}, error: {}, exception: {}",
                                            provider, instId, timeframe, klineData.getKlineTime(), errorMsg, exceptionClassName);
                                }
                            } else {
                                log.error("单个K线数据保存失败，错误信息为空 - provider: {}, instId: {}, timeframe: {}, klineTime: {}, exception: {}",
                                        provider, instId, timeframe, klineData.getKlineTime(), exceptionClassName);
                            }
                        }
                    }
                }
            }

            if (failedCount > 0) {
                log.info("K线数据保存完成 - provider: {}, instId: {}, timeframe: {}, 成功: {}, 失败: {}, 总数: {}",
                        provider, instId, timeframe, savedCount, failedCount, klineDataList.size());
            }

            // 数据清理：保持最大记录数限制
            cleanupKlineData(provider, instId, timeframe);

            log.info("成功保存K线数据 - provider: {}, instId: {}, timeframe: {}, 保存数量: {}",
                    provider, instId, timeframe, savedCount);
            return savedCount;

        } catch (Exception e) {
            log.error("保存K线数据失败 - provider: {}, instId: {}, timeframe: {}",
                    provider, instId, timeframe, e);
            return 0;
        }
    }

    /**
     * 清理K线数据，保持最大记录数限制
     */
    @Transactional
    public void cleanupKlineData(String provider, String instId, String timeframe) {
        try {
            // 统计当前记录数
            long currentCount = klineDataRepository.countByProviderAndInstIdAndTimeframe(provider, instId, timeframe);
            if (currentCount <= MAX_KLINE_RECORDS) {
                log.debug("K线数据量在限制范围内，无需清理 - provider: {}, instId: {}, timeframe: {}, 当前数量: {}", provider,
                        instId, timeframe, currentCount);
                return;
            }
            // 计算需要删除的记录数
            int deleteCount = (int) (currentCount - MAX_KLINE_RECORDS);
            // 删除最早的记录
            int deletedCount = klineDataRepository.deleteOldestByProviderAndInstIdAndTimeframe(provider, instId, timeframe,
                    deleteCount);
            log.debug("K线数据清理完成 - provider: {}, instId: {}, timeframe: {}, 删除数量: {}, 保留数量: {}", provider, instId,
                    timeframe, deletedCount, MAX_KLINE_RECORDS);
        } catch (Exception e) {
            log.error("清理K线数据失败 - provider: {}, instId: {}, timeframe: {}",
                    provider, instId, timeframe, e);
        }
    }

    /**
     * 异步加载K线数据
     */
    @EventListener
    @Async
    public void handleInstrumentUpdateEvent(InstrumentUpdateEvent event) {
        if (!event.isValid()) {
            log.warn("收到无效的合约更新事件，跳过K线数据加载");
            return;
        }
        log.info("收到合约更新事件，开始异步加载K线数据 - provider: {}, instId: {}", event.getProvider(), event.getInstId());
        CompletableFuture.runAsync(() -> {
            try {
                // 为所有时间周期加载K线数据
                for (String timeframe : DEFAULT_TIMEFRAMES) {
                    try {
                        loadKlineDataForTimeframe(event.getProvider(), event.getInstId(), timeframe);
                    } catch (Exception e) {
                        log.error("加载K线数据失败 - provider: {}, instId: {}, timeframe: {}", event.getProvider(),
                                event.getInstId(), timeframe, e);
                    }
                }
                log.debug("K线数据加载完成 - provider: {}, instId: {}", event.getProvider(), event.getInstId());
            } catch (Exception e) {
                log.error("处理合约更新事件失败 - provider: {}, instId: {}", event.getProvider(), event.getInstId(), e);
            }
        }, taskExecutor);
    }

    /**
     * 为指定时间周期加载K线数据（带并发控制）
     */
    private void loadKlineDataForTimeframe(String provider, String instId, String timeframe) {
        String apiName = String.format("%s_%s_%s", provider, instId, timeframe);

        try {
            log.debug("开始加载K线数据 - provider: {}, instId: {}, timeframe: {}", provider, instId, timeframe);
            // 获取API密钥
            ApiKey apiKey = apiKeyService.getDefaultApiKey();
            if (null == apiKey) {
                log.warn("未找到可用的API密钥，跳过K线数据加载 - {}", apiName);
                return;
            }
            // 使用并发控制和重试机制调用统一API
            List<CexMarketCandle> candles = callApiWithRateLimit(() -> unifiedCexApiService.getMarketCandles(apiKey, instId, timeframe, 100), apiName);
            if (candles.isEmpty()) {
                log.warn("未获取到K线数据 - provider: {}, instId: {}, timeframe: {}", provider, instId, timeframe);
                return;
            }
            // 保存到数据库
            @SuppressWarnings("SpringTransactionalMethodCallsInspection")
            int savedCount = saveKlineData(provider, instId, timeframe, candles);
            log.debug("K线数据加载完成 - provider: {}, instId: {}, timeframe: {}, 获取数量: {}, 保存数量: {}", provider, instId,
                    timeframe, candles.size(), savedCount);
        } catch (Exception e) {
            log.error("加载K线数据异常 - provider: {}, instId: {}, timeframe: {}", provider, instId, timeframe, e);
        }
    }

    /**
     * 获取统一图表数据（K线 + 技术指标）
     * 提供统一的包装接口，一次性获取K线数据、技术指标、标记价格等信息
     *
     * @param apiKey  API密钥（用于可能的私有数据查询）
     * @param request 统一图表数据查询请求
     * @return 完整图表数据
     */
    public UnifiedChartDataResponse getUnifiedChartData(ApiKey apiKey, UnifiedChartDataRequest request) {
        // 1. 参数验证
        if (request == null || !StringUtils.hasText(request.getInstId()) || !StringUtils.hasText(request.getTimeframe())) {
            log.warn("[getUnifiedChartData] 查询请求参数无效 - instId: {}, timeframe: {}",
                    request != null ? request.getInstId() : null,
                    request != null ? request.getTimeframe() : null);
            return null;
        }

        // 验证limit不超过最大值
        if (request.getLimit() == null || request.getLimit() > 240) {
            request.setLimit(200);
        }

        // 生成缓存键
        String cacheKey = generateCacheKey(request);
        UnifiedChartDataResponse cachedResponse = unifiedChartCache.getIfPresent(cacheKey);
        if (cachedResponse != null) {
            log.debug("[getUnifiedChartData] 缓存命中 - key: {}", cacheKey);
            return cachedResponse;
        }

        log.debug("[getUnifiedChartData] 开始查询统一图表数据 - instId: {}, timeframe: {}, limit: {}, indicators: {}, startMills: {}",
                request.getInstId(), request.getTimeframe(), request.getLimit(), request.getIndicators(), request.getStartMills());

        try {
            // 2. 计算实际需要获取的数据量（考虑技术指标需求）
            int actualLimit = calculateDataRequirement(request);

            // 3. 获取K线数据(使用统一API)
            String apiName = String.format("unified_chart_%s_%s", request.getInstId(), request.getTimeframe());
            List<CexMarketCandle> candles = callApiWithRateLimit(
                    () -> unifiedCexApiService.getMarketCandles(apiKey, request.getInstId(), request.getTimeframe(), actualLimit, request.getStartMills()),
                    apiName
            );

            if (CollectionUtils.isEmpty(candles)) {
                log.warn("[getUnifiedChartData] 获取K线数据为空 - instId: {}, timeframe: {}",
                        request.getInstId(), request.getTimeframe());
                return buildErrorResponse(request, "K线数据为空", "CANDLES_ONLY");
            }

            // 记录数据获取详情
            log.debug("[getUnifiedChartData] K线数据获取详情 - 用户请求: {}, 实际获取: {}, 指标需求: {}",
                    request.getLimit(), actualLimit, actualLimit - request.getLimit());

            // 转换为MarketCandleModel
            List<MarketCandleModel> candleModels = candles.stream()
                    .map(candle -> {
                        MarketCandleModel model = new MarketCandleModel();
                        model.setTimestamp(candle.getTimestamp());
                        model.setOpen(candle.getOpen());
                        model.setHigh(candle.getHigh());
                        model.setLow(candle.getLow());
                        model.setClose(candle.getClose());
                        model.setVolume(candle.getVolume());
                        model.setVolumeCcy(candle.getVolumeCcy());
                        model.setVolCcyQuote(candle.getVolCcyQuote());
                        model.setConfirm(candle.getConfirm());
                        return model;
                    })
                    .collect(Collectors.toList());

            // 3. 构建返回对象
            UnifiedChartDataResponse.UnifiedChartDataResponseBuilder builder = UnifiedChartDataResponse.builder()
                    .instId(request.getInstId())
                    .timeframe(request.getTimeframe())
                    .limit(request.getLimit())
                    .candles(candleModels)
                    .timestamp(System.currentTimeMillis());

            // 4. 计算技术指标（如果请求）
            Map<String, IndicatorsDataDTO> indicatorsData = null;
            boolean indicatorError = false;

            if (request.getIndicators() != null && !request.getIndicators().isEmpty()) {
                try {
                    indicatorsData = calculateIndicators(candleModels, request);

                    // 截断数据到用户请求量
                    if (actualLimit > request.getLimit()) {
                        truncateCandlesAndIndicators(candleModels, indicatorsData, request.getLimit());
                    }

                    builder.indicators(indicatorsData);
                } catch (Exception e) {
                    log.error("[getUnifiedChartData] 计算技术指标失败", e);
                    indicatorError = true;
                }
            }

            // 5. 获取标记价格(如果请求)
            if (Boolean.TRUE.equals(request.getIncludeMarkPrice())) {
                try {
                    // 使用统一方法获取标记价格
                    List<CexMarkPrice> markPrices = unifiedCexApiService.getMarkPrice(apiKey, request.getInstId());
                    if (!markPrices.isEmpty()) {
                        builder.markPrice(markPrices.get(0).getMarkPrice());
                    }
                } catch (Exception e) {
                    log.error("[getUnifiedChartData] 获取标记价格失败 - instId: {}", request.getInstId(), e);
                }
            }

            // 6. 获取资金费率(如果请求)
            if (Boolean.TRUE.equals(request.getIncludeFundingRate())) {
                try {
                    // 使用统一方法获取资金费率
                    List<CexFundingRate> fundingRates = unifiedCexApiService.getFundingRate(apiKey, request.getInstId());
                    if (!fundingRates.isEmpty()) {
                        builder.fundingRate(fundingRates.get(0));
                    }
                } catch (Exception e) {
                    log.error("[getUnifiedChartData] 获取资金费率失败 - instId: {}", request.getInstId(), e);
                }
            }

            // 设置数据状态
            if (indicatorError) {
                builder.dataStatus("PARTIAL");
            } else if (request.getIndicators() == null || request.getIndicators().isEmpty()) {
                builder.dataStatus("CANDLES_ONLY");
            } else {
                builder.dataStatus("FULL");
            }

            UnifiedChartDataResponse response = builder.build();

            // 只缓存成功的完整响应,不缓存失败或部分响应,允许后续重试
            if (isCacheableResponse(response)) {
                unifiedChartCache.put(cacheKey, response);
                log.debug("[getUnifiedChartData] 已缓存统一图表数据 - key: {}, status: {}", cacheKey, response.getDataStatus());
            } else {
                log.debug("[getUnifiedChartData] 响应不可缓存,跳过缓存 - status: {}, hasError: {}, candlesSize: {}",
                        response.getDataStatus(),
                        response.getErrorMessage() != null,
                        response.getCandles() != null ? response.getCandles().size() : 0);
            }

            log.debug("[getUnifiedChartData] 查询成功 - instId: {}, timeframe: {}, candles: {}, indicators: {}, status: {}",
                    request.getInstId(), request.getTimeframe(), candleModels.size(),
                    indicatorsData != null ? indicatorsData.size() : 0, response.getDataStatus());

            return response;

        } catch (Exception e) {
            log.error("[getUnifiedChartData] 查询失败 - instId: {}, timeframe: {}",
                    request.getInstId(), request.getTimeframe(), e);
            return buildErrorResponse(request, "查询失败: " + e.getMessage(), "CANDLES_ONLY");
        }
    }

    /**
     * 计算技术指标
     * 复用CommonTechnicalIndicatorService进行计算
     */
    private Map<String, IndicatorsDataDTO> calculateIndicators(List<MarketCandleModel> candles, UnifiedChartDataRequest request) {
        Map<String, IndicatorsDataDTO> indicatorsData = new HashMap<>();

        try {
            // 转换K线数据为OHLC格式
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

            // 构建技术指标配置列表
            List<IndicatorCalculationRequest.IndicatorConfig> configs = new ArrayList<>();

            for (String indicator : request.getIndicators()) {
                String upperIndicator = indicator.toUpperCase();

                // 获取该指标的周期参数
                List<String> periodParams = null;
                if (request.getIndicatorPeriods() != null) {
                    periodParams = request.getIndicatorPeriods().get(upperIndicator);
                }

                IndicatorCalculationRequest.IndicatorConfig config =
                        buildIndicatorConfig(upperIndicator, periodParams);

                if (config != null) {
                    configs.add(config);
                }
            }

            if (configs.isEmpty()) {
                log.debug("[calculateIndicators] 未配置有效的技术指标");
                return indicatorsData;
            }

            // 构建计算请求
            IndicatorCalculationRequest calcRequest = IndicatorCalculationRequest.builder()
                    .ohlcData(ohlcItems)
                    .indicators(configs)
                    .build();

            // 执行计算
            IndicatorCalculationResponse response = commonTechnicalIndicatorService.calculate(calcRequest);

            // 解析结果
            indicatorsData = parseIndicatorResponse(response, ohlcItems);

        } catch (Exception e) {
            log.error("[calculateIndicators] 计算技术指标失败", e);
        }

        return indicatorsData;
    }

    /**
     * 构建技术指标配置
     */
    private IndicatorCalculationRequest.IndicatorConfig buildIndicatorConfig(String indicatorName, List<String> periodParams) {

        List<Integer> periods = new ArrayList<>();

        switch (indicatorName) {
            case "EMA":
            case "SMA":
            case "WMA":
            case "RSI":
                // 使用配置的周期参数，或使用默认值
                if (periodParams != null && !periodParams.isEmpty()) {
                    periods = periodParams.stream()
                            .map(Integer::parseInt)
                            .collect(Collectors.toList());
                } else {
                    // 默认周期
                    if ("RSI".equals(indicatorName)) {
                        periods = List.of(14);
                    } else {
                        periods = List.of(20);
                    }
                }
                break;

            case "BOLL":
                // BOLL支持多周期，格式: "周期_标准差"
                if (periodParams != null && !periodParams.isEmpty()) {
                    // 处理所有BOLL周期参数（暂使用固定标准差2.0）
                    for (String param : periodParams) {
                        String[] parts = param.split("_");
                        if (parts.length >= 1) {
                            try {
                                periods.add(Integer.parseInt(parts[0]));
                            } catch (NumberFormatException e) {
                                log.warn("[buildIndicatorConfig] 无效的BOLL周期参数: {}", param);
                            }
                        }
                    }
                } else {
                    periods = List.of(20); // 默认周期20
                }
                break;

            case "MACD":
                // MACD固定周期
                periods = Arrays.asList(12, 26, 9);
                break;

            case "KDJ":
            case "CCI":
            case "ATR":
            case "OBV":
            case "ADX":
                if (periodParams != null && !periodParams.isEmpty()) {
                    periods = periodParams.stream()
                            .map(Integer::parseInt)
                            .collect(Collectors.toList());
                } else {
                    if ("KDJ".equals(indicatorName)) {
                        periods = List.of(9, 14, 21);
                    } else if ("OBV".equals(indicatorName)) {
                        periods = List.of(20, 60);
                    } else {
                        periods = List.of(14, 20, 30);
                    }
                }
                break;

            default:
                log.warn("[buildIndicatorConfig] 不支持的技术指标: {}", indicatorName);
                return null;
        }

        return IndicatorCalculationRequest.IndicatorConfig.builder()
                .name(indicatorName)
                .periods(periods)
                .build();
    }

    /**
     * 解析技术指标计算结果
     * 简化版解析，直接返回原始结果
     */
    Map<String, IndicatorsDataDTO> parseIndicatorResponse(IndicatorCalculationResponse response,
                                                          List<IndicatorCalculationRequest.OhlcItem> ohlcItems) {
        Map<String, IndicatorsDataDTO> indicatorsData = new HashMap<>();

        if (response == null || response.getResults() == null) {
            return indicatorsData;
        }

        for (Map.Entry<String, List<IndicatorCalculationResponse.IndicatorResult>> entry :
                response.getResults().entrySet()) {

            String indicatorName = entry.getKey();
            List<IndicatorCalculationResponse.IndicatorResult> results = entry.getValue();

            List<IndicatorsDataDTO.IndicatorDataPoint> values = new ArrayList<>();

            for (int i = 0; i < ohlcItems.size(); i++) {
                // 为每个周期提取数据
                Map<String, Object> multiPeriodValues = new HashMap<>();

                for (IndicatorCalculationResponse.IndicatorResult result : results) {
                    String period = result.getPeriod();
                    String key = indicatorName.toLowerCase() + "_" + period;

                    Object value = null;
                    if ("KDJ".equalsIgnoreCase(indicatorName)
                            && result.getKdjValues() != null
                            && i < result.getKdjValues().size()) {
                        value = result.getKdjValues().get(i);
                    } else if (result.getValues() != null && i < result.getValues().size()) {
                        IndicatorCalculationResponse.SingleValue sv = result.getValues().get(i);
                        if (sv != null) {
                            value = sv.getValue();
                        }
                    } else if (result.getBollValues() != null && i < result.getBollValues().size()) {
                        value = result.getBollValues().get(i);
                    } else if (result.getMacdValues() != null && i < result.getMacdValues().size()) {
                        value = result.getMacdValues().get(i);
                    } else if (result.getKdjValues() != null && i < result.getKdjValues().size()) {
                        value = result.getKdjValues().get(i);
                    }

                    multiPeriodValues.put(key, value);
                }

                // 构建数据点
                IndicatorsDataDTO.IndicatorDataPoint point = IndicatorsDataDTO.IndicatorDataPoint.builder()
                        .timestamp(ohlcItems.get(i).getTimestamp())
                        .multiPeriodValues(multiPeriodValues)
                        .build();
                values.add(point);
            }

            // 反转values（Latest First）
            Collections.reverse(values);

            // 构建DTO
            IndicatorsDataDTO dto = IndicatorsDataDTO.builder()
                    .metricName(indicatorName)
                    .values(values)
                    .build();
            indicatorsData.put(indicatorName, dto);
        }

        return indicatorsData;
    }

    /**
     * 生成缓存键
     */
    private String generateCacheKey(UnifiedChartDataRequest request) {
        StringBuilder keyBuilder = new StringBuilder("unified_chart_");
        keyBuilder.append(request.getInstId()).append("_");
        keyBuilder.append(request.getTimeframe()).append("_");
        keyBuilder.append(request.getLimit()).append("_");

        // 添加开始时间戳到缓存键
        if (request.getStartMills() != null) {
            keyBuilder.append("start_").append(request.getStartMills()).append("_");
        }

        if (request.getIndicators() != null && !request.getIndicators().isEmpty()) {
            keyBuilder.append(String.join(",", request.getIndicators())).append("_");

            if (request.getIndicatorPeriods() != null) {
                request.getIndicatorPeriods().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            keyBuilder.append(entry.getKey()).append("=");
                            keyBuilder.append(String.join("-", entry.getValue())).append(";");
                        });
            }
        }

        return keyBuilder.toString();
    }

    /**
     * 计算技术指标所需的数据量
     * 确保有足够的K线数据进行准确的技术指标计算
     *
     * @param request 统一图表数据请求
     * @return 实际需要获取的数据量
     */
    private int calculateDataRequirement(UnifiedChartDataRequest request) {
        // 用户请求的数据量（默认200）
        int userLimit = request.getLimit() != null ? request.getLimit() : 200;

        // 如果没有请求技术指标，直接返回用户请求量
        if (request.getIndicators() == null || request.getIndicators().isEmpty()) {
            return userLimit;
        }

        int maxRequiredPeriod = 0;

        // 分析各技术指标的周期需求
        for (String indicator : request.getIndicators()) {
            String upperIndicator = indicator.toUpperCase();
            int requirement = 0;

            switch (upperIndicator) {
                case "EMA":
                case "SMA":
                case "WMA":
                    // 从indicatorPeriods获取周期参数
                    List<String> periods = request.getIndicatorPeriods() != null ?
                            request.getIndicatorPeriods().get(upperIndicator) : null;
                    if (periods != null && !periods.isEmpty()) {
                        requirement = periods.stream()
                                .map(Integer::parseInt)
                                .max(Integer::compareTo)
                                .orElse(60);
                    } else {
                        requirement = 60; // 默认周期
                    }
                    break;

                case "RSI":
                    List<String> rsiPeriods = request.getIndicatorPeriods() != null ?
                            request.getIndicatorPeriods().get("RSI") : null;
                    if (rsiPeriods != null && !rsiPeriods.isEmpty()) {
                        int maxPeriod = rsiPeriods.stream()
                                .map(Integer::parseInt)
                                .max(Integer::compareTo)
                                .orElse(14);
                        requirement = maxPeriod + 1; // RSI需要period+1个数据点
                    } else {
                        requirement = 15; // 默认14+1
                    }
                    break;

                case "BOLL":
                    List<String> bollPeriods = request.getIndicatorPeriods() != null ?
                            request.getIndicatorPeriods().get("BOLL") : null;
                    if (bollPeriods != null && !bollPeriods.isEmpty()) {
                        // BOLL参数格式: "周期_标准差"，如 "20_2.0"
                        requirement = bollPeriods.stream()
                                .map(param -> param.split("_")[0])
                                .map(Integer::parseInt)
                                .max(Integer::compareTo)
                                .orElse(20);
                    } else {
                        requirement = 20; // 默认周期
                    }
                    break;

                case "MACD":
                    // MACD固定需要 slowPeriod + signalPeriod = 26 + 9 = 35个数据点
                    requirement = 35;
                    break;
                case "CCI":
                case "KDJ":
                case "ATR":
                case "OBV":
                case "ADX":
                    List<String> cciKdjAtObvPeriods = request.getIndicatorPeriods() != null ?
                            request.getIndicatorPeriods().get(upperIndicator) : null;
                    if (cciKdjAtObvPeriods != null && !cciKdjAtObvPeriods.isEmpty()) {
                        requirement = cciKdjAtObvPeriods.stream()
                                .map(Integer::parseInt)
                                .max(Integer::compareTo)
                                .orElse(60);
                    } else {
                        requirement = 60; // 默认周期
                    }
                    break;
                default:
                    log.warn("[calculateDataRequirement] 未知的技术指标: {}", indicator);
                    continue;
            }

            maxRequiredPeriod = Math.max(maxRequiredPeriod, requirement);
            log.debug("[calculateDataRequirement] 指标 {} 周期需求: {}, 当前最大需求: {}",
                    indicator, requirement, maxRequiredPeriod);
        }

        // 计算最终数据获取量：用户请求量 + 技术指标需求
        int result = userLimit + maxRequiredPeriod;

        // 设置最大限制
        final int MAX_USER_LIMIT = 240;
        final int MAX_INDICATOR_PERIOD = 60;
        result = Math.min(result, MAX_USER_LIMIT + MAX_INDICATOR_PERIOD);

        log.debug("[calculateDataRequirement] 数据需求计算完成 - 用户请求: {}, 技术指标需求: {}, 最终获取: {}",
                userLimit, maxRequiredPeriod, result);

        return result;
    }

    /**
     * 截断K线数据和技术指标数据到用户请求的数量
     * 保留最新的数据点，确保返回数据量符合用户期望
     *
     * @param candles        K线数据列表
     * @param indicatorsData 技术指标数据
     * @param targetSize     目标数据量
     */
    private void truncateCandlesAndIndicators(List<MarketCandleModel> candles, Map<String, IndicatorsDataDTO> indicatorsData,
                                              int targetSize) {
        // 参数校验：如果数据为空或数量不足，无需截断
        if (null == candles || candles.size() <= targetSize) {
            return;
        }

        // 截断K线数据 - 从开头截取最新的targetSize个数据点
        List<MarketCandleModel> truncatedCandles = new ArrayList<>(candles.subList(0, targetSize));
        candles.clear();
        candles.addAll(truncatedCandles);

        // 截断技术指标数据 - 从开头截取最新的targetSize个数据点
        if (null != indicatorsData && !indicatorsData.isEmpty()) {
            for (Map.Entry<String, IndicatorsDataDTO> entry : indicatorsData.entrySet()) {
                IndicatorsDataDTO dto = entry.getValue();
                if (dto != null && dto.getValues() != null && dto.getValues().size() > targetSize) {
                    // 截断values列表
                    List<IndicatorsDataDTO.IndicatorDataPoint> truncatedValues =
                            new ArrayList<>(dto.getValues().subList(0, targetSize));
                    dto.setValues(truncatedValues);
                }
            }
        }

        log.debug("[truncateCandlesAndIndicators] 数据截断完成 - 原始数据量: {}, 目标数据量: {}",
                candles.size(), targetSize);
    }

    /**
     * 检查响应是否可以被缓存
     * 只有完整的成功响应才会被缓存,失败的响应不会被缓存,允许后续重试
     *
     * @param response 统一图表数据响应
     * @return true-可以缓存,false-不缓存
     */
    private boolean isCacheableResponse(UnifiedChartDataResponse response) {
        // 基本有效性检查
        if (response == null) {
            return false;
        }

        // 有错误消息的响应不缓存
        if (response.getErrorMessage() != null) {
            return false;
        }

        // K线数据为空或不存在的响应不缓存
        if (response.getCandles() == null || response.getCandles().isEmpty()) {
            return false;
        }

        // 只有完整成功(FULL)状态的响应才缓存
        // PARTIAL和CANDLES_ONLY状态不缓存,允许重新计算
        return "FULL".equals(response.getDataStatus());
    }

    /**
     * 构建错误响应
     */
    private UnifiedChartDataResponse buildErrorResponse(UnifiedChartDataRequest request, String errorMessage, String status) {
        return UnifiedChartDataResponse.builder()
                .instId(request.getInstId())
                .timeframe(request.getTimeframe())
                .limit(request.getLimit())
                .candles(Collections.emptyList())
                .indicators(Collections.emptyMap())
                .timestamp(System.currentTimeMillis())
                .dataStatus(status)
                .errorMessage(errorMessage)
                .build();
    }

}

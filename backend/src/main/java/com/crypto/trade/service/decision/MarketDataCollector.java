package com.crypto.trade.service.decision;

import com.crypto.trade.dto.Top30MarketTickerDto;
import com.crypto.trade.model.MarketContext;
import com.crypto.trade.model.TechnicalIndicator;
import com.crypto.trade.service.TechnicalIndicatorService;
import com.crypto.trade.service.UnifiedMarketTickerService;
import com.crypto.trade.service.unified.UnifiedBalanceService;
import com.crypto.trade.service.unified.UnifiedPositionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MarketDataCollector
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class MarketDataCollector {

    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH");
    @Autowired
    private UnifiedBalanceService unifiedBalanceService;
    @Autowired
    private UnifiedPositionService unifiedPositionService;
    @Autowired
    private UnifiedMarketTickerService unifiedMarketTickerService;
    @Autowired
    private TechnicalIndicatorService technicalIndicatorService;

    /**
     * 收集完整的市场数据上下文
     *
     * @param userId 用户ID
     * @return 市场上下文
     */
    public MarketContext collectMarketData(String userId) {
        try {
            log.debug("开始收集市场数据: userId={}", userId);

            // 1. 收集Top30市场数据
            Top30MarketTickerDto top30Data = collectTop30MarketData();

            // 2. 收集用户账户数据
            // AccountEquity accountEquity = collectAccountEquity(userId);

            // 3. 收集技术指标数据
            List<TechnicalIndicator> technicalIndicators = collectTechnicalIndicators(top30Data);

            // 4. 分析市场状况
            MarketContext.MarketCondition marketCondition = analyzeMarketCondition(top30Data);

            // 5. 分析市场情绪
            MarketContext.MarketSentiment marketSentiment = analyzeMarketSentiment(top30Data);

            // 6. 分析市场波动性
            MarketContext.MarketVolatility marketVolatility = analyzeMarketVolatility(top30Data);

            // 构建市场上下文
            MarketContext context = MarketContext.builder()
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .marketCondition(marketCondition)
                    .availableSymbols(extractSymbols(top30Data))
                    .currentPrices(extractPrices(top30Data))
                    .volume24h(extractVolumes(top30Data))
                    .priceChange24h(extractPriceChanges(top30Data))
                    .technicalIndicators(technicalIndicators)
                    .sentiment(marketSentiment)
                    .volatility(marketVolatility)
                    .build();

            log.debug("市场数据收集完成: userId={}, symbols={}", userId, context.getAvailableSymbols().size());

            return context;

        } catch (Exception e) {
            log.error("收集市场数据失败: userId={}", userId, e);
            return createEmptyMarketContext(userId);
        }
    }

    /**
     * 收集特定合约的数据
     *
     * @param userId     用户ID
     * @param instrument 合约代码
     * @return 市场上下文
     */
    public MarketContext collectInstrumentData(String userId, String instrument) {
        try {
            log.debug("收集合约数据: userId={}, instrument={}", userId, instrument);

            // 收集特定合约的市场数据
            Map<String, BigDecimal> currentPrices = new HashMap<>();
            Map<String, BigDecimal> volumes = new HashMap<>();
            Map<String, BigDecimal> priceChanges = new HashMap<>();

            // 这里可以调用专门的合约数据API
            // 目前使用简化的实现
            currentPrices.put(instrument, BigDecimal.ZERO);
            volumes.put(instrument, BigDecimal.ZERO);
            priceChanges.put(instrument, BigDecimal.ZERO);

            // 收集该合约的技术指标
            List<TechnicalIndicator> indicators = new ArrayList<>();
            // TODO: 需要传入正确的参数，暂时返回空列表
            // technicalIndicatorService.getTechnicalIndicators(instrument, "1H", null);

            MarketContext context = MarketContext.builder()
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .marketCondition(MarketContext.MarketCondition.UNCERTAIN)
                    .availableSymbols(Collections.singletonList(instrument))
                    .currentPrices(currentPrices)
                    .volume24h(volumes)
                    .priceChange24h(priceChanges)
                    .technicalIndicators(indicators)
                    .sentiment(MarketContext.MarketSentiment.NEUTRAL)
                    .volatility(MarketContext.MarketVolatility.MEDIUM)
                    .build();

            log.debug("合约数据收集完成: userId={}, instrument={}", userId, instrument);

            return context;

        } catch (Exception e) {
            log.error("收集合约数据失败: userId={}, instrument={}", userId, instrument, e);
            return createEmptyMarketContext(userId);
        }
    }

    /**
     * 收集Top30市场数据
     */
    private Top30MarketTickerDto collectTop30MarketData() {
        try {
            String currentHourStr = LocalDateTime.now().format(HOUR_FORMATTER);
            return unifiedMarketTickerService.getTop30MarketTickers("OKX", "SWAP", currentHourStr);
        } catch (Exception e) {
            log.warn("获取Top30市场数据失败，使用空数据", e);
            return new Top30MarketTickerDto(); // 返回空对象
        }
    }

    /**
     * 收集账户权益数据
     */
    /*
    private AccountEquity collectAccountEquity(String userId) {
        try {
            return unifiedBalanceService.getAccountEquity(userId);
        } catch (Exception e) {
            log.warn("获取账户权益失败: userId={}", userId, e);
            return null;
        }
    }
    */

    /**
     * 收集技术指标数据
     */
    private List<TechnicalIndicator> collectTechnicalIndicators(Top30MarketTickerDto top30Data) {
        try {
            // TODO: TechnicalIndicatorService需要不同的参数，暂时返回空列表
            // 当前service没有getBatchIndicators方法
            return new ArrayList<>();
        } catch (Exception e) {
            log.warn("获取技术指标失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 分析市场状况
     */
    private MarketContext.MarketCondition analyzeMarketCondition(Top30MarketTickerDto top30Data) {
        if (top30Data == null || top30Data.getTop30Tickers().isEmpty()) {
            return MarketContext.MarketCondition.UNCERTAIN;
        }

        long risingCount = top30Data.getTop30Tickers().stream()
                .mapToLong(ticker -> ticker.getChange24hPercent().compareTo(BigDecimal.ZERO) > 0 ? 1 : 0)
                .sum();

        long fallingCount = top30Data.getTop30Tickers().stream()
                .mapToLong(ticker -> ticker.getChange24hPercent().compareTo(BigDecimal.ZERO) < 0 ? 1 : 0)
                .sum();

        double risingRatio = (double) risingCount / top30Data.getTop30Tickers().size();

        if (risingRatio >= 0.7) {
            return MarketContext.MarketCondition.BULL_MARKET;
        } else if (risingRatio <= 0.3) {
            return MarketContext.MarketCondition.BEAR_MARKET;
        } else {
            return MarketContext.MarketCondition.SIDEWAYS;
        }
    }

    /**
     * 分析市场情绪
     */
    private MarketContext.MarketSentiment analyzeMarketSentiment(Top30MarketTickerDto top30Data) {
        if (top30Data == null || top30Data.getTop30Tickers().isEmpty()) {
            return MarketContext.MarketSentiment.NEUTRAL;
        }

        // 基于价格变化幅度分析情绪
        OptionalDouble avgChange = top30Data.getTop30Tickers().stream()
                .mapToDouble(ticker -> ticker.getChange24hPercent().doubleValue())
                .average();

        if (avgChange.isPresent()) {
            double avgChangeValue = avgChange.getAsDouble();
            if (avgChangeValue > 5.0) {
                return MarketContext.MarketSentiment.EXTREME_GREED;
            } else if (avgChangeValue > 2.0) {
                return MarketContext.MarketSentiment.GREED;
            } else if (avgChangeValue < -5.0) {
                return MarketContext.MarketSentiment.EXTREME_FEAR;
            } else if (avgChangeValue < -2.0) {
                return MarketContext.MarketSentiment.FEAR;
            } else {
                return MarketContext.MarketSentiment.NEUTRAL;
            }
        }

        return MarketContext.MarketSentiment.NEUTRAL;
    }

    /**
     * 分析市场波动性
     */
    private MarketContext.MarketVolatility analyzeMarketVolatility(Top30MarketTickerDto top30Data) {
        if (top30Data == null || top30Data.getTop30Tickers().isEmpty()) {
            return MarketContext.MarketVolatility.MEDIUM;
        }

        // 基于价格变化的标准差分析波动性
        double[] changes = top30Data.getTop30Tickers().stream()
                .mapToDouble(ticker -> Math.abs(ticker.getChange24hPercent().doubleValue()))
                .toArray();

        double avgChange = Arrays.stream(changes).average().orElse(0.0);
        double variance = Arrays.stream(changes)
                .map(change -> Math.pow(change - avgChange, 2))
                .average().orElse(0.0);
        double stdDev = Math.sqrt(variance);

        if (stdDev > 10.0) {
            return MarketContext.MarketVolatility.EXTREME;
        } else if (stdDev > 5.0) {
            return MarketContext.MarketVolatility.HIGH;
        } else if (stdDev > 2.0) {
            return MarketContext.MarketVolatility.MEDIUM;
        } else {
            return MarketContext.MarketVolatility.LOW;
        }
    }

    /**
     * 提取合约代码列表
     */
    private List<String> extractSymbols(Top30MarketTickerDto top30Data) {
        if (top30Data == null || top30Data.getTop30Tickers().isEmpty()) {
            return new ArrayList<>();
        }

        return top30Data.getTop30Tickers().stream()
                .map(ticker -> ticker.getInstId())
                .collect(Collectors.toList());
    }

    /**
     * 提取当前价格
     */
    private Map<String, BigDecimal> extractPrices(Top30MarketTickerDto top30Data) {
        Map<String, BigDecimal> prices = new HashMap<>();
        if (top30Data != null && top30Data.getTop30Tickers() != null) {
            top30Data.getTop30Tickers().forEach(ticker -> {
                prices.put(ticker.getInstId(), ticker.getLast());
            });
        }
        return prices;
    }

    /**
     * 提取24小时交易量
     */
    private Map<String, BigDecimal> extractVolumes(Top30MarketTickerDto top30Data) {
        Map<String, BigDecimal> volumes = new HashMap<>();
        if (top30Data != null && top30Data.getTop30Tickers() != null) {
            top30Data.getTop30Tickers().forEach(ticker -> {
                volumes.put(ticker.getInstId(), ticker.getVol24h());
            });
        }
        return volumes;
    }

    /**
     * 提取24小时价格变化
     */
    private Map<String, BigDecimal> extractPriceChanges(Top30MarketTickerDto top30Data) {
        Map<String, BigDecimal> changes = new HashMap<>();
        if (top30Data != null && top30Data.getTop30Tickers() != null) {
            top30Data.getTop30Tickers().forEach(ticker -> {
                changes.put(ticker.getInstId(), ticker.getChange24hPercent());
            });
        }
        return changes;
    }

    /**
     * 创建空的市场上下文
     */
    private MarketContext createEmptyMarketContext(String userId) {
        return MarketContext.builder()
                .userId(userId)
                .timestamp(LocalDateTime.now())
                .marketCondition(MarketContext.MarketCondition.UNCERTAIN)
                .availableSymbols(new ArrayList<>())
                .currentPrices(new HashMap<>())
                .volume24h(new HashMap<>())
                .priceChange24h(new HashMap<>())
                .technicalIndicators(new ArrayList<>())
                .sentiment(MarketContext.MarketSentiment.NEUTRAL)
                .volatility(MarketContext.MarketVolatility.MEDIUM)
                .build();
    }

    /**
     * 获取数据收集统计信息
     */
    public DataCollectionStats getCollectionStats() {
        try {
            Top30MarketTickerDto top30Data = collectTop30MarketData();
            int symbolCount = top30Data != null ? top30Data.getTop30Tickers().size() : 0;
            int indicatorCount = 0;

            if (symbolCount > 0) {
                List<String> symbols = extractSymbols(top30Data);
                List<TechnicalIndicator> indicators = collectTechnicalIndicators(top30Data);
                indicatorCount = indicators.size();
            }

            return DataCollectionStats.builder()
                    .top30Symbols(symbolCount)
                    .technicalIndicators(indicatorCount)
                    .lastUpdateTime(LocalDateTime.now())
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("获取数据收集统计失败", e);
            return DataCollectionStats.builder()
                    .success(false)
                    .errorMessage("统计获取失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 数据收集统计信息
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DataCollectionStats {
        private int top30Symbols;
        private int technicalIndicators;
        private LocalDateTime lastUpdateTime;
        private boolean success;
        private String errorMessage;
    }
}
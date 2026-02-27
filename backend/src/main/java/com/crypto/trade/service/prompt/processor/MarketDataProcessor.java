package com.crypto.trade.service.prompt.processor;

import com.crypto.trade.dto.MarketTickerDto;
import com.crypto.trade.dto.cex.model.CexContractInfo;
import com.crypto.trade.dto.cex.model.CexMarketCandle;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.service.UnifiedInstrumentService;
import com.crypto.trade.service.UnifiedMarketTickerService;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.market.UnifiedPriceDataService;
import com.crypto.trade.service.prompt.AbstractPromptProcessor;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.service.prompt.PromptProcessException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * MarketDataProcessor
 * 处理器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataProcessor
        extends AbstractPromptProcessor {

    private final UnifiedMarketTickerService unifiedMarketTickerService;
    private final UnifiedInstrumentService unifiedInstrumentService;
    private final UnifiedPriceDataService unifiedPriceDataService;
    private final ApiKeyService apiKeyService;
    // private final TradingOrderService tradingOrderService; // 暂时保留备用
    // private final TechnicalIndicatorService technicalIndicatorService; // 暂时保留备用

    @Override
    public String getName() {
        return "MarketDataProcessor";
    }

    @Override
    public int getPriority() {
        return 50; // 中低优先级
    }

    @Override
    public boolean shouldExecute(PromptContext context) {
        // 总是尝试执行，让处理器自己决定是否需要获取数据
        return getBooleanParameter("enableMarketData", true);
    }

    @Override
    public SegmentModel process(PromptContext context) throws PromptProcessException {
        return safeProcess(context, () -> {
            // 检查是否启用市场数据
            boolean enableMarketData = getBooleanParameter("enableMarketData", true);
            if (!enableMarketData) {
                return SegmentModel.builder()
                        .content("")
                        .showTitle(false)
                        .build();
            }

            // 获取市场数据范围参数
            String marketDataRange = getStringParameter("marketDataRange", "Top15");
            String outputFormat = getStringParameter("outputFormat", "TABLE"); // TABLE, JSON, TEXT

            ApiKey apiKey = apiKeyService.getDecryptedKey(context.getApiKeyId());
            // 直接从数据源获取结构化数据
            List<MarketTickerDto> marketData = getMarketData(context, apiKey);

            if (marketData == null || marketData.isEmpty()) {
                return SegmentModel.builder()
                        .title(String.format("%s 合约概况", marketDataRange))
                        .content("无法获取市场数据")
                        .category(SegmentModel.Category.MARKET)
                        .priority(SegmentModel.Priority.MEDIUM_LOW)
                        .showTitle(true)
                        .addMetadata("processor", getName())
                        .addMetadata("processorPriority", getPriority())
                        .addMetadata("marketDataRange", marketDataRange)
                        .addMetadata("enableMarketData", enableMarketData)
                        .addMetadata("dataStatus", "UNAVAILABLE")
                        .build();
            }

            // 根据输出格式生成内容
            String content;
            switch (outputFormat.toUpperCase()) {
                case "JSON":
                    content = formatAsJson(marketData);
                    break;
                case "TEXT":
                    content = formatAsText(marketData);
                    break;
                case "TABLE":
                default:
                    content = formatAsTable(marketData);
                    break;
            }

            return SegmentModel.builder()
                    .title(String.format("%s 合约概况", marketDataRange))
                    .content(content)
                    .category(SegmentModel.Category.MARKET)
                    .priority(SegmentModel.Priority.MEDIUM_LOW)
                    .showTitle(true)
                    .addMetadata("processor", getName())
                    .addMetadata("processorPriority", getPriority())
                    .addMetadata("marketDataRange", marketDataRange)
                    .addMetadata("enableMarketData", enableMarketData)
                    .addMetadata("outputFormat", outputFormat)
                    .addMetadata("contractCount", marketData.size())
                    .addMetadata("dataStatus", "SUCCESS")
                    .build();
        });
    }

    /**
     * 获取市场数据 - 从 UnifiedMarketTickerService 获取 top15 合约(按交易量)
     */
    private List<MarketTickerDto> getMarketData(PromptContext context, ApiKey apiKey) {
        try {
            log.debug("开始获取市场数据 - 数据范围: Top15");

            // 从 UnifiedMarketTickerService 获取 top15 合约(按交易量)
            List<MarketTickerDto> marketTickers = unifiedMarketTickerService.getTopNContracts(15, "volume",
                    context.getApiKeyId());

            if (CollectionUtils.isEmpty(marketTickers)) {
                log.warn("市场数据为空");
                return null;
            }

            // 计算4H涨跌幅并获取ctVal
            for (MarketTickerDto ticker : marketTickers) {
                try {
                    // 计算4H涨跌幅
                    if (null == ticker.getChange4hPercent()) {
                        BigDecimal change4h = calculate4HChangePercent(ticker.getInstId(), apiKey);
                        ticker.setChange4hPercent(change4h);
                    }

                    // 获取合约面值
                    if (null == ticker.getCtVal()) {
                        BigDecimal ctVal = getCtVal(ticker.getInstId());
                        ticker.setCtVal(ctVal);
                    }

                    // 计算涨跌统计
                    RiseFallStats stats = calculateRiseFallStats(ticker.getInstId(), apiKey);
                    if (stats != null) {
                        ticker.setRiseProbability(stats.getRiseProbability());
                        ticker.setMaxRise(stats.getMaxRise());
                        ticker.setMaxFall(stats.getMaxFall());
                    }
                } catch (Exception e) {
                    log.debug("处理ticker数据失败: {}", ticker.getInstId(), e);
                }
            }

            log.debug("市场数据获取成功，合约数量: {}", marketTickers.size());
            return marketTickers;

        } catch (Exception e) {
            log.error("获取市场数据失败", e);
            return null;
        }
    }

    /**
     * 计算4H涨跌幅
     * <p>
     * 使用已完成的4H周期计算涨跌幅
     * 取倒数第3根K线的收盘价作为开盘价，倒数第2根K线的收盘价作为收盘价
     * </p>
     *
     * @param instId 合约ID
     * @param apiKey apiKey
     * @return 4H涨跌幅百分比，失败时返回null
     */
    private BigDecimal calculate4HChangePercent(String instId, @NonNull ApiKey apiKey) {
        try {
            // 使用UnifiedPriceDataService获取最近3根4H K线数据
            List<CexMarketCandle> candles = unifiedPriceDataService.getMarkPriceCandles(apiKey, instId, "4H", 3);
            if (!candles.isEmpty() && candles.size() >= 3) {
                // 4H收盘价：倒数第2根K线（已完成的周期）
                BigDecimal close4h = candles.get(candles.size() - 2).getClose();
                // 4H开盘价：倒数第3根K线（前一个周期）
                BigDecimal open4h = candles.get(candles.size() - 3).getClose();

                if (null != open4h && open4h.compareTo(BigDecimal.ZERO) > 0) {
                    return close4h.subtract(open4h)
                            .divide(open4h, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
                }
            }
        } catch (Exception e) {
            log.debug("计算4H涨跌幅失败: {}", instId, e);
        }
        return null;
    }

    /**
     * 计算涨跌统计（涨跌概率、最大涨幅、最大跌幅）
     * <p>
     * 基于最近100根4H K线计算：
     * - 涨跌概率：上涨K线数量占比
     * - 最大涨幅：单根K线的最大涨幅
     * - 最大跌幅：单根K线的最大跌幅
     * </p>
     *
     * @param instId 合约ID
     * @param apiKey apiKey
     * @return 涨跌统计对象，失败时返回null
     */
    private RiseFallStats calculateRiseFallStats(String instId, ApiKey apiKey) {
        try {
            // 获取最近100根4H K线
            List<CexMarketCandle> candles = unifiedPriceDataService.getMarkPriceCandles(apiKey, instId, "4H", 100);

            if (candles.isEmpty() || candles.size() < 10) {
                log.debug("K线数据不足，无法计算涨跌统计: {}，数量: {}", instId, candles.size());
                return null;
            }

            int riseCount = 0;
            BigDecimal maxRise = BigDecimal.ZERO;
            BigDecimal maxFall = BigDecimal.ZERO;

            // 遍历K线计算统计值
            for (CexMarketCandle candle : candles) {
                BigDecimal open = candle.getOpen();
                BigDecimal close = candle.getClose();

                if (open == null || close == null || open.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }

                // 计算涨跌幅
                BigDecimal change = close.subtract(open)
                        .divide(open, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));

                // 判断涨跌：close > open 为涨
                if (close.compareTo(open) > 0) {
                    riseCount++;
                    if (change.compareTo(maxRise) > 0) {
                        maxRise = change;
                    }
                } else {
                    if (change.compareTo(maxFall) < 0) {
                        maxFall = change;
                    }
                }
            }

            // 计算涨跌概率
            BigDecimal riseProbability = new BigDecimal(riseCount)
                    .divide(new BigDecimal(candles.size()), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

            return new RiseFallStats(
                    riseProbability.setScale(2, RoundingMode.HALF_UP),
                    maxRise.setScale(2, RoundingMode.HALF_UP),
                    maxFall.setScale(2, RoundingMode.HALF_UP)
            );

        } catch (Exception e) {
            log.warn("计算合约 {} 涨跌统计失败", instId, e);
            return null;
        }
    }

    /**
     * 获取合约面值(ctVal)
     *
     * @param instId 合约ID
     * @return 合约面值，失败时返回null
     */
    private BigDecimal getCtVal(String instId) {
        try {
            return unifiedInstrumentService.getCexContractInfo(instId)
                    .map(CexContractInfo::getCtVal)
                    .orElse(null);
        } catch (Exception e) {
            log.error("获取合约面值失败 - instId: {}", instId, e);
            return null;
        }
    }

    /**
     * 格式化为表格形式 - 显示全部字段
     */
    private String formatAsTable(List<MarketTickerDto> marketTickers) {
        if (CollectionUtils.isEmpty(marketTickers)) {
            return "暂无市场数据";
        }

        StringBuilder tableContent = new StringBuilder();

        // 添加表格标题
        tableContent.append("### 市场概况\n\n");

        // 创建表格头部 - 显示所有字段
        tableContent.append("| 排名 | 合约 | 最新价 | 开盘价 | 最高价 | 最低价 | 24H涨跌 | 4H涨跌 | 涨跌概率(4H) | 最大涨幅(4H) " +
                "| 最大跌幅(4H) | 最小交易单位 | 24H交易额(USDT) |\n");
        tableContent.append("|------|------|--------|--------|--------|--------|---------|---------|-------------" +
                "|-------------|-------------|-------------|-------------------|\n");

        // 为每个合约创建表格行
        for (MarketTickerDto ticker : marketTickers) {
            String row = formatTickerAsTableRow(ticker);
            tableContent.append(row).append("\n");
        }

        tableContent.append("\n\n");
        tableContent.append("\n涨跌概率(4H): 基于最近100根4小时K线，统计上涨K线占比\n");
        tableContent.append("\n最大涨幅(4H): 最近100根4小时K线中的最大单根涨幅\n");
        tableContent.append("\n最大跌幅(4H): 最近100根4小时K线中的最大单根跌幅\n");

        return tableContent.toString();
    }

    /**
     * 将单个行情数据格式化为表格行
     */
    private String formatTickerAsTableRow(MarketTickerDto ticker) {
        try {
            String rank = ticker.getRank() != null ? ticker.getRank().toString() : "-";
            String instId = ticker.getInstId() != null ? ticker.getInstId() : "-";
            String last = formatPrice(ticker.getLast());
            String open24h = formatPrice(ticker.getOpen24h());
            String high24h = formatPrice(ticker.getHigh24h());
            String low24h = formatPrice(ticker.getLow24h());
            String change24h = formatPercent(ticker.getChange24hPercent());
            String change4h = formatPercent(ticker.getChange4hPercent());
            String riseProbability = formatPercent2(ticker.getRiseProbability());
            String maxRise = formatPercent(ticker.getMaxRise());
            String maxFall = formatPercent(ticker.getMaxFall());
            String ctVal = formatPrice(ticker.getCtVal());
            String volume = formatVolume(ticker.getVolume24hUsdt());

            return String.format("| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |",
                    rank, instId, last, open24h, high24h, low24h, change24h, change4h,
                    riseProbability, maxRise, maxFall, ctVal, volume);

        } catch (Exception e) {
            log.debug("格式化行情行失败: {}", ticker.getInstId(), e);
            return "| - | - | - | - | - | - | - | - | - | - | - | - | - |";
        }
    }

    /**
     * 格式化价格
     */
    private String formatPrice(BigDecimal price) {
        return price == null ? "N/A" : price.stripTrailingZeros().toPlainString();
    }

    /**
     * 格式化涨跌幅
     */
    private String formatPercent(BigDecimal percent) {
        if (percent == null) {
            return "N/A";
        }
        String sign = percent.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        return sign + percent.stripTrailingZeros().toPlainString() + "%";
    }

    /**
     * 格式化涨跌幅（不带+号，用于涨跌概率等）
     */
    private String formatPercent2(BigDecimal percent) {
        if (percent == null) {
            return "N/A";
        }
        return percent.stripTrailingZeros().toPlainString() + "%";
    }

    /**
     * 格式化交易量
     */
    private String formatVolume(BigDecimal volume) {
        return volume == null ? "N/A" : volume.stripTrailingZeros().toPlainString();
    }

    /**
     * 格式化为JSON形式
     */
    private String formatAsJson(List<MarketTickerDto> marketTickers) {
        StringBuilder jsonContent = new StringBuilder();
        jsonContent.append("### 市场概况 (JSON格式)\n\n");
        jsonContent.append("```json\n");
        jsonContent.append("[\n");

        for (int i = 0; i < marketTickers.size(); i++) {
            MarketTickerDto ticker = marketTickers.get(i);
            jsonContent.append("  {\n");
            jsonContent.append(String.format("    \"rank\": %s,\n", ticker.getRank()));
            jsonContent.append(String.format("    \"instId\": \"%s\",\n", ticker.getInstId()));
            jsonContent.append(String.format("    \"last\": %s,\n", ticker.getLast()));
            jsonContent.append(String.format("    \"open24h\": %s,\n", ticker.getOpen24h()));
            jsonContent.append(String.format("    \"high24h\": %s,\n", ticker.getHigh24h()));
            jsonContent.append(String.format("    \"low24h\": %s,\n", ticker.getLow24h()));
            jsonContent.append(String.format("    \"change24hPercent\": %s,\n", ticker.getChange24hPercent()));
            jsonContent.append(String.format("    \"change4hPercent\": %s,\n", ticker.getChange4hPercent()));
            jsonContent.append(String.format("    \"rise_probability_4h\": %s,\n", ticker.getRiseProbability()));
            jsonContent.append(String.format("    \"max_rise_4h\": %s,\n", ticker.getMaxRise()));
            jsonContent.append(String.format("    \"max_fall_4h\": %s\n", ticker.getMaxFall()));
            jsonContent.append("  }");
            if (i < marketTickers.size() - 1) {
                jsonContent.append(",");
            }
            jsonContent.append("\n");
        }

        jsonContent.append("]\n");
        jsonContent.append("```");
        return jsonContent.toString();
    }

    /**
     * 格式化为文本形式
     */
    private String formatAsText(List<MarketTickerDto> marketTickers) {
        StringBuilder textContent = new StringBuilder();
        textContent.append("### 市场概况\n\n");

        for (MarketTickerDto ticker : marketTickers) {
            textContent.append(String.format("%d. %s: %s USDT",
                    ticker.getRank(),
                    ticker.getInstId(),
                    formatPrice(ticker.getLast())
            ));

            if (ticker.getChange24hPercent() != null) {
                textContent.append(String.format(", 24H: %s", formatPercent(ticker.getChange24hPercent())));
            }

            if (ticker.getChange4hPercent() != null) {
                textContent.append(String.format(", 4H: %s", formatPercent(ticker.getChange4hPercent())));
            }

            if (ticker.getRiseProbability() != null) {
                textContent.append(String.format(", 涨跌概率(4H): %s", formatPercent2(ticker.getRiseProbability())));
            }

            if (ticker.getMaxRise() != null) {
                textContent.append(String.format(", 最大涨幅(4H): %s", formatPercent(ticker.getMaxRise())));
            }

            if (ticker.getMaxFall() != null) {
                textContent.append(String.format(", 最大跌幅(4H): %s", formatPercent(ticker.getMaxFall())));
            }

            if (ticker.getVolume24hUsdt() != null) {
                textContent.append(String.format(", 交易额: %s USDT", formatVolume(ticker.getVolume24hUsdt())));
            }

            textContent.append("\n");
        }

        return textContent.toString();
    }

    /**
     * 涨跌统计数据持有类
     */
    @Data
    @AllArgsConstructor
    private static class RiseFallStats {
        /**
         * 涨跌概率(%)
         */
        private BigDecimal riseProbability;

        /**
         * 最大涨幅(%)
         */
        private BigDecimal maxRise;

        /**
         * 最大跌幅(%)
         */
        private BigDecimal maxFall;
    }
}
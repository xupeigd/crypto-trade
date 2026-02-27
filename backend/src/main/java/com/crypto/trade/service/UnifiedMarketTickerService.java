package com.crypto.trade.service;

import com.crypto.trade.dto.MarketTickerDto;
import com.crypto.trade.dto.Top30MarketTickerDto;
import com.crypto.trade.dto.cex.model.CexMarketTicker;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.FuturesTickerData;
import com.crypto.trade.repository.FuturesTickerDataRepository;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import com.crypto.trade.util.DateTimeUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * UnifiedMarketTickerService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class UnifiedMarketTickerService {

    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH");

    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    UnifiedCexApiService unifiedCexApiService;
    @Autowired
    FuturesTickerDataRepository futuresTickerDataRepository;

    /**
     * 应用启动时检查market ticker数据
     * <p>
     * 按活跃apiKey的vendor和交易模式（模拟/实盘）精细化检查：
     * 1. 获取所有活跃的apiKey，按vendor分组
     * 2. 对每个vendor的每种交易模式分别检查是否有当前小时的数据
     * 3. 如果某个组合缺少数据，则单独拉取该组合的数据
     * 4. 如果已有当前小时数据，进行缓存预热
     * 5. 拉取失败时记录日志并继续处理其他组合，不中断启动流程
     * </p>
     */
    @SuppressWarnings("SpringTransactionalMethodCallsInspection")
    @PostConstruct
    public void init() {
        try {
            log.info("开始启动时market ticker数据检查（按vendor和交易模式精细化检查）...");

            // 1. 获取所有活跃的apiKey并按vendor分组
            Map<String, List<ApiKey>> vendorApiKeysMap = groupActiveApiKeysByVendor();

            if (vendorApiKeysMap.isEmpty()) {
                log.warn("未找到活跃的API Key，跳过启动时数据检查");
                return;
            }

            int totalChecked = 0;
            int totalFetched = 0;
            int totalWarmed = 0;
            int totalFailed = 0;

            // 2. 获取当前小时字符串
            String currentHourStr = getCurrentHourStr();

            // 3. 遍历每个vendor
            for (Map.Entry<String, List<ApiKey>> vendorEntry : vendorApiKeysMap.entrySet()) {
                String vendor = vendorEntry.getKey();
                List<ApiKey> apiKeys = vendorEntry.getValue();

                // 4. 获取该vendor的所有交易模式（去重）
                Set<Boolean> tradingModes = apiKeys.stream()
                        .map(ApiKey::getIsLiveTrading)
                        .collect(Collectors.toSet());

                // 5. 对每种交易模式分别检查和拉取
                for (Boolean isLiveTrading : tradingModes) {
                    totalChecked++;
                    String tradingMode = isLiveTrading ? "实盘" : "模拟";

                    try {
                        // 6. 检查该vendor和交易模式的最新数据小时
                        Optional<String> latestHourOpt = futuresTickerDataRepository
                                .findLatestTsHourStrByVendorAndIsLiveTrading(vendor, isLiveTrading);

                        if (latestHourOpt.isEmpty()) {
                            // 6.1 完全没有数据，需要拉取
                            log.warn("未找到ticker数据，立即执行数据拉取 - vendor: {}, {}交易",
                                    vendor, tradingMode);

                            List<ApiKey> filteredApiKeys = apiKeys.stream()
                                    .filter(k -> isLiveTrading.equals(k.getIsLiveTrading()))
                                    .collect(Collectors.toList());

                            syncVendorMarketTickers(vendor, filteredApiKeys);
                            totalFetched++;

                            log.info("成功拉取ticker数据 - vendor: {}, {}交易", vendor, tradingMode);

                        } else {
                            // 6.2 有数据，检查是否是当前小时
                            String latestHourStr = latestHourOpt.get();

                            if (currentHourStr.equals(latestHourStr)) {
                                // 6.2.1 数据是当前小时的，进行缓存预热
                                log.debug("供应商 {} 的数据已是当前小时，开始缓存Top30 - {}交易, hour: {}",
                                        vendor, tradingMode, latestHourStr);

                                warmUpTop30Cache(vendor, isLiveTrading, latestHourStr);
                                totalWarmed++;

                            } else {
                                // 6.2.2 数据不是当前小时的，需要拉取
                                log.debug("供应商 {} 数据需要更新，最新: {}，当前: {} - {}交易",
                                        vendor, latestHourStr, currentHourStr, tradingMode);

                                List<ApiKey> filteredApiKeys = apiKeys.stream()
                                        .filter(k -> isLiveTrading.equals(k.getIsLiveTrading()))
                                        .collect(Collectors.toList());

                                syncVendorMarketTickers(vendor, filteredApiKeys);
                                totalFetched++;

                                log.info("成功拉取ticker数据 - vendor: {}, {}交易", vendor, tradingMode);
                            }
                        }

                    } catch (Exception e) {
                        totalFailed++;
                        log.error("启动时处理ticker数据失败 - vendor: {}, {}交易，继续处理其他组合",
                                vendor, tradingMode, e);
                    }
                }
            }

            log.info("启动时market ticker数据检查完成 - 检查: {} 个组合, 拉取: {} 个, 预热: {} 个, 失败: {} 个",
                    totalChecked, totalFetched, totalWarmed, totalFailed);

        } catch (Exception e) {
            log.error("启动时market ticker数据检查发生异常", e);
        }
    }

    /**
     * 定时任务：每30分钟执行一次market ticker数据同步
     */
    @Scheduled(cron = "0 */30 * * * ?")
    public void syncMarketTickersScheduled() {
        log.debug("开始执行定时market ticker数据同步任务");

        CompletableFuture.runAsync(() -> {
            try {
                // 获取活跃的API Keys
                Map<String, List<ApiKey>> vendorApiKeysMap = groupActiveApiKeysByVendor();

                if (vendorApiKeysMap.isEmpty()) {
                    log.warn("未找到活跃的API Keys");
                    return;
                }

                // 并行处理各供应商的数据
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (Map.Entry<String, List<ApiKey>> entry : vendorApiKeysMap.entrySet()) {
                    String vendor = entry.getKey();
                    List<ApiKey> apiKeys = entry.getValue();

                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            //noinspection SpringTransactionalMethodCallsInspection
                            syncVendorMarketTickers(vendor, apiKeys);
                        } catch (Exception e) {
                            log.error("同步供应商 {} 的market ticker数据失败", vendor, e);
                        }
                    });
                    futures.add(future);
                }

                // 等待所有供应商同步完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                log.debug("定时market ticker数据同步任务执行完成");

            } catch (Exception e) {
                log.error("定时market ticker数据同步任务执行失败", e);
            }
        });
    }

    /**
     * 获取指定供应商的Top30市场行情数据
     */
    public Top30MarketTickerDto getTop30MarketTickers(String vendor, String instType, String hourStr) {
        log.debug("获取Top30市场行情数据 - vendor: {}, instType: {}, hour: {}", vendor, instType, hourStr);
        try {
            // 查询数据库获取Top30数据
            List<FuturesTickerData> top30Data = futuresTickerDataRepository.findTop30ByVendorAndInstTypeAndHourStr(vendor, instType, hourStr);
            if (top30Data.isEmpty()) {
                log.warn("未找到Top30数据 - vendor: {}, instType: {}, hour: {}", vendor, instType, hourStr);
                synchronized (UnifiedMarketTickerService.class) {
                    fetchOkxMarketTickers(null);
                    top30Data = futuresTickerDataRepository.findTop30ByVendorAndInstTypeAndHourStr(vendor, instType, hourStr);
                }
            }
            // 转换为DTO
            List<MarketTickerDto> top30Tickers = top30Data.stream()
                    .map(this::convertToMarketTickerDto)
                    .collect(Collectors.toList());
            // 为每个ticker计算衍生数据
            top30Tickers.forEach(ticker -> {
                ticker.calculateVolume24hUsdt();
                ticker.calculateChange24hPercent();
            });
            // 设置排名
            for (int i = 0; i < top30Tickers.size(); i++) {
                top30Tickers.get(i).setVolumeRank(i + 1);
            }
            // 构建Top30MarketTickerDto
            Top30MarketTickerDto result = Top30MarketTickerDto.builder()
                    .vendor(vendor)
                    .instType(instType)
                    .tsHourStr(hourStr)
                    .top30Tickers(top30Tickers)
                    .generatedAt(LocalDateTime.now())
                    .build();

            // 计算统计信息
            result.calculateStatistics();
            log.debug("成功获取Top30市场行情数据 - vendor: {}, count: {}", vendor, top30Tickers.size());
            return result;
        } catch (Exception e) {
            log.error("获取Top30市场行情数据失败 - vendor: {}, instType: {}, hour: {}", vendor, instType, hourStr, e);
            return null;
        }
    }

    /**
     * 手动同步指定供应商的market ticker数据
     * 使用智能upsert逻辑：已存在的数据更新，不存在的数据新增
     */
    @Transactional
    public int syncVendorMarketTickers(String vendor, List<ApiKey> apiKeys) {
        log.debug("手动同步供应商 {} 的market ticker数据 - API Key数量: {}", vendor, apiKeys.size());

        if (apiKeys == null || apiKeys.isEmpty()) {
            log.warn("供应商 {} 没有可用的API Key", vendor);
            return 0;
        }

        int totalCount = 0;

        // 为每个apiKey分别同步ticker数据(区分正式和模拟交易)
        for (ApiKey apiKey : apiKeys) {
            Boolean isLiveTrading = apiKey.getIsLiveTrading();
            String tradingMode = isLiveTrading ? "实盘" : "模拟";

            try {
                log.debug("开始同步供应商 {} 的ticker数据 - API Key: {}, {}交易",
                        vendor, apiKey.getKeyId(), tradingMode);

                // 获取当前时间字符串
                String currentHourStr = getCurrentHourStr();

                // 调用相应供应商的API获取ticker数据
                List<FuturesTickerData> tickerData = fetchMarketTickersFromVendor(vendor, apiKey);
                if (tickerData.isEmpty()) {
                    log.warn("供应商 {} 未获取到market ticker数据 - API Key: {}, {}交易",
                            vendor, apiKey.getKeyId(), tradingMode);
                    continue;
                }

                // 设置vendor、isLiveTrading、tsHourStr并准备数据
                for (FuturesTickerData data : tickerData) {
                    data.setVendor(vendor);
                    data.setIsLiveTrading(isLiveTrading);
                    data.setTsHourStr(currentHourStr);
                    // 确保instType正确设置
                    if (data.getInstType() == null) {
                        data.setInstType("SWAP"); // 默认为SWAP类型
                    }
                }

                // 智能upsert逻辑
                smartUpsertTickerData(vendor, isLiveTrading, currentHourStr, tickerData);

                totalCount += tickerData.size();

                // 缓存Top30数据(仅对第一个apiKey)
                if (totalCount == tickerData.size()) {
                    warmUpTop30Cache(vendor, isLiveTrading, currentHourStr);
                }

            } catch (Exception e) {
                log.error("同步供应商 {} 的ticker数据失败 - API Key: {}, {}交易",
                        vendor, apiKey.getKeyId(), tradingMode, e);
                // 继续处理其他apiKey,不中断整体流程
            }
        }

        log.debug("供应商 {} 数据同步完成，总计: {} 条", vendor, totalCount);
        return totalCount;
    }

    /**
     * 智能upsert ticker数据
     * 基于新的唯一索引: (vendor, inst_id, ts_hour_str, is_live_trading)
     */
    private void smartUpsertTickerData(String vendor, Boolean isLiveTrading,
                                       String hourStr, List<FuturesTickerData> newDataList) {
        String tradingMode = isLiveTrading ? "实盘" : "模拟";

        try {
            // 1. 查询现有数据
            List<FuturesTickerData> existingData =
                    futuresTickerDataRepository.findByVendorAndIsLiveTradingAndTsHourStr(
                            vendor, isLiveTrading, hourStr);
            log.debug("查询到供应商 {} ({}) 在 {} 的现有数据 {} 条",
                    vendor, tradingMode, hourStr, existingData.size());

            // 2. 构建Map (key: instId, value: entity)
            Map<String, FuturesTickerData> existingMap = existingData.stream()
                    .collect(Collectors.toMap(
                            FuturesTickerData::getInstId,
                            data -> data,
                            (existing, replacement) -> existing
                    ));

            // 3. 分类：需要更新的数据和需要新增的数据
            List<FuturesTickerData> toUpdate = new ArrayList<>();
            List<FuturesTickerData> toInsert = new ArrayList<>();

            for (FuturesTickerData newData : newDataList) {
                FuturesTickerData existing = existingMap.get(newData.getInstId());

                if (existing != null) {
                    // 数据已存在，设置ID表示更新
                    newData.setId(existing.getId());
                    toUpdate.add(newData);
                } else {
                    // 数据不存在，新增
                    toInsert.add(newData);
                }
            }

            log.debug("数据分类完成 - 供应商: {}, {}交易 - 需要更新: {} 条, 需要新增: {} 条",
                    vendor, tradingMode, toUpdate.size(), toInsert.size());

            // 4. 批量保存
            int updatedCount = 0;
            int insertedCount = 0;

            if (!toUpdate.isEmpty()) {
                List<FuturesTickerData> updated = futuresTickerDataRepository.saveAll(toUpdate);
                updatedCount = updated.size();
                log.debug("更新供应商 {} ({}) 的market ticker数据，数量: {}",
                        vendor, tradingMode, updatedCount);
            }

            if (!toInsert.isEmpty()) {
                List<FuturesTickerData> inserted = futuresTickerDataRepository.saveAll(toInsert);
                insertedCount = inserted.size();
                log.debug("新增供应商 {} ({}) 的market ticker数据，数量: {}",
                        vendor, tradingMode, insertedCount);
            }

            int totalCount = updatedCount + insertedCount;
            log.debug("供应商 {} ({}) ticker数据upsert完成，总计: {} (更新: {}, 新增: {})",
                    vendor, tradingMode, totalCount, updatedCount, insertedCount);

        } catch (Exception e) {
            log.error("智能upsert失败 - vendor: {}, tradingMode: {}, hourStr: {}",
                    vendor, tradingMode, hourStr, e);
            throw e;
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取当前小时字符串
     */
    private String getCurrentHourStr() {
        return LocalDateTime.now().format(HOUR_FORMATTER);
    }

    /**
     * 按供应商分组活跃的API Keys
     */
    private Map<String, List<ApiKey>> groupActiveApiKeysByVendor() {
        List<ApiKey> activeKeys = apiKeyService.getActiveKeys();
        if (activeKeys.isEmpty()) {
            log.warn("未找到活跃的API Keys");
            return new HashMap<>();
        }

        return activeKeys.stream()
                .collect(Collectors.groupingBy(key -> key.getCexName().toUpperCase()));
    }

    /**
     * 从指定供应商获取market ticker数据
     */
    private List<FuturesTickerData> fetchMarketTickersFromVendor(String vendor, ApiKey apiKey) {
        try {
            switch (vendor.toUpperCase()) {
                case "OKX":
                    return fetchOkxMarketTickers(apiKey);
                default:
                    log.warn("暂不支持供应商: {}", vendor);
                    return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("从供应商 {} 获取market ticker数据失败", vendor, e);
            return new ArrayList<>();
        }
    }

    /**
     * 从OKX获取market ticker数据
     */
    private List<FuturesTickerData> fetchOkxMarketTickers(ApiKey apiKey) {
        try {
            log.debug("从OKX获取market ticker数据");
            // 使用统一方法获取ticker数据
            List<CexMarketTicker> cexTickers = unifiedCexApiService.getMarketTickers(apiKey, "SWAP", null);
            if (cexTickers.isEmpty()) {
                log.warn("OKX API返回的market ticker数据为空");
                return new ArrayList<>();
            }
            // 转换为FuturesTickerData
            List<FuturesTickerData> tickerData = new ArrayList<>();
            for (CexMarketTicker cexTicker : cexTickers) {
                try {
                    FuturesTickerData data = convertCexTickerToFuturesTickerData(cexTicker);
                    if (data != null) {
                        tickerData.add(data);
                    }
                } catch (Exception e) {
                    log.warn("转换ticker数据失败，symbol: {}", cexTicker.getSymbol(), e);
                }
            }
            // 移除saveAll调用，数据保存统一由syncVendorMarketTickers方法处理
            log.debug("从OKX获取到 {} 个market ticker数据", tickerData.size());
            return tickerData;
        } catch (Exception e) {
            log.error("从OKX获取market ticker数据失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 将CexMarketTicker转换为FuturesTickerData
     */
    private FuturesTickerData convertCexTickerToFuturesTickerData(CexMarketTicker cexTicker) {
        try {
            return FuturesTickerData.builder()
                    .vendor("OKX")
                    .instType("SWAP") // 统一DTO中可能没有instType,默认为SWAP
                    .instId(cexTicker.getSymbol())
                    .last(cexTicker.getLastPrice())
                    .lastSz(cexTicker.getVolume()) // 使用volume代替lastSz
                    .bidPrice(cexTicker.getBidPrice())
                    .bidSz(cexTicker.getBidQuantity())
                    .askPrice(cexTicker.getAskPrice())
                    .askSz(cexTicker.getAskQuantity())
                    .open24h(cexTicker.getOpen24h())  // ✅ 修复: 正确映射open24h字段
                    .high24h(cexTicker.getHigh24h())
                    .low24h(cexTicker.getLow24h())
                    .volCcy24h(cexTicker.getVolumeCcy24h())
                    .vol24h(cexTicker.getVolume24h())
                    .ts(cexTicker.getTimestamp())
                    .sodUtc0(null) // 统一DTO可能没有此字段
                    .sodUtc8(null) // 统一DTO可能没有此字段
                    .tsMinsStr(cexTicker.getTimestamp() != null ?
                            LocalDateTime.ofEpochSecond(cexTicker.getTimestamp() / 1000L, 0, ZoneOffset.UTC)
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null)
                    .dataIngestionTime(LocalDateTime.now())
                    .tsHourStr(cexTicker.getTimestamp() != null ?
                            LocalDateTime.ofEpochSecond(cexTicker.getTimestamp() / 1000L, 0, ZoneOffset.UTC)
                                    .format(HOUR_FORMATTER) : null)
                    .build();
        } catch (Exception e) {
            log.error("转换ticker数据失败", e);
            return null;
        }
    }

    /**
     * 转换FuturesTickerData为MarketTickerDto
     */
    private MarketTickerDto convertToMarketTickerDto(FuturesTickerData data) {
        try {
            return MarketTickerDto.builder()
                    .vendor(data.getVendor())
                    .instType(data.getInstType())
                    .instId(data.getInstId())
                    .last(data.getLast())
                    .lastSz(data.getLastSz())
                    .bidPrice(data.getBidPrice())
                    .bidSz(data.getBidSz())
                    .askPrice(data.getAskPrice())
                    .askSz(data.getAskSz())
                    .open24h(data.getOpen24h())
                    .high24h(data.getHigh24h())
                    .low24h(data.getLow24h())
                    .volCcy24h(data.getVolCcy24h())
                    .vol24h(data.getVol24h())
                    .ts(data.getTs())
                    .tsHourStr(data.getTsHourStr())
                    .dataIngestionTime(data.getDataIngestionTime())
                    .build();
        } catch (Exception e) {
            log.error("转换FuturesTickerData为MarketTickerDto失败", e);
            return null;
        }
    }

    /**
     * 预热Top30缓存
     *
     * @param vendor        供应商名称
     * @param isLiveTrading 是否实盘交易
     * @param hourStr       小时字符串
     */
    private void warmUpTop30Cache(String vendor, Boolean isLiveTrading, String hourStr) {
        try {
            String tradingMode = isLiveTrading ? "实盘" : "模拟";
            log.debug("预热Top30缓存 - vendor: {}, tradingMode: {}, hour: {}", vendor, tradingMode, hourStr);

            // 注意: 缓存功能已废弃,此方法保留仅为兼容性
            // Top30MarketTickerDto top30Data = getTop30MarketTickers(vendor, "SWAP", hourStr);
            log.debug("Top30缓存预热功能已禁用 - vendor: {}, tradingMode: {}", vendor, tradingMode);

        } catch (Exception e) {
            log.error("预热Top30缓存失败 - vendor: {}, tradingMode: {}, hour: {}",
                    vendor, isLiveTrading ? "实盘" : "模拟", hourStr, e);
        }
    }

    /**
     * 获取TopN合约信息
     * 根据apiKeyId确定供应商，每个供应商单独排名
     *
     * @param topN     返回前N条记录（最大30）
     * @param sortBy   排序字段: "volume"(交易额) 或 "change"(波动率)
     * @param apiKeyId API密钥ID，用于确定供应商（可选，为空时使用系统默认apiKey）
     * @return TopN合约列表
     */
    public List<MarketTickerDto> getTopNContracts(int topN, String sortBy, Long apiKeyId) {
        // 参数验证
        if (topN <= 0) {
            topN = 15;
        }
        if (topN > 30) {
            topN = 30;
        }

        if (null == apiKeyId) {
            return List.of();
        }
        ApiKey apiKey = apiKeyService.getDecryptedKey(apiKeyId);
        if (null == apiKey) {
            return List.of();
        }

        // 获取供应商信息
        String vendor = apiKey.getCexName();
        if (null == vendor) {
            log.error("[getTopNContracts] 无法确定供应商 - apiKeyId: {}", apiKeyId);
            return List.of();
        }

        // 获取交易模式信息
        Boolean isLiveTrading = apiKey.getIsLiveTrading();
        String tradingMode = isLiveTrading ? "实盘" : "模拟";

        // 获取当前小时的hourStr
        String hourStr = DateTimeUtils.nowUtc8().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH"));

        log.debug("[getTopNContracts] 查询TopN合约 - topN: {}, sortBy: {}, vendor: {}, tradingMode: {}, hourStr: {}",
                topN, sortBy, vendor, tradingMode, hourStr);

        try {
            // 根据sortBy选择查询方法，传入vendor和isLiveTrading参数
            List<FuturesTickerData> tickerDataList;

            if ("change".equalsIgnoreCase(sortBy)) {
                // 按涨跌幅排序
                tickerDataList = futuresTickerDataRepository.findTopNSwapByChange24h(vendor, isLiveTrading, hourStr,
                        PageRequest.of(0, topN));

                // 降级策略: 如果当前小时无数据,查询最近3小时
                if (CollectionUtils.isEmpty(tickerDataList)) {
                    log.warn("[getTopNContracts] 当前小时无涨跌幅数据,尝试降级查询 - hourStr: {}", hourStr);
                    LocalDateTime threeHoursAgo = DateTimeUtils.nowUtc8().minusHours(3);
                    tickerDataList = futuresTickerDataRepository.findTopSwapContractsOrderByVolume(vendor, isLiveTrading, threeHoursAgo);
                    log.info("[getTopNContracts] 降级查询返回 {} 条记录", tickerDataList.size());
                }
            } else {
                // 默认按交易额排序
                tickerDataList = futuresTickerDataRepository.findTopNSwapByVolume24h(
                        vendor, isLiveTrading, hourStr, PageRequest.of(0, topN));

                // 降级策略: 如果当前小时无数据,查询最近3小时
                if (CollectionUtils.isEmpty(tickerDataList)) {
                    log.warn("[getTopNContracts] 当前小时无交易额数据,尝试降级查询 - hourStr: {}", hourStr);
                    LocalDateTime threeHoursAgo = DateTimeUtils.nowUtc8().minusHours(3);
                    tickerDataList = futuresTickerDataRepository.findTopSwapContractsOrderByVolume(vendor, isLiveTrading, threeHoursAgo);
                    log.info("[getTopNContracts] 降级查询返回 {} 条记录", tickerDataList.size());
                }
            }

            // 如果最终仍无数据,返回空列表
            if (CollectionUtils.isEmpty(tickerDataList)) {
                log.warn("[getTopNContracts] 无法获取任何市场数据 - vendor: {}, tradingMode: {}, hourStr: {}",
                        vendor, tradingMode, hourStr);
                return List.of();
            }

            // 转换为MarketTickerDto
            List<MarketTickerDto> result = new ArrayList<>();
            int rank = 1;
            for (FuturesTickerData data : tickerDataList) {
                MarketTickerDto dto = convertToMarketTickerDto(data);
                if (dto != null) {
                    dto.calculateVolume24hUsdt();
                    dto.calculateChange24hPercent();
                    dto.parseAssetsFromInstId();
                    dto.setRank(rank++);
                    result.add(dto);
                }
            }

            log.debug("[getTopNContracts] 查询完成 - vendor: {}, tradingMode: {}, 返回{}条记录", vendor, tradingMode, result.size());
            return result;
        } catch (Exception e) {
            log.error("[getTopNContracts] 查询失败 - vendor: {}, tradingMode: {}", vendor, tradingMode, e);
            return List.of();
        }
    }

}
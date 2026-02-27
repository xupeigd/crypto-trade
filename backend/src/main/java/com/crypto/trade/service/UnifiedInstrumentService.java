package com.crypto.trade.service;

import com.crypto.trade.dto.cex.model.CexContractInfo;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.CexInstrument;
import com.crypto.trade.repository.CexInstrumentRepository;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * UnifiedInstrumentService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class UnifiedInstrumentService {

    private final ApiKeyService apiKeyService;
    private final UnifiedCexApiService unifiedCexApiService;
    private final CexInstrumentRepository cexInstrumentRepository;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    public UnifiedInstrumentService(ApiKeyService apiKeyService,
                                    UnifiedCexApiService unifiedCexApiService,
                                    CexInstrumentRepository cexInstrumentRepository) {
        this.apiKeyService = apiKeyService;
        this.unifiedCexApiService = unifiedCexApiService;
        this.cexInstrumentRepository = cexInstrumentRepository;
    }

    /**
     * 应用启动时检查合约数据
     * <p>
     * 按活跃apiKey的CEX和交易模式（模拟/实盘）精细化检查：
     * 1. 获取所有活跃的apiKey，按CEX分组
     * 2. 对每个CEX的每种交易模式分别检查是否有6小时内数据
     * 3. 如果某个组合缺少数据，则单独拉取该组合的数据
     * 4. 拉取失败时记录日志并继续处理其他组合，不中断启动流程
     * </p>
     */
    @SuppressWarnings("SpringTransactionalMethodCallsInspection")
    @PostConstruct
    public void checkDataOnStartup() {
        try {
            log.info("开始启动时合约数据检查（按CEX和交易模式精细化检查）...");
            LocalDateTime sixHoursAgo = LocalDateTime.now().minusHours(1);

            // 获取所有活跃的apiKey并按CEX分组
            Map<String, List<ApiKey>> cexApiKeysMap = groupActiveApiKeysByCex();

            if (cexApiKeysMap.isEmpty()) {
                log.warn("未找到活跃的API Key，跳过启动时数据检查");
                return;
            }

            int totalChecked = 0;
            int totalFetched = 0;
            int totalFailed = 0;

            // 遍历每个CEX

            for (Map.Entry<String, List<ApiKey>> cexEntry : cexApiKeysMap.entrySet()) {
                String cexName = cexEntry.getKey();
                List<ApiKey> apiKeys = cexEntry.getValue();

                // 获取该CEX的所有交易模式（去重）
                Set<Boolean> tradingModes = apiKeys.stream()
                        .map(ApiKey::getIsLiveTrading)
                        .collect(Collectors.toSet());

                // 对每种交易模式分别检查和拉取
                for (Boolean isLiveTrading : tradingModes) {
                    totalChecked++;
                    String tradingMode = isLiveTrading ? "实盘" : "模拟";

                    try {
                        // 检查该CEX和交易模式是否有6小时内的数据
                        boolean hasRecentData = cexInstrumentRepository
                                .existsByProviderAndIsLiveTradingAndDataIngestionTimeAfter(
                                        cexName, isLiveTrading, sixHoursAgo);

                        if (!hasRecentData) {
                            log.warn("未找到6小时内的合约数据，立即执行数据拉取 - CEX: {}, {}交易",
                                    cexName, tradingMode);

                            // 过滤出该交易模式的apiKey
                            List<ApiKey> filteredApiKeys = apiKeys.stream()
                                    .filter(k -> isLiveTrading.equals(k.getIsLiveTrading()))
                                    .collect(Collectors.toList());

                            // 单独拉取该CEX和交易模式的数据
                            fetchInstrumentsForCex(cexName, filteredApiKeys);
                            totalFetched++;

                            log.info("成功拉取合约数据 - CEX: {}, {}交易", cexName, tradingMode);
                        } else {
                            log.debug("发现6小时内的合约数据，跳过 - CEX: {}, {}交易",
                                    cexName, tradingMode);
                        }

                    } catch (Exception e) {
                        totalFailed++;
                        log.error("启动时拉取合约数据失败 - CEX: {}, {}交易，继续处理其他组合",
                                cexName, tradingMode, e);
                    }
                }
            }

            log.info("启动时合约数据检查完成 - 检查: {} 个组合, 拉取: {} 个, 失败: {} 个",
                    totalChecked, totalFetched, totalFailed);

        } catch (Exception e) {
            log.error("启动时合约数据检查发生异常", e);
        }
    }

    /**
     * 定时获取合约信息（每30分钟执行一次）
     */
    @Scheduled(cron = "0 0/30 * * * ?") // 30分钟 = 1800000毫秒
    @Transactional
    public void fetchInstrumentsScheduled() {
        log.debug("开始执行定时合约信息拉取任务");
        try {
            // 获取活跃的API密钥并按CEX去重
            Map<String, List<ApiKey>> cexApiKeysMap = groupActiveApiKeysByCex();
            if (CollectionUtils.isEmpty(cexApiKeysMap)) {
                log.warn("未找到活跃的API密钥");
                return;
            }
            log.debug("获取到 {} 个CEX的活跃API密钥", cexApiKeysMap.size());
            // 遍历每个CEX，获取SWAP合约信息
            for (Map.Entry<String, List<ApiKey>> entry : cexApiKeysMap.entrySet()) {
                String cexName = entry.getKey();
                List<ApiKey> apiKeys = entry.getValue();
                try {
                    fetchInstrumentsForCex(cexName, apiKeys);
                } catch (Exception e) {
                    log.error("获取CEX {} 的合约信息失败", cexName, e);
                    // 继续处理其他CEX，不中断整体流程
                }
            }
            log.debug("定时合约信息拉取任务执行完成");
        } catch (Exception e) {
            log.error("定时合约信息拉取任务执行失败", e);
        }
    }

    /**
     * 获取活跃的API密钥并按CEX分组去重
     *
     * @return Map<CEX名称, API密钥列表>
     */
    private Map<String, List<ApiKey>> groupActiveApiKeysByCex() {
        List<ApiKey> activeKeys = apiKeyService.getActiveKeys();

        if (CollectionUtils.isEmpty(activeKeys)) {
            log.warn("未找到活跃的API密钥");
            return new HashMap<>();
        }

        // 按CEX名称分组
        return activeKeys.stream()
                .collect(Collectors.groupingBy(ApiKey::getCexName));
    }

    /**
     * 为指定CEX获取合约信息
     * 为每个apiKey分别拉取合约信息,以区分正式和模拟交易
     *
     * @param cexName CEX名称
     * @param apiKeys 该CEX的活跃API密钥列表
     */
    @Transactional
    protected void fetchInstrumentsForCex(String cexName, List<ApiKey> apiKeys) {
        log.debug("开始获取CEX {} 的SWAP合约信息 - API Key数量: {}", cexName, apiKeys.size());

        if (CollectionUtils.isEmpty(apiKeys)) {
            log.warn("CEX {} 没有可用的API密钥", cexName);
            return;
        }

        // 为每个apiKey分别拉取合约信息(区分正式和模拟交易)
        int successCount = 0;
        for (ApiKey apiKey : apiKeys) {
            try {
                Boolean isLiveTrading = apiKey.getIsLiveTrading();
                String tradingMode = isLiveTrading ? "实盘" : "模拟";

                log.debug("使用API Key ({}) 拉取 {} 交易合约信息", apiKey.getKeyId(), tradingMode);

                switch (cexName.toUpperCase()) {
                    case "OKX":
                        fetchOkxInstruments(apiKey);
                        successCount++;
                        break;
                    default:
                        log.warn("暂不支持CEX: {}", cexName);
                        break;
                }
            } catch (Exception e) {
                Boolean isLiveTrading = apiKey.getIsLiveTrading();
                String tradingMode = isLiveTrading ? "实盘" : "模拟";
                log.error("获取CEX {} 的合约信息失败 - API Key: {}, {}交易", cexName, apiKey.getKeyId(), tradingMode, e);
                // 继续处理其他apiKey,不中断整体流程
            }
        }

        log.debug("CEX {} 合约信息拉取完成 - 成功: {}/{}", cexName, successCount, apiKeys.size());
    }

    /**
     * 为CEX选择一个可用的API密钥
     */
    private ApiKey selectApiKeyForCex(List<ApiKey> apiKeys) {
        if (CollectionUtils.isEmpty(apiKeys)) {
            return null;
        }

        // 优先选择第一个可用的密钥
        for (ApiKey apiKey : apiKeys) {
            try {
                // 测试密钥是否可用（简单检查）
                if (apiKey.getAccessKey() != null && !apiKey.getAccessKey().trim().isEmpty()) {
                    return apiKey;
                }
            } catch (Exception e) {
                log.warn("API密钥不可用: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * 获取OKX的SWAP合约信息
     */
    @Transactional
    protected void fetchOkxInstruments(ApiKey apiKey) {
        Boolean isLiveTrading = apiKey.getIsLiveTrading();
        String tradingMode = isLiveTrading ? "实盘" : "模拟";

        log.debug("开始获取OKX SWAP合约信息 - {}交易", tradingMode);

        try {
            // 调用通用API获取SWAP合约信息
            List<com.crypto.trade.dto.cex.model.CexInstrument> dtoInstruments = unifiedCexApiService.getInstruments(apiKey, "SWAP", null);

            if (CollectionUtils.isEmpty(dtoInstruments)) {
                log.warn("API返回的SWAP合约信息为空 - {}交易", tradingMode);
                return;
            }

            log.debug("从API获取到 {} 个SWAP合约 - {}交易", dtoInstruments.size(), tradingMode);

            // 转换为Entity CexInstrument并保存,传入isLiveTrading标识
            List<CexInstrument> entityInstruments = convertDtoInstruments(dtoInstruments, "OKX", isLiveTrading);

            if (!CollectionUtils.isEmpty(entityInstruments)) {
                // 使用批量upsert方法,避免重复键错误
                batchUpsertCexInstruments(entityInstruments);
                log.debug("成功保存 {} 个OKX SWAP合约信息 - {}交易", entityInstruments.size(), tradingMode);

                // 清理旧数据(可选)
                cleanupOldData("OKX", isLiveTrading);
            } else {
                log.warn("转换后的OKX合约信息为空 - {}交易", tradingMode);
            }

        } catch (Exception e) {
            log.error("获取OKX SWAP合约信息失败 - {}交易", tradingMode, e);
            throw e;
        }
    }

    /**
     * 将DTO合约信息转换为Entity CexInstrument
     *
     * @param dtoInstruments DTO合约信息列表
     * @param provider       服务商名称
     * @param isLiveTrading  是否实盘交易
     * @return Entity CexInstrument列表
     */
    private List<CexInstrument> convertDtoInstruments(List<com.crypto.trade.dto.cex.model.CexInstrument> dtoInstruments,
                                                      String provider,
                                                      Boolean isLiveTrading) {
        List<CexInstrument> entityInstruments = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();

        for (com.crypto.trade.dto.cex.model.CexInstrument dtoInstrument : dtoInstruments) {
            try {
                CexInstrument entityInstrument = com.crypto.trade.entity.CexInstrument.builder()
                        .provider(provider)
                        .isLiveTrading(isLiveTrading)  // 设置交易模式标识
                        .instType(dtoInstrument.getInstrumentType())
                        .instId(dtoInstrument.getSymbol())
                        .baseCcy(dtoInstrument.getBaseCurrency())
                        .quoteCcy(dtoInstrument.getQuoteCurrency())
                        .settleCcy(dtoInstrument.getSettleCurrency())
                        .category(null) // 新接口没有此字段
                        .ctVal(dtoInstrument.getContractValue() != null ? dtoInstrument.getContractValue().toString() : null)
                        .ctMult(null) // 新接口没有此字段
                        .ctValCcy(null) // 新接口没有此字段
                        .optType(null) // 新接口没有此字段
                        .stk(null) // 新接口没有此字段
                        .listTime(null) // 新接口没有此字段
                        .expTime(null) // 新接口没有此字段
                        .lever(dtoInstrument.getMaxLeverage() != null ? dtoInstrument.getMaxLeverage().toString() : null)
                        .tickSz(dtoInstrument.getTickSize() != null ? dtoInstrument.getTickSize().toString() : null)
                        .lotSz(dtoInstrument.getLotSize() != null ? dtoInstrument.getLotSize().toString() : null)
                        .minSz(dtoInstrument.getMinOrderSize() != null ? dtoInstrument.getMinOrderSize().toString() : null)
                        .maxLmtSz(null) // 新接口没有此字段
                        .maxMktSz(null) // 新接口没有此字段
                        .maxTsSz(null) // API没有此字段，设为null
                        .state(dtoInstrument.getState())
                        .alias(null) // 新接口没有此字段
                        .maxLmt(null) // API没有此字段，设为null
                        .maxMkt(null) // API没有此字段，设为null
                        .positionIdx(null) // API没有此字段，设为null
                        .isLeverage(null) // API没有此字段，设为null
                        .feeRate(null) // API没有此字段，设为null
                        .dataIngestionTime(now)
                        .build();

                entityInstruments.add(entityInstrument);

            } catch (Exception e) {
                log.warn("转换合约信息失败, instId: {}, error: {}", dtoInstrument.getSymbol(), e.getMessage());
                // 继续处理其他合约，不中断整体流程
            }
        }

        return entityInstruments;
    }

    /**
     * 清理旧数据（保留最近7天的数据）
     *
     * @param provider      服务商名称
     * @param isLiveTrading 是否实盘交易
     */
    @Transactional
    protected void cleanupOldData(String provider, Boolean isLiveTrading) {
        try {
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            String tradingMode = isLiveTrading ? "实盘" : "模拟";

            // 检查该交易模式下是否有需要清理的数据
            long count = cexInstrumentRepository.countByProviderAndIsLiveTrading(provider, isLiveTrading);
            if (count <= 500) {
                log.debug("{}交易 - {} 合约数据量不超过500条，暂不执行清理操作", tradingMode, provider);
                return;
            }

            // 删除7天前的数据
            int deletedCount = cexInstrumentRepository.deleteByProviderAndIsLiveTradingAndDataIngestionTimeBefore(
                    provider, isLiveTrading, sevenDaysAgo);
            if (deletedCount > 0) {
                log.debug("清理了 {} 条7天前的旧合约信息 - provider: {}, {}交易",
                        deletedCount, provider, tradingMode);
            }

        } catch (Exception e) {
            log.error("清理旧合约信息失败 - provider: {}", provider, e);
            // 不影响主流程，继续执行
        }
    }

    /**
     * 手动触发合约信息拉取
     */
    @Transactional
    public void fetchInstrumentsManually() {
        log.debug("手动触发合约信息拉取");
        fetchInstrumentsScheduled();
    }

    /**
     * 获取指定服务商的合约数量
     */
    public long getInstrumentCountByProvider(String provider) {
        return cexInstrumentRepository.countByProvider(provider);
    }

    /**
     * 获取指定服务商的活跃合约数量
     */
    public long getActiveInstrumentCountByProvider(String provider) {
        return cexInstrumentRepository.findByProviderAndState(provider, "live").size();
    }

    /**
     * 批量插入或更新合约信息
     * 分批处理以避免SQL语句过长，每次处理100条记录
     */
    private void batchUpsertCexInstruments(List<CexInstrument> instruments) {
        if (CollectionUtils.isEmpty(instruments)) {
            return;
        }

        int batchSize = 100;
        int totalInstruments = instruments.size();
        int processedCount = 0;
        String lastProvider = null;

        try {
            // 分批处理，每次100条记录
            for (int i = 0; i < totalInstruments; i += batchSize) {
                int endIndex = Math.min(i + batchSize, totalInstruments);
                List<CexInstrument> batch = instruments.subList(i, endIndex);

                // 逐条upsert，避免批量操作的问题
                for (CexInstrument instrument : batch) {
                    try {
                        cexInstrumentRepository.upsertInstrument(instrument);
                        processedCount++;
                        lastProvider = instrument.getProvider();

                        // 发布合约更新事件，触发K线数据加载
//                        publishInstrumentUpdateEvent(instrument);

                    } catch (Exception e) {
                        log.warn("单个合约upsert失败, provider: {}, instId: {}, error: {}",
                                instrument.getProvider(), instrument.getInstId(), e.getMessage());
                        // 继续处理其他合约，不中断整个批次
                    }
                }

                // 每个批次后稍作停顿，避免数据库压力过大
                if (endIndex < totalInstruments) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("批量处理被中断");
                        break;
                    }
                }
            }

            log.debug("批量upsert完成，成功处理 {}/{} 个合约信息", processedCount, totalInstruments);

        } catch (Exception e) {
            log.error("批量upsert合约信息失败，已处理 {}/{} 个", processedCount, totalInstruments, e);
            throw e;
        }
    }

//    /**
//     * 发布合约更新事件
//     * 只发布活跃状态的合约事件，避免为无效合约加载K线数据
//     */
//    private void publishInstrumentUpdateEvent(CexInstrument instrument) {
//        try {
//            // 只为活跃状态的合约发布事件
//            if (!"live".equals(instrument.getState())) {
//                log.debug("跳过非活跃合约的事件发布 - provider: {}, instId: {}, state: {}",
//                        instrument.getProvider(), instrument.getInstId(), instrument.getState());
//                return;
//            }
//
//            // 创建事件对象
//            InstrumentUpdateEvent event = InstrumentUpdateEvent.builder()
//                    .provider(instrument.getProvider())
//                    .instId(instrument.getInstId())
//                    .instType(instrument.getInstType())
//                    .baseAsset(instrument.getBaseCcy())
//                    .quoteAsset(instrument.getQuoteCcy())
//                    .isActive("live".equals(instrument.getState()))
//                    .updateTime(LocalDateTime.now())
//                    .volumeRank(null) // 暂时设为null，后续可以根据需要填充
//                    .volume24hUsdt(null) // 暂时设为null，后续可以根据需要填充
//                    .build();
//
//            // 发布事件
//            eventPublisher.publishEvent(event);
//
//            log.debug("成功发布合约更新事件 - provider: {}, instId: {}, eventType: {}",
//                    event.getProvider(), event.getInstId(), event.getEventType());
//
//        } catch (Exception e) {
//            log.error("发布合约更新事件失败 - provider: {}, instId: {}",
//                    instrument.getProvider(), instrument.getInstId(), e);
//            // 不影响主流程，继续处理其他合约
//        }
//    }

    /**
     * 获取合约信息（兼容OKXTradingService.ContractInfo格式）
     * <p>
     * 已重构为委托调用,内部使用 getCexContractInfo() 并转换类型
     * </p>
     *
     * @param instId 合约ID
     * @return 合约信息，如果未找到则返回Optional.empty()
     * @deprecated 请使用 {@link #getCexContractInfo(String)} 获取通用CEX合约信息
     */
//    @Deprecated
//    public Optional<OKXTradingService.ContractInfo> getContractInfo(String instId) {
//        // 委托给新方法并转换类型
//        return getCexContractInfo(instId)
//                .map(this::convertToOkxContractInfo);
//    }

    /**
     * 将通用 CexContractInfo 转换为 OKX ContractInfo(向后兼容)
     * <p>
     * 此方法仅用于维持向后兼容性
     * </p>
     *
     * @param cexInfo 通用CEX合约信息
     * @return OKX合约信息
     */
//    private OKXTradingService.ContractInfo convertToOkxContractInfo(CexContractInfo cexInfo) {
//        if (cexInfo == null) {
//            return null;
//        }
//
//        OKXTradingService.ContractInfo info = new OKXTradingService.ContractInfo();
//        info.instId = cexInfo.getInstId();
//        info.lotSz = cexInfo.getLotSz();
//        info.minSz = cexInfo.getMinSz();
//        info.ctVal = cexInfo.getCtVal();
//        info.ctMult = cexInfo.getCtMult();
//
//        return info;
//    }

    /**
     * 获取通用CEX合约信息
     * <p>
     * 返回交易所无关的通用合约信息模型。
     * </p>
     *
     * @param instId 合约ID
     * @return 合约信息，如果未找到则返回Optional.empty()
     */
    public Optional<CexContractInfo> getCexContractInfo(String instId) {
        try {
            log.debug("获取通用CEX合约信息 - instId: {}", instId);

            // 优先查询OKX的SWAP合约
            Optional<CexInstrument> instrumentOpt = cexInstrumentRepository
                    .findLatestActiveByProviderAndInstId("OKX", instId);

            if (instrumentOpt.isPresent()) {
                CexInstrument instrument = instrumentOpt.get();
                CexContractInfo contractInfo = convertToCexContractInfo(instrument);
                log.debug("成功获取通用CEX合约信息 - instId: {}, ctVal: {}, lotSz: {}",
                        instId, contractInfo.getCtVal(), contractInfo.getLotSz());
                return Optional.of(contractInfo);
            } else {
                log.warn("未找到合约信息 - instId: {}", instId);
                return Optional.empty();
            }

        } catch (Exception e) {
            log.error("获取通用CEX合约信息失败 - instId: {}", instId, e);
            return Optional.empty();
        }
    }

    /**
     * 获取合约面值
     *
     * @param instId 合约ID
     * @return 合约面值，获取失败时返回BigDecimal.ONE
     */
    public BigDecimal getContractValue(String instId) {
        try {
            Optional<CexInstrument> instrumentOpt = cexInstrumentRepository
                    .findLatestActiveByProviderAndInstId("OKX", instId);

            if (instrumentOpt.isPresent()) {
                String ctVal = instrumentOpt.get().getCtVal();
                if (ctVal != null && !ctVal.trim().isEmpty()) {
                    return new BigDecimal(ctVal);
                }
            }

            log.warn("无法获取合约面值，使用默认值 - instId: {}", instId);
            return BigDecimal.ONE;

        } catch (Exception e) {
            log.error("获取合约面值失败 - instId: {}, 使用默认值", instId, e);
            return BigDecimal.ONE;
        }
    }

    /**
     * 获取最小交易单位
     *
     * @param instId 合约ID
     * @return 最小交易数量，获取失败时返回BigDecimal.ONE
     */
    public BigDecimal getMinTradingSize(String instId) {
        try {
            Optional<CexInstrument> instrumentOpt = cexInstrumentRepository
                    .findLatestActiveByProviderAndInstId("OKX", instId);

            if (instrumentOpt.isPresent()) {
                String minSz = instrumentOpt.get().getMinSz();
                if (minSz != null && !minSz.trim().isEmpty()) {
                    return new BigDecimal(minSz);
                }
            }

            log.warn("无法获取最小交易单位，使用默认值 - instId: {}", instId);
            return BigDecimal.ONE;

        } catch (Exception e) {
            log.error("获取最小交易单位失败 - instId: {}, 使用默认值", instId, e);
            return BigDecimal.ONE;
        }
    }

    /**
     * 将CexInstrument转换为通用CEX合约信息
     *
     * @param cexInstrument CEX合约实体
     * @return CexContractInfo
     */
    private CexContractInfo convertToCexContractInfo(CexInstrument cexInstrument) {
        try {
            return CexContractInfo.builder()
                    .instId(cexInstrument.getInstId())
                    .instType(cexInstrument.getInstType())
                    .baseCcy(cexInstrument.getBaseCcy())
                    .quoteCcy(cexInstrument.getQuoteCcy())
                    .settleCcy(cexInstrument.getSettleCcy())
                    .ctVal(parseBigDecimal(cexInstrument.getCtVal(), BigDecimal.ONE))
                    .ctMult(parseBigDecimal(cexInstrument.getCtMult(), BigDecimal.ONE))
                    .lotSz(parseBigDecimal(cexInstrument.getLotSz(), BigDecimal.ONE))
                    .tickSz(parseBigDecimal(cexInstrument.getTickSz(), BigDecimal.ONE))
                    .minSz(parseBigDecimal(cexInstrument.getMinSz(), BigDecimal.ONE))
                    .lever(parseBigDecimal(cexInstrument.getLever(), BigDecimal.ZERO))
                    .state(cexInstrument.getState())
                    .maxLmtSz(parseBigDecimal(cexInstrument.getMaxLmtSz(), BigDecimal.ZERO))
                    .minLmtSz(BigDecimal.ZERO) // CexInstrument没有minLmtSz字段,使用默认值
                    .timestamp(System.currentTimeMillis())
                    .build();

        } catch (Exception e) {
            log.error("转换通用CEX合约信息失败 - instId: {}", cexInstrument.getInstId(), e);
            // 返回默认值
            return CexContractInfo.builder()
                    .instId(cexInstrument.getInstId())
                    .ctVal(BigDecimal.ONE)
                    .ctMult(BigDecimal.ONE)
                    .lotSz(BigDecimal.ONE)
                    .tickSz(BigDecimal.ONE)
                    .minSz(BigDecimal.ONE)
                    .build();
        }
    }

    /**
     * 解析BigDecimal,失败时返回默认值
     */
    private BigDecimal parseBigDecimal(String value, BigDecimal defaultValue) {
        if (value != null && !value.trim().isEmpty()) {
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                log.warn("解析BigDecimal失败: {}, 使用默认值: {}", value, defaultValue);
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
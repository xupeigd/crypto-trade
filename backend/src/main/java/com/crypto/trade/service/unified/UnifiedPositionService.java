package com.crypto.trade.service.unified;

import com.crypto.trade.dto.cex.common.PositionSide;
import com.crypto.trade.dto.cex.model.CexPosition;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.enums.CexApiCallStatus;
import com.crypto.trade.enums.CexApiType;
import com.crypto.trade.event.CexApiCallEvent;
import com.crypto.trade.event.PositionChangedEvent;
import com.crypto.trade.event.PositionUpdateEvent;
import com.crypto.trade.model.PositionRiskModel;
import com.crypto.trade.model.PositionSummaryModel;
import com.crypto.trade.repository.ApiKeyRepository;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import com.crypto.trade.service.market.PositionQueryService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * UnifiedPositionService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class UnifiedPositionService {

    // 缓存：仓位数据列表，5分钟过期
    final Cache<Long, List<CexPosition>> positionCache;
    // 缓存：仓位汇总数据，5分钟过期
    final Cache<Long, PositionSummaryModel> summaryCache;
    // 缓存：仓位风险评估数据，5分钟过期
    final Cache<Long, PositionRiskModel> riskCache;
    // ✅ 新增：API调用缓存，1分钟过期，避免短时间内重复调用API
    final Cache<Long, List<CexPosition>> apiCallCache;
    @Autowired
    ApiKeyRepository apiKeyRepository;
    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    UnifiedCexApiService unifiedCexApiService;
    @Autowired
    ApplicationEventPublisher eventPublisher;
    // 应用关闭标志位，防止异步任务在Spring容器销毁时继续执行
    private volatile boolean shuttingDown = false;

    public UnifiedPositionService() {
        this.positionCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();

        this.summaryCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();

        this.riskCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();

        // ✅ 新增：API调用缓存，1分钟过期，避免短时间内重复调用API
        this.apiCallCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * 每10秒执行一次仓位数据更新
     * 统一获取所有活跃API密钥的仓位数据
     */
    @Scheduled(cron = "0/30 * * * * ?")
    public void updatePositionData() {
        // 检查应用是否正在关闭，避免在Spring容器销毁时继续执行
        if (shuttingDown) {
            log.debug("应用正在关闭，跳过仓位数据更新任务");
            return;
        }

        log.debug("开始执行统一仓位数据更新任务");
        try {
            // 获取所有活跃的OKX API密钥
            List<ApiKey> activeKeys = apiKeyRepository.findActiveKeysByCexName("OKX");
            if (activeKeys.isEmpty()) {
                log.debug("未找到活跃的OKX API密钥，跳过本次更新");
                return;
            }
            log.debug("开始更新{}个活跃API密钥的仓位数据", activeKeys.size());

            // ✅ 新增：清除API调用缓存，确保定时任务总是获取最新的数据
            activeKeys.forEach(key -> apiCallCache.invalidate(key.getKeyId()));

            // 顺序获取所有API密钥的仓位数据,避免并发导致数据库连接池耗尽
            Map<Long, List<CexPosition>> positionDataMap = activeKeys.stream()
                    .map(key -> {
                        try {
                            List<CexPosition> positions = getPositionData(key.getKeyId());
                            if (null != positions) {
                                return Map.entry(key.getKeyId(), positions);
                            }
                        } catch (Exception e) {
                            log.error("获取API密钥 {} 的仓位数据失败", key.getKeyId(), e);
                            // 发布错误更新事件
                            PositionUpdateEvent errorEvent = PositionUpdateEvent.error(key.getKeyId(), e.getMessage());
                            eventPublisher.publishEvent(errorEvent);
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            // 更新内存缓存
            positionDataMap.forEach(this::updateCaches);
            // 发布数据更新事件
            positionDataMap.forEach((keyId, positions) -> {
                // 发布成功更新事件
                PositionUpdateEvent successEvent = PositionUpdateEvent.success(keyId, positions, positionDataMap);
                eventPublisher.publishEvent(successEvent);
            });
            log.debug("仓位数据更新完成，成功更新{}个API密钥", positionDataMap.size());
        } catch (Exception e) {
            log.error("统一仓位数据更新任务执行失败", e);
        }
    }

    /**
     * 获取指定API密钥的仓位数据
     * ✅ 重构：支持多种合约类型(SWAP、FUTURES、OPTION)
     * ✅ 重构：返回通用CEX Model
     * ✅ 优化：添加API调用缓存，避免短时间内重复调用API
     */
    List<CexPosition> getPositionData(Long keyId) {
        // ✅ 新增：先检查API调用缓存
        List<CexPosition> cachedPositions = apiCallCache.getIfPresent(keyId);
        if (cachedPositions != null) {
            log.debug("从API调用缓存获取仓位数据 - apiKeyId: {}", keyId);
            return new ArrayList<>(cachedPositions);  // 返回副本，避免缓存被修改
        }

        ApiKey decryptedKey = apiKeyService.getDecryptedKey(keyId);
        if (null == decryptedKey) {
            log.warn("无法获取API密钥 {} 的解密信息", keyId);
            return null;
        }
        try {
            // ✅ 修改：获取多种合约类型(SWAP、FUTURES、OPTION)
//            List<String> instTypes = Arrays.asList("SWAP", "FUTURES", "OPTION");
            List<String> instTypes = List.of("SWAP");
            List<CexPosition> allPositions = new ArrayList<>();
            long currentTime = System.currentTimeMillis();  // 统一时间戳
            for (String instType : instTypes) {
                try {
                    // 现在直接返回通用CEX Model
                    List<CexPosition> positions = unifiedCexApiService.getPositions(decryptedKey, instType);
                    if (positions != null && !positions.isEmpty()) {
                        allPositions.addAll(positions);
                    }
                } catch (Exception e) {
                    log.warn("获取{}类型持仓失败 - API Key: {}", instType, keyId, e);
                    // 继续处理其他类型，不中断流程
                }
            }
            if (CollectionUtils.isEmpty(allPositions)) {
                List<CexPosition> emptyResult = new ArrayList<>();
                apiCallCache.put(keyId, emptyResult);  // 缓存空结果
                return emptyResult;
            }
            // 过滤出有持仓的记录(持仓数量不为0)
            List<CexPosition> filteredPositions = allPositions.stream()
                    .filter(pos -> pos.getQuantity() != null && pos.getQuantity().compareTo(BigDecimal.ZERO) != 0)
                    .collect(Collectors.toList());

            // ✅ 新增：将结果存入API调用缓存
            apiCallCache.put(keyId, new ArrayList<>(filteredPositions));

            return filteredPositions;
        } catch (Exception e) {
            log.error("获取仓位数据失败 - API Key: {}", keyId, e);
            return null;
        }
    }

    /**
     * 更新所有相关的缓存数据
     */
    void updateCaches(Long keyId, List<CexPosition> positions) {
        // 获取旧持仓数据
        List<CexPosition> oldPositions = positionCache.getIfPresent(keyId);
        // 检查持仓是否发生变化
        boolean changed = checkPositionChanged(oldPositions, positions);
        // 更新仓位数据缓存
        positionCache.put(keyId, positions);
        // 更新仓位汇总缓存
        PositionSummaryModel summary = calculatePositionSummary(keyId, positions);
        summaryCache.put(keyId, summary);
        // 更新风险评估缓存
        PositionRiskModel risk = assessPositionRisk(keyId, positions, summary);
        riskCache.put(keyId, risk);
        // 如果持仓发生变化，发布持仓变更事件
        if (changed) {
            PositionChangedEvent event = PositionChangedEvent.of(keyId, oldPositions, positions, true);
            eventPublisher.publishEvent(event);
            log.debug("持仓已变更，发布PositionChangedEvent - apiKeyId: {}", keyId);
        }
    }

    /**
     * 检查持仓是否发生变化
     * 比较品种symbol + 方向side + 仓位quantity + 开仓时间createTime
     *
     * @param oldPositions 旧持仓列表
     * @param newPositions 新持仓列表
     * @return 是否发生变化
     */
    private boolean checkPositionChanged(List<CexPosition> oldPositions, List<CexPosition> newPositions) {
        // 如果新旧持仓都为空，认为没有变化
        if (CollectionUtils.isEmpty(oldPositions) && CollectionUtils.isEmpty(newPositions)) {
            return false;
        }
        // 如果一方为空另一方不为空，认为发生变化
        if (CollectionUtils.isEmpty(oldPositions) || CollectionUtils.isEmpty(newPositions)) {
            return true;
        }
        // 如果数量不同，认为发生变化
        if (oldPositions.size() != newPositions.size()) {
            return true;
        }
        // 比较每个持仓的(品种+方向+仓位+开仓时间)
        for (CexPosition oldPos : oldPositions) {
            boolean found = newPositions.stream().anyMatch(newPos ->
                    isPositionEqual(oldPos, newPos));
            if (!found) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断两个持仓是否相等
     * 比较品种symbol + 方向side + 仓位quantity + 开仓时间createTime
     *
     * @param pos1 持仓1
     * @param pos2 持仓2
     * @return 是否相等
     */
    private boolean isPositionEqual(CexPosition pos1, CexPosition pos2) {
        if (pos1 == null || pos2 == null) {
            return false;
        }
        // 比较品种
        if (!Objects.equals(pos1.getSymbol(), pos2.getSymbol())) {
            return false;
        }
        // 比较方向
        if (pos1.getSide() != pos2.getSide()) {
            return false;
        }
        // 比较仓位
        if (!Objects.equals(pos1.getQuantity(), pos2.getQuantity())) {
            return false;
        }
        // 比较开仓时间
        if (!Objects.equals(pos1.getCreateTime(), pos2.getCreateTime())) {
            return false;
        }
        return true;
    }

    /**
     * 计算仓位汇总数据
     */
    PositionSummaryModel calculatePositionSummary(Long keyId, List<CexPosition> positions) {
        if (CollectionUtils.isEmpty(positions)) {
            return PositionSummaryModel.empty();
        }
        PositionSummaryModel.PositionSummaryModelBuilder builder = PositionSummaryModel.builder();
        builder.apiKeyId(keyId);
        int totalPositions = positions.size();
        int longPositions = 0;
        int shortPositions = 0;
        BigDecimal totalUnrealizedPnl = BigDecimal.ZERO;
        BigDecimal longUnrealizedPnl = BigDecimal.ZERO;
        BigDecimal shortUnrealizedPnl = BigDecimal.ZERO;
        BigDecimal totalMargin = BigDecimal.ZERO;
        BigDecimal totalNotional = BigDecimal.ZERO;
        BigDecimal maxSinglePositionPnl = BigDecimal.ZERO;
        BigDecimal maxSinglePositionMargin = BigDecimal.ZERO;
        for (CexPosition position : positions) {
            // 统计多空持仓数量
            PositionSide posSide = position.getSide();
            if (PositionSide.LONG == posSide) {
                longPositions++;
            } else if (PositionSide.SHORT == posSide) {
                shortPositions++;
            }
            // 累计盈亏
            BigDecimal upl = position.getUnrealizedPnl() != null ? position.getUnrealizedPnl() : BigDecimal.ZERO;
            totalUnrealizedPnl = totalUnrealizedPnl.add(upl);
            if (PositionSide.LONG == posSide) {
                longUnrealizedPnl = longUnrealizedPnl.add(upl);
            } else {
                shortUnrealizedPnl = shortUnrealizedPnl.add(upl);
            }
            // 累计保证金
            BigDecimal margin = position.getMargin() != null ? position.getMargin() : BigDecimal.ZERO;
            totalMargin = totalMargin.add(margin);
            // 累计持仓价值
            BigDecimal notional = position.getNotionalValue() != null ? position.getNotionalValue() : BigDecimal.ZERO;
            totalNotional = totalNotional.add(notional);
            // 记录最大值
            if (upl.abs().compareTo(maxSinglePositionPnl.abs()) > 0) {
                maxSinglePositionPnl = upl;
            }
            if (margin.compareTo(maxSinglePositionMargin) > 0) {
                maxSinglePositionMargin = margin;
            }
        }
        builder.totalPositions(totalPositions);
        builder.longPositions(longPositions);
        builder.shortPositions(shortPositions);
        builder.totalUnrealizedPnl(totalUnrealizedPnl);
        builder.longUnrealizedPnl(longUnrealizedPnl);
        builder.shortUnrealizedPnl(shortUnrealizedPnl);
        builder.totalMargin(totalMargin);
        builder.totalNotional(totalNotional);
        builder.maxSinglePositionPnl(maxSinglePositionPnl);
        builder.maxSinglePositionMargin(maxSinglePositionMargin);
        // 计算风险等级和评分
        String riskLevel = calculateRiskLevel(totalUnrealizedPnl, totalMargin, totalNotional);
        Integer riskScore = calculateRiskScore(totalUnrealizedPnl, totalMargin, totalNotional);
        builder.riskLevel(riskLevel);
        builder.riskScore(riskScore);
        // 计算保证金使用率（如果有总权益数据的话会更准确，这里基于持仓数据估算）
        BigDecimal marginUsageRate = BigDecimal.ZERO;
        if (totalNotional.compareTo(BigDecimal.ZERO) > 0) {
            marginUsageRate = totalMargin.divide(totalNotional, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        builder.marginUsageRate(marginUsageRate);
        builder.updateTime(LocalDateTime.now());
        return builder.build();
    }

    /**
     * 评估仓位风险
     */
    PositionRiskModel assessPositionRisk(Long keyId, List<CexPosition> positions, PositionSummaryModel summary) {
        if (CollectionUtils.isEmpty(positions)) {
            return PositionRiskModel.lowRisk(); // 默认低风险
        }
        PositionRiskModel.PositionRiskModelBuilder builder = PositionRiskModel.builder();
        builder.apiKeyId(keyId);
        // 基于汇总数据计算各项风险指标
        BigDecimal totalPnl = summary.getTotalUnrealizedPnl();
        BigDecimal totalMargin = summary.getTotalMargin();
        BigDecimal totalNotional = summary.getTotalNotional();
        // 计算保证金使用率风险
        int leverageRisk = 0;
        if (totalNotional.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal leverageRatio = totalNotional.divide(
                    totalMargin.add(BigDecimal.ONE), 4, RoundingMode.HALF_UP); // 避免除零
            leverageRisk = leverageRatio.compareTo(new BigDecimal("10")) > 0 ? 80 :
                    leverageRatio.compareTo(new BigDecimal("5")) > 0 ? 60 :
                            leverageRatio.compareTo(new BigDecimal("3")) > 0 ? 40 : 20;
        }
        // 计算集中度风险（基于单个持仓占比）
        int concentrationRisk = 0;
        if (totalNotional.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal maxRatio = summary.getMaxSinglePositionMargin().divide(
                    totalMargin.add(BigDecimal.ONE), 4, RoundingMode.HALF_UP);
            concentrationRisk = maxRatio.compareTo(new BigDecimal("0.5")) > 0 ? 90 :
                    maxRatio.compareTo(new BigDecimal("0.3")) > 0 ? 70 :
                            maxRatio.compareTo(new BigDecimal("0.2")) > 0 ? 50 : 30;
        }
        // 计算市场风险（基于未实现盈亏波动）
        Integer marketRisk = totalPnl.abs().compareTo(totalMargin.multiply(new BigDecimal("0.2"))) > 0 ? 75 :
                totalPnl.abs().compareTo(totalMargin.multiply(new BigDecimal("0.1"))) > 0 ? 50 : 25;
        // 流动性风险和回撤风险使用固定值（实际中需要更复杂的计算）
        Integer liquidityRisk = 30;
        Integer drawdownRisk = totalPnl.compareTo(BigDecimal.ZERO) < 0 ?
                Math.min(60, totalPnl.abs().divide(totalMargin.add(BigDecimal.ONE),
                        0, RoundingMode.HALF_UP).intValue()) : 20;
        // 计算综合风险评分
        Integer riskScore = (leverageRisk + concentrationRisk + marketRisk + liquidityRisk + drawdownRisk) / 5;
        // 确定风险等级
        String overallRiskLevel = riskScore >= 80 ? "CRITICAL" :
                riskScore >= 60 ? "HIGH" :
                        riskScore >= 40 ? "MEDIUM" : "LOW";
        // 设置风险指标
        builder.overallRiskLevel(overallRiskLevel);
        builder.riskScore(riskScore);
        builder.concentrationRisk(concentrationRisk);
        builder.leverageRisk(leverageRisk);
        builder.marketRisk(marketRisk);
        builder.liquidityRisk(liquidityRisk);
        builder.drawdownRisk(drawdownRisk);
        // 设置比率数据
        builder.marginUsageRate(summary.getMarginUsageRate());
        builder.pnlToEquityRatio(totalNotional.compareTo(BigDecimal.ZERO) > 0 ?
                totalPnl.divide(totalNotional, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        builder.maxPositionRatio(totalMargin.compareTo(BigDecimal.ZERO) > 0 ?
                summary.getMaxSinglePositionMargin().divide(totalMargin, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        builder.volatilityRatio(BigDecimal.valueOf(0.25)); // 默认波动率
        // 设置风险建议
        String riskWarning = riskScore >= 80 ? "当前风险水平极高，立即减少仓位" :
                riskScore >= 60 ? "当前风险水平较高，建议减少仓位" :
                        riskScore >= 40 ? "当前风险水平适中，需要密切关注市场变化" :
                                "当前风险水平较低，可适当增加仓位";
        String recommendedAction = riskScore >= 60 ? "REDUCE" : "MAINTAIN";
        builder.riskWarning(riskWarning);
        builder.recommendedAction(recommendedAction);
        builder.currentExposure(totalNotional);
        builder.assessmentTime(LocalDateTime.now());
        return builder.build();
    }

    /**
     * 计算风险等级
     */
    String calculateRiskLevel(BigDecimal totalPnl, BigDecimal totalMargin, BigDecimal totalNotional) {
        if (totalMargin.compareTo(BigDecimal.ZERO) == 0) {
            return "LOW";
        }
        // 基于亏损比例和保证金使用率计算风险
        BigDecimal lossRatio = totalPnl.compareTo(BigDecimal.ZERO) < 0 ?
                totalPnl.abs().divide(totalMargin, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal marginRatio = totalNotional.compareTo(BigDecimal.ZERO) > 0 ?
                totalMargin.divide(totalNotional, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        if (lossRatio.compareTo(new BigDecimal("0.3")) > 0 || marginRatio.compareTo(new BigDecimal("0.9")) > 0) {
            return "HIGH";
        } else if (lossRatio.compareTo(new BigDecimal("0.15")) > 0 || marginRatio.compareTo(new BigDecimal("0.7")) > 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * 计算风险评分
     */
    Integer calculateRiskScore(BigDecimal totalPnl, BigDecimal totalMargin, BigDecimal totalNotional) {
        if (totalMargin.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        BigDecimal lossRatio = totalPnl.compareTo(BigDecimal.ZERO) < 0 ?
                totalPnl.abs().divide(totalMargin, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal marginRatio = totalNotional.compareTo(BigDecimal.ZERO) > 0 ?
                totalMargin.divide(totalNotional, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        int lossScore = lossRatio.compareTo(new BigDecimal("0.3")) > 0 ? 80 :
                lossRatio.compareTo(new BigDecimal("0.15")) > 0 ? 50 : 20;
        int marginScore = marginRatio.compareTo(new BigDecimal("0.9")) > 0 ? 90 :
                marginRatio.compareTo(new BigDecimal("0.7")) > 0 ? 60 : 30;
        return (lossScore + marginScore) / 2;
    }

    /**
     * 获取指定API密钥的最新仓位数据（智能刷新）
     * <p>
     * 优先从缓存获取数据，如果缓存未命中，自动刷新数据后返回。
     * 调用方无需关心缓存逻辑，实现缓存透明化。
     * </p>
     * <p>
     * 缓存策略：
     * <ul>
     *   <li>优先从Caffeine缓存读取（5分钟TTL）</li>
     *   <li>缓存未命中时自动调用OKX API刷新</li>
     *   <li>刷新失败时返回空列表（避免null指针）</li>
     * </ul>
     * </p>
     *
     * @param apiKeyId API密钥ID
     * @return 仓位数据列表，失败返回空列表
     */
    public List<CexPosition> getLatestPositionDataWithAutoRefresh(Long apiKeyId) {
        // 1. 先尝试从缓存获取
        List<CexPosition> positions = positionCache.getIfPresent(apiKeyId);
        // 2. 缓存未命中，自动刷新
        if (CollectionUtils.isEmpty(positions)) {
            log.debug("缓存未命中，自动刷新仓位数据 - apiKeyId: {}", apiKeyId);
            boolean refreshSuccess = refreshPositionData(apiKeyId);
            // 3. 刷新成功后再次尝试从缓存获取
            if (refreshSuccess) {
                positions = positionCache.getIfPresent(apiKeyId);
            }
            // 4. 如果仍然为空，返回空列表而非null(避免调用方空指针异常)
            if (null == positions) {
                log.warn("仓位数据获取失败 - apiKeyId: {}", apiKeyId);
                return Collections.emptyList();
            }
        } else {
            log.debug("缓存命中 - apiKeyId: {}, 仓位数量: {}", apiKeyId, positions.size());
        }
        return positions;
    }

    /**
     * 获取指定API密钥的最新仓位数据（智能刷新，带控制参数）
     * <p>
     * 提供更细粒度的控制，允许调用方决定是否自动刷新。
     * </p>
     *
     * @param apiKeyId API密钥ID
     * @param useCache 是否使用缓存 true/false
     * @return 仓位数据列表，根据autoRefresh参数决定行为
     */
    public List<CexPosition> getLatestPositionData(Long apiKeyId, boolean useCache) {
        List<CexPosition> positions = useCache ? positionCache.getIfPresent(apiKeyId) : Collections.emptyList();
        if (!useCache) {
            log.debug("缓存未命中，执行自动刷新 - apiKeyId: {}", apiKeyId);
            boolean refreshSuccess = refreshPositionData(apiKeyId);
            if (refreshSuccess) {
                positions = positionCache.getIfPresent(apiKeyId);
            }
        }
        return positions;
    }

    /**
     * 获取指定API密钥的仓位汇总数据
     */
    public PositionSummaryModel getPositionSummary(Long apiKeyId) {
        return summaryCache.getIfPresent(apiKeyId);
    }

    /**
     * 获取指定API密钥的仓位风险评估数据
     */
    public PositionRiskModel getPositionRisk(Long apiKeyId) {
        return riskCache.getIfPresent(apiKeyId);
    }

    /**
     * 获取所有API密钥的最新仓位数据
     */
    public Map<Long, List<CexPosition>> getAllLatestPositionData() {
        return new ConcurrentHashMap<>(positionCache.asMap());
    }

    /**
     * 获取所有API密钥的仓位汇总数据
     */
    public Map<Long, PositionSummaryModel> getAllPositionSummaries() {
        return new ConcurrentHashMap<>(summaryCache.asMap());
    }

    /**
     * 刷新指定API密钥的仓位数据
     */
    public boolean refreshPositionData(Long keyId) {
        try {
            List<CexPosition> positions = getPositionData(keyId);
            if (null != positions) {
                updateCaches(keyId, positions);
                // 发布更新事件
                PositionUpdateEvent successEvent = PositionUpdateEvent.success(keyId, positions,
                        Map.of(keyId, positions));
                eventPublisher.publishEvent(successEvent);
                return true;
            }
        } catch (Exception e) {
            log.error("刷新API密钥 {} 的仓位数据失败", keyId, e);
            // 发布错误更新事件
            PositionUpdateEvent errorEvent = PositionUpdateEvent.error(keyId, e.getMessage());
            eventPublisher.publishEvent(errorEvent);
        }
        return false;
    }

    // ========== CEX API调用事件监听 ==========

    /**
     * 监听CEX API调用事件,清除相关仓位缓存
     * <p>
     * 当PLACE_ORDER或CLOSE_POSITION发生时,立即清除对应API Key的缓存
     * 确保仓位数据的实时性
     * </p>
     *
     * @param event API调用事件
     */
    @EventListener
    public void onCexApiCall(CexApiCallEvent event) {
        // 检查应用是否正在关闭
        if (shuttingDown) {
            log.debug("应用正在关闭，跳过CexApiCallEvent处理");
            return;
        }

        // 只处理成功的事件
        if (CexApiCallStatus.SUCCESS != event.getStatus()) {
            return;
        }

        // 只处理下单和平仓操作
        CexApiType apiType = event.getApiType();
        if (!apiType.isPlaceOrder() && !apiType.isClosePosition()) {
            return;
        }

        // 获取API Key ID
        Long apiKeyId = event.getApiKeyId();
        if (null == apiKeyId) {
            log.warn("无法从事件中获取API Key ID - apiType: {}, instId: {}", apiType, event.getInstId());
            return;
        }

        // 更新该API Key的仓位缓存
        refreshPositionData(apiKeyId);

        log.debug("已清除活跃仓位缓存 - apiType: {}, apiKeyId: {}, instId: {}", apiType, apiKeyId, event.getInstId());
    }

    /**
     * ✅ 新增：计算分组统计信息
     * 用于okx-positions页面的统计展示
     *
     * @param apiKeyId  API密钥ID
     * @param positions 持仓列表
     * @return 分组统计数据
     */
    public PositionQueryService.PositionStatisticsModel calculatePositionStatistics(Long apiKeyId, List<CexPosition> positions) {
        if (CollectionUtils.isEmpty(positions)) {
            return new PositionQueryService.PositionStatisticsModel();
        }

        PositionQueryService.PositionStatisticsModel statistics = new PositionQueryService.PositionStatisticsModel();

        // 计算基础统计
        statistics.setTotalPositions(positions.size());

        BigDecimal totalNotional = BigDecimal.ZERO;
        BigDecimal totalUnrealizedPnl = BigDecimal.ZERO;
        BigDecimal totalMargin = BigDecimal.ZERO;

        for (CexPosition pos : positions) {
            totalNotional = totalNotional.add(pos.getNotionalValue() != null ? pos.getNotionalValue() : BigDecimal.ZERO);
            totalUnrealizedPnl = totalUnrealizedPnl.add(pos.getUnrealizedPnl() != null ? pos.getUnrealizedPnl() : BigDecimal.ZERO);
            totalMargin = totalMargin.add(pos.getMargin() != null ? pos.getMargin() : BigDecimal.ZERO);
        }

        statistics.setTotalNotional(totalNotional);
        statistics.setTotalUnrealizedPnl(totalUnrealizedPnl);
        statistics.setTotalMargin(totalMargin);

        // ✅ 优化：单次遍历同时计算三个维度的统计信息，避免重复遍历数据
        Map<String, PositionQueryService.PositionStatisticsModel.StatisticsItem> sideStats = new HashMap<>();
        Map<String, PositionQueryService.PositionStatisticsModel.StatisticsItem> typeStats = new HashMap<>();
        Map<String, PositionQueryService.PositionStatisticsModel.StatisticsItem> currencyStats = new HashMap<>();

        for (CexPosition pos : positions) {
            BigDecimal notional = pos.getNotionalValue() != null ? pos.getNotionalValue() : BigDecimal.ZERO;
            BigDecimal pnl = pos.getUnrealizedPnl() != null ? pos.getUnrealizedPnl() : BigDecimal.ZERO;
            BigDecimal margin = pos.getMargin() != null ? pos.getMargin() : BigDecimal.ZERO;

            // 按持仓方向统计
            String sideKey = pos.getSide() != null ? pos.getSide().name() : "UNKNOWN";
            PositionQueryService.PositionStatisticsModel.StatisticsItem sideItem = sideStats.computeIfAbsent(sideKey, k -> {
                PositionQueryService.PositionStatisticsModel.StatisticsItem item = new PositionQueryService.PositionStatisticsModel.StatisticsItem();
                item.setName(k);
                item.setCount(0);
                item.setTotalNotional(BigDecimal.ZERO);
                item.setTotalPnl(BigDecimal.ZERO);
                item.setTotalMargin(BigDecimal.ZERO);
                return item;
            });
            sideItem.setCount(sideItem.getCount() + 1);
            sideItem.setTotalNotional(sideItem.getTotalNotional().add(notional));
            sideItem.setTotalPnl(sideItem.getTotalPnl().add(pnl));
            sideItem.setTotalMargin(sideItem.getTotalMargin().add(margin));

            // 按合约类型统计
            String typeKey = pos.getInstrumentType() != null ? pos.getInstrumentType() : "UNKNOWN";
            PositionQueryService.PositionStatisticsModel.StatisticsItem typeItem = typeStats.computeIfAbsent(typeKey, k -> {
                PositionQueryService.PositionStatisticsModel.StatisticsItem item = new PositionQueryService.PositionStatisticsModel.StatisticsItem();
                item.setName(k);
                item.setCount(0);
                item.setTotalNotional(BigDecimal.ZERO);
                item.setTotalPnl(BigDecimal.ZERO);
                item.setTotalMargin(BigDecimal.ZERO);
                return item;
            });
            typeItem.setCount(typeItem.getCount() + 1);
            typeItem.setTotalNotional(typeItem.getTotalNotional().add(notional));
            typeItem.setTotalPnl(typeItem.getTotalPnl().add(pnl));
            typeItem.setTotalMargin(typeItem.getTotalMargin().add(margin));

            // 按币种统计（从symbol提取币种）
            String currencyKey = pos.getSymbol().split("-")[0];
            PositionQueryService.PositionStatisticsModel.StatisticsItem currencyItem = currencyStats.computeIfAbsent(currencyKey, k -> {
                PositionQueryService.PositionStatisticsModel.StatisticsItem item = new PositionQueryService.PositionStatisticsModel.StatisticsItem();
                item.setName(k);
                item.setCount(0);
                item.setTotalNotional(BigDecimal.ZERO);
                item.setTotalPnl(BigDecimal.ZERO);
                item.setTotalMargin(BigDecimal.ZERO);
                return item;
            });
            currencyItem.setCount(currencyItem.getCount() + 1);
            currencyItem.setTotalNotional(currencyItem.getTotalNotional().add(notional));
            currencyItem.setTotalPnl(currencyItem.getTotalPnl().add(pnl));
            currencyItem.setTotalMargin(currencyItem.getTotalMargin().add(margin));
        }

        // 转换为List并设置到统计模型
        statistics.setSideStatistics(new ArrayList<>(sideStats.values()));
        statistics.setTypeStatistics(new ArrayList<>(typeStats.values()));
        statistics.setCurrencyStatistics(new ArrayList<>(currencyStats.values()));

        log.debug("计算分组统计完成 - apiKeyId: {}, 总持仓数: {}", apiKeyId, positions.size());
        return statistics;
    }

    /**
     * 获取持仓统计数据（智能刷新）
     * <p>
     * 自动处理缓存获取、空值判断、数据刷新等逻辑。
     * Controller层无需关心缓存实现，直接调用此方法即可。
     * </p>
     * <p>
     * 处理流程：
     * <ul>
     *   <li>1. 使用智能刷新获取持仓数据</li>
     *   <li>2. 处理空值情况（返回空统计对象）</li>
     *   <li>3. 计算统计数据（按方向、类型、币种分组）</li>
     * </ul>
     * </p>
     *
     * @param apiKeyId API密钥ID
     * @return 持仓统计数据，无持仓时返回空对象
     */
    public PositionQueryService.PositionStatisticsModel getPositionStatistics(Long apiKeyId) {
        // 1. 使用智能刷新获取持仓数据
        List<CexPosition> cachedPositions = getLatestPositionData(apiKeyId, true);

        // 2. 处理空值情况
        if (CollectionUtils.isEmpty(cachedPositions)) {
            log.debug("持仓数据为空，返回空统计对象 - apiKeyId: {}", apiKeyId);
            return new PositionQueryService.PositionStatisticsModel();
        }

        // 3. 计算统计数据
        return calculatePositionStatistics(apiKeyId, cachedPositions);
    }

    /**
     * 服务启动时预热positionCache
     * <p>
     * 异步执行：不阻塞服务启动,预热在后台进行
     * 静默失败：如果预热失败,记录错误日志但不影响服务运行
     * </p>
     */
    @PostConstruct
    public void warmUpPositionCache() {
        // 异步预热,不阻塞服务启动
        CompletableFuture.runAsync(() -> {
            try {
                log.info("开始预热positionCache（异步执行）");
                updatePositionData();
                log.info("positionCache预热完成");
            } catch (Exception e) {
                log.error("positionCache预热失败（不影响服务运行）", e);
            }
        });
    }

    /**
     * 应用关闭时的清理方法
     * 设置关闭标志位，防止异步任务在Spring容器销毁时继续执行
     */
    @PreDestroy
    public void destroy() {
        log.info("UnifiedPositionService 正在关闭，设置关闭标志位");
        shuttingDown = true;
        // 清理缓存
        positionCache.invalidateAll();
        summaryCache.invalidateAll();
        riskCache.invalidateAll();
        apiCallCache.invalidateAll();
        log.info("UnifiedPositionService 资源清理完成");
    }

}
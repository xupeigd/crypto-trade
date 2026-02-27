package com.crypto.trade.service.unified;

import com.crypto.trade.dto.cex.model.CexAccountBalance;
import com.crypto.trade.dto.cex.model.CexAccountPortfolio;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.enums.CexApiCallStatus;
import com.crypto.trade.enums.CexApiType;
import com.crypto.trade.event.CexApiCallEvent;
import com.crypto.trade.model.AccountDetailModel;
import com.crypto.trade.service.BalanceUpdateEvent;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * UnifiedBalanceService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class UnifiedBalanceService {

    private static final long MIN_REFRESH_INTERVAL_MS = 5000L; // 5秒最小刷新间隔
    // 缓存：详细的余额数据，5分钟过期
    private final Cache<Long, Map<String, CexAccountBalance>> ccyBalanceCache;
    private final Cache<Long, CexAccountPortfolio> accountBalance;
    // 防重复刷新：记录每个API Key的最后刷新时间（5秒最小间隔）
    private final Map<Long, Long> lastRefreshTime = new ConcurrentHashMap<>();
    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    UnifiedCexApiService unifiedCexApiService;
    @Autowired
    ApplicationEventPublisher eventPublisher;
    // 应用关闭标志位，防止异步任务在Spring容器销毁时继续执行
    private volatile boolean shuttingDown = false;

    public UnifiedBalanceService() {
        this.ccyBalanceCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();
        this.accountBalance = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * 每分钟执行一次余额数据更新
     * 统一获取所有活跃API密钥的余额数据
     */
    @Scheduled(cron = "7 */1 * * * ?")
    public void updateBalanceData() {
        // 检查应用是否正在关闭，避免在Spring容器销毁时继续执行
        if (shuttingDown) {
            log.debug("应用正在关闭，跳过余额数据更新任务");
            return;
        }

        log.debug("开始执行统一余额数据更新任务");
        try {
            // 获取所有活跃的OKX API密钥
            List<ApiKey> activeKeys = apiKeyService.getActiveKeys();
            if (activeKeys.isEmpty()) {
                log.debug("未找到活跃的OKX API密钥，跳过本次更新");
                return;
            }
            log.debug("开始更新{}个活跃API密钥的余额数据", activeKeys.size());
            Map<Long, CexAccountPortfolio> id2AccountBalance = new HashMap<>();
            // 顺序获取所有API密钥的余额数据,避免并发导致数据库连接池耗尽
            Map<Long, Map<String, CexAccountBalance>> ccyNewData = activeKeys.stream()
                    .map(key -> {
                        try {
                            CexAccountPortfolio accountPortfolio = getAccountBalance(key.getKeyId());
                            if (null != accountPortfolio) {
                                id2AccountBalance.put(key.getKeyId(), accountPortfolio);
                                if (!CollectionUtils.isEmpty(accountPortfolio.getBalances())) {
                                    Map<String, CexAccountBalance> ccy2Details = accountPortfolio.getBalances().stream()
                                            .collect(Collectors.toMap(CexAccountBalance::getCurrency, v -> v));
                                    return Map.entry(key.getKeyId(), ccy2Details);
                                }
                            }
                        } catch (Exception e) {
                            log.error("获取API密钥 {} 的余额数据失败", key.getKeyId(), e);
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            // 更新内存缓存
            ccyNewData.forEach(ccyBalanceCache::put);
            id2AccountBalance.forEach(accountBalance::put);
            // 发布数据更新事件
            id2AccountBalance.forEach((keyId, accountData) -> eventPublisher.publishEvent(new BalanceUpdateEvent(this, keyId, accountData)));
            log.debug("余额数据更新完成，成功更新{}个API密钥", ccyNewData.size());
        } catch (Exception e) {
            log.error("统一余额数据更新任务执行失败", e);
        }
    }

    private CexAccountPortfolio getAccountBalance(Long keyId) {
        ApiKey decryptedKey = apiKeyService.getDecryptedKey(keyId);
        if (null == decryptedKey) {
            log.warn("无法获取API密钥 {} 的解密信息", keyId);
            return null;
        }
        // 调用API获取余额数据 - 返回CexAccountBalance列表
        List<CexAccountBalance> cexBalances = unifiedCexApiService.getAccountBalance(decryptedKey, null);
        if (CollectionUtils.isEmpty(cexBalances)) {
            return null;
        }
        // 将CexAccountBalance转换为CexAccountPortfolio
        CexAccountPortfolio portfolio = new CexAccountPortfolio();
        portfolio.setBalances(cexBalances);
        portfolio.setUpdateTime(LocalDateTime.now());
        // 计算总权益和可用权益
        BigDecimal totalEquity = cexBalances.stream()
                .map(CexAccountBalance::getAvailableBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        portfolio.setTotalEquity(totalEquity);
        portfolio.setAvailableEquity(totalEquity);
        return portfolio;
    }

    /**
     * 获取指定API密钥的最新余额数据
     *
     * @param apiKeyId API密钥ID
     * @return 账户余额数据，如果不存在则返回null
     */
    public CexAccountPortfolio getLatestBalanceData(Long apiKeyId) {
        return accountBalance.getIfPresent(apiKeyId);
    }

    public CexAccountBalance getLatestBalanceData(Long apiKeyId, String ccy) {
        Map<String, CexAccountBalance> ccy2Details = ccyBalanceCache.getIfPresent(apiKeyId);
        return null != ccy2Details ? ccy2Details.get(ccy) : null;
    }

    /**
     * 获取所有API密钥的最新余额数据
     *
     * @return 余额数据映射 (API密钥ID -> 余额数据)
     */
    public Map<Long, CexAccountPortfolio> getAllLatestBalanceData() {
        return new ConcurrentHashMap<>(accountBalance.asMap());
    }

    public boolean refreshBalanceData(Long keyId) {
        // 防重复刷新：5秒内已刷新过则跳过
        long now = System.currentTimeMillis();
        Long lastRefresh = lastRefreshTime.get(keyId);

        if (null != lastRefresh && (now - lastRefresh) < MIN_REFRESH_INTERVAL_MS) {
            log.debug("API Key {} 在5秒内已刷新过，跳过本次刷新", keyId);
            return true;
        }

        // 执行实际的刷新逻辑
        CexAccountPortfolio accountPortfolio = getAccountBalance(keyId);
        if (null != accountPortfolio && !CollectionUtils.isEmpty(accountPortfolio.getBalances())) {
            Map<String, CexAccountBalance> ccy2Details = accountPortfolio.getBalances().stream()
                    .collect(Collectors.toMap(CexAccountBalance::getCurrency, v -> v)); // 转换为Map
            ccyBalanceCache.put(keyId, ccy2Details);
            accountBalance.put(keyId, accountPortfolio);
            eventPublisher.publishEvent(new BalanceUpdateEvent(this, keyId, accountPortfolio));

            // 记录刷新时间
            lastRefreshTime.put(keyId, now);

            return true;
        }
        return false;
    }

    public AccountDetailModel getAccountUsdtDetail(Long keyId) {
        log.debug("获取账户详情信息，apiKeyId: {}", keyId);
        try {
            // 验证API Key是否存在且活跃
            ApiKey apiKey = apiKeyService.getDecryptedKey(keyId);
            if (null == apiKey || !"active".equals(apiKey.getStatus())) {
                log.debug("API Key不存在或未激活，apiKeyId: {}", keyId);
                return AccountDetailModel.empty();
            }

            // 获取最新余额数据
            CexAccountBalance accountData = getLatestBalanceData(keyId, "USDT");

            // 空值保护：如果仍然无法获取数据，返回默认值
            if (null == accountData) {
                log.warn("无法获取账户余额数据且刷新失败，apiKeyId: {}，返回默认值", keyId);
                return AccountDetailModel.empty();
            }

            // 初始化账户详情数据
            BigDecimal totalEquity = BigDecimal.ZERO;
            BigDecimal availableBalance = BigDecimal.ZERO;
            BigDecimal usedMargin = accountData.getUsedMargin();
            BigDecimal unrealizedPnl = accountData.getUnrealizedPnl();

            // 从数据中提取余额信息
            totalEquity = accountData.getEquityInUsd() != null ? accountData.getEquityInUsd() : BigDecimal.ZERO;
            availableBalance = accountData.getAvailableBalance() != null ? accountData.getAvailableBalance() : BigDecimal.ZERO;
            log.debug("成功获取账户余额数据 - API Key: {}, 总权益: {}, 可用余额: {}",
                    keyId, totalEquity, availableBalance);

            // 构建响应数据
            AccountDetailModel response = new AccountDetailModel();
            response.setTotalEquity(totalEquity);
            response.setUsedMargin(usedMargin);
            response.setUnrealizedPnl(unrealizedPnl);

            // 计算可用余额：总权益 - 已用保证金，但与账户可用余额取较大值
            BigDecimal calculatedAvailable = totalEquity.subtract(usedMargin);
            response.setAvailableBalance(calculatedAvailable.max(availableBalance));

            // 计算保证金使用率
            if (totalEquity.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal marginRatio = usedMargin
                        .divide(totalEquity, 8, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                response.setMarginRatio(marginRatio);
            } else {
                response.setMarginRatio(BigDecimal.ZERO);
            }
            response.setLastUpdateTime(LocalDateTime.now());
            log.debug("账户详情计算完成 - API Key: {}, 权益: {}, 已用保证金: {}, 可用余额: {}, 未实现盈亏: {}, 保证金使用率: {}%",
                    keyId, response.getTotalEquity(), response.getUsedMargin(), response.getAvailableBalance(),
                    response.getUnrealizedPnl(), response.getMarginRatio());

            return response;
        } catch (Exception e) {
            log.error("获取账户详情失败，apiKeyId: {}", keyId, e);
            return AccountDetailModel.empty();
        }
    }

    /**
     * 监听CEX API调用事件，自动刷新余额缓存
     * <p>
     * 当下单、平仓、取消订单等操作完成后，立即刷新对应的API Key余额缓存
     * </p>
     *
     * @param event API调用事件
     */
    @EventListener
    @Async("cacheTaskExecutor")
    public void onCexApiCallEvent(CexApiCallEvent event) {
        try {
            // 检查应用是否正在关闭
            if (shuttingDown) {
                log.debug("应用正在关闭，跳过CexApiCallEvent处理");
                return;
            }

            // 只处理成功的API调用
            if (event.getStatus() != CexApiCallStatus.SUCCESS) {
                return;
            }

            Long apiKeyId = event.getApiKeyId();
            CexApiType apiType = event.getApiType();

            if (null == apiKeyId || null == apiType) {
                return;
            }

            // 判断是否为余额变更操作
            boolean needRefresh = false;
            switch (apiType) {
                case PLACE_ORDER:          // 下单
                case CLOSE_POSITION:       // 平仓
                case CANCEL_ORDER:         // 取消订单
                case CANCEL:               // 取消订单
                case SET_ALGO_ORDER:       // 设置策略订单
                case AMEND_ALGO_ORDER:     // 修改策略订单
                case CANCEL_ALGO_ORDER:    // 取消策略订单
                    needRefresh = true;
                    break;
                default:
                    break;
            }

            if (needRefresh) {
                log.debug("检测到余额变更操作 {}，立即刷新API Key {} 的余额缓存",
                        apiType, apiKeyId);
                refreshBalanceData(apiKeyId);
            }

        } catch (Exception e) {
            log.error("处理CexApiCallEvent刷新余额缓存失败", e);
        }
    }

    /**
     * 服务启动后预热缓存
     * <p>
     * 异步执行：不阻塞服务启动，预热在后台进行
     * 静默失败：如果预热失败，记录错误日志但不影响服务运行
     * </p>
     */
    @PostConstruct
    public void initCache() {
        // 异步预热，不阻塞服务启动
        CompletableFuture.runAsync(() -> {
            try {
                log.info("开始预热余额缓存（异步执行）");
                updateBalanceData();
                log.info("余额缓存预热完成");
            } catch (Exception e) {
                log.error("余额缓存预热失败（不影响服务运行）", e);
            }
        });
    }

    /**
     * 应用关闭时的清理方法
     * 设置关闭标志位，防止异步任务在Spring容器销毁时继续执行
     */
    @PreDestroy
    public void destroy() {
        log.info("UnifiedBalanceService 正在关闭，设置关闭标志位");
        shuttingDown = true;
        // 清理缓存
        ccyBalanceCache.invalidateAll();
        accountBalance.invalidateAll();
        lastRefreshTime.clear();
        log.info("UnifiedBalanceService 资源清理完成");
    }

}
package com.crypto.trade.service;

import com.crypto.trade.dto.OrderRequest;
import com.crypto.trade.dto.cex.model.*;
import com.crypto.trade.dto.cex.request.*;
import com.crypto.trade.dto.cex.response.CexAlgoOrderOperationResponse;
import com.crypto.trade.dto.cex.response.CexAlgoOrderResponse;
import com.crypto.trade.dto.cex.response.CexOperationResponse;
import com.crypto.trade.dto.cex.response.CexOrderResponse;
import com.crypto.trade.dto.common.InstrumentPriceInfo;
import com.crypto.trade.dto.common.TradingResult;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.enums.CexApiCallStatus;
import com.crypto.trade.enums.CexApiType;
import com.crypto.trade.event.CexApiCallEvent;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import com.crypto.trade.service.unified.UnifiedBalanceService;
import com.crypto.trade.service.unified.UnifiedPositionService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * UnifiedTradingService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class UnifiedTradingService {

    /**
     * 算法订单缓存（止盈止损单）
     * Key: apiKeyId
     * Value: List<CexAlgoOrder>
     * TTL: 5分钟
     * 最大容量: 100个API Key
     */
    private final Cache<Long, Map<String, List<CexAlgoOrder>>> algoOrdersCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build();

    @Autowired
    UnifiedCexApiService unifiedCexApiService;
    @Autowired
    UnifiedPositionService unifiedPositionService;
    @Autowired
    @Qualifier("realTimePriceService")
    RealTimePriceService priceDataService;
    @Autowired
    UnifiedBalanceService unifiedBalanceService;
    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    @Qualifier("cacheTaskExecutor")
    TaskExecutor cacheTaskExecutor;

    // ==================== 订单交易相关 ====================

    /**
     * 下单(通用CEX接口)
     *
     * @param apiKey       API密钥
     * @param orderRequest 订单请求参数
     * @return 下单结果
     */
    public TradingResult placeOrder(ApiKey apiKey, OrderRequest orderRequest) {
        try {
            log.debug("开始下单 - 合约: {}, 方向: {}, 数量: {}, 价格: {}",
                    orderRequest.getInstId(), orderRequest.getSide(), orderRequest.getSz(), orderRequest.getPx());

            // 构建通用CEX下单请求
            CexPlaceOrderRequest cexRequest = buildCexPlaceOrderRequest(orderRequest);

            // 使用通用CEX API进行下单
            CexOrderResponse cexOrderResponse = unifiedCexApiService.placeOrder(apiKey, cexRequest);

            // 解析响应
            return parseCexOrderResponse(cexOrderResponse);

        } catch (Exception e) {
            log.error("下单失败", e);
            return TradingResult.failure("下单失败: " + e.getMessage());
        }
    }

    /**
     * 查询待成交订单(通用CEX接口)
     *
     * @param apiKey   API密钥
     * @param instType 产品类型
     * @return 订单列表
     */
    public List<CexOrder> queryPendingOrders(ApiKey apiKey, String instType) {
        return unifiedCexApiService.getPendingOrders(apiKey, instType, null);
    }

    /**
     * 查询历史订单(通用CEX接口)
     *
     * @param apiKey   API密钥
     * @param instType 产品类型
     * @param instId   产品ID
     * @param state    订单状态
     * @return 订单列表
     */
    public List<CexOrder> queryHistoryOrders(ApiKey apiKey, String instType, String instId, String state) {
        return unifiedCexApiService.getHistoryOrders(apiKey, instType, instId, state);
    }

    /**
     * 撤单(通用CEX接口)
     *
     * @param apiKey        API密钥
     * @param cancelRequest 撤单请求
     * @return 操作结果
     */
    public CexOperationResponse cancelOrder(ApiKey apiKey, CexCancelOrderRequest cancelRequest) {
        return unifiedCexApiService.cancelOrder(apiKey, cancelRequest);
    }

    /**
     * 平仓(通用CEX接口)
     *
     * @param apiKey               API密钥
     * @param closePositionRequest 平仓请求
     * @return 操作结果
     */
    public CexOperationResponse closePosition(ApiKey apiKey, CexClosePositionRequest closePositionRequest) {
        return unifiedCexApiService.closePosition(apiKey, closePositionRequest);
    }

    // ==================== 账户余额相关 ====================

    /**
     * 查询账户余额(通用CEX接口)
     *
     * @param apiKey API密钥
     * @param ccy    币种，例如USDT
     * @return 余额信息
     */
    public CexAccountBalance queryAccountBalance(ApiKey apiKey, String ccy) {
        try {
            log.debug("查询账户余额 - API Key ID: {}, 币种: {}", apiKey.getKeyId(), ccy);

            // 从统一余额服务获取最新余额数据
            CexAccountBalance accountBalance = unifiedBalanceService.getLatestBalanceData(apiKey.getKeyId(), ccy);

            if (null == accountBalance) {
                log.warn("API密钥 {} 的余额数据不存在，尝试强制刷新", apiKey.getKeyId());
                boolean refreshSuccess = unifiedBalanceService.refreshBalanceData(apiKey.getKeyId());
                if (refreshSuccess) {
                    accountBalance = unifiedBalanceService.getLatestBalanceData(apiKey.getKeyId(), ccy);
                }
            }

            if (null == accountBalance) {
                log.error("无法获取API密钥 {} 的余额数据", apiKey.getKeyId());
                throw new RuntimeException("余额数据获取失败");
            }

            log.debug("成功查询账户余额 - API Key ID: {}, 币种: {}, 总权益: {}",
                    apiKey.getKeyId(), ccy, accountBalance.getTotalBalance());

            return accountBalance;

        } catch (Exception e) {
            log.error("查询账户余额失败 - API Key ID: {}, 币种: {}", apiKey.getKeyId(), ccy, e);
            throw new RuntimeException("查询账户余额失败: " + e.getMessage(), e);
        }
    }

    // ==================== 持仓相关 ====================

    /**
     * 查询持仓信息(通用CEX接口)
     *
     * @param apiKey   API密钥
     * @param instType 合约类型 (SWAP - 永续合约)
     * @return 持仓列表
     */
    public List<CexPosition> queryPositions(ApiKey apiKey, String instType) {
        try {
            log.debug("查询持仓信息 - 类型: {}", instType);
            List<CexPosition> positions = unifiedCexApiService.getPositions(apiKey, instType);
            log.debug("成功查询持仓信息 - 类型: {}, 持仓数量: {}", instType, positions.size());
            return positions;
        } catch (Exception e) {
            log.error("查询持仓信息失败 - 类型: {}", instType, e);
            throw new RuntimeException("查询持仓信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询历史持仓(通用CEX接口)
     *
     * @param apiKey   API密钥
     * @param instType 合约类型
     * @param instId   产品ID
     * @param after    请求此时间戳之前的数据
     * @param before   请求此时间戳之后的数据
     * @param limit    返回数量
     * @return 历史持仓列表
     */
    public List<CexPosition> queryPositionsHistory(ApiKey apiKey, String instType, String instId,
                                                   String after, String before, Integer limit) {
        return unifiedCexApiService.getPositionsHistory(apiKey, instType, instId, after, before, limit);
    }

    // ==================== 算法订单相关 ====================

    /**
     * 创建算法订单(通用CEX接口)
     *
     * @param apiKey  API密钥
     * @param request 算法订单请求
     * @return 操作结果
     */
    public CexAlgoOrderOperationResponse setAlgoOrder(ApiKey apiKey, CexAlgoOrderRequest request) {
        CexAlgoOrderOperationResponse response = unifiedCexApiService.setAlgoOrder(apiKey, request);
        // 更新缓存
        if (response != null && Boolean.TRUE.equals(response.isSuccess())) {
            cacheTaskExecutor.execute(() -> {
                unifiedPositionService.refreshPositionData(apiKey.getKeyId());
                refreshAlgoOrdersCache(apiKey, "SWAP");
            });
        }
        return response;
    }

    /**
     * 修改算法订单(通用CEX接口)
     *
     * @param apiKey  API密钥
     * @param request 修改算法订单请求
     * @return 操作结果
     */
    public CexAlgoOrderOperationResponse amendAlgoOrder(ApiKey apiKey, CexAmendAlgoOrderRequest request) {
        CexAlgoOrderOperationResponse response = unifiedCexApiService.amendAlgoOrder(apiKey, request);

        // 更新缓存
        if (response != null && Boolean.TRUE.equals(response.isSuccess())) {
            cacheTaskExecutor.execute(() -> {
                unifiedPositionService.refreshPositionData(apiKey.getKeyId());
                refreshAlgoOrdersCache(apiKey, "SWAP");
            });
        }

        return response;
    }

    /**
     * 取消算法订单(通用CEX接口)
     *
     * @param apiKey  API密钥
     * @param request 取消算法订单请求
     * @return 操作结果
     */
    public CexAlgoOrderOperationResponse cancelAlgoOrder(ApiKey apiKey, CexCancelAlgoOrderRequest request) {
        CexAlgoOrderOperationResponse response = unifiedCexApiService.cancelAlgoOrder(apiKey, request);

        // 更新缓存
        if (response != null && Boolean.TRUE.equals(response.isSuccess())) {
            cacheTaskExecutor.execute(() -> {
                unifiedPositionService.refreshPositionData(apiKey.getKeyId());
                refreshAlgoOrdersCache(apiKey, "SWAP");
            });
        }

        return response;
    }

    /**
     * 获取算法订单列表(通用CEX接口)
     *
     * @param apiKey   API密钥
     * @param instType 产品类型
     * @return 算法订单列表
     */
    public CexAlgoOrderResponse getAlgoOrders(ApiKey apiKey, String instType) {
        // 先从缓存获取
        Map<String, List<CexAlgoOrder>> instType2Algos = algoOrdersCache.getIfPresent(apiKey.getKeyId());
        List<CexAlgoOrder> cachedOrders = CollectionUtils.isEmpty(instType2Algos) ? null : instType2Algos.get(instType);
        if (null != cachedOrders) {
            return CexAlgoOrderResponse.builder()
                    .algoOrders(cachedOrders)
                    .success(true)
                    .build();
        }
        refreshAlgoOrdersCache(apiKey, instType);
        instType2Algos = algoOrdersCache.getIfPresent(apiKey.getKeyId());
        cachedOrders = CollectionUtils.isEmpty(instType2Algos) ? null : instType2Algos.get(instType);
        return CexAlgoOrderResponse.builder()
                .algoOrders(cachedOrders)
                .success(true)
                .build();
    }

    /**
     * 监听CEX API调用事件
     * <p>
     * 当下单/平仓/取消订单操作成功时,自动失效对应的算法订单缓存
     * </p>
     *
     * @param event CEX API调用事件
     */
    @EventListener
    public void onCexApiCallEvent(CexApiCallEvent event) {
        // 验证apiKeyId
        if (null == event.getApiKeyId()) {
            return;
        }

        // 仅处理成功的事件
        if (event.getStatus() != CexApiCallStatus.SUCCESS) {
            return;
        }

        // 判断是否为算法订单相关操作
        CexApiType apiType = event.getApiType();
        if (isAlgoOrderRelatedOperation(apiType)) {
            ApiKey apiKey = apiKeyService.getDecryptedKey(event.getApiKeyId());
            if (null == apiKey) {
                return;
            }
            refreshAlgoOrdersCache(apiKey, "SWAP");
            log.debug("算法订单缓存已失效 - apiKeyId: {}, apiType: {}",
                    event.getApiKeyId(), apiType);
        }
    }

    /**
     * 判断API类型是否与算法订单相关
     * <p>
     * 相关操作包括:
     * <ul>
     * <li>普通订单操作: 下单/平仓/撤单</li>
     * <li>算法订单操作: 创建/修改/取消算法订单</li>
     * </ul>
     * </p>
     *
     * @param apiType API类型
     * @return true如果与算法订单相关
     */
    private boolean isAlgoOrderRelatedOperation(CexApiType apiType) {
        if (apiType == null) {
            return false;
        }

        // 普通订单操作
        if (apiType == CexApiType.PLACE_ORDER
                || apiType == CexApiType.CLOSE_POSITION
                || apiType == CexApiType.CANCEL_ORDER
                || apiType == CexApiType.CANCEL) {
            return true;
        }

        // 算法订单操作
        return apiType.isAlgoOrder();
    }


    void refreshAlgoOrdersCache(ApiKey apiKey, String instType) {
        Long keyId = apiKey.getKeyId();
        try {
            // 获取算法订单数据
            CexAlgoOrderResponse response = unifiedCexApiService.getAlgoOrders(apiKey, instType, null, null,
                    null);

            if (response != null && response.getAlgoOrders() != null) {
                if (!CollectionUtils.isEmpty(response.getAlgoOrders())) {
                    // 更新缓存
                    algoOrdersCache.put(keyId, Map.of(instType, new ArrayList<>(response.getAlgoOrders())));
                    log.debug("算法订单缓存已更新 - apiKeyId: {}, 订单数量: {}", keyId, response.getAlgoOrders().size());
                } else {
                    // 清空缓存
                    algoOrdersCache.put(keyId, Map.of(instType, new ArrayList<>()));
                    log.debug("算法订单缓存已清空 - apiKeyId: {}", keyId);
                }
            }
        } catch (Exception e) {
            log.error("刷新算法订单缓存失败 - apiKeyId: {}, error: {}", keyId, e.getMessage(), e);
        }
    }


    /**
     * 定时刷新算法订单缓存
     * <p>
     * 每30秒轮询一次所有活跃API Key的算法订单数据,使用线程池控制并发
     * </p>
     * <p>
     * 调度策略: fixedDelay = 30000 (30秒)
     * </p>
     */
    @Scheduled(fixedDelay = 30000)
    public void refreshAlgoOrdersCache() {
        log.debug("开始执行算法订单缓存刷新任务");

        try {
            // 获取所有活跃的API Key
            List<ApiKey> activeKeys = apiKeyService.getActiveKeys();
            if (CollectionUtils.isEmpty(activeKeys)) {
                log.debug("未找到活跃的API Key,跳过本次刷新");
                return;
            }

            log.debug("开始刷新{}个活跃API Key的算法订单缓存", activeKeys.size());

            // 使用线程池并发刷新缓存
            for (ApiKey apiKey : activeKeys) {
                ApiKey decryptedKey = apiKeyService.getDecryptedKey(apiKey.getKeyId());
                cacheTaskExecutor.execute(() -> refreshAlgoOrdersCache(decryptedKey, "SWAP"));
            }

            log.debug("算法订单缓存刷新任务已提交");

        } catch (Exception e) {
            log.error("算法订单缓存刷新任务执行失败", e);
        }
    }

    // ==================== 市场数据相关 ====================

    /**
     * 获取标记价格K线数据(通用CEX接口)
     *
     * @param instId 合约ID
     * @param period 周期 (1H, 4H, 1D等)
     * @param limit  数量限制
     * @return K线数据
     */
    public List<CexMarketCandle> getMarkPriceCandles(String instId, String period, Integer limit) {
        period = period.endsWith("h") || period.endsWith("d") || period.endsWith("w")
                ? period.toUpperCase()
                : period;

        return unifiedCexApiService.getMarketCandles(null, instId, period, limit);
    }

    /**
     * 获取资金费率(通用CEX接口)
     *
     * @param apiKey apiKey
     * @param instId 合约ID
     * @return 资金费率信息
     */
    public List<CexFundingRate> getFundingRate(ApiKey apiKey, String instId) {
        log.debug("获取合约资金费率: {}", instId);

        try {
            List<CexFundingRate> fundingRateDataList = unifiedCexApiService.getFundingRate(apiKey, instId);
            log.debug("成功获取合约 {} 的资金费率,数据数量: {}", instId, fundingRateDataList.size());
            return fundingRateDataList;

        } catch (Exception e) {
            log.error("获取资金费率失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取资金费率失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取标记价格(通用CEX接口)
     * 优先从交易所API获取，失败时回退到本地数据源
     *
     * @param apiKey apiKey
     * @param instId 合约ID
     * @return 标记价格
     */
    public CexMarkPrice getMarkPrice(ApiKey apiKey, String instId) {
        try {
            List<CexMarkPrice> markPrices = unifiedCexApiService.getMarkPrice(apiKey, instId);
            if (!CollectionUtils.isEmpty(markPrices)) {
                return markPrices.get(0);
            }
            return null;
        } catch (Exception e) {
            log.error("从交易所API获取标记价格失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取标记价格失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取缓存的标记价格数据(通用CEX接口)
     * 优先从交易所API获取，失败时回退到PriceDataService
     *
     * @param apiKey apiKey
     * @param instId 合约ID
     * @return InstrumentPriceInfo 价格信息
     */
    public InstrumentPriceInfo getCachedMarkPrice(ApiKey apiKey, String instId) {
        log.debug("获取合约 {} 的缓存标记价格", instId);

        // 使用PriceDataService获取真实价格数据
        InstrumentPriceInfo priceInfo = priceDataService.getRealPriceData(apiKey, instId);

        if (priceInfo != null) {
            log.debug("从PriceDataService获取合约 {} 标记价格成功: {}", instId, priceInfo.getMarkPrice());
        } else {
            priceInfo = new InstrumentPriceInfo();
        }

        try {
            // 优先从交易所API获取
            CexMarkPrice fetchPriceInfo = getMarkPrice(apiKey, instId);
            if (null != fetchPriceInfo) {
                log.debug("从交易所API获取合约 {} 标记价格成功: {}",
                        instId, fetchPriceInfo.getMarkPrice());
                priceInfo.setMarkPrice(fetchPriceInfo.getMarkPrice());
                return priceInfo;
            }
        } catch (Exception e) {
            log.warn("从交易所API获取合约 {} 标记价格失败，回退到PriceDataService: {}",
                    instId, e.getMessage());
        }

        // 回退到PriceDataService的数据
        return priceInfo;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建通用CEX下单请求
     */
    private CexPlaceOrderRequest buildCexPlaceOrderRequest(OrderRequest orderRequest) {
        return CexPlaceOrderRequest.builder()
                .symbol(orderRequest.getInstId())
                .side(orderRequest.getSide())
                .orderType("limit")  // 默认限价单
                .quantity(orderRequest.getSz() != null ? orderRequest.getSz().toString() : null)
                .price(orderRequest.getPx() != null ? orderRequest.getPx().toString() : null)
                .positionSide(orderRequest.getPosSide())
                .build();
    }

    /**
     * 解析通用CEX订单响应
     */
    private TradingResult parseCexOrderResponse(CexOrderResponse response) {
        if (response == null) {
            return TradingResult.failure("订单响应为空");
        }

        if (Boolean.TRUE.equals(response.getSuccess())) {
            // 获取第一个订单的ID
            String orderId = null;
            if (!CollectionUtils.isEmpty(response.getOrders())) {
                orderId = response.getOrders().get(0).getOrderId();
            }
            return TradingResult.success(orderId, "下单成功");
        } else {
            String errorMsg = response.getErrorMessage();
            return TradingResult.failure(errorMsg != null ? errorMsg : "下单失败");
        }
    }
}

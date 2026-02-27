//package com.crypto.trade.service;
//
//import com.crypto.trade.dto.cex.model.CexAlgoOrder;
//import com.crypto.trade.service.cex.UnifiedCexApiService;
//import com.crypto.trade.service.unified.UnifiedBalanceService;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.github.benmanes.caffeine.cache.Cache;
//import com.github.benmanes.caffeine.cache.Caffeine;
//import lombok.AllArgsConstructor;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Qualifier;
//
//import java.math.BigDecimal;
//import java.util.List;
//import java.util.concurrent.TimeUnit;
//
/// **
// * OKX交易服务类
// * 提供OKX合约交易相关API调用功能
// * 重构后使用统一的unifiedCexApiService进行API调用
// * <p>
// * 注意：此类已标记为包级可见，仅作为底层实现保留
// * 业务层应使用UnifiedTradingService，不直接依赖此类
// * </p>
// * <p>
// * 所有方法已迁移至 UnifiedTradingService
// * </p>
// *
// * @deprecated 请使用 {@link com.crypto.trade.service.UnifiedTradingService}
// */
//@Slf4j
/**
 * OKXTradingService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
//class OKXTradingService {
//
//    private final ObjectMapper objectMapper = new ObjectMapper();
//    /**
//     * 算法订单缓存（止盈止损单）
//     * Key: apiKeyId
//     * Value: List<CexAlgoOrder>
//     * TTL: 5分钟
//     * 最大容量: 100个API Key
//     */
//    private final Cache<Long, List<CexAlgoOrder>> algoOrdersCache = Caffeine.newBuilder()
//            .maximumSize(100)
//            .expireAfterWrite(5, TimeUnit.MINUTES)
//            .recordStats()
//            .build();
//
//    @Autowired
//    UnifiedCexApiService unifiedCexApiService;
//    @Autowired
//    @Qualifier("realTimePriceService")
//    RealTimePriceService priceDataService;
//    @Autowired
//    UnifiedBalanceService unifiedBalanceService;
//    @Autowired
//    UnifiedInstrumentService unifiedInstrumentService;
//
//    // ==================== 所有方法已迁移至 UnifiedTradingService ====================
//
//    /**
//     * 下单(使用通用CEX Model)
//     *
//     * @deprecated 请使用 {@link com.crypto.trade.service.UnifiedTradingService#placeOrder}
//     */
//    /*
//    public TradingResult placeOrder(ApiKey apiKey, OrderRequest orderRequest) {
//        try {
//            log.debug("开始下单 - 合约: {}, 方向: {}, 数量: {}, 价格: {}",
//                    orderRequest.instId, orderRequest.side, orderRequest.sz, orderRequest.px);
//
//            // 构建通用CEX下单请求
//            CexPlaceOrderRequest cexRequest = buildCexPlaceOrderRequest(orderRequest);
//
//            // 使用通用CEX API进行下单
//            CexOrderResponse cexOrderResponse = unifiedCexApiService.placeOrder(apiKey, cexRequest);
//
//            // 解析响应
//            return parseCexOrderResponse(cexOrderResponse);
//
//        } catch (Exception e) {
//            log.error("下单失败", e);
//            // 使用统一的错误处理
//            return TradingResult.failure("下单失败: " + e.getMessage());
//        }
//    }
//    */
//
//    /**
//     * 查询待成交订单
//     *
//     * @deprecated 请使用 {@link com.crypto.trade.service.UnifiedTradingService#queryPendingOrders}
//     */
//    /*
//    public List<CexOrder> queryPendingOrders(ApiKey apiKey, String instType) {
//        // 直接使用通用CexOrder，无需适配
//        return unifiedCexApiService.getPendingOrders(apiKey, instType, null);
//    }
//    */
//
//    /**
//     * 查询账户余额
//     * 获取OKX账户余额信息
//     *
//     * @deprecated 请使用 {@link com.crypto.trade.service.UnifiedTradingService#queryAccountBalance}
//     */
//    /*
//    public AccountBalanceResponse queryAccountBalance(ApiKey apiKey, String ccy) {
//        try {
//            log.debug("查询账户余额 - API Key ID: {}, 币种: {}", apiKey.getKeyId(), ccy);
//
//            // 使用统一余额服务查询
//            CexAccountBalance balance = unifiedBalanceService.queryBalance(apiKey, ccy);
//
//            if (balance == null) {
//                log.warn("未查询到余额信息 - API Key ID: {}, 币种: {}", apiKey.getKeyId(), ccy);
//                return new AccountBalanceResponse(Collections.emptyList());
//            }
//
//            // 转换为旧格式以保持向后兼容
//            AccountBalanceResponse response = new AccountBalanceResponse();
//            List<OkxAccountBalanceDetail> details = new ArrayList<>();
//            OkxAccountBalanceDetail detail = convertToOkxAccountBalance(balance, ccy);
//            details.add(detail);
//            response.setData(details);
//
//            log.debug("成功查询账户余额 - API Key ID: {}, 币种: {}, 余额: {}",
//                    apiKey.getKeyId(), ccy, detail.getBal());
//
//            return response;
//
//        } catch (Exception e) {
//            log.error("查询账户余额失败 - API Key ID: {}, 币种: {}", apiKey.getKeyId(), ccy, e);
//            throw new RuntimeException("查询账户余额失败: " + e.getMessage(), e);
//        }
//    }
//    */
//
//    /**
//     * 平仓
//     *
//     * @deprecated 请使用 {@link com.crypto.trade.service.UnifiedTradingService#closePosition}
//     */
//    /*
//    public TradingResult closePosition(ApiKey apiKey, ClosePositionRequest closePositionRequest) {
//        try {
//            log.debug("开始平仓 - 合约: {}, 方向: {}, 数量: {}",
//                    closePositionRequest.instId, closePositionRequest.posSide, closePositionRequest.sz);
//
//            // 构建通用CEX平仓请求
//            CexClosePositionRequest cexRequest = CexClosePositionRequest.builder()
//                    .symbol(closePositionRequest.instId)
//                    .positionSide(closePositionRequest.posSide)
//                    .quantity(closePositionRequest.sz.toString())
//                    .build();
//
//            // 使用通用CEX API进行平仓
//            CexOperationResponse cexResponse = unifiedCexApiService.closePosition(apiKey, cexRequest);
//
//            // 解析响应
//            return parseCexOperationResponse(cexResponse);
//
//        } catch (Exception e) {
//            log.error("平仓失败", e);
//            // 使用统一的错误处理
//            return TradingResult.failure("平仓失败: " + e.getMessage());
//        }
//    }
//    */
//
//    /**
//     * 获取合约信息
//     *
//     * @deprecated 请使用 {@link com.crypto.trade.service.UnifiedInstrumentService#getCexContractInfo}
//     */
//    /*
//    public ContractInfo getContractInfo(String instId) throws Exception {
//        // 使用统一合约信息服务
//        Optional<CexContractInfo> cexContractInfoOpt = unifiedInstrumentService.getCexContractInfo(instId);
//
//        if (!cexContractInfoOpt.isPresent()) {
//            throw new RuntimeException("未找到合约信息: " + instId);
//        }
//
//        CexContractInfo cexInfo = cexContractInfoOpt.get();
//
//        // 转换为旧格式
//        ContractInfo contractInfo = new ContractInfo();
//        contractInfo.instId = cexInfo.getInstId();
//        contractInfo.lotSz = cexInfo.getLotSz();
//        contractInfo.minSz = cexInfo.getMinSz();
//        contractInfo.ctVal = cexInfo.getCtVal();
//        contractInfo.ctMult = cexInfo.getCtMult();
//
//        return contractInfo;
//    }
//    */
//
//    /**
//     * 获取资金费率
//     *
//     * @deprecated 请使用 {@link com.crypto.trade.service.UnifiedTradingService#getFundingRate}
//     */
//    /*
//    public String getFundingRate(ApiKey apiKey, String instId) throws Exception {
//        List<CexFundingRate> fundingRates = unifiedCexApiService.getFundingRate(apiKey, instId);
//
//        if (CollectionUtils.isEmpty(fundingRates)) {
//            log.warn("未获取到资金费率数据 - instId: {}", instId);
//            return "[]";
//        }
//
//        // 转换为JSON字符串以保持向后兼容
//        ObjectMapper mapper = new ObjectMapper();
//        return mapper.writeValueAsString(fundingRates);
//    }
//    */
//
//    /**
//     * 获取标记价格K线数据
//     *
//     * @deprecated 请使用 {@link com.crypto.trade.service.market.UnifiedPriceDataService#getMarkPriceCandles}
//     */
//    /*
//    public List<OkxMarketCandle> getMarkPriceCandles(String instId, String period, Integer limit) throws Exception {
//        // 使用统一价格数据服务
//        List<CexMarketCandle> candles = priceDataService.getMarkPriceCandles(null, instId, period, limit);
//
//        // 转换为OKX格式以保持向后兼容
//        return candles.stream()
//                .map(this::convertToOkxCandle)
//                .collect(Collectors.toList());
//    }
//    */
//
//    /**
//     * 获取缓存的标记价格
//     *
//     * @deprecated 请使用 {@link com.crypto.trade.service.UnifiedTradingService#getCachedMarkPrice}
//     */
//    /*
//    public InstrumentPriceInfo getCachedMarkPrice(ApiKey apiKey, String instId) {
//        try {
//            // 使用统一交易服务的缓存方法
//            InstrumentPriceInfo priceInfo = unifiedCexApiService.getCachedMarkPrice(apiKey, instId);
//
//            if (priceInfo != null) {
//                log.debug("从缓存获取标记价格成功 - instId: {}, markPrice: {}",
//                        instId, priceInfo.getMarkPrice());
//                return priceInfo;
//            }
//
//            log.warn("从缓存获取标记价格失败,返回null - instId: {}", instId);
//            return null;
//
//        } catch (Exception e) {
//            log.error("获取缓存标记价格失败 - instId: {}", instId, e);
//            return null;
//        }
//    }
//    */
//
//    /**
//     * 查询算法订单
//     *
//     * @deprecated 请使用 {@link com.crypto.trade.service.UnifiedTradingService#getAlgoOrders}
//     */
//    /*
//    public List<CexAlgoOrder> queryAlgoOrders(ApiKey apiKey, boolean useCache) {
//        try {
//            if (useCache) {
//                List<CexAlgoOrder> cachedOrders = algoOrdersCache.getIfPresent(apiKey.getKeyId());
//                if (cachedOrders != null) {
//                    log.debug("从缓存获取算法订单 - apiKeyId: {}, 数量: {}", apiKey.getKeyId(), cachedOrders.size());
//                    return cachedOrders;
//                }
//            }
//
//            // 使用通用CEX API查询算法订单
//            CexAlgoOrderResponse algoOrderResponse = unifiedCexApiService.getAlgoOrders(
//                    apiKey,
//                    "SWAP",
//                    null,
//                    null,
//                    null
//            );
//
//            List<CexAlgoOrder> algoOrders = algoOrderResponse.getAlgoOrders();
//
//            // 放入缓存
//            algoOrdersCache.put(apiKey.getKeyId(), algoOrders);
//            log.debug("查询算法订单成功 - apiKeyId: {}, 数量: {}", apiKey.getKeyId(), algoOrders.size());
//
//            return algoOrders;
//
//        } catch (Exception e) {
//            log.error("查询算法订单失败 - apiKeyId: {}", apiKey.getKeyId(), e);
//            return Collections.emptyList();
//        }
//    }
//    */
//
//    /**
//     * 设置全仓止盈止损
//     *
//     * @deprecated 请使用 {@link com.crypto.trade.service.UnifiedTradingService#setAlgoOrder}
//     */
//    /*
//    public TradingResult setTotalStopLoss(ApiKey apiKey, String algoId, String instId, String posSide, String side, String sz,
//                                           String tpTriggerPx, String slTriggerPx, String tpTriggerPxType, String slTriggerPxType) {
//        try {
//            log.debug("开始设置全仓止盈止损 - algoId: {}, instId: {}, posSide: {}, tpTriggerPx: {}, slTriggerPx: {}",
//                    algoId, instId, posSide, tpTriggerPx, slTriggerPx);
//
//            // 构建通用CEX算法订单请求
//            CexAlgoOrderRequest.CexAlgoOrderRequestBuilder builder = CexAlgoOrderRequest.builder()
//                    .symbol(instId)
//                    .tradeMode("cross")
//                    .currency("USDT")
//                    .side(side)
//                    .positionSide(posSide)
//                    .closeFraction("1")
//                    .cancelOnClosePosition(true)
//                    .reduceOnly(true);
//
//            // 设置止盈
//            if (StringUtils.hasText(tpTriggerPx) && !"0".equals(tpTriggerPx)) {
//                builder.takeProfitTriggerPrice(tpTriggerPx)
//                       .takeProfitOrderPrice("-1")
//                       .takeProfitTriggerPriceType("last");
//            }
//
//            // 设置止损
//            if (StringUtils.hasText(slTriggerPx) && !"0".equals(slTriggerPx)) {
//                builder.stopLossTriggerPrice(slTriggerPx)
//                       .stopLossOrderPrice("-1")
//                       .stopLossTriggerPriceType("last");
//            }
//
//            builder.orderType("oco");
//
//            // 使用通用CEX API设置算法订单
//            CexAlgoOrderOperationResponse response = unifiedCexApiService.setAlgoOrder(apiKey, builder.build());
//
//            // 解析响应
//            if (response.isSuccess()) {
//                return TradingResult.success(response.getAlgoId(), "设置成功");
//            } else {
//                return TradingResult.failure(response.getErrorMessage());
//            }
//
//        } catch (Exception e) {
//            log.error("设置全仓止盈止损失败", e);
//            return TradingResult.failure("设置失败: " + e.getMessage());
//        }
//    }
//    */
//
//    /**
//     * 取消全仓止盈止损
//     *
//     * @deprecated 请使用 {@link com.crypto.trade.service.UnifiedTradingService#cancelAlgoOrder}
//     */
//    /*
//    public TradingResult cancelTotalStopLoss(ApiKey apiKey, String algoId) {
//        try {
//            log.debug("开始取消全仓止盈止损 - algoId: {}", algoId);
//
//            // 构建通用CEX取消算法订单请求
//            CexCancelAlgoOrderRequest cancelRequest = CexCancelAlgoOrderRequest.builder()
//                    .algoId(algoId)
//                    .build();
//
//            // 使用通用CEX API取消算法订单
//            CexAlgoOrderOperationResponse response = unifiedCexApiService.cancelAlgoOrder(apiKey, cancelRequest);
//
//            // 解析响应
//            if (response.isSuccess()) {
//                return TradingResult.success(response.getAlgoId(), "取消成功");
//            } else {
//                return TradingResult.failure(response.getErrorMessage());
//            }
//
//        } catch (Exception e) {
//            log.error("取消全仓止盈止损失败", e);
//            return TradingResult.failure("取消失败: " + e.getMessage());
//        }
//    }
//    */
//
//    /**
//     * 批量取消算法订单
//     *
//     * @deprecated 请使用 {@link com.crypto.trade.service.UnifiedTradingService#cancelAlgoOrder}
//     */
//    /*
//    public TradingResult cancelStopLossAlgos(ApiKey apiKey, String instId, String algoId) {
//        try {
//            log.debug("开始批量取消算法订单 - instId: {}, algoId: {}", instId, algoId);
//
//            // 构建通用CEX取消算法订单请求
//            CexCancelAlgoOrderRequest cancelRequest = CexCancelAlgoOrderRequest.builder()
//                    .symbol(instId)
//                    .algoId(algoId)
//                    .build();
//
//            // 使用通用CEX API取消算法订单
//            CexAlgoOrderOperationResponse response = unifiedCexApiService.cancelAlgoOrder(apiKey, cancelRequest);
//
//            // 解析响应
//            if (response.isSuccess()) {
//                return TradingResult.success(response.getAlgoId(), "取消成功");
//            } else {
//                return TradingResult.failure(response.getErrorMessage());
//            }
//
//        } catch (Exception e) {
//            log.error("批量取消算法订单失败", e);
//            return TradingResult.failure("取消失败: " + e.getMessage());
//        }
//    }
//    */
//
//    /**
//     * 监听CEX API调用事件,自动失效相关缓存
//     *
//     * @deprecated 缓存管理由 UnifiedTradingService 统一处理
//     */
//    /*
//    @EventListener
//    public void onCexApiCallEvent(CexApiCallEvent event) {
//        // 检查apiKeyId
//        if (event.getApiKeyId() == null) {
//            return;
//        }
//
//        // 监听PLACE_ORDER、CANCEL_ORDER、CANCEL事件
//        CexApiType apiType = event.getApiType();
//        if (apiType == CexApiType.PLACE_ORDER ||
//                apiType == CexApiType.CANCEL_ORDER ||
//                apiType == CexApiType.CANCEL) {
//            evictAlgoOrdersCache(event.getApiKeyId());
//            log.debug("算法订单缓存已失效 - apiKeyId: {}, apiType: {}",
//                    event.getApiKeyId(), apiType);
//        }
//    }
//    */
//
//    /**
//     * 失效指定API Key的算法订单缓存
//     *
//     * @deprecated 缓存管理由 UnifiedTradingService 统一处理
//     */
//    /*
//    public void evictAlgoOrdersCache(Long apiKeyId) {
//        if (apiKeyId != null) {
//            algoOrdersCache.invalidate(apiKeyId);
//            log.debug("清除算法订单缓存 - apiKeyId: {}", apiKeyId);
//        }
//    }
//    */
//
//    /**
//     * 获取算法订单缓存统计信息
//     *
//     * @deprecated 缓存管理由 UnifiedTradingService 统一处理
//     */
//    /*
//    public Map<String, Object> getAlgoOrdersCacheStats() {
//        CacheStats stats = algoOrdersCache.stats();
//        return Map.of(
//                "hitRate", stats.hitRate(),
//                "hitCount", stats.hitCount(),
//                "missCount", stats.missCount(),
//                "size", algoOrdersCache.estimatedSize()
//        );
//    }
//    */
//
//    // ==================== 静态内部类 (保留以维持向后兼容) ====================
//
//    /**
//     * 止盈请求参数类
//     *
//     * @deprecated 请使用 {@link com.crypto.trade.dto.cex.request.CexAlgoOrderRequest}
//     */
//    @Data
//    @NoArgsConstructor
//    @AllArgsConstructor
//    public static class TakeProfitRequest {
//        public String instId;                    // 合约品种
//        public String tdMode;                    // 交易模式
//        public String ccy;                       // 保证金币种
//        public String side;                      // 订单方向
//        public String posSide;                   // 持仓方向：long/short
//        public BigDecimal sz;                    // 委托数量
//        // 止盈参数
//        public String tpTriggerPx;               // 止盈触发价格
//        public String tpTriggerPxType;           // 止盈触发类型：1=价格，2=百分比
//        public String tpOrderType;               // 止盈委托类型：limit, market
//        public String tpTriggerPxMode;           // 止盈触发模式：1=仓位模式，2=全仓模式
//    }
//
//    /**
//     * 止盈止损请求参数类
//     *
//     * @deprecated 请使用 {@link com.crypto.trade.dto.cex.request.CexAlgoOrderRequest}
//     */
//    @Data
//    @AllArgsConstructor
//    @NoArgsConstructor
//    public static class StopLossRequest {
//        public String instId;                    // 合约品种
//        public String tdMode;                    // 交易模式
//        public String ccy;                       // 保证金币种
//        public String side;                      // 订单方向
//        public String posSide;                   // 持仓方向：long/short
//        public BigDecimal sz;                    // 委托数量
//        // 止盈参数
//        public String tpTriggerPx;               // 止盈触发价格
//        public String tpTriggerPxType;           // 止盈触发类型：1=价格，2=百分比
//        public String tpOrderType;               // 止盈委托类型：limit, market
//        public String tpTriggerPxMode;           // 止盈触发模式：1=仓位模式，2=全仓模式
//        // 止损参数
//        public String slTriggerPx;               // 止损触发价格
//        public String slTriggerPxType;           // 止损触发类型：1=价格，2=百分比
//        public String slOrderType;               // 止损委托类型：limit, market
//        public String slTriggerPxMode;           // 止损触发模式：1=仓位模式，2=全仓模式
//    }
//
//    /**
//     * 合约信息类
//     *
//     * @deprecated 请使用 {@link com.crypto.trade.dto.cex.model.CexContractInfo}
//     */
//    public static class ContractInfo {
//        public String instId;      // 合约ID
//        public BigDecimal lotSz;   // 最小下单单位
//        public BigDecimal minSz;   // 最小下单数量
//        public BigDecimal ctVal;   // 合约面值
//        public BigDecimal ctMult;  // 合约乘数
//    }
//}

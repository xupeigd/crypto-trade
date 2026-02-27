package com.crypto.trade.rest.controller;

import com.crypto.trade.dto.CancelOrderReq;
import com.crypto.trade.dto.*;
import com.crypto.trade.dto.cex.adapter.CexPositionAdapter;
import com.crypto.trade.dto.cex.model.*;
import com.crypto.trade.dto.cex.request.CexAlgoOrderRequest;
import com.crypto.trade.dto.cex.request.CexAmendAlgoOrderRequest;
import com.crypto.trade.dto.cex.request.CexCancelAlgoOrderRequest;
import com.crypto.trade.dto.cex.request.CexClosePositionRequest;
import com.crypto.trade.dto.cex.response.CexAlgoOrderOperationResponse;
import com.crypto.trade.dto.cex.response.CexOperationResponse;
import com.crypto.trade.dto.common.ActiveApiKeyDTO;
import com.crypto.trade.dto.common.InstrumentPriceInfo;
import com.crypto.trade.dto.common.PagedResponse;
import com.crypto.trade.dto.common.TradingResult;
import com.crypto.trade.dto.market.InstrumentOverviewDTO;
import com.crypto.trade.dto.market.UnifiedChartDataRequest;
import com.crypto.trade.dto.market.UnifiedChartDataResponse;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.CexTradingOrder;
import com.crypto.trade.entity.PositionSnapshot;
import com.crypto.trade.entity.TradingOrder;
import com.crypto.trade.model.*;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.model.request.*;
import com.crypto.trade.repository.ApiKeyRepository;
import com.crypto.trade.rest.controller.model.response.RealTimePriceModel;
import com.crypto.trade.service.*;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import com.crypto.trade.service.market.UnifiedPriceDataService;
import com.crypto.trade.service.unified.UnifiedPositionService;
import com.crypto.trade.util.JsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TradingController
 * REST控制器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@RestController
@RequestMapping("/trading")
public class TradingController {

    /**
     * 技术指标最大周期限制
     * 在最大240个OHLC数据点限制下，确保技术指标计算的准确性
     */
    private static final int MAX_INDICATOR_PERIOD = 60;   // 技术指标最大周期
    private static final int MAX_USER_LIMIT = 240;       // 用户最大数据请求量

    @Resource
    ApiKeyService apiKeyService;
    @Resource
    TradingOrderService tradingOrderService;
    @Resource
    UnifiedTradingService unifiedTradingService;
    @Resource
    @Qualifier("marketPriceService")
    UnifiedPriceDataService priceDataService;
    @Resource
    RealTimePriceService realTimePriceService;
    @Autowired
    UnifiedCexApiService unifiedCexApiService;
    @Autowired
    ApiKeyRepository apiKeyRepository;
    @Resource
    UnifiedPositionService unifiedPositionService;
    @Resource
    UnifiedInstrumentService unifiedInstrumentService;
    @Autowired
    UnifiedMarketTickerService unifiedMarketTickerService;
    @Autowired
    PositionSnapshotPersistenceService positionSnapshotPersistenceService;

    /**
     * 下单接口
     */
    @PostMapping("/order")
    public ApiResponse<TradingResult> placeOrder(@RequestBody OrderReq request) {
        try {
            log.debug("收到下单请求 - API Key: {}, 合约: {}, 方向: {}, 金额: {}, 杠杆: {}",
                    request.apiKeyId, request.instId, request.side, request.amount, request.lever);
            // 验证API Key
            if (null == request.apiKeyId) {
                return ApiResponse.fail("API Key ID不能为空");
            }
            // 验证参数
            if (!StringUtils.hasText(request.instId)) {
                return ApiResponse.fail("合约品种不能为空");
            }
            if (!StringUtils.hasText(request.side) ||
                    (!"buy".equals(request.side) && !"sell".equals(request.side))) {
                return ApiResponse.fail("订单方向必须是buy或sell");
            }
            if (!StringUtils.hasText(request.orderType) ||
                    (!"market".equals(request.orderType) && !"limit".equals(request.orderType))) {
                return ApiResponse.fail("订单类型必须是market或limit");
            }
            if (null == request.amount || request.amount.compareTo(BigDecimal.ZERO) <= 0) {
                return ApiResponse.fail("成本金额必须大于0");
            }
            if (null == request.lever || request.lever.compareTo(BigDecimal.ZERO) <= 0) {
                return ApiResponse.fail("杠杆倍数必须大于0");
            }
            // 验证限价单价格
            if ("limit".equals(request.orderType) && (null == request.px || request.px.compareTo(BigDecimal.ZERO) <= 0)) {
                return ApiResponse.fail("限价单必须设置有效价格");
            }
            // 验证止盈止损参数
            if (null != request.takeProfitPrice && request.takeProfitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return ApiResponse.fail("止盈价格必须大于0");
            }
            if (null != request.stopLossPrice && request.stopLossPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return ApiResponse.fail("止损价格必须大于0");
            }
            // 调用交易服务下单
            // 从request读取source,如果为空则使用默认值"web"
            String source = (request.getSource() != null && !request.getSource().trim().isEmpty())
                    ? request.getSource()
                    : "web";
            TradingResult result = tradingOrderService.placeOrder(request.apiKeyId, request.instId, request.side, request.orderType,
                    null, request.amount, request.lever, request.px, request.takeProfitPrice, request.stopLossPrice,
                    request.posSide, source, false);
            if (result.getSuccess()) {
                TradingResult response = TradingResult.success(result.getOrderId(), result.getMessage())
                        .withManualRiskMode(result.getIsManualRiskMode());
                return ApiResponse.ok(response);
            } else {
                return ApiResponse.fail(result.getMessage());
            }
        } catch (Exception e) {
            log.error("下单处理失败", e);
            return ApiResponse.fail("下单失败: " + e.getMessage());
        }
    }

    /**
     * 撤单接口
     */
    @PostMapping("/{apiKeyId}/cancel/{orderId}")
    public ApiResponse<TradingResult> cancelOrder(@PathVariable Long apiKeyId, @PathVariable String orderId,
                                                  @RequestBody(required = false) CancelOrderReq cancelRequest) {
        try {
            log.debug("收到撤单请求 - API Key: {}, 订单ID: {}, 合约品种: {}", apiKeyId, orderId,
                    null != cancelRequest ? cancelRequest.getInstId() : "未提供");
            // 验证参数
            if (!StringUtils.hasText(orderId)) {
                return ApiResponse.fail("订单ID不能为空");
            }
            if (null == apiKeyId) {
                return ApiResponse.fail("API Key ID不能为空");
            }
            if (null == cancelRequest || !StringUtils.hasText(cancelRequest.getInstId())) {
                return ApiResponse.fail("合约品种不能为空");
            }
            // 调用交易服务撤单
            TradingResult result = tradingOrderService.cancelOrder(apiKeyId, cancelRequest.getInstId(), orderId);
            if (result.getSuccess()) {
                TradingResult response = TradingResult.success(result.getOrderId(), result.getMessage())
                        .withClOrdId(orderId);
                return ApiResponse.ok(response);
            } else {
                return ApiResponse.fail(result.getMessage());
            }
        } catch (Exception e) {
            log.error("撤单处理失败", e);
            return ApiResponse.fail("撤单失败: " + e.getMessage());
        }
    }

    /**
     * 获取活跃订单列表
     */
    @GetMapping("/orders/active")
    public ApiResponse<List<ActiveOrderModel>> getActiveOrders(@RequestParam(required = false) Long apiKeyId) {
        try {
            List<TradingOrder> orders;
            if (null != apiKeyId) {
                // 获取指定API Key的活跃订单
                orders = tradingOrderService.getActiveOrders(apiKeyId);
            } else {
                // 获取所有活跃API Key的活跃订单
                List<Long> activeApiKeyIds = apiKeyRepository.findByStatus("active")
                        .stream()
                        .map(ApiKey::getKeyId)
                        .toList();
                orders = activeApiKeyIds.stream()
                        .flatMap(id -> tradingOrderService.getActiveOrders(id).stream())
                        .collect(Collectors.toList());
            }
            List<ActiveOrderModel> result = orders.stream()
                    .map(ActiveOrderModel::fromEntity)
                    .collect(Collectors.toList());
            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.error("获取活跃订单失败", e);
            return ApiResponse.fail("获取活跃订单失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前委托订单列表
     */
    @GetMapping("/orders/{apiKeyId}/pending")
    public ApiResponse<List<OrderModel>> getPendingOrders(@PathVariable Long apiKeyId) {
        try {
            List<OrderModel> orders = tradingOrderService.getPendingOrders(apiKeyId, true);
            return ApiResponse.ok(orders);
        } catch (Exception e) {
            log.error("获取当前委托订单失败", e);
            return ApiResponse.fail("获取当前委托订单失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定API Key的历史订单列表(支持分页)
     */
    @GetMapping("/orders/{apiKeyId}/history")
    public ApiResponse<PagedResponse<TradingOrderModel>> getHistoryOrders(@PathVariable Long apiKeyId,
                                                                          @RequestParam(defaultValue = "7") Integer days,
                                                                          @RequestParam(defaultValue = "0") Integer page,
                                                                          @RequestParam(defaultValue = "20") Integer size) {
        try {
            // 性能优化：限制最大分页大小，防止一次查询过多数据
            final int MAX_SIZE = 100;
            if (size > MAX_SIZE) {
                size = MAX_SIZE;
            }

            // 构建请求对象
            HistoryOrdersPageRequest request = HistoryOrdersPageRequest.builder()
                    .apiKeyId(apiKeyId)
                    .days(days)
                    .page(page)
                    .size(size)
                    .build();

            // 获取指定API Key的历史订单(分页)
            PagedResponse<TradingOrder> pageResult = tradingOrderService.getHistoryOrdersPage(request);

            // 转换为Model格式
            List<TradingOrderModel> result = pageResult.getData().stream()
                    .map(this::convertOrderToModel)
                    .collect(Collectors.toList());

            // 构建分页响应
            PagedResponse<TradingOrderModel> response = PagedResponse.<TradingOrderModel>builder()
                    .data(result)
                    .total(pageResult.getTotal())
                    .page(pageResult.getPage())
                    .size(pageResult.getSize())
                    .build();

            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("获取历史订单失败", e);
            return ApiResponse.fail("获取历史订单失败: " + e.getMessage());
        }
    }

    /**
     * 根据调用记录ID获取关联订单
     */
    @GetMapping("/orders/by-record/{recordId}")
    public ApiResponse<List<TradingOrderModel>> getOrdersByRecordId(@PathVariable Long recordId) {
        try {
            List<TradingOrder> orders = tradingOrderService.getOrdersByRecordId(recordId);
            List<TradingOrderModel> result = orders.stream()
                    .map(this::convertOrderToModel)
                    .collect(Collectors.toList());
            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.error("获取关联订单失败", e);
            return ApiResponse.fail("获取关联订单失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户可用的API Key列表
     */
    @GetMapping("/api-keys")
    public ApiResponse<List<ActiveApiKeyDTO>> getActiveApiKeys() {
        try {
            List<ApiKey> apiKeys = apiKeyRepository.findByStatus("active");
            List<ActiveApiKeyDTO> result = apiKeys.stream()
                    .map(this::convertToActiveApiKeyDTO)
                    .collect(Collectors.toList());
            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.error("获取API Key列表失败", e);
            throw new RuntimeException("获取API Key列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取合约资金费率
     */
    @GetMapping("/funding-rate/{instId}")
    public ApiResponse<FundingRateModel> getFundingRate(@PathVariable String instId, @RequestParam Long apiKeyId) {
        try {
            log.debug("获取合约资金费率: {}, apiKeyId: {}", instId, apiKeyId);
            if (!StringUtils.hasText(instId)) {
                return ApiResponse.fail("合约品种不能为空");
            }
            // 获取ApiKey对象
            ApiKey apiKey = apiKeyService.getDecryptedKey(apiKeyId);
            if (null == apiKey) {
                return ApiResponse.fail("未找到指定的API Key: " + apiKeyId);
            }
            List<CexFundingRate> fundingRates = unifiedTradingService.getFundingRate(apiKey, instId);
            // 转换为FundingRateModel
            if (!CollectionUtils.isEmpty(fundingRates)) {
                CexFundingRate fundingRate = fundingRates.get(0);
                FundingRateModel result = FundingRateModel.builder()
                        .instId(instId)
                        .fundingRate(fundingRate.getFundingRate() != null ? fundingRate.getFundingRate().toString() : null)
                        .fundingTime(fundingRate.getFundingTime())
                        .build();
                return ApiResponse.ok(result);
            } else {
                return ApiResponse.fail("未找到资金费率数据");
            }
        } catch (Exception e) {
            log.error("获取资金费率失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取资金费率失败: " + e.getMessage());
        }
    }

    /**
     * 获取合约标记价格
     */
    @GetMapping("/mark-price/{instId}")
    public ApiResponse<MarkPriceModel> getMarkPrice(@PathVariable String instId, @RequestParam Long apiKeyId) {
        try {
            log.debug("获取合约标记价格: {}, apiKeyId: {}", instId, apiKeyId);
            if (!StringUtils.hasText(instId)) {
                return ApiResponse.fail("合约品种不能为空");
            }
            // 获取ApiKey对象
            ApiKey apiKey = apiKeyService.getDecryptedKey(apiKeyId);
            if (null == apiKey) {
                return ApiResponse.fail("未找到指定的API Key: " + apiKeyId);
            }
            // 使用通用CEX方法获取标记价格
            List<CexMarkPrice> markPrices = unifiedCexApiService.getMarkPrice(apiKey, instId);
            if (!CollectionUtils.isEmpty(markPrices)) {
                CexMarkPrice cexMarkPrice = markPrices.get(0);
                MarkPriceModel retModel = new MarkPriceModel();
                retModel.setTimestamp(cexMarkPrice.getTimestamp());
                retModel.setMarkPrice(cexMarkPrice.getMarkPrice());
                return ApiResponse.ok(retModel);
            } else {
                return ApiResponse.fail("获取标记价格失败: " + instId);
            }
        } catch (Exception e) {
            log.error("获取标记价格失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取标记价格失败: " + e.getMessage());
        }
    }

    /**
     * 获取标记价格K线数据
     */
    @GetMapping("/mark-price-candles/{instId}")
    public ApiResponse<List<MarketCandleModel>> getMarkPriceCandles(@PathVariable String instId,
                                                                    @RequestParam(defaultValue = "1H") String period,
                                                                    @RequestParam(defaultValue = "100") Integer limit) {
        try {
            log.debug("获取合约标记价格K线数据: {}, period: {}, limit: {}", instId, period, limit);
            if (!StringUtils.hasText(instId)) {
                throw new RuntimeException("合约品种不能为空");
            }
            List<CexMarketCandle> candles = unifiedTradingService.getMarkPriceCandles(instId, period, limit);
            // 转换为MarketCandleModel
            List<MarketCandleModel> marketCandles = candles.stream()
                    .map(v -> JsonUtils.transform(v, MarketCandleModel.class))
                    .collect(Collectors.toList());
            return ApiResponse.ok(marketCandles);
        } catch (Exception e) {
            log.error("获取标记价格K线数据失败: {}", e.getMessage(), e);
            return ApiResponse.ok(Collections.emptyList());
        }
    }

    /**
     * 将TradingOrder转换为TradingOrderModel
     */
    private TradingOrderModel convertOrderToModel(TradingOrder order) {
        TradingOrderModel model = new TradingOrderModel();

        // 映射TradingOrder字段(系统订单字段)
        model.setId(order.getId());
        model.setOrderUuid(order.getOrderUuid());
        model.setApiKeyId(order.getApiKeyId());
        model.setInstId(order.getInstId());
        model.setSide(order.getSide());
        model.setOrderType(order.getOrderType());
        model.setLever(order.getLever());
        model.setPosSide(order.getPosSide());
        model.setAmt(order.getAmt());
        model.setSource(order.getSource());
        model.setOrderStatus(order.getOrderStatus());
        model.setErrorMsg(order.getErrorMsg());
        model.setTakeProfitPrice(order.getTakeProfitPrice());
        model.setStopLossPrice(order.getStopLossPrice());
        model.setTakeProfitPct(order.getTakeProfitPct());
        model.setStopLossPct(order.getStopLossPct());
        model.setSz(order.getSz());

        // 转换时间字段为时间戳
        model.setCreatedTime(null == order.getCreatedTime() ? null
                : order.getCreatedTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        model.setUpdatedTime(null == order.getUpdatedTime() ? null
                : order.getUpdatedTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        model.setSubmittedTime(null == order.getSubmittedTime() ? null
                : order.getSubmittedTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        model.setCompletedTime(null == order.getCompletedTime() ? null
                : order.getCompletedTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

        // 映射CEX订单详情(如果存在)
        CexTradingOrder cexOrder = order.getCexOrder();
        if (cexOrder != null) {
            TradingOrderModel.CexOrderDetails cexDetails = new TradingOrderModel.CexOrderDetails();
            cexDetails.setOrderId(cexOrder.getOrderId());
            cexDetails.setExchange(cexOrder.getExchange());
            cexDetails.setTdMode(cexOrder.getTdMode());
            cexDetails.setCcy(cexOrder.getCcy());
            cexDetails.setSz(cexOrder.getSz());

            cexDetails.setPx(cexOrder.getPx());
            cexDetails.setOrderState(cexOrder.getOrderState());
            cexDetails.setAvgPx(cexOrder.getAvgPx());
            cexDetails.setFilledSz(cexOrder.getFilledSz());
            cexDetails.setFilledAmt(cexOrder.getFilledAmt());
            cexDetails.setFillRatio(cexOrder.getFillRatio());
            cexDetails.setFee(cexOrder.getFee());
            cexDetails.setFeeCcy(cexOrder.getFeeCcy());
            cexDetails.setCTime(null == cexOrder.getCTime() ? null
                    : cexOrder.getCTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            cexDetails.setUTime(null == cexOrder.getUTime() ? null
                    : cexOrder.getUTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

            model.setCexOrder(cexDetails);

            // 设置执行时间
            model.setExecTime(null == cexOrder.getExecTime() ? null
                    : cexOrder.getExecTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }

        return model;
    }

    /**
     * 将ApiKey实体转换为ActiveApiKeyDTO
     *
     * @param apiKey API Key实体
     * @return ActiveApiKeyDTO
     */
    private ActiveApiKeyDTO convertToActiveApiKeyDTO(ApiKey apiKey) {
        return ActiveApiKeyDTO.builder()
                .keyId(apiKey.getKeyId())
                .keyName(apiKey.getCexName()) // 使用cexName作为keyName
                .accessKey(maskApiKey(apiKey.getAccessKey()))
                .vendor(apiKey.getCexName()) // 使用cexName作为vendor
                .status(apiKey.getStatus())
                .isLiveTrading(apiKey.getIsLiveTrading())
                .createdTime(apiKey.getCreatedTime())
                .build();
    }

    /**
     * 掩码API Key用于显示
     */
    private String maskApiKey(String apiKey) {
        if (null == apiKey || apiKey.length() <= 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "***" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 获取交易额TopN的永续合约列表
     * 基于t_futures_ticker_data表中过去24小时交易额排序
     * 根据apiKeyId区分供应商，每个供应商单独排名
     *
     * @param count    获取合约数量，默认30
     * @param apiKeyId API密钥ID，用于确定供应商（必传）
     * @return TopN永续合约基础信息列表
     */
    @GetMapping("/instruments/topn")
    public ApiResponse<List<InstrumentBasicInfoModel>> getTopNSwapContracts(@RequestParam(defaultValue = "30") int count,
                                                                            @RequestParam Long apiKeyId,
                                                                            @RequestParam(defaultValue = "volume") String orderBy) {
        try {
            log.info("获取TopN永续合约 - count: {}, apiKeyId: {}", count, apiKeyId);

            // 使用新的UnifiedMarketTickerService获取TopN合约数据
            List<MarketTickerDto> marketTickers = unifiedMarketTickerService.getTopNContracts(count, orderBy, apiKeyId);

            // 转换为InstrumentBasicInfoModel格式以保持前端兼容性
            List<InstrumentBasicInfoModel> instruments = marketTickers.stream()
                    .map(this::convertToInstrumentBasicInfo)
                    .collect(Collectors.toList());

            log.info("成功获取TopN永续合约 - count: {}, apiKeyId: {}, 返回{}条记录", count, apiKeyId, instruments.size());
            return ApiResponse.ok(instruments);
        } catch (Exception e) {
            log.error("获取TopN永续合约失败 - count: {}, apiKeyId: {}", count, apiKeyId, e);
            return ApiResponse.fail("获取TopN合约失败: " + e.getMessage());
        }
    }

    /**
     * 将MarketTickerDto转换为InstrumentBasicInfoModel
     * 用于保持前端接口兼容性
     *
     * @param dto 市场行情数据传输对象
     * @return 合约基础信息模型
     */
    private InstrumentBasicInfoModel convertToInstrumentBasicInfo(MarketTickerDto dto) {
        InstrumentBasicInfoModel model = new InstrumentBasicInfoModel();

        // 基础信息映射
        model.setInstId(dto.getInstId());
        model.setBaseAsset(dto.getBaseAsset());
        model.setQuoteAsset(dto.getQuoteAsset());

        // 从instId生成显示名称: BTC-USDT-SWAP -> BTC
        if (dto.getInstId() != null && dto.getInstId().contains("-")) {
            String displayName = dto.getInstId()
                    .replaceAll("-SWAP", "永续")
                    .replaceAll("-", "/");
            model.setDisplayName(displayName);
        } else {
            model.setDisplayName(dto.getInstId());
        }

        // 固定为SWAP类型
        model.setCategory("SWAP");

        // 排名映射
        model.setSortOrder(dto.getRank());

        // 价格信息映射
        model.setCurrentPrice(dto.getLast());
        model.setChange24hPercent(dto.getChange24hPercent());
        model.setVolume24hUsdt(dto.getVolume24hUsdt()); // 新增：映射24H USDT交易额

        // 新实现不支持4H涨跌幅,设置为null
        model.setChange4hPercent(null);

        return model;
    }

    /**
     * 获取指定合约的实时价格数据
     * 基于K线数据计算，确保数据一致性和准确性
     *
     * @param instId 合约ID
     * @return 实时价格数据
     */
    @GetMapping("/instruments/{instId}/realtime-price")
    public ApiResponse<RealTimePriceModel> getRealtimePrice(@PathVariable String instId, @RequestParam Long apiKeyId) {
        try {
            if (!StringUtils.hasText(instId)) {
                return ApiResponse.fail("合约ID不能为空");
            }

            // 验证apiKeyId
            ApiKey apiKey = apiKeyService.getDecryptedKey(apiKeyId);
            if (null == apiKey) {
                return ApiResponse.fail("未找到指定的API Key: " + apiKeyId);
            }

            // 直接通过RealTimePriceService获取基于K线数据的价格信息
            InstrumentPriceInfo priceInfo = realTimePriceService.getRealPriceData(apiKey, instId);
            if (null == priceInfo) {
                return ApiResponse.fail("未找到合约价格数据: " + instId);
            }

            // 构建响应对象
            RealTimePriceModel response = new RealTimePriceModel();
            response.setInstId(priceInfo.getInstId());
            response.setLastPrice(priceInfo.getPrimaryPrice());
            response.setChangePercent24H(priceInfo.getChangePercent());
            response.setChangePercent1H(priceInfo.getChangePercent1H());
            response.setChangePercent4H(priceInfo.getChangePercent4H());
            response.setUpdateTime(priceInfo.getUpdateTime());

            log.debug("获取合约实时价格成功, instId: {}, price: {}, 24h涨跌幅: {}%",
                    instId, response.getLastPrice(), response.getChangePercent24H());
            return ApiResponse.ok(response);

        } catch (Exception e) {
            log.error("获取合约实时价格失败, instId: {}", instId, e);
            return ApiResponse.fail("获取价格数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取合约概况信息
     * 包含资金费率和指定周期的平均波动率及极值
     *
     * @param instId  合约ID，如 "BTC-USDT-SWAP"
     * @param periods 时间周期列表，支持 1D/4H/1H/5m，例如 ?periods=1D&periods=4H
     * @return 合约概况信息
     */
    @GetMapping("/instruments/{instId}/overview")
    public ApiResponse<InstrumentOverviewDTO> getInstrumentOverview(@PathVariable String instId, @RequestParam Long apiKeyId,
                                                                    @RequestParam(required = false) List<String> periods) {
        try {
            // 验证合约ID
            if (!StringUtils.hasText(instId)) {
                return ApiResponse.fail("合约ID不能为空");
            }
            // 验证周期参数
            if (null != periods && !periods.isEmpty()) {
                for (String period : periods) {
                    if (!"1D".equalsIgnoreCase(period) && !"4H".equalsIgnoreCase(period)
                            && !"1H".equalsIgnoreCase(period) && !"5m".equalsIgnoreCase(period)) {
                        return ApiResponse.fail("不支持的时间周期: " + period + "。支持的周期: 1D/4H/1H/5m");
                    }
                }
            }
            // 获取指定的API Key
            ApiKey apiKey = apiKeyService.getDecryptedKey(apiKeyId);
            if (null == apiKey) {
                return ApiResponse.fail("未找到指定的API Key: " + apiKeyId);
            }
            // 调用服务获取合约概况
            InstrumentOverviewDTO overview = priceDataService.getInstrumentOverview(apiKey, instId, periods);
            if (null == overview) {
                return ApiResponse.fail("未找到合约概况数据: " + instId);
            }
            // 添加成功标识
            log.debug("获取合约概况成功 - 合约: {}, 周期: {}", instId, periods);
            return ApiResponse.ok(overview);
        } catch (Exception e) {
            log.error("获取合约概况失败 - 合约: {}", instId, e);
            return ApiResponse.fail("获取合约概况失败: " + e.getMessage());
        }
    }

    /**
     * 获取实时仓位数据
     * 直接从OKX API获取活跃仓位信息
     *
     * @param apiKeyId API Key ID (可选)
     * @return 实时仓位数据
     */
    @GetMapping("/positions/{apiKeyId}/live")
    public ApiResponse<List<PositionModel>> getLivePositionsByApiKey(@PathVariable Long apiKeyId) {
        try {
            log.debug("获取实时仓位数据 - API Key: {}", apiKeyId);
            // 验证API Key
            ApiKey apiKey;
            try {
                // 使用CexKeyService获取解密后的API密钥
                apiKey = apiKeyService.getDecryptedKey(apiKeyId);
            } catch (Exception e) {
                log.error("获取解密API密钥失败 - API Key: {}", apiKeyId, e);
                return ApiResponse.fail("API密钥处理失败: " + e.getMessage());
            }
            if (!"active".equals(apiKey.getStatus())) {
                return ApiResponse.fail("API Key未激活");
            }
            // ✅ 优化：使用智能刷新方法，Controller层不再关心缓存逻辑
            // 服务层会自动处理缓存未命中时的刷新
            // 直接使用通用CexPosition，无需适配
            List<CexPosition> pos = unifiedPositionService.getLatestPositionData(apiKeyId, true);
            log.debug("仓位数据获取完成 - API Key: {}, 仓位数量: {}", apiKeyId, CollectionUtils.isEmpty(pos) ? 0 : pos.size());
            // 获取算法订单数据（算法订单不在统一仓位服务中，需单独获取）
            List<CexAlgoOrder> algoOrders = CollectionUtils.isEmpty(pos)
                    ? Collections.emptyList()
                    : unifiedTradingService.getAlgoOrders(apiKey, "SWAP").getAlgoOrders();
            // 解析响应并转换为前端格式，同时整合算法订单数据
            List<PositionModel> positions = parsePositionsResponse(pos, algoOrders);
            log.debug("成功获取实时仓位数据 - API Key: {}, 仓位数量: {}", apiKeyId, positions.size());
            return ApiResponse.ok(positions);
        } catch (Exception e) {
            log.error("获取实时仓位数据失败 - API Key: {}", apiKeyId, e);
            return ApiResponse.fail("获取仓位数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定API Key的历史持仓数据
     * 从PositionSnapshot表读取历史持仓数据(每5分钟同步一次,最多7天历史)
     * 用于获取历史已平仓的仓位记录
     */
    @GetMapping("/positions/{apiKeyId}/history")
    public ApiResponse<List<PositionModel>> getPositionsHistory(@PathVariable Long apiKeyId,
                                                                @RequestParam(defaultValue = "SWAP") String instType,
                                                                @RequestParam(required = false) String instId,
                                                                @RequestParam(required = false) String after,
                                                                @RequestParam(required = false) String before,
                                                                @RequestParam(defaultValue = "10") Integer limit) {
        try {
            log.debug("获取历史仓位数据 - API Key: {}, instType: {}, instId: {}, after: {}, before: {}, limit: {}", apiKeyId,
                    instType, instId, after, before, limit);

            // 验证API Key
            ApiKey apiKey;
            try {
                apiKey = apiKeyService.getDecryptedKey(apiKeyId);
            } catch (Exception e) {
                log.error("获取解密API密钥失败 - API Key: {}", apiKeyId, e);
                return ApiResponse.fail("API密钥处理失败: " + e.getMessage());
            }
            if (null == apiKey) {
                return ApiResponse.fail("API Key不存在");
            }

            // 从PositionSnapshot表查询历史持仓数据
            List<PositionSnapshot> snapshots = positionSnapshotPersistenceService.getPositionHistory(apiKeyId, instType,
                    before, limit);

            // 转换为统一的PositionModel
            List<PositionModel> result = snapshots.stream()
                    .map(this::convertSnapshotToModel)
                    .collect(Collectors.toList());

            log.debug("成功获取历史仓位数据 - API Key: {}, 数量: {}", apiKeyId, result.size());
            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.error("获取历史仓位数据失败 - API Key: {}", apiKeyId, e);
            return ApiResponse.fail("获取历史仓位数据失败: " + e.getMessage());
        }
    }

    /**
     * 将PositionSnapshot实体转换为PositionModel
     *
     * @param snapshot 仓位快照实体
     * @return PositionModel
     */
    private PositionModel convertSnapshotToModel(PositionSnapshot snapshot) {
        PositionModel model = new PositionModel();

        // 基础字段映射
        model.setInstId(snapshot.getInstId());
        model.setInstType(snapshot.getInstType());
        model.setPosSide(snapshot.getPosSide());
        model.setPosId(snapshot.getPosId());

        // 数量和价格字段
        model.setPos(snapshot.getPos());
        model.setAvailPos(snapshot.getAvailPos());
        model.setAvgPx(snapshot.getAvgPx());
        model.setMarkPx(snapshot.getMarkPx());
        model.setLast(snapshot.getLastPx());

        // 杠杆和保证金字段
        model.setLever(snapshot.getLever());
        model.setMargin(snapshot.getMargin());
        model.setImr(snapshot.getImr());
        model.setMmr(snapshot.getMmr());
        model.setMgnRatio(snapshot.getMgnRatio());
        model.setMgnMode(snapshot.getMgnMode());

        // 盈亏字段
        model.setUpl(snapshot.getUpl());
        model.setUplLastPx(snapshot.getUplLastPx());
        model.setRealizedPnl(snapshot.getRealizedPnl());

        // 价值字段
        model.setNotionalUsd(snapshot.getNotionalUsd());
        model.setOpenAvgPx(snapshot.getOpenAvgPx());
        model.setCloseAvgPx(snapshot.getCloseAvgPx());

        // 其他字段
        model.setLiqPx(snapshot.getLiqPx());
        model.setFee(snapshot.getFee());
        model.setFundingFee(snapshot.getFundingFee());
        model.setCcy(snapshot.getCcy());

        // 时间字段
        model.setCTime(snapshot.getCtime());
        model.setUTime(snapshot.getUtime());
        model.setDataIngestionTime(snapshot.getDataIngestionTime());

        // 计算真实仓位 (pos * ctVal)
        BigDecimal ctVal = getCtValByInstId(snapshot.getInstId());
        if (snapshot.getPos() != null) {
            model.setPosition(snapshot.getPos().multiply(ctVal));
        }

        // 确保cTime有值
        if (model.getCTime() == null || model.getCTime() <= 0) {
            model.setCTime(snapshot.getCtime() != null && snapshot.getCtime() > 0
                    ? snapshot.getCtime() : System.currentTimeMillis());
        }

        // 计算保证金维持率百分比 (mgnRatio * 100)
        if (snapshot.getMgnRatio() != null) {
            try {
                double ratio = snapshot.getMgnRatio().doubleValue();
                model.setMarginRatioPercent(ratio * 100);
            } catch (Exception e) {
                model.setMarginRatioPercent(0D);
            }
        } else {
            model.setMarginRatioPercent(0D);
        }

        return model;
    }

    /**
     * 市价平仓
     *
     * @param request 平仓请求参数
     * @return 平仓结果
     */
    @PostMapping("/close-position")
    public ApiResponse<ClosePositionResult> closePosition(@RequestBody ClosePositionReq request) {
        try {
            log.debug("收到市价平仓请求 - API Key: {}, 合约: {}, 方向: {}, 数量: {}",
                    request.apiKeyId, request.instId, request.posSide, request.sz);

            // 验证参数
            if (null == request.apiKeyId) {
                return ApiResponse.fail("API Key ID不能为空");
            }
            if (!StringUtils.hasText(request.instId)) {
                return ApiResponse.fail("合约品种不能为空");
            }
            if (!StringUtils.hasText(request.posSide) ||
                    (!"long".equals(request.posSide) && !"short".equals(request.posSide))) {
                return ApiResponse.fail("持仓方向必须是long或short");
            }
            if (null == request.sz || request.sz.compareTo(BigDecimal.ZERO) <= 0) {
                return ApiResponse.fail("平仓数量必须大于0");
            }
            // 获取API Key
            ApiKey apiKey;
            try {
                apiKey = apiKeyService.getDecryptedKey(request.apiKeyId);
            } catch (Exception e) {
                log.error("获取解密API密钥失败 - API Key: {}", request.apiKeyId, e);
                return ApiResponse.fail("API密钥处理失败: " + e.getMessage());
            }
            if (!"active".equals(apiKey.getStatus())) {
                return ApiResponse.fail("API Key未激活");
            }
            // 构建平仓请求
            CexClosePositionRequest cexRequest = CexClosePositionRequest.builder()
                    .symbol(request.instId)
                    .positionSide(request.posSide)
                    .quantity(request.sz.toString())
                    .tdMode(StringUtils.hasText(request.getMgnMode()) ? request.getMgnMode().toLowerCase() : "isolated")
                    .mgnMode(StringUtils.hasText(request.getMgnMode()) ? request.getMgnMode().toLowerCase() : "isolated")
                    .build();

            CexOperationResponse cexResult = unifiedTradingService.closePosition(apiKey, cexRequest);
            TradingResult result = Boolean.TRUE.equals(cexResult.getSuccess())
                    ? TradingResult.success(cexResult.getRequestId(), "平仓成功")
                    : TradingResult.failure(cexResult.getErrorMessage());
            if (result.success) {
                ClosePositionResult response = ClosePositionResult.success(result.orderId, result.message,
                                request.instId, request.posSide, request.sz)
                        .withCloseType("market");
                return ApiResponse.ok(response);
            } else {
                return ApiResponse.fail(result.message);
            }
        } catch (Exception e) {
            log.error("市价平仓失败", e);
            return ApiResponse.fail("市价平仓失败: " + e.getMessage());
        }
    }

    /**
     * 获取合约的ctVal（合约面值）
     *
     * @param instId 合约ID
     * @return ctVal值，获取失败时返回BigDecimal.ONE
     */
    private BigDecimal getCtValByInstId(String instId) {
        try {
            Optional<CexContractInfo> contractInfoOpt = unifiedInstrumentService.getCexContractInfo(instId);
            if (contractInfoOpt.isPresent() && contractInfoOpt.get().getCtVal() != null) {
                return contractInfoOpt.get().getCtVal();
            }
            log.warn("合约信息或ctVal为空，instId: {}, 使用默认值1", instId);
            return BigDecimal.ONE;
        } catch (Exception e) {
            log.warn("获取合约ctVal失败，instId: {}, 使用默认值1, 错误: {}", instId, e.getMessage());
            return BigDecimal.ONE;
        }
    }

    /**
     * 解析仓位API响应数据
     * <p>
     * 将CEX通用仓位数据转换为前端所需的PositionModel，包括：
     * <ul>
     * <li>基础仓位信息（合约ID、仓位数量、开仓均价等）</li>
     * <li>全仓止盈止损信息（closeOrderAlgo）</li>
     * <li>个别仓位止盈止损策略（algoOrders）</li>
     * <li>保证金维持率、强平价格等风险指标</li>
     * </ul>
     * </p>
     *
     * @param positions  CEX通用仓位列表
     * @param algoOrders 算法订单列表（止盈止损策略）
     * @return 转换后的仓位模型列表
     */
    private List<PositionModel> parsePositionsResponse(List<CexPosition> positions, List<CexAlgoOrder> algoOrders) {
        List<PositionModel> retPositions = new ArrayList<>();
        try {
            // 构建算法订单映射表，按algoId分组
            Map<String, List<CexAlgoOrder>> algoOrderMap = CollectionUtils.isEmpty(algoOrders) ? Collections.emptyMap()
                    : algoOrders.stream()
                    .collect(Collectors.groupingBy(v -> v.getSymbol() + ":" + v.getPosSide().toUpperCase(), Collectors.toList()));

            // 获取数据数组
            if (!CollectionUtils.isEmpty(positions)) {
                for (CexPosition cexPosition : positions) {
                    // 将CexPosition转换为PositionModel(使用CexPositionAdapter统一转换方法)
                    PositionModel position = CexPositionAdapter.toPositionModel(cexPosition);
                    assert null != position;

                    String algKey = cexPosition.getSymbol() + ":" + cexPosition.getSide().toString();

                    // 计算真实仓位（考虑合约面值）
                    // 使用CexPosition接口的getQuantity()方法获取持仓数量
                    BigDecimal ctVal = getCtValByInstId(cexPosition.getSymbol());
                    BigDecimal realPosition = cexPosition.getQuantity().multiply(ctVal);
                    position.setPosition(realPosition);

                    // 正确设置cTime字段（持仓创建时间）
                    Long cTime = cexPosition.getCreateTime();
                    if (null == cTime || cTime <= 0) {
                        // 如果cTime无效，使用当前时间作为默认值
                        cTime = System.currentTimeMillis();
                        log.warn("OKX返回的cTime字段无效，使用当前时间作为默认值 - 合约: {}", cexPosition.getSymbol());
                    }
                    position.setCTime(cTime);

                    // 设置数据摄入时间为当前时间
                    position.setDataIngestionTime(System.currentTimeMillis());

                    // 计算保证金维持率百分比 (marginRatio * 100)
                    // 保证金维持率是衡量账户风险的重要指标
                    if (null != cexPosition.getMarginRatio()) {
                        try {
                            double ratio = cexPosition.getMarginRatio().doubleValue();
                            double marginRatioPercent = ratio * 100;
                            position.setMarginRatioPercent(marginRatioPercent);
                        } catch (NumberFormatException e) {
                            position.setMarginRatioPercent(0D);
                        }
                    } else {
                        position.setMarginRatioPercent(0D);
                    }

                    // 计算预计强平价格 (简化计算，实际需要根据合约类型计算)
                    // 强平价格 = 开仓均价 * (1 ± 0.9/杠杆倍数)
                    // 多头：价格下跌到强平价；空头：价格上涨到强平价
                    String avgPx = cexPosition.getAvgPrice().toString();
                    String lever = cexPosition.getLeverage().toString();
                    // 获取仓位方向（long/short）
                    String posSide = cexPosition.getSide().name().toLowerCase();
                    if (!avgPx.isEmpty() && !lever.isEmpty()) {
                        try {
                            double avgPrice = Double.parseDouble(avgPx);
                            double leverage = Double.parseDouble(lever);
                            // 简化的强平价格计算（实际公式更复杂，需要考虑维持保证金率）
                            double liquidationPrice = avgPrice * (1 - 0.9 / leverage);
                            if ("short".equals(posSide)) {
                                liquidationPrice = avgPrice * (1 + 0.9 / leverage);
                            }
                            position.setEstimatedLiquidationPx(liquidationPrice);
                        } catch (NumberFormatException e) {
                            position.setEstimatedLiquidationPx(0D);
                        }
                    } else {
                        position.setEstimatedLiquidationPx(0D);
                    }
                    String totalAlgoId = "";
                    // 解析全仓止盈止损信息（closeOrderAlgo）
                    // 全仓止盈止损是针对整个持仓的策略，触发后会平掉所有仓位
                    // 注意：closeOrderAlgo已在convertCexPositionToPositionModel中设置
                    if (!CollectionUtils.isEmpty(position.getCloseOrderAlgo())) {
                        CexAlgoOrder closeOrderAlgo = position.getCloseOrderAlgo().get(0);
                        totalAlgoId = closeOrderAlgo.getAlgoId();
                        if (null != closeOrderAlgo.getTpTriggerPx()) {
                            position.setTotalTakeProfitPrice(closeOrderAlgo.getTpTriggerPx().toString());
                        }
                        if (null != closeOrderAlgo.getSlTriggerPx()) {
                            position.setTotalStopLossPrice(closeOrderAlgo.getSlTriggerPx().toString());
                        }
                    }

                    // 整合算法订单数据（个别仓位止盈止损策略）
                    // 个别仓位止盈止损是针对部分仓位的分批止盈止损策略
                    List<CexAlgoOrder> cexAlgoOrders = algoOrderMap.get(algKey);
                    if (!CollectionUtils.isEmpty(cexAlgoOrders)) {
                        List<PositionStopLossStrategyModel> positionStrategies = new ArrayList<>();
                        for (CexAlgoOrder algoOrder : cexAlgoOrders) {
                            // 跳过全仓止盈止损（已在上面的代码处理）
                            if (StringUtils.hasText(totalAlgoId)
                                    && algoOrder.getAlgoId().equals(totalAlgoId)) {
                                continue;
                            }
                            // 跳过数量为0的订单
                            if ("0".equals(algoOrder.getQuantity().toString())) {
                                continue;
                            }
                            String algoInstId = algoOrder.getSymbol();

                            // 将OrderSide（BUY/SELL）转换为仓位方向（long/short）

                            // 检查是否匹配当前仓位（合约ID和仓位方向都匹配）
                            // 直接使用Builder构建策略模型，避免Map转换
                            PositionStopLossStrategyModel.PositionStopLossStrategyModelBuilder builder = PositionStopLossStrategyModel.builder();
                            builder.algoId(algoOrder.getAlgoId())
                                    .instId(algoInstId)
                                    .posSide(algoOrder.getPosSide());
                            BigDecimal orginalSz = algoOrder.getQuantity();
                            builder.sz(orginalSz.multiply(ctVal).toString());

                            // 分别处理止盈和止损触发价
                            // CexAlgoOrder接口提供了getTpTriggerPx()和getSlTriggerPx()方法
                            BigDecimal tpTriggerPx = algoOrder.getTpTriggerPx();
                            if (null != tpTriggerPx) {
                                String tpTriggerPxStr = tpTriggerPx.toString();
                                builder.tpTriggerPx(tpTriggerPxStr);

                                // 计算止盈比例（百分比）
                                // 多头止盈 = (止盈价 - 开仓价) / 开仓价 * 100
                                // 空头止盈 = (开仓价 - 止盈价) / 开仓价 * 100
                                try {
                                    double positionAvgPx = cexPosition.getAvgPrice().doubleValue();
                                    double tpPrice = tpTriggerPx.doubleValue();
                                    if (positionAvgPx > 0 && tpPrice > 0) {
                                        String positionPosSide = cexPosition.getSide().name().toLowerCase();
                                        double tpRatio = "long".equals(positionPosSide) ?
                                                (tpPrice - positionAvgPx) / positionAvgPx * 100 :
                                                (positionAvgPx - tpPrice) / positionAvgPx * 100;
                                        builder.tpRatio(String.format("%.2f", tpRatio));
                                    }
                                } catch (Exception e) {
                                    log.debug("计算止盈比例失败: {}", e.getMessage());
                                }
                            }

                            BigDecimal slTriggerPx = algoOrder.getSlTriggerPx();
                            if (null != slTriggerPx) {
                                String slTriggerPxStr = slTriggerPx.toString();
                                builder.slTriggerPx(slTriggerPxStr);

                                // 计算止损比例（百分比）
                                // 多头止损 = (止损价 - 开仓价) / 开仓价 * 100（通常为负值）
                                // 空头止损 = (开仓价 - 止损价) / 开仓价 * 100（通常为负值）
                                try {
                                    double positionAvgPx = cexPosition.getAvgPrice().doubleValue();
                                    double slPrice = slTriggerPx.doubleValue();
                                    if (positionAvgPx > 0 && slPrice > 0) {
                                        String positionPosSide = cexPosition.getSide().name().toLowerCase();
                                        double slRatio = "long".equals(positionPosSide) ?
                                                (slPrice - positionAvgPx) / positionAvgPx * 100 :
                                                (positionAvgPx - slPrice) / positionAvgPx * 100;
                                        builder.slRatio(String.format("%.2f", slRatio));
                                    }
                                } catch (Exception e) {
                                    log.debug("计算止损比例失败: {}", e.getMessage());
                                }
                            }

                            // 计算触发进度（止盈/止损执行的百分比）
                            // 通过标记价格（markPx）计算当前距离触发价格的进度
                            try {
                                BigDecimal markPx = cexPosition.getMarkPrice();
                                if (null != markPx && markPx.compareTo(BigDecimal.ZERO) > 0) {
                                    double currentPrice = markPx.doubleValue();
                                    String positionPosSide = cexPosition.getSide().name().toLowerCase();

                                    // 计算止盈进度
                                    if (null != tpTriggerPx) {
                                        double tpPrice = tpTriggerPx.doubleValue();
                                        double positionAvgPx = cexPosition.getAvgPrice().doubleValue();
                                        // 多头：当前价相对开仓价的涨幅 / 目标涨幅
                                        // 空头：当前价相对开仓价的跌幅 / 目标跌幅
                                        double tpProgress = "long".equals(positionPosSide) ?
                                                (currentPrice - positionAvgPx) / (tpPrice - positionAvgPx) * 100 :
                                                (positionAvgPx - currentPrice) / (positionAvgPx - tpPrice) * 100;
                                        // 判断是否盈利（多头：当前价>开仓价；空头：当前价<开仓价）
                                        boolean isTp = ("long".equals(positionPosSide) && currentPrice > positionAvgPx)
                                                || ("short".equals(positionPosSide) && currentPrice < positionAvgPx);
                                        // 只有盈利时才显示进度，否则为0
                                        builder.tpProgress(!isTp ? 0 : Math.max(0, Math.min(100, tpProgress)));
                                    }

                                    // 计算止损进度
                                    if (null != slTriggerPx) {
                                        double slPrice = slTriggerPx.doubleValue();
                                        double positionAvgPx = cexPosition.getAvgPrice().doubleValue();
                                        // 多头：当前价相对开仓价的跌幅 / 止损跌幅
                                        // 空头：当前价相对开仓价的涨幅 / 止损涨幅
                                        double slProgress = "long".equals(positionPosSide) ?
                                                (positionAvgPx - currentPrice) / (positionAvgPx - slPrice) * 100 :
                                                (currentPrice - positionAvgPx) / (slPrice - positionAvgPx) * 100;
                                        // 判断是否亏损（多头：当前价<开仓价；空头：当前价>开仓价）
                                        boolean isSl = ("long".equals(positionPosSide) && currentPrice < positionAvgPx)
                                                || ("short".equals(positionPosSide) && currentPrice > positionAvgPx);
                                        // 只有亏损时才显示进度，否则为0
                                        builder.slProgress(!isSl ? 0 : Math.max(0, Math.min(100, slProgress)));
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("计算触发进度失败: {}", e.getMessage());
                            }

                            // 构建策略模型对象
                            PositionStopLossStrategyModel strategyModel = builder.build();
                            if (null != strategyModel) {
                                positionStrategies.add(strategyModel);
                            }
                        }

                        // 如果存在止盈止损策略，设置到position对象中
                        if (!positionStrategies.isEmpty()) {
                            position.setPositionStopLossStrategies(positionStrategies);
                        }
                    }

                    // 只返回有持仓的仓位 (quantity > 0)
                    // 过滤掉已平仓的仓位（数量为0或负数）
                    if (Double.parseDouble(cexPosition.getQuantity().toString()) > 0) {
                        retPositions.add(position);
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析仓位API响应异常: {}", e.getMessage(), e);
        }
        return retPositions;
    }

    /**
     * 设置全仓止盈止损
     */
    @PostMapping("/total-stop-loss")
    public ApiResponse<StopLossResult> setTotalStopLoss(@RequestBody TotalStopLossReq request) {
        try {
            // 验证API Key
            ApiKey apiKey = apiKeyService.getDecryptedKey(request.getApiKeyId().longValue());
            if (null == apiKey) {
                return ApiResponse.fail("API Key不存在");
            }
            // 调用统一交易服务,构建算法订单请求
            CexAlgoOrderRequest.CexAlgoOrderRequestBuilder builder = CexAlgoOrderRequest.builder()
                    .symbol(request.getInstId())
                    .tradeMode(request.getMgnMode() != null ? request.getMgnMode().toLowerCase() : "cross")  // 使用请求中的保证金模式,默认全仓
                    .currency("USDT")
                    .side(request.getSide())
                    .positionSide(request.getPosSide().toLowerCase())
                    .closeFraction("1")  // 全部平仓
                    .cancelOnClosePosition(true)
                    .reduceOnly(true);

            // 设置止盈
            if (StringUtils.hasText(request.getTpTriggerPx()) && !"0".equals(request.getTpTriggerPx())) {
                builder.takeProfitTriggerPrice(request.getTpTriggerPx())
                        .takeProfitOrderPrice("-1")  // 市价单
                        .takeProfitTriggerPriceType("last");
            }

            // 设置止损
            if (StringUtils.hasText(request.getSlTriggerPx()) && !"0".equals(request.getSlTriggerPx())) {
                builder.stopLossTriggerPrice(request.getSlTriggerPx())
                        .stopLossOrderPrice("-1")  // 市价单
                        .stopLossTriggerPriceType("last");
            }

            // OCO订单(止盈止损二选一)
            builder.orderType("oco");

            CexAlgoOrderOperationResponse response = unifiedTradingService.setAlgoOrder(apiKey, builder.build());
            TradingResult result = Boolean.TRUE.equals(response.isSuccess())
                    ? TradingResult.success(response.getAlgoId(), "设置成功")
                    : TradingResult.failure(response.getErrorMessage());

            if (result.success) {
                StopLossResult stopLossResult = StopLossResult.success(result.orderId, result.message,
                        request.getTpTriggerPx(), request.getSlTriggerPx());
                return ApiResponse.ok(stopLossResult);
            } else {
                return ApiResponse.fail(result.message);
            }
        } catch (Exception e) {
            log.error("设置全仓止盈止损失败", e);
            return ApiResponse.fail("设置失败: " + e.getMessage());
        }
    }

    /**
     * 修改全仓止盈止损
     */
    @PostMapping("/total-stop-loss/amend")
    public ApiResponse<StopLossResult> amendTotalStopLoss(@RequestBody TotalStopLossAmendReq request) {
        try {
            log.debug("修改全仓止盈止损请求: apiKeyId={}, algoId={}, instId={}",
                    request.getApiKeyId(), request.getAlgoId(), request.getInstId());
            // 验证API Key
            ApiKey apiKey = apiKeyService.getDecryptedKey(request.getApiKeyId().longValue());
            if (null == apiKey) {
                return ApiResponse.fail("API Key不存在");
            }
            // 验证必要参数
            if (!StringUtils.hasText(request.getAlgoId())) {
                return ApiResponse.fail("算法订单ID不能为空");
            }
            if (!StringUtils.hasText(request.getInstId())) {
                return ApiResponse.fail("合约品种不能为空");
            }
            // 构建修改算法订单请求
            CexAmendAlgoOrderRequest.CexAmendAlgoOrderRequestBuilder builder = CexAmendAlgoOrderRequest.builder()
                    .algoId(request.getAlgoId());

            // 设置止盈触发价修改
            if (StringUtils.hasText(request.getTpTriggerPx())) {
                builder.newTakeProfitTriggerPrice(request.getTpTriggerPx());
            }

            // 设置止损触发价修改
            if (StringUtils.hasText(request.getSlTriggerPx())) {
                builder.newStopLossTriggerPrice(request.getSlTriggerPx());
            }

            if (StringUtils.hasText(request.getInstId())) {
                builder.symbol(request.getInstId());
            }

            CexAlgoOrderOperationResponse response = unifiedTradingService.amendAlgoOrder(apiKey, builder.build());
            TradingResult result = Boolean.TRUE.equals(response.isSuccess())
                    ? TradingResult.success(response.getAlgoId(), "修改成功")
                    : TradingResult.failure(response.getErrorMessage());

            if (result.success) {
                StopLossResult stopLossResult = StopLossResult.success(result.getOrderId(), result.getMessage());
                log.debug("修改全仓止盈止损成功: orderId={}", result.getOrderId());
                return ApiResponse.ok(stopLossResult);
            } else {
                log.warn("修改全仓止盈止损失败: {}", result.getMessage());
                return ApiResponse.fail(result.getMessage());
            }
        } catch (Exception e) {
            log.error("修改全仓止盈止损失败", e);
            return ApiResponse.fail("修改失败: " + e.getMessage());
        }
    }

    /**
     * 删除全仓止盈止损
     */
    @PostMapping("/total-stop-loss/cancel")
    public ApiResponse<TradingResult> cancelTotalStopLoss(@RequestBody TotalStopLossCancelReq request) {
        try {
            // 验证API Key
            ApiKey apiKey = apiKeyRepository.findById(request.getApiKeyId().longValue())
                    .orElse(null);
            if (null == apiKey) {
                return ApiResponse.fail("API Key不存在");
            }
            // 调用统一交易服务
            CexCancelAlgoOrderRequest cancelRequest = CexCancelAlgoOrderRequest.builder()
                    .algoId(request.getAlgoId())
                    .build();

            CexAlgoOrderOperationResponse response = unifiedTradingService.cancelAlgoOrder(apiKey, cancelRequest);
            TradingResult result = Boolean.TRUE.equals(response.isSuccess())
                    ? TradingResult.success(response.getAlgoId(), "取消成功")
                    : TradingResult.failure(response.getErrorMessage());

            if (result.success) {
                TradingResult responseResult = TradingResult.success(result.orderId, result.message);
                return ApiResponse.ok(responseResult);
            } else {
                return ApiResponse.fail(result.message);
            }
        } catch (Exception e) {
            log.error("取消全仓止盈止损失败", e);
            return ApiResponse.fail("取消失败: " + e.getMessage());
        }
    }

    /**
     * 撤销算法订单
     */
    @PostMapping("/cancel-algos")
    public ApiResponse<Void> cancelAlgos(@RequestBody CancelAlgosReq request) {
        try {
            log.debug("收到撤销算法订单请求 - API Key: {}, 合约: {}, 算法订单ID: {}",
                    request.getApiKeyId(), request.getInstId(), request.getAlgoId());
            // 验证API Key
            if (null == request.getApiKeyId()) {
                return ApiResponse.fail("API Key ID不能为空");
            }
            ApiKey apiKey = apiKeyService.getDecryptedKey(request.getApiKeyId().longValue());
            if (null == apiKey) {
                return ApiResponse.fail("API Key不存在");
            }
            // 验证参数
            if (!StringUtils.hasText(request.getInstId())) {
                return ApiResponse.fail("合约品种不能为空");
            }
            if (!StringUtils.hasText(request.getAlgoId())) {
                return ApiResponse.fail("算法订单ID不能为空");
            }
            // 调用统一交易服务
            CexCancelAlgoOrderRequest cancelRequest = CexCancelAlgoOrderRequest.builder()
                    .symbol(request.getInstId())
                    .algoId(request.getAlgoId())
                    .build();

            CexAlgoOrderOperationResponse response = unifiedTradingService.cancelAlgoOrder(apiKey, cancelRequest);
            return Boolean.TRUE.equals(response.isSuccess())
                    ? ApiResponse.ok(null)
                    : ApiResponse.fail(response.getErrorMessage());
        } catch (Exception e) {
            log.error("撤销算法订单失败", e);
            return ApiResponse.fail("撤销失败: " + e.getMessage());
        }
    }

    /**
     * 技术指标计算接口
     */
    @GetMapping("/technical-indicators/{metricName}/{instId}")
    public ApiResponse<List<MarketCandleModel>> calculateTechnicalIndicator(@PathVariable String metricName,
                                                                            @PathVariable String instId,
                                                                            @RequestParam String timeframe,
                                                                            @RequestParam(defaultValue = "200") Integer limit,
                                                                            @RequestParam(defaultValue = "20") Integer period,
                                                                            @RequestParam(required = false) Double stdDev) {
        try {
            log.debug("计算技术指标 - 合约: {}, 时间帧: {}, 指标: {}, 周期: {}, 限制: {}",
                    instId, timeframe, metricName, period, limit);
            if (!StringUtils.hasText(instId)) {
                return ApiResponse.fail("合约品种不能为空");
            }
            if (!StringUtils.hasText(timeframe)) {
                return ApiResponse.fail("时间帧不能为空");
            }
            if (!StringUtils.hasText(metricName)) {
                return ApiResponse.fail("指标名称不能为空");
            }
            // 获取K线数据用于计算技术指标
            List<CexMarketCandle> marketCandles = unifiedTradingService.getMarkPriceCandles(instId, convertTimeFrame(timeframe), limit);
            if (marketCandles.isEmpty()) {
                return ApiResponse.fail("无法获取K线数据");
            }
            // 计算技术指标
            List<MarketCandleModel> marketCandleModels = marketCandles.stream()
                    .map(v -> JsonUtils.transform(v, MarketCandleModel.class))
                    .collect(Collectors.toList());
            return ApiResponse.ok(marketCandleModels);
        } catch (Exception e) {
            log.error("计算技术指标失败", e);
            return ApiResponse.fail("计算技术指标失败: " + e.getMessage());
        }
    }

    /**
     * 获取完整图表数据接口（包含K线数据和技术指标）
     */
    @GetMapping("/complete-chart-data/{instId}")
    public ApiResponse<CompleteChartDataModel> getCompleteChartData(@PathVariable String instId,
                                                                    @RequestParam(required = false) Long apiKeyId,
                                                                    @RequestParam String timeframe,
                                                                    @RequestParam(defaultValue = "200") Integer limit,
                                                                    @RequestParam(required = false) List<String> indicators,
                                                                    @RequestParam(required = false) List<Integer> emaPeriods,
                                                                    @RequestParam(required = false) List<Integer> smaPeriods,
                                                                    @RequestParam(required = false) List<Integer> wmaPeriods,
                                                                    @RequestParam(required = false) List<Integer> rsiPeriods,
                                                                    @RequestParam(required = false) String[] bollParams,
                                                                    @RequestParam(required = false) List<Integer> macdPeriods,
                                                                    @RequestParam(required = false) List<Integer> kdjPeriods,
                                                                    @RequestParam(required = false) List<Integer> cciPeriods,
                                                                    @RequestParam(required = false) List<Integer> atrPeriods,
                                                                    @RequestParam(required = false) List<Integer> obvPeriods,
                                                                    @RequestParam(required = false) List<Integer> adxPeriods,
                                                                    @RequestParam(required = false) Long startMills) {
        try {
            log.debug("获取完整图表数据 - 合约: {}, apiKeyId: {}, 时间帧: {}, 限制: {}, 指标: {}",
                    instId, apiKeyId, timeframe, limit, indicators);

            if (!StringUtils.hasText(instId)) {
                return ApiResponse.fail("合约品种不能为空");
            }

            if (!StringUtils.hasText(timeframe)) {
                return ApiResponse.fail("时间帧不能为空");
            }

            // 获取ApiKey对象
            ApiKey apiKey = null;
            if (apiKeyId != null) {
                apiKey = apiKeyService.getDecryptedKey(apiKeyId);
                if (null == apiKey) {
                    return ApiResponse.fail("未找到指定的API Key: " + apiKeyId);
                }
            } else {
                apiKey = apiKeyService.getDefaultApiKey();
                if (apiKey == null) {
                    return ApiResponse.fail("未配置可用的默认API Key");
                }
            }

            // 验证用户请求的数据量限制
            if (limit > MAX_USER_LIMIT) {
                return ApiResponse.fail("数据请求量不能超过" + MAX_USER_LIMIT + "，当前请求：" + limit);
            }

            // 验证技术指标周期参数
            if (null != indicators && !indicators.isEmpty()) {
                for (String indicator : indicators) {
                    String upperIndicator = indicator.toUpperCase();
                    switch (upperIndicator) {
                        case "EMA":
                            // 验证多周期参数
                            if (null != emaPeriods) {
                                for (Integer period : emaPeriods) {
                                    if (period > MAX_INDICATOR_PERIOD) {
                                        return ApiResponse.fail("EMA周期不能超过" + MAX_INDICATOR_PERIOD + "，当前请求：" + period);
                                    }
                                }
                            }
                            break;

                        case "SMA":
                            // 验证多周期参数
                            if (null != smaPeriods) {
                                for (Integer period : smaPeriods) {
                                    if (period > MAX_INDICATOR_PERIOD) {
                                        return ApiResponse.fail("SMA周期不能超过" + MAX_INDICATOR_PERIOD + "，当前请求：" + period);
                                    }
                                }
                            }
                            break;

                        case "WMA":
                            // 验证多周期参数
                            if (null != wmaPeriods) {
                                for (Integer period : wmaPeriods) {
                                    if (period > MAX_INDICATOR_PERIOD) {
                                        return ApiResponse.fail("WMA周期不能超过" + MAX_INDICATOR_PERIOD + "，当前请求：" + period);
                                    }
                                }
                            }
                            break;

                        case "RSI":
                            // 验证多周期参数
                            if (null != rsiPeriods) {
                                for (Integer period : rsiPeriods) {
                                    if (period > MAX_INDICATOR_PERIOD - 1) {
                                        return ApiResponse.fail("RSI周期不能超过" + (MAX_INDICATOR_PERIOD - 1) + "，当前请求：" + period);
                                    }
                                }
                            }
                            break;
                        case "BOLL":
                            // 验证BOLL多周期参数（格式：周期_标准差，如 "20_2.0"）
                            if (null != bollParams) {
                                for (String param : bollParams) {
                                    try {
                                        String[] parts = param.split("_");
                                        if (parts.length != 2) {
                                            return ApiResponse.fail("BOLL参数格式错误，应为 '周期_标准差'，如：20_2.0，当前参数：" + param);
                                        }
                                        int period = Integer.parseInt(parts[0]);
                                        double stdDev = Double.parseDouble(parts[1]);
                                        if (period > MAX_INDICATOR_PERIOD) {
                                            return ApiResponse.fail("BOLL周期不能超过" + MAX_INDICATOR_PERIOD + "，当前请求：" + period);
                                        }
                                        if (period <= 0) {
                                            return ApiResponse.fail("BOLL周期必须大于0，当前请求：" + period);
                                        }
                                        if (stdDev <= 0) {
                                            return ApiResponse.fail("BOLL标准差必须大于0，当前请求：" + stdDev);
                                        }
                                    } catch (NumberFormatException e) {
                                        return ApiResponse.fail("BOLL参数格式错误，应为 '周期_标准差'，如：20_2.0，当前参数：" + param);
                                    }
                                }
                            }
                            break;

                        case "MACD":
                            // 验证多周期参数（fast, slow, signal）
                            if (null != macdPeriods && macdPeriods.size() == 3) {
                                for (Integer period : macdPeriods) {
                                    if (period > MAX_INDICATOR_PERIOD) {
                                        return ApiResponse.fail("MACD周期不能超过" + MAX_INDICATOR_PERIOD + "，当前请求：" + period);
                                    }
                                }
                            }
                            break;

                        case "KDJ":
                            if (null != kdjPeriods) {
                                for (Integer period : kdjPeriods) {
                                    if (period > MAX_INDICATOR_PERIOD) {
                                        return ApiResponse.fail("KDJ周期不能超过" + MAX_INDICATOR_PERIOD + "，当前请求：" + period);
                                    }
                                    if (period <= 0) {
                                        return ApiResponse.fail("KDJ周期必须大于0，当前请求：" + period);
                                    }
                                }
                            }
                            break;

                        case "CCI":
                            if (null != cciPeriods) {
                                for (Integer period : cciPeriods) {
                                    if (period > MAX_INDICATOR_PERIOD) {
                                        return ApiResponse.fail("CCI周期不能超过" + MAX_INDICATOR_PERIOD + "，当前请求：" + period);
                                    }
                                    if (period <= 0) {
                                        return ApiResponse.fail("CCI周期必须大于0，当前请求：" + period);
                                    }
                                }
                            }
                            break;

                        case "ATR":
                            if (null != atrPeriods) {
                                for (Integer period : atrPeriods) {
                                    if (period > MAX_INDICATOR_PERIOD) {
                                        return ApiResponse.fail("ATR周期不能超过" + MAX_INDICATOR_PERIOD + "，当前请求：" + period);
                                    }
                                    if (period <= 0) {
                                        return ApiResponse.fail("ATR周期必须大于0，当前请求：" + period);
                                    }
                                }
                            }
                            break;

                        case "OBV":
                            if (null != obvPeriods) {
                                for (Integer period : obvPeriods) {
                                    if (period > MAX_INDICATOR_PERIOD) {
                                        return ApiResponse.fail("OBV周期不能超过" + MAX_INDICATOR_PERIOD + "，当前请求：" + period);
                                    }
                                    if (period <= 0) {
                                        return ApiResponse.fail("OBV周期必须大于0，当前请求：" + period);
                                    }
                                }
                            }
                            break;

                        case "ADX":
                            if (null != adxPeriods) {
                                for (Integer period : adxPeriods) {
                                    if (period > MAX_INDICATOR_PERIOD) {
                                        return ApiResponse.fail("ADX周期不能超过" + MAX_INDICATOR_PERIOD + "，当前请求：" + period);
                                    }
                                    if (period <= 0) {
                                        return ApiResponse.fail("ADX周期必须大于0，当前请求：" + period);
                                    }
                                }
                            }
                            break;

                        default:
                            log.warn("未知的技术指标类型: {}", indicator);
                            break;
                    }
                }
            }

            // ========== 转换为统一请求格式 ==========
            UnifiedChartDataRequest unifiedRequest = buildUnifiedRequest(
                    instId, timeframe, limit, indicators,
                    emaPeriods, smaPeriods, wmaPeriods, rsiPeriods, bollParams, macdPeriods,
                    kdjPeriods, cciPeriods, atrPeriods, obvPeriods, adxPeriods,
                    startMills
            );

            // ========== 调用统一服务 ==========
            // 根据apiKey的isLiveTrading区分正式和模拟交易数据
            UnifiedChartDataResponse unifiedResponse = priceDataService.getUnifiedChartData(apiKey, unifiedRequest);

            if (unifiedResponse == null) {
                return ApiResponse.fail("无法获取K线数据");
            }

            // ========== 检查错误状态和数据有效性 ==========
            // 检查是否有错误消息
            if (unifiedResponse.getErrorMessage() != null) {
                log.error("获取图表数据失败 - instId: {}, timeframe: {}, error: {}",
                        instId, timeframe, unifiedResponse.getErrorMessage());
                return ApiResponse.fail("获取K线数据失败: " + unifiedResponse.getErrorMessage());
            }

            // 检查数据状态
            String dataStatus = unifiedResponse.getDataStatus();
            if ("CANDLES_ONLY".equals(dataStatus) || "PARTIAL".equals(dataStatus)) {
                // 如果K线数据为空,返回失败
                if (unifiedResponse.getCandles() == null || unifiedResponse.getCandles().isEmpty()) {
                    log.error("K线数据为空 - instId: {}, timeframe: {}, status: {}",
                            instId, timeframe, dataStatus);
                    return ApiResponse.fail("K线数据为空,请稍后重试");
                }

                // 如果状态为PARTIAL但有数据,记录警告但继续返回(保持向后兼容)
                if ("PARTIAL".equals(dataStatus)) {
                    log.warn("图表数据状态为PARTIAL - instId: {}, 部分指标计算失败: {}",
                            instId, unifiedResponse.getErrorMessage());
                }
            }
            // ========== 错误检查结束 ==========

            // ========== 转换返回类型（保持向后兼容）==========
            CompleteChartDataModel responseData = new CompleteChartDataModel();
            responseData.setInstId(unifiedResponse.getInstId());
            responseData.setTimeframe(unifiedResponse.getTimeframe());
            responseData.setLimit(unifiedResponse.getLimit());
            responseData.setCandles(unifiedResponse.getCandles());
            responseData.setTimestamp(unifiedResponse.getTimestamp());
            responseData.setMarkPrice(unifiedResponse.getMarkPrice());
            responseData.setIndicators(unifiedResponse.getIndicators());

            // 记录成功获取数据的日志
            log.debug("成功获取完整图表数据 - 合约: {}, 时间帧: {}, 数据量: {}, 状态: {}",
                    instId, timeframe, responseData.getCandles().size(), unifiedResponse.getDataStatus());
            return ApiResponse.ok(responseData);

        } catch (Exception e) {
            log.error("获取完整图表数据失败", e);
            return ApiResponse.fail("获取完整图表数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取支持的指标类型
     */
    @GetMapping("/technical-indicators/metrics")
    public ApiResponse<List<String>> getSupportedMetrics() {
        try {
            List<String> metrics = List.of("EMA", "SMA", "WMA", "RSI", "BOLL", "MACD", "KDJ", "CCI", "ATR", "OBV", "ADX");
            return ApiResponse.ok(metrics);
        } catch (Exception e) {
            log.error("获取支持的指标类型失败", e);
            return ApiResponse.ok(List.of("EMA", "SMA", "WMA", "RSI", "BOLL", "MACD", "KDJ", "CCI", "ATR", "OBV", "ADX"));
        }
    }

    /**
     * 转换时间帧格式
     */
    private String convertTimeFrame(String timeframe) {
        switch (timeframe.toLowerCase()) {
            case "1m":
                return "1m";
            case "5m":
                return "5m";
            case "15m":
                return "15m";
            case "30m":
                return "30m";
            case "1h":
                return "1H";
            case "4h":
                return "4H";
            case "1d":
                return "1D";
            default:
                return "1H"; // 默认1小时
        }
    }

    /**
     * 将接口参数转换为UnifiedChartDataRequest
     * 用于调用UnifiedPriceDataService.getUnifiedChartData
     *
     * @param instId     合约ID
     * @param timeframe  时间周期
     * @param limit      数据条数
     * @param indicators 指标列表
     * @param emaPeriods EMA周期参数
     * @param smaPeriods SMA周期参数
     * @param wmaPeriods WMA周期参数
     * @param rsiPeriods RSI周期参数
     * @param bollParams BOLL周期参数
     * @return UnifiedChartDataRequest
     */
    private UnifiedChartDataRequest buildUnifiedRequest(String instId, String timeframe, Integer limit, List<String> indicators,
                                                        List<Integer> emaPeriods, List<Integer> smaPeriods, List<Integer> wmaPeriods,
                                                        List<Integer> rsiPeriods, String[] bollParams, List<Integer> macdPeriods,
                                                        List<Integer> kdjPeriods, List<Integer> cciPeriods, List<Integer> atrPeriods, List<Integer> obvPeriods,
                                                        List<Integer> adxPeriods,
                                                        Long startMills) {
        UnifiedChartDataRequest.UnifiedChartDataRequestBuilder builder = UnifiedChartDataRequest.builder()
                .instId(instId)
                .timeframe(timeframe)
                .limit(limit)
                .includeMarkPrice(true);

        // 设置开始时间戳（如果提供）
        if (startMills != null && startMills > 0) {
            builder.startMills(startMills);
        }

        // 设置技术指标参数
        if (indicators != null && !indicators.isEmpty()) {
            builder.indicators(indicators);

            Map<String, List<String>> indicatorPeriods = new HashMap<>();

            // 转换EMA周期参数：List<Integer> -> List<String>
            if (indicators.contains("EMA") && emaPeriods != null && !emaPeriods.isEmpty()) {
                List<String> periods = emaPeriods.stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList());
                indicatorPeriods.put("EMA", periods);
            }

            // 转换SMA周期参数
            if (indicators.contains("SMA") && smaPeriods != null && !smaPeriods.isEmpty()) {
                List<String> periods = smaPeriods.stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList());
                indicatorPeriods.put("SMA", periods);
            }

            // 转换WMA周期参数
            if (indicators.contains("WMA") && wmaPeriods != null && !wmaPeriods.isEmpty()) {
                List<String> periods = wmaPeriods.stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList());
                indicatorPeriods.put("WMA", periods);
            }

            // 转换RSI周期参数
            if (indicators.contains("RSI") && rsiPeriods != null && !rsiPeriods.isEmpty()) {
                List<String> periods = rsiPeriods.stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList());
                indicatorPeriods.put("RSI", periods);
            }

            // 转换BOLL周期参数：String[] -> List<String>
            if (indicators.contains("BOLL") && bollParams != null && bollParams.length > 0) {
                indicatorPeriods.put("BOLL", Arrays.asList(bollParams));
            }

            // 转换MACD周期参数：List<Integer> -> List<String>
            if (indicators.contains("MACD") && macdPeriods != null && !macdPeriods.isEmpty()) {
                List<String> periods = macdPeriods.stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList());
                indicatorPeriods.put("MACD", periods);
            }

            if (indicators.contains("KDJ") && kdjPeriods != null && !kdjPeriods.isEmpty()) {
                indicatorPeriods.put("KDJ", kdjPeriods.stream().map(String::valueOf).collect(Collectors.toList()));
            }

            if (indicators.contains("CCI") && cciPeriods != null && !cciPeriods.isEmpty()) {
                indicatorPeriods.put("CCI", cciPeriods.stream().map(String::valueOf).collect(Collectors.toList()));
            }

            if (indicators.contains("ATR") && atrPeriods != null && !atrPeriods.isEmpty()) {
                indicatorPeriods.put("ATR", atrPeriods.stream().map(String::valueOf).collect(Collectors.toList()));
            }

            if (indicators.contains("OBV") && obvPeriods != null && !obvPeriods.isEmpty()) {
                indicatorPeriods.put("OBV", obvPeriods.stream().map(String::valueOf).collect(Collectors.toList()));
            }

            if (indicators.contains("ADX") && adxPeriods != null && !adxPeriods.isEmpty()) {
                indicatorPeriods.put("ADX", adxPeriods.stream().map(String::valueOf).collect(Collectors.toList()));
            }

            if (!indicatorPeriods.isEmpty()) {
                builder.indicatorPeriods(indicatorPeriods);
            }
        }

        return builder.build();
    }

    /**
     * 获取统一图表数据接口
     * 一次性获取K线数据、技术指标、标记价格等完整信息
     *
     * @param apiKeyId API密钥ID
     * @param request  统一图表数据查询请求
     * @return 完整图表数据
     */
    @PostMapping("/unified-chart-data")
    public ResponseEntity<ApiResponse<UnifiedChartDataResponse>> getUnifiedChartData(@RequestParam Long apiKeyId,
                                                                                     @RequestBody UnifiedChartDataRequest request) {
        try {
            log.debug("[getUnifiedChartData] 接收到请求 - apiKeyId: {}, instId: {}, timeframe: {}, indicators: {}",
                    apiKeyId, request.getInstId(), request.getTimeframe(), request.getIndicators());

            // 获取API密钥
            ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                    .orElseThrow(() -> new IllegalArgumentException("API密钥不存在"));

            // 调用服务
            UnifiedChartDataResponse result = priceDataService.getUnifiedChartData(apiKey, request);

            if (result == null) {
                return ResponseEntity.ok(ApiResponse.fail("获取图表数据失败"));
            }

            // 检查是否有错误
            if (result.getErrorMessage() != null) {
                return ResponseEntity.ok(ApiResponse.fail(result.getErrorMessage()));
            }

            return ResponseEntity.ok(ApiResponse.ok(result));

        } catch (Exception e) {
            log.error("[getUnifiedChartData] 处理失败", e);
            return ResponseEntity.ok(ApiResponse.fail("获取图表数据失败: " + e.getMessage()));
        }
    }

}

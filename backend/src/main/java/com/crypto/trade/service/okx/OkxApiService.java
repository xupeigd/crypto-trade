package com.crypto.trade.service.okx;

import com.crypto.trade.dto.cex.adapter.*;
import com.crypto.trade.dto.cex.model.*;
import com.crypto.trade.dto.cex.okx.*;
import com.crypto.trade.dto.cex.request.*;
import com.crypto.trade.dto.cex.response.CexAlgoOrderOperationResponse;
import com.crypto.trade.dto.cex.response.CexAlgoOrderResponse;
import com.crypto.trade.dto.cex.response.CexOperationResponse;
import com.crypto.trade.dto.cex.response.CexOrderResponse;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.enums.CexApiType;
import com.crypto.trade.enums.CexExchange;
import com.crypto.trade.enums.CexHttpMethod;
import com.crypto.trade.event.CexApiCallEvent;
import com.crypto.trade.service.CexProxyBindingService;
import com.crypto.trade.service.ProxyServiceConfigService;
import com.crypto.trade.service.cex.CexApiService;
import com.crypto.trade.service.signature.OKXSignatureService;
import com.crypto.trade.util.JsonUtils;
import com.crypto.trade.util.ResponseValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * OkxApiService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service("okxApiService")
public class OkxApiService
        implements CexApiService {

    /**
     * 账户余额
     */
    public static final String API_PATH_GET_ACCOUNT_BALANCE = "/api/v5/account/balance";
    /**
     * 持仓信息
     */
    public static final String API_PATH_GET_ACCOUNT_POSITIONS = "/api/v5/account/positions";
    /**
     * 获取策略
     */
    public static final String API_PATH_GET_ORDERS_ALGO_PENDING = "/api/v5/trade/orders-algo-pending";
    /**
     * 获取资金费率
     */
    public static final String API_PATH_GET_FUNDING_RATE = "/api/v5/public/funding-rate";
    /**
     * 获取标记价格
     */
    public static final String API_PATH_GET_MARK_PRICE = "/api/v5/public/mark-price";
    /**
     * 获取市场Tickers
     */
    public static final String API_PATH_GET_MARKET_TICKERS = "/api/v5/market/tickers";
    /**
     * 获取K线数据
     */
    public static final String API_PATH_GET_MARKET_CANDLES = "/api/v5/market/candles";
    /**
     * 获取挂单订单
     */
    public static final String API_PATH_GET_ORDERS_PENDING = "/api/v5/trade/orders-pending";
    /**
     * 取消策略
     */
    public static final String API_PATH_CANCEL_ALGOS = "/api/v5/trade/cancel-algos";
    /**
     * 获取SKU信息
     */
    public static final String API_PATH_GET_INSTRUMENTS = "/api/v5/public/instruments";
    /**
     * 设置策略
     */
    public static final String API_PATH_SET_ALGO_ORDER = "/api/v5/trade/order-algo";
    /**
     * 修改策略
     */
    public static final String API_PATH_AMEND_ALGOS = "/api/v5/trade/amend-algos";
    /**
     * 平仓
     */
    public static final String API_PATH_CLOSE_POSITION = "/api/v5/trade/close-position";
    /**
     * 下单
     */
    public static final String API_PATH_PLACE_ORDER = "/api/v5/trade/order";
    /**
     * 取消订单
     */
    public static final String API_PATH_CANCEL_ORDER = "/api/v5/trade/cancel-order";
    /**
     * 获取历史订单 (最近7天)
     */
    public static final String API_PATH_GET_ORDERS_HISTORY = "/api/v5/trade/orders-history";
    /**
     * 获取历史订单 (最近3个月)
     */
    public static final String API_PATH_GET_ORDERS_HISTORY_ARCHIVE = "/api/v5/trade/orders-history-archive";
    /**
     * 获取账户历史持仓 (最近7天)
     */
    public static final String API_PATH_GET_POSITIONS_HISTORY = "/api/v5/account/positions-history";
    /**
     * 获取K线数据-历史数据
     */
    public static final String API_PATH_GET_MARKET_HISTORY_CANDLES = "/api/v5/market/history-candles";

    private static final String OKX_BASE_URL = "https://www.okx.com";
    // OKX API错误码映射
    private static final Map<String, String> ERROR_MESSAGES = Map.of(
            "51008", "API权限不足",
            "51100", "可用保证金不足",
            "51116", "风险控制限制",
            "51120", "持仓数量限制",
            "51408", "请求频率过高",
            "51440", "请求超时",
            "51450", "系统繁忙，请稍后重试"
    );
    /**
     * 按API Key ID分组的限流器
     * Key: apiKeyId, Value: RateLimiter
     */
    private final ConcurrentHashMap<Long, RateLimiter> apiKeyRateLimiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, HttpClient> proxyHttpClients = new ConcurrentHashMap<>();

    private final HttpClient httpClient;

    @Autowired
    OKXSignatureService okxSignatureService;
    @Autowired
    ApplicationEventPublisher eventPublisher;
    @Autowired
    CexProxyBindingService cexProxyBindingService;
    @Autowired
    ProxyServiceConfigService proxyServiceConfigService;
    /**
     * 每个API Key的QPS限制
     */
    @Value("${cex.api.rate-limit.per-api-key-qps:10}")
    private double perApiKeyQps;
    /**
     * 全局QPS限制（作为兜底保护）
     */
    @Value("${cex.api.rate-limit.global-qps:15}")
    private double globalQps;

    /**
     * 限流等待超时时间（毫秒）
     */
    @Value("${cex.api.rate-limit.wait-timeout-ms:800}")
    private long rateLimitWaitTimeoutMs;
    /**
     * 全局限流器（作为兜底保护）
     */
    private RateLimiter globalRateLimiter;

    @SuppressWarnings("UnstableApiUsage")
    public OkxApiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 初始化限流器
     * 在@Value属性注入完成后执行
     */
    @PostConstruct
    @SuppressWarnings("UnstableApiUsage")
    public void initRateLimiters() {
        // 初始化全局限流器，此时globalQps已被正确注入
        this.globalRateLimiter = RateLimiter.create(globalQps);
        log.info("全局限流器初始化完成，QPS: {}", globalQps);
    }

    /**
     * 获取指定API Key的限流器
     * 如果不存在则创建新的限流器
     *
     * @param apiKeyId API Key ID
     * @return 限流器实例
     */
    @SuppressWarnings("UnstableApiUsage")
    private RateLimiter getRateLimiterForApiKey(Long apiKeyId) {
        return apiKeyRateLimiters.computeIfAbsent(apiKeyId,
                id -> {
                    RateLimiter limiter = RateLimiter.create(perApiKeyQps);
                    log.info("为API Key {} 创建限流器，QPS: {}", id, perApiKeyQps);
                    return limiter;
                });
    }

    /**
     * 限流检查与等待（优化版）
     * <p>
     * 修复假死bug：降低等待时间，从800ms降低到50ms
     * 如果限流触发，只等待较短时间，避免异步任务阻塞太久
     * </p>
     *
     * @param apiKeyId API Key ID
     */
    private void acquireRateLimit(Long apiKeyId) {
        // 1. 全局限流检查（兜底保护）
        if (!globalRateLimiter.tryAcquire()) {
            // 降低等待时间：从800ms降低到50ms
            boolean acquired = globalRateLimiter.tryAcquire(50, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("全局限流触发，API Key {} 等待超时(50ms)，将继续请求",
                        apiKeyId);
            }
        }

        // 2. API Key级别限流检查
        RateLimiter apiKeyLimiter = getRateLimiterForApiKey(apiKeyId);
        if (!apiKeyLimiter.tryAcquire()) {
            // 降低等待时间：从800ms降低到50ms
            boolean acquired = apiKeyLimiter.tryAcquire(50, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("API Key {} 限流触发(QPS={})，等待超时(50ms)，将继续请求",
                        apiKeyId, perApiKeyQps);
            }
        }
    }

    /**
     * 通用获取数据的方法
     *
     * @param apiName      api名称
     * @param path         路径
     * @param packageClass 响应包装类
     * @param apiKey       apiKey
     * @param queryParams  查询参数
     * @return list of T
     */
    private <T> List<T> commonFetchData(String apiName, String path, Class<? extends OkxApiResponse<T>> packageClass,
                                        ApiKey apiKey, String queryParams) {
        String keyId = apiKey != null ? String.valueOf(apiKey.getKeyId()) : "public";
        try {
            String response = sendSignedRequest(apiKey, "GET", path
                    + (StringUtils.hasText(queryParams) ? ("?" + queryParams) : ""), "");
            OkxApiResponse<T> apiResponse = JsonUtils.parseTo(response, packageClass);
            if (null == apiResponse || !"0".equals(apiResponse.getCode())) {
                log.debug("查询" + apiName + " / " + queryParams + " 失败 apiKey:" + keyId + ", response: " + response);
                return Collections.emptyList();
            }
            return CollectionUtils.isEmpty(apiResponse.getData()) ? Collections.emptyList() : apiResponse.getData();
        } catch (Exception e) {
            log.warn("查询" + apiName + " / " + queryParams + " 失败 apiKey:" + keyId + " error: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * 发送签名的HTTP请求到OKX API
     *
     * @param apiKey      API密钥信息
     * @param method      HTTP方法 (GET, POST, DELETE)
     * @param path        API路径 (不包含域名)
     * @param requestBody 请求体 (GET请求时为空字符串)
     * @return API响应的JSON字符串
     * @throws Exception 请求发送失败或响应解析失败
     */
    private String sendSignedRequest(ApiKey apiKey, String method, String path, String requestBody) throws Exception {
        // 限流检查：如果apiKey不为null，则进行限流控制
        if (null != apiKey) {
            acquireRateLimit(apiKey.getKeyId());
        }

        String url = OKX_BASE_URL + path;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json");
        if (null != apiKey) {
            builder.header("OK-ACCESS-KEY", apiKey.getAccessKey());
            // 添加签名
            String timestamp = okxSignatureService.getCurrentTimestamp();
            String signPath = path.replace(OKX_BASE_URL, "");
            String signature = okxSignatureService.sign(
                    timestamp, method, signPath, requestBody, apiKey.getSecretKey()
            );
            builder.header("OK-ACCESS-SIGN", signature)
                    .header("OK-ACCESS-TIMESTAMP", timestamp)
                    .header("OK-ACCESS-PASSPHRASE", apiKey.getPassPhrase());

            if (!Objects.equals(true, apiKey.getIsLiveTrading())) {
                builder.header("x-simulated-trading", "1");
            }
        }
        // 根据不同的HTTP方法设置请求
        switch (method.toUpperCase()) {
            case "GET":
                builder.GET();
                break;
            case "POST":
                builder.POST(HttpRequest.BodyPublishers.ofString(requestBody));
                break;
            case "DELETE":
                builder.DELETE();
                break;
            default:
                throw new IllegalArgumentException("不支持的HTTP方法: " + method);
        }
        HttpRequest request = builder.build();
        log.debug("发送OKX API请求 - URL: {}, Method: {}, Body: {}", url, method, requestBody);
        HttpClient currentClient = resolveHttpClientForOkx();
        // 发送请求并获取响应
        HttpResponse<String> response = currentClient.send(request, HttpResponse.BodyHandlers.ofString());
        // 记录响应
        log.debug("收到OKX API响应 - Status: {}, Body: {}", response.statusCode(), response.body());
        if (response.statusCode() != 200) {
            throw new RuntimeException("API请求失败 - 状态码: " + response.statusCode() + ", 响应: " + response.body());
        }
        return response.body();
    }

    private HttpClient resolveHttpClientForOkx() {
        return cexProxyBindingService.getActiveBindingByCex(CexExchange.OKX.getCode())
                .map(binding -> proxyHttpClients.computeIfAbsent(binding.getProxyId(), this::buildProxyHttpClient))
                .orElse(httpClient);
    }

    private HttpClient buildProxyHttpClient(Long proxyId) {
        var proxyConfig = proxyServiceConfigService.getProxyConfigById(proxyId);
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .proxy(ProxySelector.of(new InetSocketAddress(proxyConfig.getServerHost(), proxyConfig.getServerPort())))
                .build();
    }

    /**
     * 获取账户余额(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成OKX类型到CEX通用类型的转换
     * </p>
     *
     * @param apiKey API密钥信息
     * @param ccy    币种
     * @return CEX通用账户余额列表
     */
    @Override
    public List<CexAccountBalance> getAccountBalance(ApiKey apiKey, String ccy) {
        // 调用OKX API获取账户余额
        List<OkxAccountBalance> okxBalances = getAccountBalanceFromOkx(apiKey, ccy);

        // 将OKX账户余额转换为CEX通用账户余额
        return CexAccountBalanceAdapter.adapt(okxBalances);
    }

    /**
     * 获取账户余额(OKX原生接口)
     * <p>
     * 内部方法,用于OKX原生账户余额查询逻辑
     * </p>
     *
     * @param apiKey API密钥信息
     * @param ccy    币种
     * @return OKX账户余额列表
     */
    private List<OkxAccountBalance> getAccountBalanceFromOkx(ApiKey apiKey, String ccy) {
        String queryParams = StringUtils.hasText(ccy) ? String.format("ccy=%s", ccy) : "";
        return commonFetchData("账户余额", API_PATH_GET_ACCOUNT_BALANCE, OkxAccountBalanceResponse.class,
                apiKey, queryParams);
    }

    /**
     * 查询持仓信息(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成OKX类型到CEX通用类型的转换
     * </p>
     *
     * @param apiKey   API密钥信息
     * @param instType 产品类型
     * @return CEX通用持仓信息列表
     */
    @Override
    public List<CexPosition> getPositions(ApiKey apiKey, String instType) {
        // 调用OKX API获取持仓信息
        String queryParams = null != instType ? "instType=" + instType : "";
        List<OkxPosition> okxPositions = commonFetchData("持仓信息", API_PATH_GET_ACCOUNT_POSITIONS, OkxPositionResponse.class,
                apiKey, queryParams);
        // 将OKX持仓转换为CEX通用持仓
        return CexPositionAdapter.adapt(okxPositions);
    }

    /**
     * 查询持仓信息(OKX原生接口)
     * <p>
     * 内部方法,用于OKX原生持仓查询逻辑
     * </p>
     *
     * @param apiKey   API密钥信息
     * @param instType 产品类型
     * @return OKX持仓信息列表
     */
    private List<OkxPosition> getPositionsFromOkx(ApiKey apiKey, String instType) {
        String queryParams = null != instType ? "instType=" + instType : "";
        return commonFetchData("持仓信息", API_PATH_GET_ACCOUNT_POSITIONS, OkxPositionResponse.class,
                apiKey, queryParams);
    }

    /**
     * 获取SKU信息数据(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成OKX类型到CEX通用类型的转换
     * </p>
     *
     * @param apiKey   API密钥信息
     * @param instType 产品类型
     * @param instId   产品ID
     * @return CEX通用SKU信息列表
     */
    @Override
    public List<CexInstrument> getInstruments(ApiKey apiKey, String instType, String instId) {
        // 调用OKX API获取SKU信息
        List<OkxInstrumentInfo> okxInstruments = getInstrumentsFromOkx(apiKey, instType, instId);

        // 将OKX SKU信息转换为CEX通用SKU信息
        return CexInstrumentAdapter.adapt(okxInstruments);
    }

    /**
     * 获取合约信息(OKX原始方法)
     * <p>
     * 此方法返回OKX特定类型,仅供内部适配使用。
     * 外部调用方应使用 {@link #getInstruments(ApiKey, String, String)} 获取通用CEX类型。
     * </p>
     */
    private List<OkxInstrumentInfo> getInstrumentsFromOkx(ApiKey apiKey, String instType, String instId) {
        String queryParams = String.format("instType=%s&instId=%s", StringUtils.hasText(instType) ? instType : "",
                StringUtils.hasText(instId) ? instId : "");
        return commonFetchData("获取产品Info", API_PATH_GET_INSTRUMENTS, OkxInstrumentInfoResponse.class,
                apiKey, queryParams);
    }

    /**
     * 下单(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成CEX通用类型到OKX类型的转换
     * </p>
     *
     * @param apiKey  API密钥信息
     * @param request CEX通用下单请求
     * @return CEX通用下单响应
     * @throws Exception 下单失败
     */
    @Override
    public CexOrderResponse placeOrder(ApiKey apiKey, CexPlaceOrderRequest request) throws Exception {
        // 将CEX通用请求转换为OKX请求
        PlaceOrderReq okxRequest = CexRequestAdapter.toOkxPlaceOrderRequest(request);

        // 调用OKX API下单
        OkxAlgoState.OkxAlgoStateResponse okxResponse = placeOrderToOkx(apiKey, okxRequest);

        // 将OKX响应转换为CEX通用响应
        return CexResponseAdapter.toCexOrderResponse(okxResponse);
    }

    /**
     * 下单(OKX原生接口)
     * <p>
     * 内部方法,用于OKX原生下单逻辑
     * </p>
     *
     * @param apiKey  API密钥信息
     * @param request OKX下单请求
     * @return OKX下单响应
     */
    private OkxAlgoState.OkxAlgoStateResponse placeOrderToOkx(ApiKey apiKey, PlaceOrderReq request) {
        LocalDateTime callTime = LocalDateTime.now();
        String requestParams = JsonUtils.toJsonString(request);
        String instId = null != request ? request.getInstId() : null;

        try {
            log.debug("下单 - API Key ID: {}, Order Data: {}", apiKey.getKeyId(), request);
            String response = sendSignedRequest(apiKey, "POST", API_PATH_PLACE_ORDER, requestParams);
            OkxAlgoState.OkxAlgoStateResponse apiResponse = JsonUtils.parseTo(response, OkxAlgoState.OkxAlgoStateResponse.class);

            LocalDateTime responseTime = LocalDateTime.now();
            long durationMs = Duration.between(callTime, responseTime).toMillis();

            // 提取ordId
            String orderId = null;
            if (null != apiResponse && !CollectionUtils.isEmpty(apiResponse.getData())) {
                OkxAlgoState algoState = apiResponse.getData().get(0);
                if (null != algoState) {
                    orderId = algoState.getOrdId();
                }
            }

            // 发布成功事件
            publishSuccessEventWithKeyId(CexApiType.PLACE_ORDER, requestParams, callTime, responseTime,
                    durationMs, orderId, instId, response, apiKey.getKeyId());

            return apiResponse;
        } catch (Exception e) {
            // 发布失败事件
            LocalDateTime responseTime = LocalDateTime.now();
            long durationMs = Duration.between(callTime, responseTime).toMillis();
            publishFailedEventWithKeyId(CexApiType.PLACE_ORDER, requestParams, callTime, responseTime,
                    durationMs, instId, "placeOrder fail! " + e.getMessage(), apiKey.getKeyId());

            log.error("下单失败: " + e.getMessage());
            return OkxAlgoState.OkxAlgoStateResponse.fail("下单失败: " + e.getMessage());
        }
    }

    /**
     * 下单
     *
     * @param apiKey    API密钥信息
     * @param orderData 订单数据JSON字符串
     * @return 下单响应DTO
     * @throws Exception API调用失败
     */
    private OkxOrderResponseData placeOrder(ApiKey apiKey, String orderData) throws Exception {
        LocalDateTime callTime = LocalDateTime.now();
        String instId = extractInstIdFromJson(orderData);

        try {
            log.debug("下单 - API Key ID: {}, Order Data: {}", apiKey.getKeyId(), orderData);
            String response = sendSignedRequest(apiKey, "POST", API_PATH_PLACE_ORDER, orderData);
            JsonNode rootNode = ResponseValidator.safeParseJson(response);

            if (null == rootNode || !"0".equals(rootNode.path("code").asText())) {
                String errorMsg = "下单失败: " + ResponseValidator.extractErrorMessage(response);

                // 发布失败事件
                LocalDateTime responseTime = LocalDateTime.now();
                long durationMs = Duration.between(callTime, responseTime).toMillis();
                publishFailedEvent(CexApiType.PLACE_ORDER, orderData, callTime, responseTime,
                        durationMs, instId, errorMsg);

                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            JsonNode dataNode = rootNode.path("data");
            if (dataNode.isArray() && !dataNode.isEmpty()) {
                OkxOrderResponseData responseData = JsonUtils.transform(dataNode.get(0), OkxOrderResponseData.class);

                // 发布成功事件
                LocalDateTime responseTime = LocalDateTime.now();
                long durationMs = Duration.between(callTime, responseTime).toMillis();
                publishSuccessEvent(CexApiType.PLACE_ORDER, orderData, callTime, responseTime,
                        durationMs, responseData.getOrdId(), instId, response);

                return responseData;
            }

            // 发布失败事件
            LocalDateTime responseTime = LocalDateTime.now();
            long durationMs = Duration.between(callTime, responseTime).toMillis();
            publishFailedEvent(CexApiType.PLACE_ORDER, orderData, callTime, responseTime,
                    durationMs, instId, "下单响应格式错误");
            throw new RuntimeException("下单响应格式错误");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // 发布失败事件
            LocalDateTime responseTime = LocalDateTime.now();
            long durationMs = Duration.between(callTime, responseTime).toMillis();
            publishFailedEvent(CexApiType.PLACE_ORDER, orderData, callTime, responseTime,
                    durationMs, instId, "placeOrder exception: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 撤单(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成CEX通用类型到OKX类型的转换
     * </p>
     *
     * @param apiKey  API密钥信息
     * @param request CEX通用撤单请求
     * @return CEX通用操作响应
     */
    @Override
    public CexOperationResponse cancelOrder(ApiKey apiKey, CexCancelOrderRequest request) {
        // 将CEX通用请求转换为OKX请求
        OkxOrderReq okxRequest = CexRequestAdapter.toOkxCancelOrderRequest(request);

        // 调用OKX API撤单
        OkxAlgoState.OkxAlgoStateResponse okxResponse = cancelOrderToOkx(apiKey, okxRequest);

        // 将OKX响应转换为CEX通用响应
        return CexResponseAdapter.toCexOperationResponse(okxResponse);
    }

    /**
     * 撤单(OKX原生接口)
     * <p>
     * 内部方法,用于OKX原生撤单逻辑
     * </p>
     *
     * @param apiKey API密钥信息
     * @param req    OKX撤单请求数据
     * @return OKX撤单响应DTO
     */
    private OkxAlgoState.OkxAlgoStateResponse cancelOrderToOkx(ApiKey apiKey, OkxOrderReq req) {
        LocalDateTime callTime = LocalDateTime.now();
        String requestParams = JsonUtils.toJsonString(req);
        String instId = null != req ? req.getInstId() : null;

        OkxAlgoState.OkxAlgoStateResponse apiResponse;
        try {
            log.debug("撤单 - API Key ID: {}, Order ID: {}, Inst ID: {}", apiKey.getKeyId(), req.getOrdId(), req.getInstId());
            String response = sendSignedRequest(apiKey, "POST", API_PATH_CANCEL_ORDER, requestParams);
            apiResponse = JsonUtils.parseTo(response, OkxAlgoState.OkxAlgoStateResponse.class);

            LocalDateTime responseTime = LocalDateTime.now();
            long durationMs = Duration.between(callTime, responseTime).toMillis();

            // 提取ordId
            String orderId = null;
            if (null != apiResponse && !CollectionUtils.isEmpty(apiResponse.getData())) {
                OkxAlgoState algoState = apiResponse.getData().get(0);
                if (null != algoState) {
                    orderId = algoState.getOrdId();
                }
            }

            // 发布成功事件
            publishSuccessEventWithKeyId(CexApiType.CANCEL_ORDER, requestParams, callTime, responseTime,
                    durationMs, orderId, instId, response, apiKey.getKeyId());

        } catch (Exception e) {
            // 发布失败事件
            LocalDateTime responseTime = LocalDateTime.now();
            long durationMs = Duration.between(callTime, responseTime).toMillis();
            publishFailedEventWithKeyId(CexApiType.CANCEL_ORDER, requestParams, callTime, responseTime,
                    durationMs, instId, "cancelOrder fail! " + e.getMessage(), apiKey.getKeyId());

            log.error("cancelOrder fail!" + e.getMessage());
            throw new RuntimeException("cancelOrder fail!" + e.getMessage());
        }

        return apiResponse;
    }

    /**
     * 平仓(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成CEX通用类型到OKX类型的转换
     * </p>
     *
     * @param apiKey  API密钥信息
     * @param request CEX通用平仓请求
     * @return CEX通用操作响应
     */
    @Override
    public CexOperationResponse closePosition(ApiKey apiKey, CexClosePositionRequest request) {
        // 将CEX通用请求转换为OKX请求
        OkxClosePositionRequest okxRequest = CexRequestAdapter.toOkxClosePositionRequest(request);

        // 调用OKX API平仓
        OkxApiResponse<Void> okxResponse = closePositionToOkx(apiKey, okxRequest);

        // 将OKX响应转换为CEX通用响应
        return CexResponseAdapter.toCexOperationResponse(okxResponse);
    }

    /**
     * 平仓(OKX原生接口)
     * <p>
     * 内部方法,用于OKX原生平仓逻辑
     * </p>
     *
     * @param apiKey  API密钥信息
     * @param request OKX平仓请求数据
     * @return OKX平仓响应
     */
    private OkxApiResponse<Void> closePositionToOkx(ApiKey apiKey, OkxClosePositionRequest request) {
        LocalDateTime callTime = LocalDateTime.now();
        String requestParams = JsonUtils.toJsonString(request);
        String instId = extractInstIdFromClosePositionRequest(request);

        try {
            String response = sendSignedRequest(apiKey, "POST", API_PATH_CLOSE_POSITION, requestParams);
            //noinspection unchecked
            OkxApiResponse<Void> apiResponse = (OkxApiResponse<Void>) JsonUtils.parseTo(response, OkxApiResponse.class);

            LocalDateTime responseTime = LocalDateTime.now();
            long durationMs = Duration.between(callTime, responseTime).toMillis();

            // 发布成功事件
            publishSuccessEventWithKeyId(CexApiType.CLOSE_POSITION, requestParams, callTime, responseTime,
                    durationMs, null, instId, response, apiKey.getKeyId());

            return apiResponse;
        } catch (Exception e) {
            // 发布失败事件
            LocalDateTime responseTime = LocalDateTime.now();
            long durationMs = Duration.between(callTime, responseTime).toMillis();
            publishFailedEventWithKeyId(CexApiType.CLOSE_POSITION, requestParams, callTime, responseTime,
                    durationMs, instId, "closePosition fail! " + e.getMessage(), apiKey.getKeyId());

            log.error("closePosition fail !" + e.getMessage());
            return OkxApiResponse.fail("closePosition fail! " + e.getMessage());
        }
    }

    /**
     * 设置策略订单(OKX原生接口)
     * <p>
     * 内部方法,用于OKX原生策略订单创建逻辑。
     * </p>
     *
     * @param apiKey API密钥信息
     * @return 算法订单响应数据
     */
    private OkxAlgoState.OkxAlgoStateResponse setAlgoOrderToOkx(ApiKey apiKey, OkxAlgoRequest request) {
        LocalDateTime callTime = LocalDateTime.now();
        String requestParams = JsonUtils.toJsonString(request);
        String instId = null != request ? request.getInstId() : null;

        try {
            String requestBody = JsonUtils.toJsonString(request);
            log.debug("设置策略订单 - API Key ID: {}, Algo Order: {}", apiKey.getKeyId(), requestBody);
            String response = sendSignedRequest(apiKey, "POST", API_PATH_SET_ALGO_ORDER, requestBody);
            OkxAlgoState.OkxAlgoStateResponse apiResponse = JsonUtils.parseTo(response, OkxAlgoState.OkxAlgoStateResponse.class);

            LocalDateTime responseTime = LocalDateTime.now();
            long durationMs = Duration.between(callTime, responseTime).toMillis();

            // 提取algoId
            String algoId = extractAlgoIdFromSetResponse(apiResponse);

            // 发布成功事件
            publishSuccessEvent(CexApiType.SET_ALGO_ORDER, requestParams, callTime, responseTime,
                    durationMs, algoId, instId, response);

            return apiResponse;
        } catch (Exception e) {
            // 发布失败事件
            LocalDateTime responseTime = LocalDateTime.now();
            long durationMs = Duration.between(callTime, responseTime).toMillis();
            publishFailedEvent(CexApiType.SET_ALGO_ORDER, requestParams, callTime, responseTime,
                    durationMs, instId, "setAlgoOrder fail ! " + e.getMessage());

            return OkxAlgoState.OkxAlgoStateResponse.fail("setAlgoOrder fail ! " + e.getMessage());
        }
    }

    /**
     * 修改策略订单(OKX原生接口)
     * <p>
     * 内部方法,用于OKX原生策略订单修改逻辑。
     * </p>
     *
     * @param apiKey  API密钥信息
     * @param request 修改请求数据
     * @return 修改算法订单响应数据
     */
    private OkxAlgoState.OkxAlgoStateResponse amendAlgoOrdersToOkx(ApiKey apiKey, OkxAmendAlgoRequest request) {
        LocalDateTime callTime = LocalDateTime.now();
        String requestParams = JsonUtils.toJsonString(request);
        String instId = null != request ? request.getInstId() : null;
        String algoId = null != request ? request.getAlgoId() : null;

        try {
            String requestBody = JsonUtils.toJsonString(request);
            log.debug("修改算法订单 - API Key ID: {}, Request: {}", apiKey.getKeyId(), requestBody);
            String response = sendSignedRequest(apiKey, "POST", API_PATH_AMEND_ALGOS, requestBody);
            OkxAlgoState.OkxAlgoStateResponse apiResponse = JsonUtils.parseTo(response, OkxAlgoState.OkxAlgoStateResponse.class);

            LocalDateTime responseTime = LocalDateTime.now();
            long durationMs = Duration.between(callTime, responseTime).toMillis();

            // 从响应中提取algoId
            String responseAlgoId = extractAlgoIdFromAmendResponse(apiResponse);

            // 发布成功事件
            publishSuccessEvent(CexApiType.AMEND_ALGO_ORDER, requestParams, callTime, responseTime,
                    durationMs, responseAlgoId, instId, response);

            return apiResponse;
        } catch (Exception e) {
            // 发布失败事件
            LocalDateTime responseTime = LocalDateTime.now();
            long durationMs = Duration.between(callTime, responseTime).toMillis();
            publishFailedEvent(CexApiType.AMEND_ALGO_ORDER, requestParams, callTime, responseTime,
                    durationMs, instId, "amendAlgoOrders fail ! " + e.getMessage());

            return OkxAlgoState.OkxAlgoStateResponse.fail("amendAlgoOrders fail ! " + e.getMessage());
        }
    }

    /**
     * 撤销策略订单(OKX原生接口)
     * <p>
     * 内部方法,用于OKX原生策略订单取消逻辑。
     * </p>
     *
     * @param apiKey            API密钥信息
     * @param algoCancelRequest 算法订单ID
     * @return 撤销算法订单响应数据
     * @throws Exception API调用失败
     */
    private OkxOperationResponseData cancelAlgoOrderToOkx(ApiKey apiKey, OkxAlgoCancelRequest algoCancelRequest) throws Exception {
        LocalDateTime callTime = LocalDateTime.now();
        String requestParams = JsonUtils.toJsonString(List.of(algoCancelRequest));
        String instId = algoCancelRequest.getInstId();
        String algoId = algoCancelRequest.getAlgoId();

        String response;
        OkxOperationResponseData responseData;
        try {
            log.debug("撤销算法订单 - API Key ID: {}, Algo ID: {}", apiKey.getKeyId(), algoCancelRequest);
            response = sendSignedRequest(apiKey, "POST", API_PATH_CANCEL_ALGOS, requestParams);
            responseData = JsonUtils.parseTo(response, OkxOperationResponseData.class);

            LocalDateTime responseTime = LocalDateTime.now();
            long durationMs = Duration.between(callTime, responseTime).toMillis();

            // 检查是否成功
            if (null == responseData || !"0".equals(responseData.getCode())) {
                // 发布失败事件
                String errorMsg = "撤销算法订单失败: " + ResponseValidator.extractErrorMessage(response);
                publishFailedEvent(CexApiType.CANCEL_ALGO_ORDER, requestParams, callTime, responseTime,
                        durationMs, instId, errorMsg);

                // 抛出异常（保持原有行为）
                throw new RuntimeException(errorMsg);
            }

            // 发布成功事件
            publishSuccessEvent(CexApiType.CANCEL_ALGO_ORDER, requestParams, callTime, responseTime,
                    durationMs, algoId, instId, response);

        } catch (RuntimeException e) {
            // 重新抛出业务异常
            throw e;
        } catch (Exception e) {
            // 发布失败事件
            LocalDateTime responseTime = LocalDateTime.now();
            long durationMs = Duration.between(callTime, responseTime).toMillis();
            publishFailedEvent(CexApiType.CANCEL_ALGO_ORDER, requestParams, callTime, responseTime,
                    durationMs, instId, "cancelAlgoOrder exception: " + e.getMessage());
            throw e;
        }

        return responseData;
    }

    /**
     * 获取标记价格(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成OKX类型到CEX通用类型的转换
     * </p>
     *
     * @param apiKey apiKey
     * @param instId 产品ID
     * @return CEX通用标记价格数据列表
     */
    @Override
    public List<CexMarkPrice> getMarkPrice(ApiKey apiKey, String instId) {
        // 调用OKX API获取标记价格
        List<OkxMarkPrice> okxMarkPrices = getMarkPriceFromOkx(apiKey, instId);

        // 将OKX标记价格转换为CEX通用标记价格
        return CexMarkPriceAdapter.adapt(okxMarkPrices);
    }

    /**
     * 获取标记价格(OKX原生接口)
     * <p>
     * 内部方法,用于OKX原生标记价格查询逻辑
     * 此方法返回OKX特定类型,仅供内部适配使用。
     * 外部调用方应使用 {@link #getMarkPrice(ApiKey, String)} 获取通用CEX类型。
     * </p>
     *
     * @param apiKey apiKey
     * @param instId 产品ID
     * @return OKX标记价格数据列表
     */
    private List<OkxMarkPrice> getMarkPriceFromOkx(ApiKey apiKey, String instId) {
        String queryParams = "instId=" + instId;
        return commonFetchData("标记价格", API_PATH_GET_MARK_PRICE, OkxMarkPriceResponse.class, apiKey, queryParams);
    }

    /**
     * 获取市场Tickers(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成OKX类型到CEX通用类型的转换
     * </p>
     *
     * @param apiKey   apiKey
     * @param instType 产品类型
     * @param instId   产品ID
     * @return CEX通用市场Ticker数据列表
     */
    @Override
    public List<CexMarketTicker> getMarketTickers(ApiKey apiKey, String instType, String instId) {
        // 调用OKX API获取市场Tickers
        List<OkxMarketTicker> okxTickers = getMarketTickersFromOkx(apiKey, instType, instId);

        // 将OKX市场Tickers转换为CEX通用市场Tickers
        return CexMarketTickerAdapter.adapt(okxTickers);
    }

    /**
     * 获取市场Tickers(OKX原生接口)
     * <p>
     * 内部方法,用于OKX原生市场Tickers查询逻辑
     * 此方法返回OKX特定类型,仅供内部适配使用。
     * 外部调用方应使用 {@link #getMarketTickers(ApiKey, String, String)} 获取通用CEX类型。
     * </p>
     *
     * @param apiKey   apiKey
     * @param instType 产品类型
     * @param instId   产品ID
     * @return OKX市场Ticker数据列表
     */
    private List<OkxMarketTicker> getMarketTickersFromOkx(ApiKey apiKey, String instType, String instId) {
        String queryParams = "instType=" + instType;
        return commonFetchData("市场Tickers", API_PATH_GET_MARKET_TICKERS, OkxMarketTickerResponse.class, apiKey, queryParams)
                .stream()
                .filter(okxMarketTicker -> !StringUtils.hasText(instId) || instId.equals(okxMarketTicker.getInstId()))
                .collect(Collectors.toList());
    }

    /**
     * 获取资金费率(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成OKX类型到CEX通用类型的转换
     * </p>
     *
     * @param apiKey 对应的apiKey
     * @param instId 产品ID
     * @return CEX通用资金费率数据列表
     */
    @Override
    public List<CexFundingRate> getFundingRate(ApiKey apiKey, String instId) {
        // 调用OKX API获取资金费率
        List<OkxFundingRateData> okxFundingRates = getFundingRateFromOkx(apiKey, instId);

        // 将OKX资金费率转换为CEX通用资金费率
        return CexFundingRateAdapter.adapt(okxFundingRates);
    }

    /**
     * 获取资金费率(OKX原生接口)
     * <p>
     * 内部方法,用于OKX原生资金费率查询逻辑
     * 此方法返回OKX特定类型,仅供内部适配使用。
     * 外部调用方应使用 {@link #getFundingRate(ApiKey, String)} 获取通用CEX类型。
     * </p>
     *
     * @param apiKey 对应的apiKey
     * @param instId 产品ID
     * @return OKX资金费率数据列表
     */
    private List<OkxFundingRateData> getFundingRateFromOkx(ApiKey apiKey, String instId) {
        String queryParams = "instId=" + instId;
        return commonFetchData("资金费率", API_PATH_GET_FUNDING_RATE, OkxFundingRateResponse.class, apiKey, queryParams);
    }

    /**
     * 查询待成交订单(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成OKX类型到CEX通用类型的转换
     * </p>
     *
     * @param apiKey   API密钥
     * @param instType 产品类型 (SPOT, MARGIN, SWAP, FUTURES, OPTION)
     * @param instId   产品ID，如 "BTC-USDT-SWAP"
     * @return 通用CEX待成交订单数据列表
     */
    @Override
    public List<CexOrder> getPendingOrders(ApiKey apiKey, String instType, String instId) {
        // 调用OKX API获取待成交订单
        List<OkxOrder> okxOrders = getPendingOrdersFromOkx(apiKey, instType, instId);

        // 将OKX订单转换为CEX通用订单
        return CexOrderAdapter.adapt(okxOrders);
    }

    /**
     * 查询待成交订单(OKX原生接口)
     * <p>
     * 内部方法,用于OKX原生待成交订单查询逻辑
     * </p>
     *
     * @param apiKey   API密钥
     * @param instType 产品类型 (SPOT, MARGIN, SWAP, FUTURES, OPTION)
     * @param instId   产品ID，如 "BTC-USDT-SWAP"
     * @return OKX待成交订单数据列表
     */
    private List<OkxOrder> getPendingOrdersFromOkx(ApiKey apiKey, String instType, String instId) {
        String queryParams = String.format("instType=%s&instId=%s", StringUtils.hasText(instType) ? instType : "",
                StringUtils.hasText(instId) ? instId : "");
        return commonFetchData("待成交订单", API_PATH_GET_ORDERS_PENDING, OkxOrderPendingResponse.class, apiKey, queryParams);
    }

    /**
     * 查询历史订单(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成OKX类型到CEX通用类型的转换
     * </p>
     *
     * @param apiKey   API密钥
     * @param instType 产品类型 (SPOT, MARGIN, SWAP, FUTURES, OPTION)
     * @param instId   产品ID (可选)
     * @param state    订单状态 (可选)
     * @return 通用CEX历史订单列表
     */
    @Override
    public List<CexOrder> getHistoryOrders(ApiKey apiKey, String instType, String instId, String state) {
        // 调用OKX API获取历史订单
        List<OkxOrder> okxOrders = getHistoryOrdersFromOkx(apiKey, instType, instId, state);

        // 将OKX订单转换为CEX通用订单
        return CexOrderAdapter.adapt(okxOrders);
    }

    /**
     * 查询历史订单(最近7天)(OKX原生接口)
     * <p>
     * 内部方法,用于OKX原生历史订单查询逻辑
     * </p>
     *
     * @param apiKey   API密钥
     * @param instType 产品类型 (SPOT, MARGIN, SWAP, FUTURES, OPTION)
     * @param instId   产品ID (可选)
     * @param state    订单状态 (可选)
     * @return OKX历史订单列表
     */
    private List<OkxOrder> getHistoryOrdersFromOkx(ApiKey apiKey, String instType, String instId, String state) {
        StringBuilder queryParams = new StringBuilder();
        queryParams.append("instType=").append(instType);
        if (StringUtils.hasText(instId)) {
            queryParams.append("&instId=").append(instId);
        }
        if (StringUtils.hasText(state)) {
            queryParams.append("&state=").append(state);
        }

        return commonFetchData("历史订单", API_PATH_GET_ORDERS_HISTORY, OkxOrderPendingResponse.class, apiKey, queryParams.toString());
    }

    /**
     * 查询归档历史订单 (最近3个月)
     *
     * @param apiKey   API密钥
     * @param instType 产品类型 (SPOT, MARGIN, SWAP, FUTURES, OPTION)
     * @param instId   产品ID (可选)
     * @param state    订单状态 (可选)
     * @return 历史订单列表
     */
    private List<OkxOrder> getHistoryOrdersArchive(ApiKey apiKey, String instType, String instId, String state) {
        StringBuilder queryParams = new StringBuilder();
        queryParams.append("instType=").append(instType);
        if (StringUtils.hasText(instId)) {
            queryParams.append("&instId=").append(instId);
        }
        if (StringUtils.hasText(state)) {
            queryParams.append("&state=").append(state);
        }

        return commonFetchData("归档历史订单", API_PATH_GET_ORDERS_HISTORY_ARCHIVE, OkxOrderPendingResponse.class, apiKey, queryParams.toString());
    }

    /**
     * 获取历史持仓(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成OKX类型到CEX通用类型的转换
     * </p>
     *
     * @param apiKey   API密钥信息
     * @param instType 产品类型
     * @param instId   产品ID
     * @param after    请求此时间戳之前的数据
     * @param before   请求此时间戳之后的数据
     * @param limit    返回结果数量
     * @return CEX通用历史持仓列表
     */
    @Override
    public List<CexPosition> getPositionsHistory(ApiKey apiKey, String instType, String instId, String after,
                                                 String before, Integer limit) {
        // 调用OKX API获取历史持仓
        List<OkxPosition> okxPositions = getPositionsHistoryFromOkx(apiKey, instType, instId, after, before, limit);

        // 将OKX持仓转换为CEX通用持仓
        return CexPositionAdapter.adapt(okxPositions);
    }

    /**
     * 获取历史持仓(OKX原生接口)
     * <p>
     * 内部方法,用于OKX原生历史持仓查询逻辑
     * </p>
     *
     * @param apiKey   API密钥信息
     * @param instType 产品类型
     * @param instId   产品ID
     * @param after    请求此时间戳之前的数据
     * @param before   请求此时间戳之后的数据
     * @param limit    返回结果数量
     * @return OKX历史持仓列表
     */
    private List<OkxPosition> getPositionsHistoryFromOkx(ApiKey apiKey, String instType, String instId, String after,
                                                         String before, Integer limit) {
        StringBuilder queryParams = new StringBuilder();
        queryParams.append("instType=").append(instType);
        if (StringUtils.hasText(instId)) {
            queryParams.append("&instId=").append(instId);
        }
        if (StringUtils.hasText(after)) {
            queryParams.append("&after=").append(after);
        }
        if (StringUtils.hasText(before)) {
            queryParams.append("&before=").append(before);
        }
        if (null != limit) {
            queryParams.append("&limit=").append(limit);
        }
        return commonFetchData("历史持仓", API_PATH_GET_POSITIONS_HISTORY, OkxPositionResponse.class, apiKey,
                queryParams.toString());
    }

    /**
     * 查询算法订单(OKX原生接口)
     * <p>
     * 内部方法,用于OKX原生算法订单查询逻辑。
     * </p>
     *
     * @param apiKey      API密钥
     * @param instType    产品类型 (SPOT, MARGIN, SWAP, FUTURES, OPTION)
     * @param instId      产品ID，如 "BTC-USDT-SWAP"
     * @param algoId      算法订单ID
     * @param algoClOrdId 客户端自定义算法订单ID
     * @return 算法订单数据列表
     */
    private List<OkxAlgoOrder> getAlgoOrdersFromOkx(ApiKey apiKey, String instType, String instId, String algoId, String algoClOrdId) {
        String queryParams = String.format("ordType=conditional,oco&instType=%s&instId=%s&algoId=%s&algoClOrdId=%s",
                StringUtils.hasText(instType) ? instType : "", StringUtils.hasText(instId) ? instId : "",
                StringUtils.hasText(algoId) ? algoId : "", StringUtils.hasText(algoClOrdId) ? algoClOrdId : "");
        return commonFetchData("算法订单", API_PATH_GET_ORDERS_ALGO_PENDING, OkxAlgoOrderResponse.class,
                apiKey, queryParams);
    }

    /**
     * 获取市场K线数据(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成OKX类型到CEX通用类型的转换
     * </p>
     *
     * @param apiKey API密钥
     * @param instId 产品ID
     * @param period K线周期
     * @param limit  返回数据数量
     * @return CEX通用市场K线数据列表
     */
    @Override
    public List<CexMarketCandle> getMarketCandles(ApiKey apiKey, String instId, String period, Integer limit) {
        // 调用OKX API获取市场K线数据
        List<OkxMarketCandle> okxCandles = getMarketCandlesFromOkx(apiKey, instId, period, limit);
        // 将OKX市场K线转换为CEX通用市场K线
        return CexMarketCandleAdapter.adapt(okxCandles);
    }

    /**
     * 获取市场K线数据(OKX原生接口)
     * <p>
     * 内部方法,用于OKX原生市场K线查询逻辑
     * 此方法返回OKX特定类型,仅供内部适配使用。
     * 外部调用方应使用 {@link #getMarketCandles(ApiKey, String, String, Integer)} 获取通用CEX类型。
     * </p>
     *
     * @param apiKey API密钥
     * @param instId 产品ID
     * @param period K线周期
     * @param limit  返回数据数量
     * @return OKX市场K线数据列表
     */
    private List<OkxMarketCandle> getMarketCandlesFromOkx(ApiKey apiKey, String instId, String period, Integer limit) {
        return getMarketCandlesFromOkx(apiKey, instId, period, limit, null);
    }

    private List<OkxMarketCandle> getMarketCandlesFromOkx(ApiKey apiKey, String instId, String period, Integer limit,
                                                          Long startMills) {
        return getMarketCandlesFromOkx(apiKey, instId, period, limit, startMills, API_PATH_GET_MARKET_CANDLES);
    }

    private List<OkxMarketCandle> getMarketHistoryCandlesFromOkx(ApiKey apiKey, String instId, String period, Integer limit,
                                                                 Long startMills) {
        return getMarketCandlesFromOkx(apiKey, instId, period, limit, startMills, API_PATH_GET_MARKET_HISTORY_CANDLES);
    }


    /**
     * 获取市场K线数据(OKX原生接口，支持指定开始时间)
     * <p>
     * 内部方法,用于OKX原生市场K线查询逻辑
     * 此方法返回OKX特定类型,仅供内部适配使用。
     * 外部调用方应使用 {@link #getMarketCandles(ApiKey, String, String, Integer, Long)} 获取通用CEX类型。
     * </p>
     *
     * @param apiKey     API密钥
     * @param instId     产品ID
     * @param period     K线周期
     * @param limit      返回数据数量
     * @param startMills 开始时间戳(毫秒)，可选
     * @return OKX市场K线数据列表
     */
    private List<OkxMarketCandle> getMarketCandlesFromOkx(ApiKey apiKey, String instId, String period, Integer limit,
                                                          Long startMills, String path) {
        if (StringUtils.hasText(period)) {
            period = "1H".equalsIgnoreCase(period) ? "1H" : period;
            period = "1D".equalsIgnoreCase(period) ? "1D" : period;
            period = "4H".equalsIgnoreCase(period) ? "4H" : period;
            period = period.matches("\\d+[mM]") ? period.toLowerCase() : period;
        }

        StringBuilder queryParams = new StringBuilder();
        queryParams.append(String.format("instId=%s&bar=%s&limit=%s", instId, StringUtils.hasText(period) ? period : "",
                null == limit ? "" : String.valueOf(limit)));

        // 如果提供了开始时间戳，转换为endMills并调用OKX API
        // OKX的after参数是往前取数据的（返回时间戳小于after值的数据）
        // 所以我们需要将startMills转换为endMills：endMills = startMills + timeframe * limit
        // 这样OKX会返回从startMills开始的limit根K线
        if (startMills != null && startMills > 0) {
            // 计算timeframe对应的毫秒数
            long timeframeMs = getTimeframeInMs(period);

            // 计算endMills
            long endMills = startMills + timeframeMs * limit;

            queryParams.append("&after=").append(endMills);
            log.debug("[OkxApiService] startMills转换：startMills={}, timeframe={}, timeframeMs={}, limit={}, endMills={}",
                    startMills, period, timeframeMs, limit, endMills);
        }

        List<List<String>> candles = commonFetchData(API_PATH_GET_MARKET_CANDLES.equals(path) ? "市场K线" : "市场K线-历史", path, OkxMarketCandleResponse.class,
                apiKey, queryParams.toString());
        return CollectionUtils.isEmpty(candles) ? Collections.emptyList()
                : candles.stream()
                .map(OkxMarketCandle::from)
                .collect(Collectors.toList());
    }


    /**
     * 将timeframe字符串转换为毫秒数
     */
    private long getTimeframeInMs(String period) {
        if (period == null || period.isEmpty()) {
            return 60 * 1000; // 默认1分钟
        }

        switch (period.toUpperCase()) {
            case "1M":
                return 60 * 1000;
            case "3M":
                return 3 * 60 * 1000;
            case "5M":
                return 5 * 60 * 1000;
            case "15M":
                return 15 * 60 * 1000;
            case "30M":
                return 30 * 60 * 1000;
            case "1H":
                return 60 * 60 * 1000;
            case "2H":
                return 2 * 60 * 60 * 1000;
            case "4H":
                return 4 * 60 * 60 * 1000;
            case "6H":
                return 6 * 60 * 60 * 1000;
            case "12H":
                return 12 * 60 * 60 * 1000;
            case "1D":
                return 24 * 60 * 60 * 1000;
            case "3D":
                return 3 * 24 * 60 * 60 * 1000;
            case "1W":
                return 7 * 24 * 60 * 60 * 1000;
            case "1MO":
            case "1MON":
                return 30L * 24 * 60 * 60 * 1000;
            default:
                // 尝试解析数字+m/H/D等格式
                if (period.matches("\\d+[mM]")) {
                    int minutes = Integer.parseInt(period.substring(0, period.length() - 1));
                    return (long) minutes * 60 * 1000;
                } else if (period.matches("\\d+[hH]")) {
                    int hours = Integer.parseInt(period.substring(0, period.length() - 1));
                    return (long) hours * 60 * 60 * 1000;
                } else if (period.matches("\\d+[dD]")) {
                    int days = Integer.parseInt(period.substring(0, period.length() - 1));
                    return (long) days * 24 * 60 * 60 * 1000;
                }
                return 60 * 1000; // 默认1分钟
        }
    }

    @Override
    public List<CexMarketCandle> getMarketCandles(ApiKey apiKey, String instId, String period, Integer limit, Long startMills) {
        // 调用OKX API获取市场K线数据
        List<OkxMarketCandle> okxCandles = getMarketCandlesFromOkx(apiKey, instId, period, limit, startMills);
        if (CollectionUtils.isEmpty(okxCandles)) {
            okxCandles = getMarketHistoryCandlesFromOkx(apiKey, instId, period, limit, startMills);
        }
        // 将OKX市场K线转换为CEX通用市场K线
        return CexMarketCandleAdapter.adapt(okxCandles);
    }

    // ========== CEX API调用事件发布辅助方法 ==========

    /**
     * 发布成功的API调用事件(带apiKeyId)
     *
     * @param apiType       API类型
     * @param requestParams 请求参数
     * @param callTime      调用时间
     * @param responseTime  响应时间
     * @param durationMs    耗时（毫秒）
     * @param orderId       订单ID
     * @param instId        合约代码
     * @param responseBody  响应体
     * @param apiKeyId      API Key ID
     */
    private void publishSuccessEventWithKeyId(CexApiType apiType, String requestParams,
                                              LocalDateTime callTime, LocalDateTime responseTime, Long durationMs,
                                              String orderId, String instId, String responseBody, Long apiKeyId) {
        CexApiCallEvent event = CexApiCallEvent.success(
                apiType,
                CexExchange.OKX,
                CexHttpMethod.POST,
                getApiPathByType(apiType),
                requestParams,
                callTime,
                responseTime,
                durationMs,
                orderId,
                instId,
                200,
                responseBody,
                apiKeyId
        );
        eventPublisher.publishEvent(event);
    }

    /**
     * 发布失败的API调用事件(带apiKeyId)
     *
     * @param apiType       API类型
     * @param requestParams 请求参数
     * @param callTime      调用时间
     * @param responseTime  响应时间
     * @param durationMs    耗时（毫秒）
     * @param instId        合约代码
     * @param errorMessage  错误信息
     * @param apiKeyId      API Key ID
     */
    private void publishFailedEventWithKeyId(CexApiType apiType, String requestParams,
                                             LocalDateTime callTime, LocalDateTime responseTime, Long durationMs,
                                             String instId, String errorMessage, Long apiKeyId) {
        CexApiCallEvent event = CexApiCallEvent.failed(
                apiType,
                CexExchange.OKX,
                CexHttpMethod.POST,
                getApiPathByType(apiType),
                requestParams,
                callTime,
                responseTime,
                durationMs,
                instId,
                errorMessage,
                apiKeyId
        );
        eventPublisher.publishEvent(event);
    }

    /**
     * 发布成功的API调用事件
     *
     * @param apiType       API类型
     * @param requestParams 请求参数
     * @param callTime      调用时间
     * @param responseTime  响应时间
     * @param durationMs    耗时（毫秒）
     * @param orderId       订单ID
     * @param instId        合约代码
     * @param responseBody  响应体
     */
    private void publishSuccessEvent(CexApiType apiType, String requestParams,
                                     LocalDateTime callTime, LocalDateTime responseTime, Long durationMs,
                                     String orderId, String instId, String responseBody) {
        CexApiCallEvent event = CexApiCallEvent.success(
                apiType,
                CexExchange.OKX,
                CexHttpMethod.POST,
                getApiPathByType(apiType),
                requestParams,
                callTime,
                responseTime,
                durationMs,
                orderId,
                instId,
                200,
                responseBody,
                null  // apiKeyId为null,保持向后兼容
        );
        eventPublisher.publishEvent(event);
    }

    /**
     * 发布失败的API调用事件
     *
     * @param apiType       API类型
     * @param requestParams 请求参数
     * @param callTime      调用时间
     * @param responseTime  响应时间
     * @param durationMs    耗时（毫秒）
     * @param instId        合约代码
     * @param errorMessage  错误信息
     */
    private void publishFailedEvent(CexApiType apiType, String requestParams,
                                    LocalDateTime callTime, LocalDateTime responseTime, Long durationMs,
                                    String instId, String errorMessage) {
        CexApiCallEvent event = CexApiCallEvent.failed(
                apiType,
                CexExchange.OKX,
                CexHttpMethod.POST,
                getApiPathByType(apiType),
                requestParams,
                callTime,
                responseTime,
                durationMs,
                instId,
                errorMessage,
                null  // apiKeyId为null,保持向后兼容
        );
        eventPublisher.publishEvent(event);
    }

    /**
     * 根据API类型获取API路径
     *
     * @param apiType API类型
     * @return API路径
     */
    private String getApiPathByType(CexApiType apiType) {
        return switch (apiType) {
            case PLACE_ORDER -> API_PATH_PLACE_ORDER;
            case CANCEL_ORDER, CANCEL -> API_PATH_CANCEL_ORDER;
            case CLOSE_POSITION -> API_PATH_CLOSE_POSITION;
            case SET_ALGO_ORDER -> API_PATH_SET_ALGO_ORDER;
            case AMEND_ALGO_ORDER -> API_PATH_AMEND_ALGOS;
            case CANCEL_ALGO_ORDER -> API_PATH_CANCEL_ALGOS;
            default -> throw new IllegalArgumentException("Unknown API type: " + apiType);
        };
    }

    /**
     * 从JSON字符串中提取instId
     *
     * @param jsonStr JSON字符串
     * @return instId
     */
    private String extractInstIdFromJson(String jsonStr) {
        if (null == jsonStr || jsonStr.isEmpty()) {
            return null;
        }
        try {
            JsonNode rootNode = ResponseValidator.safeParseJson(jsonStr);
            if (null != rootNode && rootNode.has("instId")) {
                return rootNode.path("instId").asText();
            }
        } catch (Exception e) {
            log.warn("从JSON中提取instId失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从平仓请求中提取instId
     *
     * @param request 平仓请求对象
     * @return instId
     */
    private String extractInstIdFromClosePositionRequest(OkxClosePositionRequest request) {
        if (null == request) {
            return null;
        }
        try {
            return request.getInstId();
        } catch (Exception e) {
            log.warn("从平仓请求中提取instId失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从设置策略订单响应中提取algoId
     *
     * @param response API响应
     * @return algoId
     */
    private String extractAlgoIdFromSetResponse(OkxAlgoState.OkxAlgoStateResponse response) {
        if (null != response && !CollectionUtils.isEmpty(response.getData())) {
            OkxAlgoState algoState = response.getData().get(0);
            if (null != algoState) {
                return algoState.getAlgoId();
            }
        }
        return null;
    }

    /**
     * 从修改策略订单响应中提取algoId
     *
     * @param response API响应
     * @return algoId
     */
    private String extractAlgoIdFromAmendResponse(OkxAlgoState.OkxAlgoStateResponse response) {
        if (null != response && !CollectionUtils.isEmpty(response.getData())) {
            OkxAlgoState algoState = response.getData().get(0);
            if (null != algoState) {
                return algoState.getAlgoId();
            }
        }
        return null;
    }

    // ==================== 算法订单相关 (通用CEX接口实现) ====================

    /**
     * 创建算法订单(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成通用类型到OKX类型的转换。
     * </p>
     *
     * @param apiKey  API密钥信息
     * @param request 通用CEX算法订单创建请求
     * @return 通用CEX算法订单操作响应
     */
    @Override
    public CexAlgoOrderOperationResponse setAlgoOrder(ApiKey apiKey, CexAlgoOrderRequest request) {
        // 1. 转换为OKX请求
        OkxAlgoRequest okxRequest = CexRequestAdapter.toOkxAlgoOrderRequest(request);

        // 2. 调用OKX API
        OkxAlgoState.OkxAlgoStateResponse okxResponse = setAlgoOrderToOkx(apiKey, okxRequest);

        // 3. 转换为通用响应
        return CexAlgoOrderAdapter.toOperationResponse(okxResponse);
    }

    /**
     * 修改算法订单(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成通用类型到OKX类型的转换。
     * </p>
     *
     * @param apiKey  API密钥信息
     * @param request 通用CEX算法订单修改请求
     * @return 通用CEX算法订单操作响应
     */
    @Override
    public CexAlgoOrderOperationResponse amendAlgoOrder(ApiKey apiKey, CexAmendAlgoOrderRequest request) {
        // 1. 转换为OKX请求
        OkxAmendAlgoRequest okxRequest = CexRequestAdapter.toOkxAmendAlgoOrderRequest(request);

        // 2. 调用OKX API
        OkxAlgoState.OkxAlgoStateResponse okxResponse = amendAlgoOrdersToOkx(apiKey, okxRequest);

        // 3. 转换为通用响应
        return CexAlgoOrderAdapter.toOperationResponse(okxResponse);
    }

    /**
     * 取消算法订单(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成通用类型到OKX类型的转换。
     * </p>
     *
     * @param apiKey  API密钥信息
     * @param request 通用CEX算法订单取消请求
     * @return 通用CEX算法订单操作响应
     */
    @Override
    public CexAlgoOrderOperationResponse cancelAlgoOrder(ApiKey apiKey, CexCancelAlgoOrderRequest request) {
        // 1. 转换为OKX请求
        OkxAlgoCancelRequest okxRequest = CexRequestAdapter.toOkxCancelAlgoOrderRequest(request);

        try {
            // 2. 调用OKX API
            OkxOperationResponseData okxResponse = cancelAlgoOrderToOkx(apiKey, okxRequest);

            // 3. 转换为通用响应
            return CexAlgoOrderOperationResponse.builder()
                    .algoId(okxRequest.getAlgoId())
                    .success("0".equals(okxResponse.getCode()))
                    .errorCode(okxResponse.getCode())
                    .errorMessage(okxResponse.getMsg())
                    .timestamp(System.currentTimeMillis())
                    .build();
        } catch (Exception e) {
            log.error("取消算法订单失败: {}", e.getMessage(), e);
            return CexAlgoOrderOperationResponse.builder()
                    .algoId(okxRequest.getAlgoId())
                    .success(false)
                    .errorCode("ERROR")
                    .errorMessage("取消算法订单失败: " + e.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build();
        }
    }

    /**
     * 获取算法订单列表(通用CEX接口)
     * <p>
     * 实现CexApiService接口方法,内部完成OKX类型到通用CEX类型的转换。
     * </p>
     *
     * @param apiKey            API密钥信息
     * @param instType          产品类型 (SPOT/SWAP/FUTURES/OPTION)
     * @param instId            产品ID (如 BTC-USDT-SWAP)
     * @param algoId            算法订单ID (可选)
     * @param clientAlgoOrderId 客户端自定义算法订单ID (可选)
     * @return 通用CEX算法订单响应
     */
    @Override
    public CexAlgoOrderResponse getAlgoOrders(ApiKey apiKey, String instType, String instId,
                                              String algoId, String clientAlgoOrderId) {
        // 1. 调用OKX API获取算法订单
        List<OkxAlgoOrder> okxAlgoOrders = getAlgoOrdersFromOkx(apiKey, instType, instId, algoId, clientAlgoOrderId);

        // 2. 转换为通用算法订单
        List<CexAlgoOrder> cexAlgoOrders = CexAlgoOrderAdapter.adapt(okxAlgoOrders);

        // 3. 构建响应
        return CexAlgoOrderResponse.builder()
                .algoOrders(cexAlgoOrders)
                .success(true)
                .timestamp(System.currentTimeMillis())
                .build();
    }

}

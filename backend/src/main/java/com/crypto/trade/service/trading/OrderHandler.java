package com.crypto.trade.service.trading;

import com.crypto.trade.config.AiTradingRiskControlConfig;
import com.crypto.trade.dto.HistoryOrdersPageRequest;
import com.crypto.trade.dto.OrderRequest;
import com.crypto.trade.dto.cex.okx.OkxAlgoState;
import com.crypto.trade.dto.cex.okx.PlaceOrderReq;
import com.crypto.trade.dto.cex.request.CexCancelOrderRequest;
import com.crypto.trade.dto.cex.request.CexPlaceOrderRequest;
import com.crypto.trade.dto.cex.response.CexOperationResponse;
import com.crypto.trade.dto.cex.response.CexOrderResponse;
import com.crypto.trade.dto.common.PagedResponse;
import com.crypto.trade.dto.common.TradingResult;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.CexTradingOrder;
import com.crypto.trade.entity.RiskControlOrder;
import com.crypto.trade.entity.TradingOrder;
import com.crypto.trade.repository.CexTradingOrderRepository;
import com.crypto.trade.repository.TradingOrderRepository;
import com.crypto.trade.service.RiskControlOrderService;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * OrderHandler
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class OrderHandler {

    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    TradingOrderRepository tradingOrderRepository;
    @Autowired
    CexTradingOrderRepository cexTradingOrderRepository;
    @Autowired
    RiskControlOrderService riskControlOrderService;
    @Autowired
    UnifiedCexApiService unifiedCexApiService;
    @Autowired
    AiTradingRiskControlConfig aiTradingRiskControlConfig;

    /**
     * 下单交易
     *
     * @param orderRequest 订单请求
     * @return 交易结果
     */
    @Transactional
    public TradingResult placeOrder(OrderRequest orderRequest) {
        try {
            log.info("=== OrderHandler开始下单流程 ===");

            // 1. 风控模式检查（允许绕过）
            if (!orderRequest.isBypassRiskControl() && aiTradingRiskControlConfig.isManualMode()) {
                log.debug("当前为手动风控模式，订单进入风控审核流程");
                return createRiskControlOrder(orderRequest);
            }

            // 2. 获取API Key信息
            ApiKey apiKey = apiKeyService.getDecryptedKey(orderRequest.getApiKeyId());
            if (apiKey == null) {
                log.error("API Key不存在 - ID: {}", orderRequest.getApiKeyId());
                return TradingResult.failure("API Key不存在");
            }

            // 3. 创建TradingOrder(业务意图) - 先创建系统订单
            TradingOrder tradingOrder = createTradingOrder(orderRequest);
            log.info("系统订单创建成功 - UUID: {}, 状态: pending", tradingOrder.getOrderUuid());

            // 4. 构建通用CEX订单请求(包含clOrdId)
            CexPlaceOrderRequest cexPlaceOrderRequest = buildCexPlaceOrderRequest(orderRequest, tradingOrder.getOrderUuid());

            // 5. 调用通用CEX交易API下单 - 再执行交易
            CexOrderResponse cexOrderResponse = unifiedCexApiService.placeOrder(apiKey, cexPlaceOrderRequest);

            // 6. 转换API响应为TradingResult
            TradingResult tradingResult = convertToTradingResult(cexOrderResponse);
            if (!tradingResult.success) {
                log.error("OKX API下单失败 - 错误信息: {}", tradingResult.message);
                // 标记TradingOrder为失败
                markTradingOrderAsFailed(tradingOrder, tradingResult.message);
                return TradingResult.failure(tradingResult.message);
            }

            // 7. 创建CexTradingOrder(执行结果) - 记录CEX订单
            CexTradingOrder cexOrder = createCexTradingOrder(orderRequest, tradingResult.orderId, tradingOrder.getOrderUuid());

            // 8. 关联并更新TradingOrder状态
            linkAndUpdateOrders(tradingOrder, cexOrder);

            log.info("=== OrderHandler下单流程完成 ===");
            return TradingResult.success(tradingOrder.getOrderUuid(), "下单成功");

        } catch (Exception e) {
            log.error("OrderHandler下单失败", e);
            return TradingResult.failure("下单失败: " + e.getMessage());
        }
    }

    /**
     * 撤单
     *
     * @param apiKeyId API Key ID
     * @param instId   合约ID
     * @param orderId  订单ID
     * @return 撤单结果
     */
    @Transactional
    public TradingResult cancelOrder(Long apiKeyId, String instId, String orderId) {
        try {
            log.debug("开始撤单 - API Key: {}, 订单ID: {}, 合约品种: {}", apiKeyId, orderId, instId);

            // 1. 获取API Key信息
            ApiKey apiKey = apiKeyService.getDecryptedKey(apiKeyId);
            if (apiKey == null) {
                return TradingResult.failure("API Key不存在");
            }

            // 2. 构建通用CEX撤单请求
            CexCancelOrderRequest cexCancelOrderRequest = CexCancelOrderRequest.builder()
                    .symbol(instId)
                    .orderId(orderId)
                    .build();

            // 3. 调用通用CEX交易API撤单
            CexOperationResponse cexOperationResponse = unifiedCexApiService.cancelOrder(apiKey, cexCancelOrderRequest);

            if (!cexOperationResponse.getSuccess()) {
                return TradingResult.failure(cexOperationResponse.getErrorMessage());
            }

            log.debug("撤单成功 - 订单ID: {}", orderId);
            return TradingResult.success(orderId, "撤单成功");

        } catch (Exception e) {
            log.error("撤单失败 - 订单ID: {}", orderId, e);
            return TradingResult.failure("撤单失败: " + e.getMessage());
        }
    }

    /**
     * 创建风控订单
     */
    private TradingResult createRiskControlOrder(OrderRequest orderRequest) {
        try {
            RiskControlOrder riskOrder = riskControlOrderService.createRiskOrder(orderRequest);
            log.debug("风控审核订单创建成功 - 订单ID: {}, 原始订单ID: {}",
                    riskOrder.getOrderId(), riskOrder.getOriginalOrderId());
            return TradingResult.success(riskOrder.getOriginalOrderId(),
                    "订单已进入风控审核流程，请等待审核", true);
        } catch (Exception e) {
            log.error("创建风控审核订单失败", e);
            return TradingResult.failure("创建风控审核订单失败: " + e.getMessage());
        }
    }

    /**
     * 构建通用CEX下单请求
     *
     * @param orderRequest 订单请求
     * @param clOrdId      客户自定义订单ID(TradingOrder.orderUuid)
     */
    private CexPlaceOrderRequest buildCexPlaceOrderRequest(OrderRequest orderRequest, String clOrdId) {
        // 使用 toPlainString() 避免科学计数法,确保精度不丢失
        String quantityStr = orderRequest.getSz() != null ? orderRequest.getSz().toPlainString() : null;

        CexPlaceOrderRequest request = CexPlaceOrderRequest.builder()
                .symbol(orderRequest.getInstId())
                .tradeMode("isolated") // 逐仓模式
                .currency("USDT")
                .clientOrderId(clOrdId) // 设置客户自定义订单ID
                .side(orderRequest.getSide())
                .orderType(orderRequest.getOrderType())
                .quantity(quantityStr)
                .leverage(orderRequest.getLever().toString())
                .build();

        // 设置持仓方向（智能默认值）
        String posSide = determinePositionSide(orderRequest);
        request.setPositionSide(posSide);

        // 设置限价单价格
        if ("limit".equals(orderRequest.getOrderType()) && orderRequest.getPx() != null) {
            request.setPrice(orderRequest.getPx().toString());
        }

        // 设置止盈止损参数（仅在开仓单时设置）
        boolean isClose = ("buy".equalsIgnoreCase(orderRequest.getSide()) && "short".equalsIgnoreCase(orderRequest.getPosSide()))
                || ("sell".equalsIgnoreCase(orderRequest.getSide()) && "long".equalsIgnoreCase(orderRequest.getPosSide()));

        if (!isClose) {
            // 开仓单：设置止盈止损参数
            if (orderRequest.getTakeProfitPrice() != null) {
                request.setTakeProfitPrice(orderRequest.getTakeProfitPrice());
            }
            if (orderRequest.getStopLossPrice() != null) {
                request.setStopLossPrice(orderRequest.getStopLossPrice());
            }
        } else {
            // 平仓单不需要杠杆
            request.setLeverage(null);
            // 添加平仓数量验证日志
            log.info("【平仓数量检查】instId: {}, sz: {}, sz(plain): {}",
                    orderRequest.getInstId(),
                    orderRequest.getSz(),
                    quantityStr);
        }

        return request;
    }

    /**
     * 构建PlaceOrderReq请求(OKX特定,保留用于算法订单)
     *
     * @param orderRequest 订单请求
     * @param clOrdId      客户自定义订单ID(TradingOrder.orderUuid)
     */
    private PlaceOrderReq buildPlaceOrderRequest(OrderRequest orderRequest, String clOrdId) {
        PlaceOrderReq placeOrderReq = PlaceOrderReq.builder()
                .instId(orderRequest.getInstId())
                .tdMode("isolated") // 逐仓模式
                .ccy("USDT")
                .clOrdId(clOrdId) // 设置客户自定义订单ID
                .side(orderRequest.getSide())
                .ordType(orderRequest.getOrderType())
                .sz(orderRequest.getSz() != null ? orderRequest.getSz().toString() : null)
                .lever(orderRequest.getLever().toString())
                .build();

        // 设置持仓方向（智能默认值）
        String posSide = determinePositionSide(orderRequest);
        placeOrderReq.setPosSide(posSide);

        // 设置限价单价格
        if ("limit".equals(orderRequest.getOrderType()) && orderRequest.getPx() != null) {
            placeOrderReq.setPx(orderRequest.getPx().toString());
        }

        boolean isClose = ("buy".equalsIgnoreCase(orderRequest.getSide()) && "short".equalsIgnoreCase(orderRequest.getPosSide()))
                || ("sell".equalsIgnoreCase(orderRequest.getSide()) && "long".equalsIgnoreCase(orderRequest.getPosSide()));
        if (isClose) {
//            placeOrderReq.setCloseFraction("1");
            placeOrderReq.setReduceOnly(true);
            placeOrderReq.setLever(null);
        }

        // 设置止盈止损
        if (!isClose && (orderRequest.getTakeProfitPrice() != null || orderRequest.getStopLossPrice() != null)) {
            PlaceOrderReq.AlgoOrder algoOrder = buildAlgoOrder(orderRequest, posSide);
            placeOrderReq.setAttachAlgoOrds(List.of(algoOrder));
        }

        return placeOrderReq;
    }

    /**
     * 确定持仓方向
     */
    private String determinePositionSide(OrderRequest orderRequest) {
        if (orderRequest.getPosSide() != null && !orderRequest.getPosSide().trim().isEmpty()) {
            return orderRequest.getPosSide();
        } else {
            // 智能推断持仓方向
            return "buy".equals(orderRequest.getSide()) ? "long" : "short";
        }
    }

    /**
     * 构建算法订单（止盈止损）
     */
    private PlaceOrderReq.AlgoOrder buildAlgoOrder(OrderRequest orderRequest, String posSide) {
        PlaceOrderReq.AlgoOrder algoOrder = PlaceOrderReq.AlgoOrder.builder()
                .instId(orderRequest.getInstId())
                .side(orderRequest.getSide())
                .posSide(posSide)
                .tdMode("isolated")
                .closeFraction("1")
                .ordType("conditional")
                .cxlOnClosePos(true)
                .reduceOnly(true)
                .ccy("USDT")
                .build();

        if (orderRequest.getTakeProfitPrice() != null) {
            algoOrder.setTpOrdPx("-1");
            algoOrder.setTpTriggerPx(orderRequest.getTakeProfitPrice().toString());
        }

        if (orderRequest.getStopLossPrice() != null) {
            algoOrder.setSlOrdPx("-1");
            algoOrder.setSlTriggerPx(orderRequest.getStopLossPrice().toString());
        }

        return algoOrder;
    }

    /**
     * 将通用CEX订单响应转换为TradingResult
     *
     * @param cexOrderResponse CEX订单响应
     * @return 交易结果
     */
    private TradingResult convertToTradingResult(CexOrderResponse cexOrderResponse) {
        try {
            if (cexOrderResponse.getSuccess()) {
                // 成功响应，从订单列表中获取订单ID
                if (cexOrderResponse.getOrders() != null && !cexOrderResponse.getOrders().isEmpty()) {
                    String orderId = cexOrderResponse.getOrders().get(0).getOrderId();
                    return TradingResult.success(orderId, "下单成功");
                } else {
                    return TradingResult.success(null, "下单成功(无订单ID)");
                }
            } else {
                // 失败响应
                String errorMsg = cexOrderResponse.getErrorMessage();
                if (errorMsg == null || errorMsg.trim().isEmpty()) {
                    errorMsg = "未知错误";
                }
                return TradingResult.failure(errorMsg);
            }
        } catch (Exception e) {
            log.error("转换通用CEX响应失败: {}", e.getMessage(), e);
            return TradingResult.failure("响应转换失败: " + e.getMessage());
        }
    }

    /**
     * 将OkxAlgoStateResponse转换为TradingResult
     * 兼容两种失败格式：
     * 1. 系统异常：{code:"1", msg:"连接超时", data:null}
     * 2. 业务失败：{code:"51100", msg:"失败", data:[{sMsg:"余额不足"}]}
     */
    private TradingResult convertToTradingResult(OkxAlgoState.OkxAlgoStateResponse apiResponse) {
        try {
            if (apiResponse.isSuccess()) {
                // 成功响应，从data中获取订单信息
                OkxAlgoState orderData = apiResponse.getFirstData();
                if (orderData != null) {
                    return TradingResult.success(orderData.getOrdId(), apiResponse.getMsg());
                } else {
                    return TradingResult.success(null, apiResponse.getMsg());
                }
            } else {
                // 失败响应：安全提取错误信息
                String errorMsg = extractErrorMessage(apiResponse);
                return TradingResult.failure(errorMsg);
            }
        } catch (Exception e) {
            log.error("转换API响应失败: {}", e.getMessage(), e);
            return TradingResult.failure("API响应转换失败: " + e.getMessage());
        }
    }

    /**
     * 安全提取错误信息
     * 优先使用 data[0].sMsg（业务错误详情），降级使用 msg（系统错误）
     *
     * @param apiResponse API响应
     * @return 错误信息
     */
    private String extractErrorMessage(OkxAlgoState.OkxAlgoStateResponse apiResponse) {
        // 优先使用详细错误信息（业务失败，如余额不足）
        OkxAlgoState firstData = apiResponse.getFirstData();
        if (firstData != null && org.springframework.util.StringUtils.hasText(firstData.getSMsg())) {
            String detailMsg = firstData.getSMsg();
            // 如果详细错误与通用错误不同，组合显示
            if (!detailMsg.equals(apiResponse.getMsg())) {
                return apiResponse.getMsg() + " / " + detailMsg;
            }
            return detailMsg;
        }
        // 降级使用通用错误信息（系统异常，如网络超时）
        return apiResponse.getMsg();
    }

    /**
     * 创建TradingOrder(业务意图)
     *
     * @param orderRequest 订单请求
     * @return 创建的系统订单
     */
    private TradingOrder createTradingOrder(OrderRequest orderRequest) {
        TradingOrder systemOrder = new TradingOrder();
        systemOrder.setApiKeyId(orderRequest.getApiKeyId());
        systemOrder.setActionId(orderRequest.getActionId());   // ✅ 新增：设置TradeAction ID
        systemOrder.setRecordId(orderRequest.getRecordId());   // ✅ 新增：设置调用记录ID
        systemOrder.setSource(orderRequest.getSource());
        systemOrder.setInstId(orderRequest.getInstId());
        systemOrder.setSide(orderRequest.getSide());
        systemOrder.setPosSide(determinePositionSide(orderRequest));
        systemOrder.setOrderType(orderRequest.getOrderType());
        systemOrder.setLever(orderRequest.getLever());
        systemOrder.setAmt(null == orderRequest.getAmount() ? new BigDecimal(0) : orderRequest.getAmount());
        systemOrder.setSz(orderRequest.getSz());
        systemOrder.setTakeProfitEnabled(orderRequest.getTakeProfitPrice() != null);
        systemOrder.setStopLossEnabled(orderRequest.getStopLossPrice() != null);
        systemOrder.setTakeProfitPrice(orderRequest.getTakeProfitPrice());
        systemOrder.setStopLossPrice(orderRequest.getStopLossPrice());
        systemOrder.setTakeProfitPct(null); // 由前端直接计算价格
        systemOrder.setStopLossPct(null);
        systemOrder.setOrderStatus("pending"); // 初始状态为准备中
        systemOrder.setCreatedTime(LocalDateTime.now());

        // 保存系统订单(此时会触发@PrePersist生成orderUuid)
        TradingOrder savedSystemOrder = tradingOrderRepository.save(systemOrder);
        log.debug("系统订单创建成功 - UUID: {}, 状态: pending", savedSystemOrder.getOrderUuid());

        return savedSystemOrder;
    }

    /**
     * 创建CexTradingOrder(执行结果)
     *
     * @param orderRequest 订单请求
     * @param okxOrderId   OKX返回的订单ID
     * @param clOrdId      客户自定义订单ID
     * @return 创建的CEX订单
     */
    private CexTradingOrder createCexTradingOrder(OrderRequest orderRequest, String okxOrderId, String clOrdId) {
        // 检查是否已存在相同的CEX订单
        Optional<CexTradingOrder> existingCexOrder = cexTradingOrderRepository.findByOrderId(okxOrderId);
        if (existingCexOrder.isPresent()) {
            log.warn("发现重复的CEX订单ID: {}, 将返回已存在的订单", okxOrderId);
            return existingCexOrder.get();
        }

        String posSide = determinePositionSide(orderRequest);

        // 创建CexTradingOrder
        CexTradingOrder cexOrder = CexTradingOrder.builder()
                .orderId(okxOrderId)
                .clOrdId(clOrdId) // 设置客户自定义订单ID(TradingOrder.orderUuid)
                .exchange("okx")
                .apiKeyId(orderRequest.getApiKeyId())
                .actionId(orderRequest.getActionId()) // 设置来源TradeAction ID
                .recordId(orderRequest.getRecordId()) // ✅ 新增：设置来源调用记录ID
                .instId(orderRequest.getInstId())
                .side(orderRequest.getSide())
                .posSide(posSide)
                .orderType(orderRequest.getOrderType())
                .tdMode("isolated")
                .ccy("USDT")
                .sz(orderRequest.getSz())
                .px("limit".equals(orderRequest.getOrderType()) ? orderRequest.getPx() : null)
                .amt(orderRequest.getAmount())
                .orderState("live") // 初始状态为待成交
                .lever(orderRequest.getLever())
                .filledSz(BigDecimal.ZERO)
                .filledAmt(BigDecimal.ZERO)
                .fillRatio(BigDecimal.ZERO)
                .fee(BigDecimal.ZERO)
                .syncStatus("pending") // 修复：初始状态为pending（待同步）
                .syncRetryCount(0)
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .build();

        // 保存CEX订单
        CexTradingOrder savedCexOrder = cexTradingOrderRepository.save(cexOrder);
        log.debug("CEX订单保存成功 - CEX订单ID: {}, clOrdId: {}", savedCexOrder.getOrderId(), savedCexOrder.getClOrdId());

        return savedCexOrder;
    }

    /**
     * 关联并更新TradingOrder状态
     *
     * @param tradingOrder 系统订单
     * @param cexOrder     CEX订单
     */
    private void linkAndUpdateOrders(TradingOrder tradingOrder, CexTradingOrder cexOrder) {
        // 关联CEX订单
        tradingOrder.setCexOrderId(cexOrder.getOrderId());
        // 更新状态为已提交
        tradingOrder.setOrderStatus("submitted");
        tradingOrder.setSubmittedTime(LocalDateTime.now());
        tradingOrder.setUpdatedTime(LocalDateTime.now());

        // 保存更新
        tradingOrderRepository.save(tradingOrder);
        log.debug("系统订单更新成功 - UUID: {}, CEX订单ID: {}, 状态: submitted",
                tradingOrder.getOrderUuid(), cexOrder.getOrderId());
    }

    /**
     * 标记TradingOrder为失败
     *
     * @param tradingOrder 系统订单
     * @param errorMsg     错误信息
     */
    private void markTradingOrderAsFailed(TradingOrder tradingOrder, String errorMsg) {
        tradingOrder.setOrderStatus("failed");
        tradingOrder.setErrorMsg(errorMsg);
        tradingOrder.setUpdatedTime(LocalDateTime.now());
        tradingOrderRepository.save(tradingOrder);
        log.warn("系统订单标记为失败 - UUID: {}, 错误: {}", tradingOrder.getOrderUuid(), errorMsg);
    }

    /**
     * 保存订单记录到数据库
     * <p>
     * 重构说明：
     * - 同时创建TradingOrder(系统订单)和CexTradingOrder(CEX订单)
     * - TradingOrder记录用户意图和业务状态
     * - CexTradingOrder记录CEX订单详情
     * - 两者通过cexOrderId关联
     * </p>
     *
     * @param orderRequest 订单请求
     * @param okxOrderId   OKX返回的订单ID
     * @return 保存后的系统订单
     */
    @Transactional
    protected TradingOrder saveOrderRecord(OrderRequest orderRequest, String okxOrderId) {
        // 1. 检查是否已存在相同的CEX订单
        Optional<CexTradingOrder> existingCexOrder = cexTradingOrderRepository.findByOrderId(okxOrderId);
        if (existingCexOrder.isPresent()) {
            log.warn("发现重复的CEX订单ID: {}, 将跳过保存", okxOrderId);
            // 查找关联的系统订单
            Optional<TradingOrder> existingSystemOrder = tradingOrderRepository.findByCexOrderId(okxOrderId);
            if (existingSystemOrder.isPresent()) {
                return existingSystemOrder.get();
            }
        }

        // 2. 确定持仓方向
        String posSide = determinePositionSide(orderRequest);

        // 3. 创建CexTradingOrder(CEX订单)
        CexTradingOrder cexOrder = CexTradingOrder.builder()
                .orderId(okxOrderId)
                .exchange("okx")
                .apiKeyId(orderRequest.getApiKeyId())
                .actionId(orderRequest.getActionId()) // 设置来源TradeAction ID
                .recordId(orderRequest.getRecordId()) // ✅ 新增：设置来源调用记录ID
                .instId(orderRequest.getInstId())
                .side(orderRequest.getSide())
                .posSide(posSide)
                .orderType(orderRequest.getOrderType())
                .tdMode("isolated")
                .ccy("USDT")
                .sz(orderRequest.getSz())
                .px("limit".equals(orderRequest.getOrderType()) ? orderRequest.getPx() : null)
                .amt(orderRequest.getAmount())
                .orderState("live") // 初始状态为待成交
                .lever(orderRequest.getLever())
                .filledSz(BigDecimal.ZERO)
                .filledAmt(BigDecimal.ZERO)
                .fillRatio(BigDecimal.ZERO)
                .fee(BigDecimal.ZERO)
                .syncStatus("pending") // 修复：初始状态为pending（待同步）
                .syncRetryCount(0)
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .build();

        // 保存CEX订单
        CexTradingOrder savedCexOrder = cexTradingOrderRepository.save(cexOrder);
        log.debug("CEX订单保存成功 - CEX订单ID: {}", savedCexOrder.getOrderId());

        // 4. 创建TradingOrder(系统订单)
        TradingOrder systemOrder = new TradingOrder();
        systemOrder.setApiKeyId(orderRequest.getApiKeyId());
        systemOrder.setActionId(orderRequest.getActionId()); // 设置来源TradeAction ID
        systemOrder.setRecordId(orderRequest.getRecordId()); // 设置来源调用记录ID(冗余)
        systemOrder.setSource(orderRequest.getSource());
        systemOrder.setInstId(orderRequest.getInstId());
        systemOrder.setSide(orderRequest.getSide());
        systemOrder.setPosSide(posSide);
        systemOrder.setOrderType(orderRequest.getOrderType());
        systemOrder.setAmt(orderRequest.getAmount());
        systemOrder.setLever(orderRequest.getLever());
        systemOrder.setTakeProfitEnabled(orderRequest.getTakeProfitPrice() != null);
        systemOrder.setStopLossEnabled(orderRequest.getStopLossPrice() != null);
        systemOrder.setTakeProfitPrice(orderRequest.getTakeProfitPrice());
        systemOrder.setStopLossPrice(orderRequest.getStopLossPrice());
        systemOrder.setTakeProfitPct(null); // 由前端直接计算价格
        systemOrder.setStopLossPct(null);
        systemOrder.setOrderStatus("submitted"); // 已提交到CEX
        systemOrder.setCexOrderId(okxOrderId);
        systemOrder.setSubmittedTime(LocalDateTime.now());

        // 保存系统订单
        TradingOrder savedSystemOrder = tradingOrderRepository.save(systemOrder);
        log.debug("系统订单保存成功 - 系统订单UUID: {}, CEX订单ID: {}",
                savedSystemOrder.getOrderUuid(), savedCexOrder.getOrderId());

        return savedSystemOrder;
    }

    /**
     * 获取活跃订单列表
     *
     * @param apiKeyId API Key ID
     * @return 活跃订单列表
     */
    public List<TradingOrder> getActiveOrders(Long apiKeyId) {
        try {
            List<TradingOrder> orders = tradingOrderRepository.findActiveOrdersByApiKeyId(apiKeyId);
            log.debug("获取活跃订单 - API Key: {}, 数量: {}", apiKeyId, orders.size());
            return orders;
        } catch (Exception e) {
            log.error("获取活跃订单失败 - API Key: {}", apiKeyId, e);
            throw new RuntimeException("获取活跃订单失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取历史订单列表
     *
     * @param apiKeyId API Key ID
     * @param days     查询天数
     * @return 历史订单列表
     */
    public List<TradingOrder> getHistoryOrders(Long apiKeyId, Integer days) {
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(days);
            List<TradingOrder> orders = tradingOrderRepository.findHistoryOrdersByApiKeyId(apiKeyId, since);
            log.debug("获取历史订单 - API Key: {}, 天数: {}, 数量: {}", apiKeyId, days, orders.size());
            return orders;
        } catch (Exception e) {
            log.error("获取历史订单失败 - API Key: {}, 天数: {}", apiKeyId, days, e);
            throw new RuntimeException("获取历史订单失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取历史订单列表(分页)
     *
     * @param request 分页查询请求参数
     * @return 分页结果, 包含data(订单列表)和total(总数)
     */
    public PagedResponse<TradingOrder> getHistoryOrdersPage(HistoryOrdersPageRequest request) {
        try {
            // 设置默认值
            Integer days = request.getDays();
            if (null == days || days <= 0) {
                days = 7;
            }

            Integer page = request.getPage();
            if (null == page || page < 0) {
                page = 0;
            }

            Integer size = request.getSize();
            if (null == size || size <= 0) {
                size = 20;
            }

            LocalDateTime since = LocalDateTime.now().minusDays(days);

            // 使用Spring Data的Pageable进行分页查询
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdTime"));

            Page<TradingOrder> pageResult = tradingOrderRepository.findHistoryOrdersByApiKeyIdPage(
                    request.getApiKeyId(), since, pageable);

            PagedResponse<TradingOrder> result = PagedResponse.<TradingOrder>builder()
                    .data(pageResult.getContent())
                    .total(pageResult.getTotalElements())
                    .page(page)
                    .size(size)
                    .build();

            log.debug("获取历史订单(分页) - API Key: {}, 天数: {}, 页码: {}, 大小: {}, 总数: {}",
                    request.getApiKeyId(), days, page, size, pageResult.getTotalElements());

            return result;
        } catch (Exception e) {
            log.error("获取历史订单(分页)失败 - API Key: {}, 天数: {}, 页码: {}, 大小: {}",
                    request.getApiKeyId(), request.getDays(), request.getPage(), request.getSize(), e);
            throw new RuntimeException("获取历史订单失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据调用记录ID获取订单列表
     *
     * @param recordId 调用记录ID
     * @return 订单列表
     */
    public List<TradingOrder> getOrdersByRecordId(Long recordId) {
        try {
            List<TradingOrder> orders = tradingOrderRepository.findByRecordIdOrderByCreatedTimeDesc(recordId);
            log.debug("根据RecordId获取订单 - RecordId: {}, 数量: {}", recordId, orders.size());
            return orders;
        } catch (Exception e) {
            log.error("根据RecordId获取订单失败 - RecordId: {}", recordId, e);
            throw new RuntimeException("获取订单失败: " + e.getMessage(), e);
        }
    }
}
package com.crypto.trade.service;

import com.crypto.trade.dto.HistoryOrdersPageRequest;
import com.crypto.trade.dto.OrderRequest;
import com.crypto.trade.dto.common.PagedResponse;
import com.crypto.trade.dto.common.TradingResult;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.TradingOrder;
import com.crypto.trade.model.OrderModel;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.market.UnifiedPriceDataService;
import com.crypto.trade.service.trading.OrderHandler;
import com.crypto.trade.service.trading.PositionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * TradingOrderService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class TradingOrderService {

    @Autowired
    OrderHandler orderHandler;
    @Autowired
    PositionHandler positionHandler;
    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    @Qualifier("marketPriceService")
    UnifiedPriceDataService priceDataService;

    /**
     * 下单（优化版本）
     * <p>
     * 直接接收OrderRequest对象，避免13个参数的传递
     * 优化点：
     * 1. 减少参数传递复杂度
     * 2. sz已在Processor中计算好，无需再次计算
     * 3. 简化方法签名，提升可读性
     * </p>
     *
     * @param request 订单请求对象（已包含所有必要字段，包括sz）
     * @return 交易结果
     */
    public TradingResult placeOrder(OrderRequest request) {
        try {
            log.debug("=== TradingOrderService协调器开始下单流程（优化版本） ===");

            // 1. 验证订单请求
            TradingResult validationResult = validateOrderRequest(request);
            if (!validationResult.success) {
                return validationResult;
            }

            // 2. 委托给OrderHandler处理订单逻辑
            TradingResult result = orderHandler.placeOrder(request);

            log.debug("=== TradingOrderService协调器下单流程完成 ===");
            return result;

        } catch (Exception e) {
            log.error("TradingOrderService下单协调失败", e);
            return TradingResult.failure("下单协调失败: " + e.getMessage());
        }
    }

    /**
     * 下单交易（原版本 - 向后兼容）
     * <p>
     * 13个参数的版本，保留用于向后兼容
     * 内部会调用优化版本
     * </p>
     *
     * @param apiKeyId          API Key ID
     * @param instId            合约品种
     * @param side              方向 (buy/sell)
     * @param orderType         订单类型 (market/limit)
     * @param sz                委托数量（张）
     * @param amount            成本金额（USDT）
     * @param lever             杠杆倍数
     * @param px                委托价格（限价单使用）
     * @param takeProfitPrice   止盈价格
     * @param stopLossPrice     止损价格
     * @param posSide           持仓方向
     * @param source            订单来源
     * @param bypassRiskControl 是否绕过风控
     * @return 交易结果
     */
    public TradingResult placeOrder(Long apiKeyId, String instId, String side, String orderType, BigDecimal sz, BigDecimal amount,
                                    BigDecimal lever, BigDecimal px, BigDecimal takeProfitPrice, BigDecimal stopLossPrice,
                                    String posSide, String source, boolean bypassRiskControl) {
        try {
            log.debug("=== TradingOrderService协调器开始下单流程（兼容版本） ===");

            // 1. 构建订单请求对象
            OrderRequest orderRequest = buildOrderRequest(apiKeyId, instId, side, orderType, amount, lever, px, takeProfitPrice,
                    stopLossPrice, posSide, source, bypassRiskControl, sz);

            // 2. 调用优化版本
            return placeOrder(orderRequest);

        } catch (Exception e) {
            log.error("TradingOrderService下单协调失败", e);
            return TradingResult.failure("下单协调失败: " + e.getMessage());
        }
    }

    /**
     * 下单（向后兼容的重载方法）
     * 默认source为"web"
     */
    public TradingResult placeOrder(Long apiKeyId, String instId, String side, String orderType, BigDecimal amount,
                                    BigDecimal lever, BigDecimal px, BigDecimal takeProfitPrice, BigDecimal stopLossPrice,
                                    String posSide, boolean bypassRiskControl) {
        return placeOrder(apiKeyId, instId, side, orderType, null, amount, lever, px, takeProfitPrice, stopLossPrice,
                posSide, "web", bypassRiskControl);
    }

    /**
     * 撤单（委托给OrderHandler处理）
     *
     * @param apiKeyId API Key ID
     * @param instId   合约品种
     * @param orderId  订单ID
     * @return 撤单结果
     */
    public TradingResult cancelOrder(Long apiKeyId, String instId, String orderId) {
        return orderHandler.cancelOrder(apiKeyId, instId, orderId);
    }

    /**
     * 获取活跃订单列表（委托给OrderHandler处理）
     *
     * @param apiKeyId API Key ID
     * @return 活跃订单列表
     */
    public List<TradingOrder> getActiveOrders(Long apiKeyId) {
        return orderHandler.getActiveOrders(apiKeyId);
    }

    /**
     * 获取历史订单列表（委托给OrderHandler处理）
     *
     * @param apiKeyId API Key ID
     * @param days     查询天数
     * @return 历史订单列表
     */
    public List<TradingOrder> getHistoryOrders(Long apiKeyId, Integer days) {
        return orderHandler.getHistoryOrders(apiKeyId, days);
    }

    /**
     * 获取历史订单列表(分页)（委托给OrderHandler处理）
     *
     * @param request 分页查询请求参数
     * @return 分页结果, 包含data(订单列表)和total(总数)
     */
    public PagedResponse<TradingOrder> getHistoryOrdersPage(HistoryOrdersPageRequest request) {
        return orderHandler.getHistoryOrdersPage(request);
    }

    /**
     * 获取当前委托订单列表（支持缓存控制）
     *
     * @param apiKeyId API Key ID
     * @param useCache 是否使用缓存
     * @return 当前委托订单列表
     */
    public List<OrderModel> getPendingOrders(Long apiKeyId, boolean useCache) {
        return positionHandler.getPendingOrders(apiKeyId, useCache);
    }

    /**
     * 构建订单请求对象
     */
    private OrderRequest buildOrderRequest(Long apiKeyId, String instId, String side, String orderType, BigDecimal amount,
                                           BigDecimal lever, BigDecimal px, BigDecimal takeProfitPrice, BigDecimal stopLossPrice,
                                           String posSide, String source, boolean bypassRiskControl, BigDecimal sz) {
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setApiKeyId(apiKeyId);
        orderRequest.setInstId(instId);
        orderRequest.setSide(side);
        orderRequest.setOrderType(orderType);
        orderRequest.setAmount(amount);
        orderRequest.setLever(lever);
        orderRequest.setPx(px);
        orderRequest.setTakeProfitPrice(takeProfitPrice);
        orderRequest.setStopLossPrice(stopLossPrice);
        orderRequest.setPosSide(posSide);
        orderRequest.setSource(source);
        orderRequest.setBypassRiskControl(bypassRiskControl);
        orderRequest.setSz(sz);

        boolean isClose = ("buy".equalsIgnoreCase(side) && "short".equalsIgnoreCase(posSide))
                || ("sell".equalsIgnoreCase(side) && "long".equalsIgnoreCase(posSide));
        if (isClose || null == sz || sz.compareTo(BigDecimal.ZERO) == 0) {
            // 计算订单数量
            BigDecimal orderSize = calculateOrderSizeForRequest(orderRequest);
            orderRequest.setSz(orderSize);
        }
        return orderRequest;
    }

    /**
     * 为订单请求计算数量
     * <p>
     * 判断逻辑:
     * <ul>
     *   <li>如果amount < 1,判定为平仓订单(张数),直接返回amount</li>
     *   <li>如果amount >= 1,判定为开仓订单(成本金额),计算张数</li>
     * </ul>
     * </p>
     */
    private BigDecimal calculateOrderSizeForRequest(OrderRequest orderRequest) {
        try {
            BigDecimal amount = orderRequest.getAmount();
            // 是否平仓
            boolean isClose = ("buy".equalsIgnoreCase(orderRequest.getSide()) && "short".equalsIgnoreCase(orderRequest.getPosSide()))
                    || ("sell".equalsIgnoreCase(orderRequest.getSide()) && "long".equalsIgnoreCase(orderRequest.getPosSide()));

            // 【关键修改】判断是否为平仓订单
            // 平仓订单的特征: amount < 1 (通常张数是小数值)
            // 开仓订单的特征: amount >= 1 (成本金额通常是整数或大于1)
            if (!isClose && (amount != null && amount.compareTo(BigDecimal.ONE) < 0)) {
                // 平仓: amount直接作为张数,无需转换
                log.info("【平仓订单】直接使用amount作为张数 - instId: {}, amount(张数): {}",
                        orderRequest.getInstId(), amount);
                return amount;
            }

            // 开仓: 需要将成本金额转换为张数
            log.info("【开仓订单】计算张数 - instId: {}, amount(成本金额): {} USDT",
                    orderRequest.getInstId(), amount);

            // 获取价格信息
            ApiKey apiKey = apiKeyService.getDecryptedKey(orderRequest.getApiKeyId());
            BigDecimal currentPrice = priceDataService.getMarkPrice(apiKey, orderRequest.getInstId());

            if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) == 0) {
                // 对于市价单，使用保守的价格估算
                BigDecimal estimatedSize = amount.divide(new BigDecimal("50000"), 8, RoundingMode.HALF_UP);
                log.warn("无法获取当前价格,使用保守估算 - size: {}", estimatedSize);
                return estimatedSize;
            }


            // 确定订单价格
            BigDecimal orderPrice = ("limit".equals(orderRequest.getOrderType()) && orderRequest.getPx() != null)
                    ? orderRequest.getPx() : currentPrice;

            // 委托给PositionHandler计算张数
            BigDecimal orderSize = positionHandler.calculateOrderSize(orderRequest.getInstId(), amount, orderRequest.getLever(),
                    orderPrice, isClose, orderRequest.getSz());

            log.info("【开仓订单】张数计算完成 - 成本: {} USDT, 杠杆: {}, 价格: {}, 张数: {}",
                    amount, orderRequest.getLever(), orderPrice, orderSize);

            return orderSize;

        } catch (Exception e) {
            log.error("计算订单数量失败，使用保守估算", e);
            return orderRequest.getAmount().divide(new BigDecimal("50000"), 8, RoundingMode.HALF_UP);
        }
    }

    /**
     * 验证订单请求
     */
    private TradingResult validateOrderRequest(OrderRequest orderRequest) {
        // 基本参数验证
        if (orderRequest == null) {
            return TradingResult.failure("订单请求为空");
        }

        if (orderRequest.getApiKeyId() == null) {
            return TradingResult.failure("API Key ID不能为空");
        }

        if (!StringUtils.hasText(orderRequest.getInstId())) {
            return TradingResult.failure("合约品种不能为空");
        }

        if (!StringUtils.hasText(orderRequest.getSide())) {
            return TradingResult.failure("订单方向不能为空");
        }

        if (!StringUtils.hasText(orderRequest.getOrderType())) {
            return TradingResult.failure("订单类型不能为空");
        }

        boolean isClose = ("buy".equalsIgnoreCase(orderRequest.getSide()) && "short".equalsIgnoreCase(orderRequest.getPosSide()))
                || ("sell".equalsIgnoreCase(orderRequest.getSide()) && "long".equalsIgnoreCase(orderRequest.getPosSide()));
        if (!isClose && (orderRequest.getAmount() == null || orderRequest.getAmount().compareTo(BigDecimal.ZERO) <= 0)) {
            return TradingResult.failure("下单金额必须大于0");
        }

        if (orderRequest.getLever() == null || orderRequest.getLever().compareTo(BigDecimal.ZERO) <= 0) {
            return TradingResult.failure("杠杆倍数必须大于0");
        }

        // 验证API Key
        try {
            ApiKey apiKey = apiKeyService.getDecryptedKey(orderRequest.getApiKeyId());
            if (apiKey == null) {
                return TradingResult.failure("API Key不存在");
            }
        } catch (Exception e) {
            return TradingResult.failure("API Key验证失败: " + e.getMessage());
        }

        return TradingResult.success(null, "验证通过");
    }

    /**
     * 根据调用记录ID获取订单列表（委托给OrderHandler处理）
     *
     * @param recordId 调用记录ID
     * @return 订单列表
     */
    public List<TradingOrder> getOrdersByRecordId(Long recordId) {
        return orderHandler.getOrdersByRecordId(recordId);
    }
}
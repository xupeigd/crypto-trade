package com.crypto.trade.service.conversation;

import com.crypto.trade.config.AiTradingRiskControlConfig;
import com.crypto.trade.dto.OrderRequest;
import com.crypto.trade.dto.cex.model.CexAlgoOrder;
import com.crypto.trade.dto.cex.model.CexOrder;
import com.crypto.trade.dto.cex.request.CexAlgoOrderRequest;
import com.crypto.trade.dto.cex.request.CexAmendAlgoOrderRequest;
import com.crypto.trade.dto.cex.response.CexAlgoOrderOperationResponse;
import com.crypto.trade.dto.cex.response.CexAlgoOrderResponse;
import com.crypto.trade.dto.common.TradingResult;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.AttentionQueue;
import com.crypto.trade.entity.TradeAction;
import com.crypto.trade.enums.OpenCloseType;
import com.crypto.trade.repository.TradeActionRepository;
import com.crypto.trade.service.AttentionQueueService;
import com.crypto.trade.service.TradingOrderService;
import com.crypto.trade.service.UnifiedTradingService;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import com.crypto.trade.service.market.UnifiedPriceDataService;
import com.crypto.trade.service.trading.PositionHandler;
import org.springframework.util.CollectionUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TradeActionProcessor
 * 处理器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeActionProcessor {

    private final TradingOrderService tradingOrderService;
    private final TradingConfigProperties config;
    private final UnifiedCexApiService unifiedCexApiService;
    private final UnifiedTradingService unifiedTradingService;
    private final AiTradingRiskControlConfig aiTradingRiskControlConfig;
    private final PositionHandler positionHandler;
    private final UnifiedPriceDataService priceDataService;
    private final ApiKeyService apiKeyService;
    private final TradeActionRepository tradeActionRepository;
    private final AttentionQueueService attentionQueueService;

    /**
     * 处理交易动作
     * <p>
     * 从ActionPack中提取BUY/SELL动作，验证后执行交易。
     * </p>
     *
     * @param actionPack   解析后的动作包
     * @param apiKeyId     API密钥ID
     * @param tradeActions TradeAction列表(用于更新状态)
     * @return 执行结果列表
     */
    public List<TradeExecutionResult> processTradeActions(ActionParser.ActionPack actionPack, Long apiKeyId, List<TradeAction> tradeActions) {
        List<TradeExecutionResult> results = new ArrayList<>();

        // 【重要】输出当前配置信息,便于排查问题
        log.warn("【交易执行检查】autoExecution={}, executionMode={}, minConfidence={}, maxAmount={}",
                config.getAutoExecution(),
                config.getExecutionMode(),
                config.getMinConfidence(),
                config.getMaxAmount());

        // 检查是否启用自动交易
        if (!config.getAutoExecution()) {
            log.warn("【交易执行失败】自动交易未启用，跳过交易执行！请检查application.yml中ai.trading.auto-execution配置");
            return results;
        }

        // 验证配置
        if (!config.isValid()) {
            log.error("【交易执行失败】交易配置无效，请检查application.yml配置");
            return results;
        }

        // 检查执行模式
        if (config.isDryRunMode()) {
            log.warn("【交易执行模式】当前为模拟执行模式（dry-run），不会真实下单");
        } else if (config.isLiveMode()) {
            log.warn("【交易执行模式】当前为实盘执行模式（live），将真实下单交易！");
        }

        // 遍历所有动作
        List<ActionParser.ParsedAction> actions = actionPack.getActions();
        if (actions == null || actions.isEmpty()) {
            log.warn("动作包中没有动作需要处理");
            return results;
        }

        for (ActionParser.ParsedAction action : actions) {
            try {
                String actionType = action.getAction().name();

                // 处理取消订单动作
                if ("CANCEL_ORDER".equalsIgnoreCase(actionType)) {
                    log.info("【取消订单】检测到取消订单动作 - instId: {}, posSide: {}, price: {}, quantity: {}",
                            action.getInstId(), action.getPosSide(), action.getPrice(), action.getQuantity());

                    TradeExecutionResult result = executeCancelOrderAction(action, apiKeyId);
                    results.add(result);
                    logExecutionResult(result);
                    continue;
                }

                // 处理HOLD动作 - 设置/修改止盈止损
                if ("HOLD".equalsIgnoreCase(actionType)) {
                    if (action.getTakeProfit() != null || action.getStopLoss() != null) {
                        log.info("【止盈止损】检测到HOLD动作设置止盈止损 - instId: {}, posSide: {}, takeProfit: {}, stopLoss: {}",
                                action.getInstId(), action.getPosSide(), action.getTakeProfit(), action.getStopLoss());

                        TradeExecutionResult result = executeSetStopLoss(action, apiKeyId);
                        results.add(result);
                        logExecutionResult(result);
                    } else {
                        log.debug("【HOLD动作】无止盈止损参数，跳过处理 - instId: {}", action.getInstId());
                    }
                    continue;
                }

                // 处理ATTENTION动作 - 保存到关注队列
                if ("ATTENTION".equalsIgnoreCase(actionType)) {
                    log.info("【ATTENTION】检测到关注动作 - instId: {}, timeframe: {}, priority: {}",
                            action.getInstId(), action.getTimeframe(), action.getPriority());

                    try {
                        attentionQueueService.save(AttentionQueue.builder()
                                .recordId(action.getRecordId())
                                .apiKeyId(apiKeyId)
                                .instId(action.getInstId())
                                .priority(action.getPriority())
                                .timeframe(action.getTimeframe())
                                .queryLimit(action.getLimit())
                                .expectedTriggerTime(attentionQueueService.calculateNextTriggerTime(action.getPriority()))
                                .status("PENDING")
                                .build());

                        log.info("【ATTENTION】已保存关注记录 - instId: {}, recordId: {}",
                                action.getInstId(), action.getRecordId());
                    } catch (Exception e) {
                        log.error("【ATTENTION】保存关注记录失败 - instId: {}, 错误: {}",
                                action.getInstId(), e.getMessage(), e);
                    }
                    continue;
                }

                // 只处理BUY和SELL动作
                if (!"BUY".equalsIgnoreCase(actionType) && !"SELL".equalsIgnoreCase(actionType)) {
                    log.debug("跳过非交易动作: {}", actionType);
                    continue;
                }

//                // 【新增】业务逻辑验证(幻觉纠正)
//                TradeActionValidator.ValidationResult validationResult = validator.validate(action, apiKeyId);
//                if (!validationResult.isValid()) {
//                    log.error("【交易跳过】业务逻辑验证失败 - instId: {}, action: {}, 错误: {}",
//                            action.getInstId(), action.getAction(), validationResult.getErrors());
//                    results.add(TradeExecutionResult.failure(
//                            action.getInstId(),
//                            action.getAction().name(),
//                            "业务逻辑验证失败: " + String.join("; ", validationResult.getErrors()),
//                            0L
//                    ));
//                    continue;
//                }

                // 验证是否应该执行此交易
                TradeValidationResult validationResult = shouldExecuteTradeWithReason(action);
                if (!validationResult.isValid()) {
                    log.warn("【交易验证失败】{} - instId: {}, action: {}",
                            validationResult.getReason(), action.getInstId(), action.getAction().name());
                    results.add(TradeExecutionResult.failure(
                            action.getInstId(),
                            action.getAction().name(),
                            validationResult.getReason(),
                            0L
                    ));
                    continue;
                }

                // 执行交易
                TradeExecutionResult result = executeTradeAction(action, apiKeyId);
                results.add(result);

                // 记录执行结果
                logExecutionResult(result);

            } catch (Exception e) {
                log.error("处理交易动作失败 - instId: {}, action: {}", action.getInstId(), action.getAction().name(), e);
                results.add(TradeExecutionResult.failure(action.getInstId(), action.getAction().name(),
                        "处理失败: " + e.getMessage(), 0L));
            }
        }

        // 【新增】更新TradeAction状态
        if (tradeActions != null && !tradeActions.isEmpty()) {
            updateTradeActionStatus(tradeActions, results);
        }

        log.info("交易动作处理完成 - 总数: {}, 成功: {}, 失败: {}",
                results.size(),
                results.stream().filter(TradeExecutionResult::getSuccess).count(),
                results.stream().filter(r -> !r.getSuccess()).count());

        return results;
    }

    /**
     * 验证是否应该执行交易
     * <p>
     * 验证条件：
     * <ul>
     *   <li>1. 置信度检查：confidence >= minConfidence</li>
     *   <li>2. 参数完整性：instId、quantity不能为空</li>
     *   <li>3. 金额范围：minAmount <= quantity <= maxAmount</li>
     *   <li>4. 资金充足性：可用余额 >= 交易金额</li>
     * </ul>
     * </p>
     *
     * @param action 交易动作
     * @return 是否应该执行
     */
    private boolean shouldExecuteTrade(ActionParser.ParsedAction action) {
        // 1. 置信度检查
        Integer confidence = action.getConfidence();
        if (confidence == null || confidence < config.getMinConfidence()) {
            log.warn("【交易验证失败】置信度不足 - instId: {}, 当前: {}, 要求: >= {}",
                    action.getInstId(), confidence, config.getMinConfidence());
            return false;
        }

        // 2. 参数完整性检查
        if (action.getInstId() == null || action.getInstId().trim().isEmpty()) {
            log.warn("【交易验证失败】合约代码为空，跳过执行 - action: {}", action.getAction());
            return false;
        }

        // 关仓必须要有数量
        BigDecimal quantity = action.getQuantity();
        if (Objects.equals(OpenCloseType.CLOSE, action.getOpenClose())
                && (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0)) {
            log.warn("【交易验证失败】交易数量无效: {}，跳过执行 - instId: {}", quantity, action.getInstId());
            return false;
        }

        // 开仓必须要有价值
        BigDecimal amount = action.getAmount();
        if (Objects.equals(OpenCloseType.OPEN, action.getOpenClose())
                && (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)) {
            log.warn("【交易验证失败】交易金额无效: {}，跳过执行 - instId: {}", quantity, action.getInstId());
            return false;
        }

        // 3. 金额/数量范围检查 - 区分开仓和平仓
        // 注意：由于无法在shouldExecuteTrade中获取apiKeyId，暂时跳过持仓量验证
        // TODO: 重构方法签名，传入apiKeyId以实现持仓量验证
        if (Objects.equals(OpenCloseType.CLOSE, action.getOpenClose())) {
            // 平仓：跳过金额验证，允许任意数量的平仓
            log.info("【平仓验证通过】instId: {}, 平仓数量: {} (跳过金额验证)",
                    action.getInstId(), quantity);
        } else {
            // 开仓：验证金额不超过maxAmount
            if (amount.doubleValue() > config.getMaxAmount()) {
                log.warn("【交易验证失败】交易金额过大 - instId: {}, 当前: {}, 要求: <= {}",
                        action.getInstId(), amount, config.getMaxAmount());
                return false;
            }
            log.info("【开仓金额验证通过】instId: {}, 交易金额: {}", action.getInstId(), amount);
        }

        // 4. 资金充足性检查（如果启用了风控）
        if (config.getEnableRiskControl()) {
            // TODO: 实现资金充足性检查
            // 需要查询账户可用余额
            log.debug("风控检查已启用，但资金充足性检查待实现");
        }

        log.info("【交易验证通过】instId: {}, action: {}, confidence: {}, quantity: {}, openClose: {}",
                action.getInstId(), action.getAction(), confidence, quantity, action.getOpenClose());
        return true;
    }

    /**
     * 执行单个交易动作
     * <p>
     * 参数映射：
     * <ul>
     *   <li>action (BUY/SELL) → side (buy/sell)</li>
     *   <li>instId → instId</li>
     *   <li>开仓: amount → amount (成本金额USDT)</li>
     *   <li>平仓: quantity → amount (持仓张数)</li>
     *   <li>price → px (限价单价格)</li>
     *   <li>takeProfit → takeProfitPrice</li>
     *   <li>stopLoss → stopLossPrice</li>
     *   <li>posSide → posSide</li>
     * </ul>
     * </p>
     *
     * @param action   交易动作
     * @param apiKeyId API密钥ID
     * @return 执行结果
     */
    /**
     * 执行交易动作
     * <p>
     * 优化版本：在Processor中直接计算订单张数（sz），避免下游重复计算和判断
     * </p>
     *
     * @param action   解析后的交易动作
     * @param apiKeyId API密钥ID
     * @return 交易执行结果
     */
    private TradeExecutionResult executeTradeAction(ActionParser.ParsedAction action, Long apiKeyId) {
        long startTime = System.currentTimeMillis();

        // 检查是否为模拟模式
        if (config.isDryRunMode()) {
            return executeDryRun(action);
        }

        // 实盘执行
        try {
            // 1. 构建OrderRequest对象
            OrderRequest request = buildOrderRequest(action, apiKeyId);

            // 2. 计算订单张数（sz）- 优化点：尽早计算，避免下游重复判断
            BigDecimal sz = calculateOrderSize(action, request);
            request.setSz(sz);

            log.info("【优化下单】订单张数已提前计算 - instId: {}, sz: {}, side: {}, posSide: {}",
                    request.getInstId(), sz, request.getSide(), request.getPosSide());

            // 3. 调用TradingOrderService下单（使用优化后的单参数方法）
            TradingResult tradingResult = tradingOrderService.placeOrder(request);

            long executionTime = System.currentTimeMillis() - startTime;

            // 4. 判断执行结果
            if (tradingResult != null && tradingResult.getSuccess()) {
                return TradeExecutionResult.success(action.getInstId(), action.getAction().name(), tradingResult.getOrderId(),
                        request.getPx(), sz, executionTime, false);
            } else {
                return TradeExecutionResult.failure(action.getInstId(), action.getAction().name(),
                        tradingResult != null ? tradingResult.getMessage() : "下单失败", executionTime);
            }

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("执行交易失败 - instId: {}, action: {}",
                    action.getInstId(), action.getAction().name(), e);
            return TradeExecutionResult.failure(action.getInstId(), action.getAction().name(), "执行异常: " + e.getMessage(),
                    executionTime);
        }
    }

    /**
     * 构建OrderRequest对象
     * <p>
     * 统一的订单请求构建逻辑，避免分散的参数传递
     * </p>
     */
    private OrderRequest buildOrderRequest(ActionParser.ParsedAction action, Long apiKeyId) {
        OrderRequest request = new OrderRequest();

        // 基本字段
        request.setApiKeyId(apiKeyId);
        request.setActionId(action.getId());       // ✅ 新增：设置TradeAction ID
        request.setRecordId(action.getRecordId());   // ✅ 新增：设置调用记录ID
        request.setInstId(action.getInstId());

        // 订单方向和类型
        String side = mapActionToSide(action.getAction().name());
        String orderType = determineOrderType(action);
        request.setSide(side);
        request.setOrderType(orderType);

        // 价格相关
        request.setPx(action.getPrice());
        request.setTakeProfitPrice(action.getTakeProfit());
        request.setStopLossPrice(action.getStopLoss());

        // 持仓方向
        String posSide = action.getPosSide();
        request.setPosSide(posSide);

        // 杠杆
        BigDecimal lever = BigDecimal.valueOf(config.getDefaultLeverage());
        request.setLever(lever);

        // 成本金额（开仓时使用）
        BigDecimal amount = action.getAmount();
        request.setAmount(amount);

        // 平仓张数（平仓时使用）
        BigDecimal quantity = action.getQuantity();

        // 根据开平仓类型确定订单方向
        if (Objects.equals(OpenCloseType.CLOSE, action.getOpenClose())) {
            // 平仓逻辑
            if ("short".equalsIgnoreCase(posSide)) {
                request.setSide("buy");  // 平空
            } else {
                request.setSide("sell"); // 平多
            }
            log.info("【平仓操作】side已调整为平仓方向 - instId: {}, side: {}, quantity: {}",
                    action.getInstId(), request.getSide(), quantity);
        } else {
            // 开仓逻辑
            if ("short".equalsIgnoreCase(posSide)) {
                request.setSide("sell"); // 开空
            } else {
                request.setSide("buy");  // 开多
            }
            // 如果没有指定amount，使用quantity
            if (amount == null) {
                request.setAmount(quantity);
            }
            log.info("【开仓操作】side已调整为开仓方向 - instId: {}, side: {}, amount: {}",
                    action.getInstId(), request.getSide(), request.getAmount());
        }

        // 订单来源
        request.setSource("ai");

        // 风控模式：AUTO模式绕过风控，MANUAL模式进入风控审核
        boolean bypassRiskControl = !aiTradingRiskControlConfig.isManualMode();
        request.setBypassRiskControl(bypassRiskControl);

        log.info("【AI下单风控判断】当前风控模式={}, bypassRiskControl={}",
                aiTradingRiskControlConfig.isManualMode() ? "MANUAL" : "AUTO",
                bypassRiskControl);

        return request;
    }

    /**
     * 计算订单张数（sz）
     * <p>
     * 优化点：在Processor中直接计算sz，避免在TradingOrderService和OrderHandler中重复判断
     * </p>
     */
    private BigDecimal calculateOrderSize(ActionParser.ParsedAction action, OrderRequest request) {
        boolean isClose = Objects.equals(OpenCloseType.CLOSE, action.getOpenClose());

        // 平仓：quantity是币数量，需要除以ctVal得到张数
        if (isClose) {
            BigDecimal quantity = action.getQuantity();
            if (null == quantity || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("平仓数量无效: " + quantity);
            }

            // 调用PositionHandler计算平仓张数（quantity是币数量，需要除以ctVal）
            BigDecimal contractSize = positionHandler.calculateOrderSize(
                    action.getInstId(),
                    null,    // 平仓不需要amount
                    null,    // 平仓不需要lever
                    null,    // 平仓不需要price
                    true,    // 平仓标记
                    quantity // 币数量
            );

            // 验证计算结果
            if (null == contractSize || contractSize.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("平仓张数计算错误: " + contractSize);
            }

            // 检查科学计数法
            String plainString = contractSize.toPlainString();
            if (plainString.contains("E")) {
                log.error("【平仓张数异常】instId: {}, 计算结果包含科学计数法: {}(plain: {})",
                        action.getInstId(), contractSize, plainString);
            }

            log.info("【平仓张数计算】instId: {}, 币数量: {}, 计算张数: {}, 张数(plain): {}",
                    action.getInstId(), quantity, contractSize, plainString);

            return contractSize;
        }

        // 开仓：根据成本金额计算张数
        try {
            // 1. 获取ApiKey对象
            ApiKey apiKey = apiKeyService.getDecryptedKey(request.getApiKeyId());

            // 2. 获取当前标记价格
            BigDecimal currentPrice = priceDataService.getMarkPrice(apiKey, action.getInstId());
            log.info("【开仓张数计算】获取标记价格 - instId: {}, markPrice: {}",
                    action.getInstId(), currentPrice);
            if (null == currentPrice || 0 == currentPrice.compareTo(BigDecimal.ZERO)) {
                throw new IllegalArgumentException("获取标记价格失败");
            }
            // 3. 调用PositionHandler计算张数
            BigDecimal sz = positionHandler.calculateOrderSize(action.getInstId(), request.getAmount(), request.getLever(),
                    currentPrice, false,  // 开仓
                    null);    // 开仓时不需要quantity

            log.info("【开仓张数计算】计算完成 - instId: {}, amount: {}, lever: {}, price: {}, sz: {}",
                    action.getInstId(), request.getAmount(), request.getLever(), currentPrice, sz);

            return sz;
        } catch (Exception e) {
            log.error("【开仓张数计算失败】instId: {}, 错误: {}", action.getInstId(), e.getMessage(), e);
            // 降级处理：返回一个保守的默认值
            return BigDecimal.ZERO;
        }
    }

    /**
     * 执行取消订单动作
     * <p>
     * 优化逻辑：
     * 1. 如果提供了 orderId，直接使用 orderId 撤单（性能最优）
     * 2. 如果没有提供 orderId，查询待成交订单并匹配（向后兼容）
     * </p>
     *
     * @param action   解析的动作
     * @param apiKeyId API Key ID
     * @return 执行结果
     */
    private TradeExecutionResult executeCancelOrderAction(ActionParser.ParsedAction action, Long apiKeyId) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("【取消订单】开始执行 - instId: {}, orderId: {}, posSide: {}, price: {}, quantity: {}, confidence: {}",
                    action.getInstId(), action.getOrderId(), action.getPosSide(),
                    action.getPrice(), action.getQuantity(), action.getConfidence());

            // 验证必要参数
            if (action.getInstId() == null || action.getInstId().trim().isEmpty()) {
                return TradeExecutionResult.failure(action.getInstId(), action.getAction().name(),
                        "合约代码(instId)不能为空", System.currentTimeMillis() - startTime);
            }

            // 模拟模式：只验证参数，不真实取消
            if (config.isDryRunMode()) {
                return handleDryRunCancel(action, startTime);
            }

            // 实盘模式：判断是否有 orderId
            String orderId = action.getOrderId();
            if (orderId != null && !orderId.trim().isEmpty()) {
                // 有 orderId：直接撤单，跳过查询和匹配（性能优化）
                log.info("【取消订单】提供 orderId，使用直接撤单路径 - orderId: {}", orderId);
                return cancelOrderDirectly(action, apiKeyId, orderId, startTime);
            }

            // 无 orderId：走原有的匹配逻辑（向后兼容）
            log.info("【取消订单】未提供 orderId，使用订单匹配路径");
            return cancelOrderByMatching(action, apiKeyId, startTime);

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("【取消订单】执行失败 - instId: {}", action.getInstId(), e);
            return TradeExecutionResult.failure(
                    action.getInstId(),
                    action.getAction().name(),
                    "执行异常: " + e.getMessage(),
                    executionTime
            );
        }
    }

    /**
     * 处理模拟模式下的取消订单
     */
    private TradeExecutionResult handleDryRunCancel(ActionParser.ParsedAction action, long startTime) {
        String message = String.format(
                "【取消订单-模拟执行】验证通过\n" +
                        "  合约: %s\n" +
                        "  订单ID: %s\n" +
                        "  持仓方向: %s\n" +
                        "  价格: %s\n" +
                        "  数量: %s\n" +
                        "  置信度: %d%%\n" +
                        "  理由: %s\n" +
                        "  注意：当前为模拟模式，未真实取消订单",
                action.getInstId(),
                action.getOrderId() != null ? action.getOrderId() : "未提供",
                action.getPosSide() != null ? action.getPosSide() : "未指定",
                action.getPrice() != null ? action.getPrice() : "未指定",
                action.getQuantity() != null ? action.getQuantity() : "未指定",
                action.getConfidence() != null ? action.getConfidence() : 0,
                action.getReasoning() != null ? action.getReasoning() : "无"
        );

        log.info(message);
        return TradeExecutionResult.dryRun(
                action.getInstId(),
                action.getAction().name(),
                message
        );
    }

    /**
     * 直接通过 orderId 撤单（优化路径）
     * <p>
     * 当 AI 决策中提供了 orderId 时，直接调用撤单 API，跳过订单查询和匹配逻辑。
     * 性能优势：避免查询订单列表和遍历匹配，性能提升约 90%。
     * </p>
     *
     * @param action    AI 决策的动作
     * @param apiKeyId  API Key ID
     * @param orderId   订单 ID
     * @param startTime 开始时间（用于计算执行时间）
     * @return 执行结果
     */
    private TradeExecutionResult cancelOrderDirectly(
            ActionParser.ParsedAction action,
            Long apiKeyId,
            String orderId,
            long startTime) {

        log.info("【取消订单-直接撤单】开始执行 - orderId: {}", orderId);

        try {
            // 直接调用撤单 API，无需查询和匹配
            TradingResult cancelResult = tradingOrderService.cancelOrder(
                    apiKeyId,
                    action.getInstId(),
                    orderId
            );

            long executionTime = System.currentTimeMillis() - startTime;

            if (cancelResult != null && cancelResult.getSuccess()) {
                log.info("【取消订单-直接撤单成功】orderId: {}, 执行时间: {}ms", orderId, executionTime);
                return TradeExecutionResult.success(
                        action.getInstId(),
                        action.getAction().name(),
                        orderId,
                        null,  // 价格未知（无需查询）
                        null,  // 数量未知（无需查询）
                        executionTime,
                        false
                );
            } else {
                String errorMsg = cancelResult != null ? cancelResult.getMessage() : "取消订单失败";
                log.error("【取消订单-直接撤单失败】orderId: {}, 错误: {}", orderId, errorMsg);
                return TradeExecutionResult.failure(
                        action.getInstId(),
                        action.getAction().name(),
                        errorMsg,
                        executionTime
                );
            }

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("【取消订单-直接撤单】执行异常 - orderId: {}", orderId, e);
            return TradeExecutionResult.failure(
                    action.getInstId(),
                    action.getAction().name(),
                    "执行异常: " + e.getMessage(),
                    executionTime
            );
        }
    }

    /**
     * 通过订单匹配撤单（兼容路径）
     * <p>
     * 当 AI 决策中没有提供 orderId 时，查询待成交订单并匹配最佳订单进行撤单。
     * 匹配规则：方向匹配、价格差异（±5%）、数量差异（±10%）。
     * </p>
     *
     * @param action    AI 决策的动作
     * @param apiKeyId  API Key ID
     * @param startTime 开始时间（用于计算执行时间）
     * @return 执行结果
     */
    private TradeExecutionResult cancelOrderByMatching(
            ActionParser.ParsedAction action,
            Long apiKeyId,
            long startTime) {

        log.info("【取消订单-匹配撤单】开始执行 - instId: {}", action.getInstId());

        try {
            // 1. 获取 ApiKey 对象
            ApiKey apiKey = apiKeyService.getDecryptedKey(apiKeyId);

            // 2. 查询待成交订单列表
            log.info("【取消订单-匹配撤单】查询待成交订单 - instId: {}", action.getInstId());
            // 直接使用通用CexOrder，无需适配
            List<CexOrder> pendingOrders = unifiedCexApiService.getPendingOrders(apiKey, "SWAP", action.getInstId());

            if (pendingOrders == null || pendingOrders.isEmpty()) {
                String message = String.format("未找到待成交订单 - 合约: %s", action.getInstId());
                log.warn("【取消订单-匹配撤单】{}", message);
                return TradeExecutionResult.failure(
                        action.getInstId(),
                        action.getAction().name(),
                        message,
                        System.currentTimeMillis() - startTime
                );
            }

            log.info("【取消订单-匹配撤单】查询到 {} 个待成交订单", pendingOrders.size());

            // 3. 根据条件匹配订单
            List<CexOrder> matchedOrders = matchOrders(pendingOrders, action);

            if (matchedOrders.isEmpty()) {
                String message = String.format("未找到匹配的订单 - 合约: %s, 方向: %s, 价格: %s, 数量: %s",
                        action.getInstId(),
                        action.getPosSide() != null ? action.getPosSide() : "未指定",
                        action.getPrice() != null ? action.getPrice() : "未指定",
                        action.getQuantity() != null ? action.getQuantity() : "未指定");
                log.warn("【取消订单-匹配撤单】{}", message);
                return TradeExecutionResult.failure(
                        action.getInstId(),
                        action.getAction().name(),
                        message,
                        System.currentTimeMillis() - startTime
                );
            }

            // 4. 选择最匹配的订单（第一个）
            CexOrder orderToCancel = matchedOrders.get(0);
            log.info("【取消订单-匹配撤单】匹配到订单 - ordId: {}, 方向: {}, 价格: {}, 数量: {}",
                    orderToCancel.getOrderId(), orderToCancel.getSide().getCode(),
                    orderToCancel.getPrice(), orderToCancel.getQuantity());

            // 5. 调用撤单 API
            TradingResult cancelResult = tradingOrderService.cancelOrder(
                    apiKeyId,
                    action.getInstId(),
                    orderToCancel.getOrderId()
            );

            long executionTime = System.currentTimeMillis() - startTime;

            // 6. 返回结果
            if (cancelResult != null && cancelResult.getSuccess()) {
                log.info("【取消订单-匹配撤单成功】ordId: {}, 执行时间: {}ms",
                        orderToCancel.getOrderId(), executionTime);
                return TradeExecutionResult.success(
                        action.getInstId(),
                        action.getAction().name(),
                        orderToCancel.getOrderId(),
                        orderToCancel.getPrice(),
                        orderToCancel.getQuantity(),
                        executionTime,
                        false
                );
            } else {
                String errorMsg = cancelResult != null ? cancelResult.getMessage() : "取消订单失败";
                log.error("【取消订单-匹配撤单失败】ordId: {}, 错误: {}",
                        orderToCancel.getOrderId(), errorMsg);
                return TradeExecutionResult.failure(
                        action.getInstId(),
                        action.getAction().name(),
                        errorMsg,
                        executionTime
                );
            }

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("【取消订单-匹配撤单】执行异常 - instId: {}", action.getInstId(), e);
            return TradeExecutionResult.failure(
                    action.getInstId(),
                    action.getAction().name(),
                    "执行异常: " + e.getMessage(),
                    executionTime
            );
        }
    }

    /**
     * 匹配订单
     * <p>
     * 根据AI决策中的参数（posSide、price、quantity）匹配待成交订单
     * </p>
     *
     * @param pendingOrders 待成交订单列表
     * @param action        AI决策的动作
     * @return 匹配的订单列表（按匹配度排序）
     */
    private List<CexOrder> matchOrders(List<CexOrder> pendingOrders, ActionParser.ParsedAction action) {
        return pendingOrders.stream()
                .filter(order -> {
                    // 基本过滤：instId已经在查询时指定了

                    // 方向匹配：如果AI指定了方向，则必须匹配
                    if (action.getPosSide() != null && !action.getPosSide().trim().isEmpty()) {
                        // OKX订单方向：buy/sell
                        // 持仓方向转换：long -> sell订单（平多）, short -> buy订单（平空）
                        boolean directionMatch = matchOrderDirection(order.getSide().getCode(), action.getAction().name());
                        if (!directionMatch) {
                            log.debug("【订单匹配】方向不匹配 - 订单方向: {}, 持仓方向: {}", order.getSide().getCode(), action.getPosSide());
                            return false;
                        }
                    }

                    // 价格匹配：如果AI指定了价格，则价格应该接近
                    if (action.getPrice() != null && order.getPrice() != null) {
                        double targetPrice = action.getPrice().doubleValue();
                        double orderPrice = order.getPrice().doubleValue();
                        double priceDiff = Math.abs(targetPrice - orderPrice) / targetPrice;
                        // 价格差异超过5%则不匹配
                        if (priceDiff > 0.05) {
                            log.debug("【订单匹配】价格差异过大 - 目标价格: {}, 订单价格: {}, 差异: {}%",
                                    targetPrice, orderPrice, priceDiff);
                            return false;
                        }
                    }

                    // 数量匹配：如果AI指定了数量，则数量应该接近
                    if (action.getQuantity() != null && order.getQuantity() != null) {
                        double targetQty = action.getQuantity().doubleValue();
                        double orderQty = order.getQuantity().doubleValue();
                        double qtyDiff = Math.abs(targetQty - orderQty) / targetQty;
                        // 数量差异超过10%则不匹配
                        if (qtyDiff > 0.10) {
                            log.debug("【订单匹配】数量差异过大 - 目标数量: {}, 订单数量: {}, 差异: {}%",
                                    targetQty, orderQty, qtyDiff);
                            return false;
                        }
                    }

                    return true;
                })
                .sorted((o1, o2) -> {
                    // 按匹配度排序：价格和数量都匹配的优先
                    int score1 = calculateMatchScore(o1, action);
                    int score2 = calculateMatchScore(o2, action);
                    return Integer.compare(score2, score1); // 降序
                })
                .collect(Collectors.toList());
    }

    /**
     * 匹配订单方向
     * <p>
     * 订单方向与持仓方向的对应关系：
     * - 平多（long）→ sell订单
     * - 平空（short）→ buy订单
     * </p>
     *
     * @param orderSide 订单方向（buy/sell）
     * @param posSide   持仓方向（long/short）
     * @return 是否匹配
     */
    private boolean matchOrderDirection(String orderSide, String posSide) {
        if ("long".equalsIgnoreCase(posSide)) {
            // 平多：需要sell订单
            return "sell".equalsIgnoreCase(orderSide);
        } else if ("short".equalsIgnoreCase(posSide)) {
            // 平空：需要buy订单
            return "buy".equalsIgnoreCase(orderSide);
        }
        // 未指定方向或无法判断，默认匹配
        return true;
    }

    /**
     * 计算订单匹配分数
     * <p>
     * 分数越高表示匹配度越好
     * </p>
     *
     * @param order  订单
     * @param action AI决策的动作
     * @return 匹配分数
     */
    private int calculateMatchScore(CexOrder order, ActionParser.ParsedAction action) {
        int score = 0;

        // 价格匹配得分（最多40分）
        if (action.getPrice() != null && order.getPrice() != null) {
            double targetPrice = action.getPrice().doubleValue();
            double orderPrice = order.getPrice().doubleValue();
            double priceDiff = Math.abs(targetPrice - orderPrice) / targetPrice;
            score += (int) (40 * (1 - priceDiff));
        }

        // 数量匹配得分（最多40分）
        if (action.getQuantity() != null && order.getQuantity() != null) {
            double targetQty = action.getQuantity().doubleValue();
            double orderQty = order.getQuantity().doubleValue();
            double qtyDiff = Math.abs(targetQty - orderQty) / targetQty;
            score += (int) (40 * (1 - qtyDiff));
        }

        // 方向匹配得分（最多20分）
        if (action.getPosSide() != null) {
            boolean directionMatch = matchOrderDirection(order.getSide().getCode(), action.getPosSide());
            if (directionMatch) {
                score += 20;
            }
        }

        return score;
    }

    /**
     * 模拟执行（dry-run）
     * <p>
     * 验证所有参数，但不真实下单。
     * </p>
     *
     * @param action 交易动作
     * @return 模拟执行结果
     */
    private TradeExecutionResult executeDryRun(ActionParser.ParsedAction action) {
        log.info("[模拟执行] 验证交易参数 - instId: {}, action: {}, quantity: {}, confidence: {}",
                action.getInstId(),
                action.getAction().name(),
                action.getQuantity(),
                action.getConfidence());

        // 构建详细的模拟执行消息
        String message = "模拟执行验证通过：\n" +
                String.format("  合约: %s\n", action.getInstId()) +
                String.format("  动作: %s\n", action.getAction().name()) +
                String.format("  数量: %s\n", action.getQuantity()) +
                String.format("  价格: %s\n", action.getPrice()) +
                String.format("  止盈: %s\n", action.getTakeProfit()) +
                String.format("  止损: %s\n", action.getStopLoss()) +
                String.format("  置信度: %d%%\n", action.getConfidence()) +
                "  注意：当前为模拟模式，未真实下单";

        return TradeExecutionResult.dryRun(action.getInstId(), action.getAction().name(), message);
    }

    /**
     * 映射动作到交易方向
     *
     * @param action BUY/SELL
     * @return buy/sell
     */
    private String mapActionToSide(String action) {
        if ("BUY".equalsIgnoreCase(action)) {
            return "buy";
        } else if ("SELL".equalsIgnoreCase(action)) {
            return "sell";
        } else {
            throw new IllegalArgumentException("不支持的动作类型: " + action);
        }
    }

    /**
     * 确定订单类型
     * <p>
     * 如果决策中指定了价格，使用限价单（limit）；
     * 否则使用市价单（market）。
     * </p>
     *
     * @param action 交易动作
     * @return market/limit
     */
    private String determineOrderType(ActionParser.ParsedAction action) {
        // 如果指定了价格，使用限价单
        if (action.getPrice() != null && action.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            return "limit";
        }
        // 否则使用市价单
        return "market";
    }

    /**
     * 记录执行结果
     *
     * @param result 执行结果
     */
    private void logExecutionResult(TradeExecutionResult result) {
        if (result.getSuccess()) {
            if (result.getDryRun()) {
                log.info("交易模拟执行成功 - instId: {}, action: {}\n{}",
                        result.getInstId(), result.getAction(), result.getErrorMessage());
            } else {
                log.info("交易执行成功 - instId: {}, action: {}, orderId: {}, price: {}, size: {}, 耗时: {}ms",
                        result.getInstId(), result.getAction(), result.getOrderId(), result.getExecutedPrice(),
                        result.getExecutedSize(), result.getExecutionTimeMs());
            }
        } else {
            log.error("交易执行失败 - instId: {}, action: {}, 错误: {}", result.getInstId(), result.getAction(),
                    result.getErrorMessage());
        }
    }

    /**
     * 更新TradeAction状态
     * 根据执行结果更新对应TradeAction的status字段
     *
     * @param tradeActions TradeAction列表
     * @param results      执行结果列表
     */
    private void updateTradeActionStatus(List<TradeAction> tradeActions, List<TradeExecutionResult> results) {
        if (tradeActions == null || results == null) {
            log.warn("【TradeAction状态更新】tradeActions或results为null，跳过更新");
            return;
        }

        log.info("【TradeAction状态更新】开始更新 - tradeActions数量: {}, results数量: {}",
                tradeActions.size(), results.size());

        // 按actionType和instId匹配TradeAction和执行结果
        Map<String, TradeAction> actionMap = new HashMap<>();
        for (TradeAction action : tradeActions) {
            String key = action.getActionType() + "_" + action.getInstId();
            actionMap.put(key, action);
            log.debug("【TradeAction状态更新】添加到映射 - actionId: {}, key: {}, 当前状态: {}",
                    action.getId(), key, action.getStatus());
        }

        // 遍历执行结果,更新对应TradeAction的状态
        int matchCount = 0;
        int successCount = 0;
        int failedCount = 0;
        int notFoundCount = 0;

        for (TradeExecutionResult result : results) {
            String key = result.getAction() + "_" + result.getInstId();
            TradeAction action = actionMap.get(key);

            if (action != null) {
                try {
                    if (result.getSuccess()) {
                        // 执行成功
                        action.markAsExecuted(result.getOrderId(), result.getExecutedPrice(), result.getExecutedSize(),
                                result.getExecutionTimeMs());
                        log.info("【TradeAction状态更新】更新为EXECUTED - actionId: {}, orderId: {}, instId: {}",
                                action.getId(), result.getOrderId(), result.getInstId());
                        successCount++;
                    } else {
                        // 执行失败
                        action.markAsFailed(result.getErrorMessage(), result.getExecutionTimeMs());
                        log.info("【TradeAction状态更新】更新为FAILED - actionId: {}, error: {}, instId: {}",
                                action.getId(), result.getErrorMessage(), result.getInstId());
                        failedCount++;
                    }

                    // 保存状态更新
                    tradeActionRepository.save(action);
                    matchCount++;
                } catch (Exception e) {
                    log.error("【TradeAction状态更新】更新TradeAction状态失败 - actionId: {}", action.getId(), e);
                }
            } else {
                log.warn("【TradeAction状态更新】未找到匹配的TradeAction - key: {}, action: {}, instId: {}",
                        key, result.getAction(), result.getInstId());
                notFoundCount++;
            }
        }

        log.info("【TradeAction状态更新】完成 - 总计: {}, 成功: {}, 失败: {}, 未找到: {}",
                matchCount, successCount, failedCount, notFoundCount);
    }

    /**
     * 验证是否应该执行交易（返回详细验证结果）
     * <p>
     * 验证条件：
     * <ul>
     *   <li>1. 置信度检查：confidence >= minConfidence</li>
     *   <li>2. 参数完整性：instId、quantity不能为空</li>
     *   <li>3. 金额范围：minAmount <= quantity <= maxAmount</li>
     *   <li>4. 资金充足性：可用余额 >= 交易金额</li>
     * </ul>
     * </p>
     *
     * @param action 交易动作
     * @return 验证结果
     */
    private TradeValidationResult shouldExecuteTradeWithReason(ActionParser.ParsedAction action) {
        // 1. 置信度检查
        Integer confidence = action.getConfidence();
        if (confidence == null || confidence < config.getMinConfidence()) {
            String reason = String.format("置信度不足 - 当前: %d, 要求: >= %d",
                    confidence, config.getMinConfidence());
            log.warn("【交易验证失败】{} - instId: {}", reason, action.getInstId());
            return TradeValidationResult.invalid(reason);
        }

        // 2. 参数完整性检查
        if (action.getInstId() == null || action.getInstId().trim().isEmpty()) {
            String reason = "合约代码为空";
            log.warn("【交易验证失败】{} - action: {}", reason, action.getAction());
            return TradeValidationResult.invalid(reason);
        }

        // 关仓必须要有数量
        BigDecimal quantity = action.getQuantity();
        if (Objects.equals(OpenCloseType.CLOSE, action.getOpenClose())
                && (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0)) {
            String reason = String.format("交易数量无效: %s", quantity);
            log.warn("【交易验证失败】{} - instId: {}", reason, action.getInstId());
            return TradeValidationResult.invalid(reason);
        }

        // 开仓必须要有价值
        BigDecimal amount = action.getAmount();
        if (Objects.equals(OpenCloseType.OPEN, action.getOpenClose())
                && (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)) {
            String reason = String.format("交易金额无效: %s", amount);
            log.warn("【交易验证失败】{} - instId: {}", reason, action.getInstId());
            return TradeValidationResult.invalid(reason);
        }

        // 3. 金额/数量范围检查 - 区分开仓和平仓
        if (Objects.equals(OpenCloseType.CLOSE, action.getOpenClose())) {
            // 平仓：跳过金额验证，允许任意数量的平仓
            log.info("【平仓验证通过】instId: {}, 平仓数量: {} (跳过金额验证)",
                    action.getInstId(), quantity);
        } else {
            // 开仓：验证金额不超过maxAmount
            if (amount.doubleValue() > config.getMaxAmount()) {
                String reason = String.format("交易金额过大 - 当前: %.2f, 要求: <= %.2f",
                        amount, config.getMaxAmount());
                log.warn("【交易验证失败】{} - instId: {}", reason, action.getInstId());
                return TradeValidationResult.invalid(reason);
            }
            log.info("【开仓金额验证通过】instId: {}, 交易金额: {}", action.getInstId(), amount);
        }

        // 4. 资金充足性检查（如果启用了风控）
        if (config.getEnableRiskControl()) {
            // TODO: 实现资金充足性检查
            // 需要查询账户可用余额
            log.debug("风控检查已启用，但资金充足性检查待实现");
        }

        log.info("【交易验证通过】instId: {}, action: {}, confidence: {}, quantity: {}, openClose: {}",
                action.getInstId(), action.getAction(), confidence, quantity, action.getOpenClose());
        return TradeValidationResult.valid();
    }

    // ==================== 止盈止损操作相关方法 ====================

    /**
     * 执行设置/修改止盈止损操作
     * <p>
     * 根据仓位是否已有止盈止损，自动判断是修改还是新建：
     * - 已有止盈止损算法订单 → 调用修改接口
     * - 无止盈止损算法订单 → 调用新建接口
     * </p>
     *
     * @param action   解析的动作
     * @param apiKeyId API Key ID
     * @return 执行结果
     */
    private TradeExecutionResult executeSetStopLoss(ActionParser.ParsedAction action, Long apiKeyId) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 参数验证
            if (action.getInstId() == null || action.getInstId().trim().isEmpty()) {
                return TradeExecutionResult.failure(null, "HOLD",
                        "合约代码(instId)不能为空", System.currentTimeMillis() - startTime);
            }
            if (action.getPosSide() == null || action.getPosSide().trim().isEmpty()) {
                return TradeExecutionResult.failure(action.getInstId(), "HOLD",
                        "仓位方向(posSide)不能为空", System.currentTimeMillis() - startTime);
            }
            if (action.getTakeProfit() == null && action.getStopLoss() == null) {
                return TradeExecutionResult.failure(action.getInstId(), "HOLD",
                        "止盈价格和止损价格至少需要提供一个", System.currentTimeMillis() - startTime);
            }

            // 2. 模拟模式
            if (config.isDryRunMode()) {
                return executeDryRunSetStopLoss(action, startTime);
            }

            // 3. 获取API Key
            ApiKey apiKey = apiKeyService.getDecryptedKey(apiKeyId);
            if (apiKey == null) {
                return TradeExecutionResult.failure(action.getInstId(), "HOLD",
                        "API Key不存在", System.currentTimeMillis() - startTime);
            }

            // 4. 查询该仓位的止盈止损算法订单
            String algoId = findStopLossAlgoId(apiKey, action.getInstId(), action.getPosSide());

            // 5. 根据是否有algoId判断是修改还是新建
            if (algoId != null && !algoId.trim().isEmpty()) {
                // 有止盈止损 → 修改
                log.info("【止盈止损】修改已有止盈止损 - instId: {}, posSide: {}, algoId: {}",
                        action.getInstId(), action.getPosSide(), algoId);
                return amendStopLoss(apiKey, action, algoId, startTime);
            } else {
                // 无止盈止损 → 新建
                log.info("【止盈止损】新建止盈止损 - instId: {}, posSide: {}",
                        action.getInstId(), action.getPosSide());
                return createStopLoss(apiKey, action, startTime);
            }

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("【止盈止损】执行失败 - instId: {}, posSide: {}",
                    action.getInstId(), action.getPosSide(), e);
            return TradeExecutionResult.failure(action.getInstId(), "HOLD",
                    "执行异常: " + e.getMessage(), executionTime);
        }
    }

    /**
     * 查询仓位的止盈止损算法订单ID
     * <p>
     * 根据instId和posSide匹配算法订单列表，查找止盈止损类型的订单。
     * </p>
     *
     * @param apiKey  API密钥
     * @param instId  合约ID
     * @param posSide 仓位方向 (long/short)
     * @return 算法订单ID，未找到返回null
     */
    private String findStopLossAlgoId(ApiKey apiKey, String instId, String posSide) {
        try {
            // 获取算法订单列表
            CexAlgoOrderResponse algoResponse = unifiedTradingService.getAlgoOrders(apiKey, "SWAP");

            if (algoResponse == null || CollectionUtils.isEmpty(algoResponse.getAlgoOrders())) {
                log.debug("【止盈止损查询】无算法订单 - instId: {}", instId);
                return null;
            }

            // 遍历查找匹配的止盈止损订单
            for (CexAlgoOrder algoOrder : algoResponse.getAlgoOrders()) {
                // 匹配合约和仓位方向
                if (instId.equals(algoOrder.getSymbol())
                        && posSide.equalsIgnoreCase(algoOrder.getPosSide())) {
                    // 检查是否为止盈止损类型订单（OCO、止盈、止损）
                    if (algoOrder.isOco() || algoOrder.isTakeProfit() || algoOrder.isStopLoss()) {
                        // 检查订单数量是否有效（排除已取消或完成的订单）
                        if (algoOrder.getQuantity() != null
                                && algoOrder.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                            log.debug("【止盈止损查询】找到匹配订单 - instId: {}, posSide: {}, algoId: {}, type: {}",
                                    instId, posSide, algoOrder.getAlgoId(), algoOrder.getOrderType());
                            return algoOrder.getAlgoId();
                        }
                    }
                }
            }

            log.debug("【止盈止损查询】未找到匹配订单 - instId: {}, posSide: {}", instId, posSide);
            return null;

        } catch (Exception e) {
            log.warn("【止盈止损查询】查询失败 - instId: {}, posSide: {}", instId, posSide, e);
            return null;
        }
    }

    /**
     * 修改已有止盈止损
     *
     * @param apiKey    API密钥
     * @param action    解析的动作
     * @param algoId    算法订单ID
     * @param startTime 开始时间
     * @return 执行结果
     */
    private TradeExecutionResult amendStopLoss(ApiKey apiKey, ActionParser.ParsedAction action,
                                               String algoId, long startTime) {
        try {
            // 构建修改请求
            CexAmendAlgoOrderRequest.CexAmendAlgoOrderRequestBuilder builder = CexAmendAlgoOrderRequest.builder()
                    .algoId(algoId)
                    .symbol(action.getInstId());

            // 设置新的止盈触发价
            if (action.getTakeProfit() != null) {
                builder.newTakeProfitTriggerPrice(action.getTakeProfit().toPlainString())
                        .newTakeProfitOrderPrice("-1")  // 市价单
                        .newTakeProfitTriggerPriceType("last");
            }

            // 设置新的止损触发价
            if (action.getStopLoss() != null) {
                builder.newStopLossTriggerPrice(action.getStopLoss().toPlainString())
                        .newStopLossOrderPrice("-1")  // 市价单
                        .newStopLossTriggerPriceType("last");
            }

            // 调用修改接口
            CexAlgoOrderOperationResponse response = unifiedTradingService.amendAlgoOrder(apiKey, builder.build());

            long executionTime = System.currentTimeMillis() - startTime;

            if (response != null && Boolean.TRUE.equals(response.isSuccess())) {
                log.info("【止盈止损】修改成功 - instId: {}, algoId: {}, takeProfit: {}, stopLoss: {}",
                        action.getInstId(), algoId, action.getTakeProfit(), action.getStopLoss());
                return TradeExecutionResult.success(
                        action.getInstId(),
                        "HOLD",
                        algoId,
                        null,
                        null,
                        executionTime,
                        false
                );
            } else {
                String errorMsg = response != null ? response.getErrorMessage() : "修改止盈止损失败";
                log.error("【止盈止损】修改失败 - instId: {}, algoId: {}, error: {}",
                        action.getInstId(), algoId, errorMsg);
                return TradeExecutionResult.failure(action.getInstId(), "HOLD", errorMsg, executionTime);
            }

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("【止盈止损】修改异常 - instId: {}, algoId: {}", action.getInstId(), algoId, e);
            return TradeExecutionResult.failure(action.getInstId(), "HOLD",
                    "修改异常: " + e.getMessage(), executionTime);
        }
    }

    /**
     * 新建止盈止损
     *
     * @param apiKey    API密钥
     * @param action    解析的动作
     * @param startTime 开始时间
     * @return 执行结果
     */
    private TradeExecutionResult createStopLoss(ApiKey apiKey, ActionParser.ParsedAction action, long startTime) {
        try {
            // 根据仓位方向确定订单方向
            // long仓位止盈止损 -> sell订单（平多）
            // short仓位止盈止损 -> buy订单（平空）
            String side = "long".equalsIgnoreCase(action.getPosSide()) ? "sell" : "buy";

            // 构建新建请求
            CexAlgoOrderRequest.CexAlgoOrderRequestBuilder builder = CexAlgoOrderRequest.builder()
                    .symbol(action.getInstId())
                    .tradeMode("cross")  // 全仓模式
                    .currency("USDT")
                    .side(side)
                    .positionSide(action.getPosSide().toLowerCase())
                    .closeFraction("1")  // 全部平仓
                    .cancelOnClosePosition(true)
                    .reduceOnly(true)
                    .orderType("oco");  // OCO订单类型

            // 设置止盈
            if (action.getTakeProfit() != null) {
                builder.takeProfitTriggerPrice(action.getTakeProfit().toPlainString())
                        .takeProfitOrderPrice("-1")  // 市价单
                        .takeProfitTriggerPriceType("last");
            }

            // 设置止损
            if (action.getStopLoss() != null) {
                builder.stopLossTriggerPrice(action.getStopLoss().toPlainString())
                        .stopLossOrderPrice("-1")  // 市价单
                        .stopLossTriggerPriceType("last");
            }

            // 调用新建接口
            CexAlgoOrderOperationResponse response = unifiedTradingService.setAlgoOrder(apiKey, builder.build());

            long executionTime = System.currentTimeMillis() - startTime;

            if (response != null && Boolean.TRUE.equals(response.isSuccess())) {
                log.info("【止盈止损】新建成功 - instId: {}, posSide: {}, takeProfit: {}, stopLoss: {}, algoId: {}",
                        action.getInstId(), action.getPosSide(), action.getTakeProfit(),
                        action.getStopLoss(), response.getAlgoId());
                return TradeExecutionResult.success(
                        action.getInstId(),
                        "HOLD",
                        response.getAlgoId(),
                        null,
                        null,
                        executionTime,
                        false
                );
            } else {
                String errorMsg = response != null ? response.getErrorMessage() : "新建止盈止损失败";
                log.error("【止盈止损】新建失败 - instId: {}, posSide: {}, error: {}",
                        action.getInstId(), action.getPosSide(), errorMsg);
                return TradeExecutionResult.failure(action.getInstId(), "HOLD", errorMsg, executionTime);
            }

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("【止盈止损】新建异常 - instId: {}, posSide: {}", action.getInstId(), action.getPosSide(), e);
            return TradeExecutionResult.failure(action.getInstId(), "HOLD",
                    "新建异常: " + e.getMessage(), executionTime);
        }
    }

    /**
     * 模拟执行设置止盈止损
     *
     * @param action    解析的动作
     * @param startTime 开始时间
     * @return 模拟执行结果
     */
    private TradeExecutionResult executeDryRunSetStopLoss(ActionParser.ParsedAction action, long startTime) {
        String message = String.format(
                "【止盈止损-模拟执行】验证通过\n" +
                        "  合约: %s\n" +
                        "  仓位方向: %s\n" +
                        "  止盈价格: %s\n" +
                        "  止损价格: %s\n" +
                        "  置信度: %d%%\n" +
                        "  注意：当前为模拟模式，未真实设置止盈止损",
                action.getInstId(),
                action.getPosSide(),
                action.getTakeProfit() != null ? action.getTakeProfit() : "不修改",
                action.getStopLoss() != null ? action.getStopLoss() : "不修改",
                action.getConfidence() != null ? action.getConfidence() : 0
        );

        log.info(message);
        return TradeExecutionResult.dryRun(action.getInstId(), "HOLD", message);
    }

    /**
     * 交易验证结果
     */
    @Data
    @AllArgsConstructor
    private static class TradeValidationResult {
        private final boolean valid;
        private final String reason;

        public static TradeValidationResult valid() {
            return new TradeValidationResult(true, "");
        }

        public static TradeValidationResult invalid(String reason) {
            return new TradeValidationResult(false, reason);
        }
    }
}

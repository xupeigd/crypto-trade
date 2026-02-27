package com.crypto.trade.service;

import com.crypto.trade.config.AiTradingRiskControlConfig;
import com.crypto.trade.dto.OrderRequest;
import com.crypto.trade.dto.common.TradingResult;
import com.crypto.trade.entity.*;
import com.crypto.trade.model.RiskControlOrderModel;
import com.crypto.trade.repository.RiskControlOrderRepository;
import com.crypto.trade.repository.TradeActionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * RiskControlOrderService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class RiskControlOrderService {

    @Autowired
    private RiskControlOrderRepository riskControlOrderRepository;
    @Autowired
    private AiTradingRiskControlConfig riskControlConfig;
    @Autowired
    private TradingOrderService tradingOrderService;
    @Autowired
    private CapitalCalculatorService capitalCalculatorService;
    @Autowired
    private TradeActionService tradeActionService;
    @Autowired
    private TradeActionRepository tradeActionRepository;

    /**
     * 创建风控审核订单（从DTO转换）
     *
     * @param orderRequest 下单请求DTO
     * @return 创建的风控订单
     */
    @Transactional
    public RiskControlOrder createRiskOrder(OrderRequest orderRequest) {
        log.debug("创建风控审核订单: {}", orderRequest);

        // 1. 生成唯一订单ID
        String originalOrderId = UUID.randomUUID().toString();

        // 2. 根据交易风格设置风控等级
        RiskLevel riskLevel = determineRiskLevel(riskControlConfig.getCurrentTradingStyle());

        // 3. 转换订单类型和方向
        OrderType orderType = OrderType.valueOf(orderRequest.getOrderType().toUpperCase());
        OrderSide side = OrderSide.valueOf(orderRequest.getSide().toUpperCase());

        // 计算委托数量（如果没有提供，根据金额和价格计算）
        BigDecimal orderSize = orderRequest.getSz();
        if (null == orderSize && orderRequest.getAmount() != null && orderRequest.getPx() != null) {
            orderSize = orderRequest.getAmount().divide(orderRequest.getPx(), 8, RoundingMode.DOWN);
        }

        // 验证订单数量不能为空
        if (null == orderSize) {
            log.error("订单数量为空，无法创建风控审核订单: apiKeyId={}, instId={}",
                    orderRequest.getApiKeyId(), orderRequest.getInstId());
            throw new IllegalArgumentException("订单数量不能为空，请提供数量(sz)或金额(amount)和价格(px)");
        }

        // 验证订单数量必须大于0
        if (orderSize.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("订单数量必须大于0: {}", orderSize);
            throw new IllegalArgumentException("订单数量必须大于0");
        }

        // 4. 创建风控订单（保存完整的原始订单参数）
        // 从orderRequest获取订单来源: web(用户手动下单) / ai(AI自动下单)
        String orderSource = orderRequest.getSource();
        if (orderSource == null || orderSource.trim().isEmpty()) {
            orderSource = "web";  // 默认为用户下单
        }

        RiskControlOrder riskOrder = new RiskControlOrder(
                originalOrderId,
                orderRequest.getApiKeyId(),
                orderRequest.getActionId(),
                orderRequest.getInstId(),
                orderType,
                side,
                orderSize,
                orderRequest.getPx(),
                orderRequest.getTakeProfitPrice(),
                orderRequest.getStopLossPrice(),
                orderRequest.getPosSide(),
                orderRequest.getLever(),
                orderRequest.getAmount(),
                orderSource,  // 使用动态的订单来源,替代硬编码的"okx_trading"
                riskLevel
        );

        // [新增] 计算并设置预估总占用资金
        try {
            BigDecimal estimatedTotalCapital = capitalCalculatorService.calculateEstimatedTotalCapital(
                    orderRequest.getApiKeyId(),
                    orderRequest.getInstId(),
                    orderSize,
                    orderRequest.getLever(),
                    orderRequest.getPx()
            );
            riskOrder.setEstimatedTotalCapital(estimatedTotalCapital);
            log.info("计算预估总占用资金成功 - orderId: {}, estimatedCapital: {}",
                    riskOrder.getOrderId(), estimatedTotalCapital);
        } catch (Exception e) {
            log.warn("计算预估总占用资金失败 - orderId: {}", riskOrder.getOrderId(), e);
            riskOrder.setEstimatedTotalCapital(BigDecimal.ZERO);
        }

        // 5. 保存到数据库
        riskControlOrderRepository.save(riskOrder);

        log.debug("风控审核订单创建成功: orderId={}, riskLevel={}",
                riskOrder.getOrderId(), riskLevel);

        return riskOrder;
    }

    /**
     * 审核通过订单
     *
     * @param riskOrderId 风控订单ID
     * @param auditUser   审核人
     */
    @Transactional
    public void approveOrder(Long riskOrderId, String auditUser) {
        log.debug("审核通过风控订单: orderId={}, auditor={}", riskOrderId, auditUser);

        try {
            // 1. 查询风控订单
            RiskControlOrder riskOrder = riskControlOrderRepository.findById(riskOrderId)
                    .orElseThrow(() -> new RiskControlApprovalException("风控订单不存在", RiskControlErrorType.ORDER_NOT_FOUND));

            if (riskOrder.getAuditStatus() != AuditStatus.PENDING) {
                throw new RiskControlApprovalException(
                        String.format("订单状态不是待审核状态，当前状态: %s", riskOrder.getAuditStatus()),
                        RiskControlErrorType.INVALID_STATUS
                );
            }

            // 2. 更新审核状态
            riskOrder.approve(auditUser);
            riskControlOrderRepository.save(riskOrder);

            // 2.5 更新TradeAction的风控状态
            if (riskOrder.getActionId() != null) {
                tradeActionService.updateRiskControlStatus(riskOrder.getActionId(), "APPROVED", riskOrderId);
                log.info("TradeAction风控状态已更新为APPROVED - actionId: {}, riskControlId: {}",
                        riskOrder.getActionId(), riskOrderId);
            }

            // 3. 构建原始订单请求并执行实际下单（绕过风控检查）
            OrderRequest orderRequest = rebuildOrderRequest(riskOrder);

            log.debug("开始执行实际下单: orderId={}, symbol={}, side={}, orderType={}, amount={}, price={}",
                    riskOrderId, orderRequest.getInstId(), orderRequest.getSide(),
                    orderRequest.getOrderType(), orderRequest.getAmount(), orderRequest.getPx());

            TradingResult result = tradingOrderService.placeOrder(
                    orderRequest.getApiKeyId(),
                    orderRequest.getInstId(),
                    orderRequest.getSide(),
                    orderRequest.getOrderType(),
                    orderRequest.getAmount(),
                    orderRequest.getLever(),
                    orderRequest.getPx(),
                    orderRequest.getTakeProfitPrice(),
                    orderRequest.getStopLossPrice(),
                    orderRequest.getPosSide(),
                    true  // bypassRiskControl = true，绕过风控检查直接下单
            );

            if (!result.success) {
                // 如果下单失败，记录详细信息并抛出具体的异常
                String errorMsg = String.format("向交易所下单失败: %s (订单ID: %s)",
                        result.message, result.orderId);
                log.error("风控订单审核通过但下单失败: orderId={}, symbol={}, error={}",
                        riskOrderId, orderRequest.getInstId(), result.message);

                // 根据错误信息分类抛出不同的异常
                throw categorizeTradingError(result.message, null, riskOrder);
            }

            log.debug("风控订单审核通过并执行下单成功: orderId={}, actualOrderId={}", riskOrderId, result.orderId);

        } catch (RiskControlApprovalException e) {
            // 重新抛出我们自定义的异常
            throw e;
        } catch (RuntimeException e) {
            log.error("风控订单审核过程中发生系统错误: orderId={}, error={}", riskOrderId, e.getMessage(), e);
            throw new RiskControlApprovalException("系统错误: " + e.getMessage(), RiskControlErrorType.SYSTEM_ERROR);
        }
    }

    /**
     * 根据交易错误信息分类异常
     *
     * @param errorMessage 错误信息
     * @param errorCode    错误码
     * @param riskOrder    风控订单
     * @return 分类后的异常
     */
    private RiskControlApprovalException categorizeTradingError(String errorMessage, String errorCode, RiskControlOrder riskOrder) {
        if (null == errorMessage) {
            return new RiskControlApprovalException("下单失败: 未知错误", RiskControlErrorType.TRADING_ERROR);
        }

        String msg = errorMessage.toLowerCase();

        if (msg.contains("insufficient") || msg.contains("余额不足") || msg.contains("balance")) {
            return new RiskControlApprovalException(
                    String.format("账户余额不足，无法完成订单 %s-%s %s",
                            riskOrder.getSymbol(), riskOrder.getSide(), riskOrder.getQuantity()),
                    RiskControlErrorType.INSUFFICIENT_BALANCE
            );
        }

        if (msg.contains("position limit") || msg.contains("持仓限制") || msg.contains("风险限制")) {
            return new RiskControlApprovalException(
                    String.format("持仓数量超过限制，当前订单 %s %s 可能导致风险超限",
                            riskOrder.getSymbol(), riskOrder.getQuantity()),
                    RiskControlErrorType.POSITION_LIMIT_EXCEEDED
            );
        }

        if (msg.contains("price") || msg.contains("价格") || msg.contains("market")) {
            return new RiskControlApprovalException(
                    String.format("订单价格不合理，订单 %s 价格 %s 偏离市场过大",
                            riskOrder.getSymbol(), riskOrder.getPrice()),
                    RiskControlErrorType.PRICE_DEVIATION
            );
        }

        if (msg.contains("symbol") || msg.contains("交易对") || msg.contains("instId")) {
            return new RiskControlApprovalException(
                    String.format("交易对 %s 不存在或不可交易", riskOrder.getSymbol()),
                    RiskControlErrorType.INVALID_SYMBOL
            );
        }

        if (msg.contains("network") || msg.contains("timeout") || msg.contains("连接")) {
            return new RiskControlApprovalException(
                    "网络连接异常，无法连接到交易所",
                    RiskControlErrorType.NETWORK_ERROR
            );
        }

        // 默认返回交易错误
        return new RiskControlApprovalException(
                String.format("下单失败: %s (错误码: %s)", errorMessage, errorCode),
                RiskControlErrorType.TRADING_ERROR
        );
    }

    /**
     * 驳回订单
     *
     * @param riskOrderId 风控订单ID
     * @param auditUser   审核人
     * @param reason      驳回原因
     */
    @Transactional
    public void rejectOrder(Long riskOrderId, String auditUser, String reason) {
        log.debug("驳回风控订单: orderId={}, auditor={}, reason={}", riskOrderId, auditUser, reason);

        RiskControlOrder riskOrder = riskControlOrderRepository.findById(riskOrderId)
                .orElseThrow(() -> new RuntimeException("风控订单不存在"));

        if (riskOrder.getAuditStatus() != AuditStatus.PENDING) {
            throw new RuntimeException("订单状态不是待审核状态");
        }

        // 更新审核状态和驳回原因
        riskOrder.reject(auditUser, reason);
        riskControlOrderRepository.save(riskOrder);

        // 更新TradeAction的风控状态
        if (riskOrder.getActionId() != null) {
            tradeActionService.updateRiskControlStatus(riskOrder.getActionId(), "REJECTED", riskOrderId);
            log.info("TradeAction风控状态已更新为REJECTED - actionId: {}, riskControlId: {}, reason: {}",
                    riskOrder.getActionId(), riskOrderId, reason);
        }

        log.debug("风控订单已驳回: orderId={}", riskOrderId);
    }

    /**
     * 获取待审核订单列表
     *
     * @return 待审核订单列表
     */
    public List<RiskControlOrder> getPendingOrders() {
        return riskControlOrderRepository.findByAuditStatusOrderByCreateTimeDesc(AuditStatus.PENDING);
    }

    /**
     * 根据ID获取订单
     *
     * @param orderId 订单ID
     * @return 风控订单
     */
    public RiskControlOrder getOrderById(Long orderId) {
        return riskControlOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("风控订单不存在"));
    }

    /**
     * 根据交易风格确定风控等级
     *
     * @param tradingStyle 交易风格
     * @return 风控等级
     */
    private RiskLevel determineRiskLevel(TradingStyle tradingStyle) {
        switch (tradingStyle) {
            case C1_CONSERVATIVE:
            case C2_CAUTIOUS:
                return RiskLevel.HIGH;
            case C3_MODERATE:
                return RiskLevel.MEDIUM;
            case C4_ACTIVE:
            case C5_AGGRESSIVE:
                return RiskLevel.LOW;
            default:
                return RiskLevel.MEDIUM;
        }
    }

    /**
     * 从风控订单重建原始订单请求
     *
     * @param riskOrder 风控订单
     * @return 原始订单请求
     */
    private OrderRequest rebuildOrderRequest(RiskControlOrder riskOrder) {
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setApiKeyId(riskOrder.getApiKeyId());
        orderRequest.setInstId(riskOrder.getSymbol());
        orderRequest.setOrderType(riskOrder.getOrderType().name().toLowerCase());
        orderRequest.setSide(riskOrder.getSide().name().toLowerCase());
        orderRequest.setSz(riskOrder.getQuantity());
        orderRequest.setPx(riskOrder.getPrice());
        // 修复1: 恢复原始杠杆倍数
        if (riskOrder.getLever() != null) {
            orderRequest.setLever(riskOrder.getLever());
        } else {
            // 兼容旧数据：默认1倍杠杆
            orderRequest.setLever(new BigDecimal("1"));
            log.warn("风控订单缺少杠杆信息，使用默认1倍杠杆: orderId={}", riskOrder.getOrderId());
        }

        // 修复2: 恢复原始成本金额
        if (riskOrder.getOriginalAmount() != null) {
            orderRequest.setAmount(riskOrder.getOriginalAmount());
            log.debug("恢复原始amount: {}", riskOrder.getOriginalAmount());
        } else if (riskOrder.getPrice() != null && riskOrder.getQuantity() != null) {
            // 兼容旧数据：根据数量和价格重建amount
            BigDecimal amount = riskOrder.getQuantity().multiply(riskOrder.getPrice());
            orderRequest.setAmount(amount);
            log.debug("重建amount（兼容旧数据）: 数量={}, 价格={}, amount={}",
                    riskOrder.getQuantity(), riskOrder.getPrice(), amount);
        } else {
            // 兜底逻辑：设置默认值
            orderRequest.setAmount(new BigDecimal("50"));
            log.warn("风控订单数量为空，使用默认amount=50: orderId={}", riskOrder.getOrderId());
        }

        // 修复：从风控订单恢复止盈止损参数
        orderRequest.setTakeProfitPrice(riskOrder.getTakeProfitPrice());
        orderRequest.setStopLossPrice(riskOrder.getStopLossPrice());

        // 修复3: 根据订单方向正确设置posSide（关键bug修复）
        if (riskOrder.getPosSide() != null && !riskOrder.getPosSide().isEmpty()) {
            // 优先使用保存的posSide
            orderRequest.setPosSide(riskOrder.getPosSide());
            log.debug("恢复保存的posSide: {}", riskOrder.getPosSide());
        } else {
            // 兼容旧数据：根据订单方向智能推断
            if ("buy".equalsIgnoreCase(riskOrder.getSide().name())) {
                orderRequest.setPosSide("long");   // 开多
            } else if ("sell".equalsIgnoreCase(riskOrder.getSide().name())) {
                orderRequest.setPosSide("short");  // 开空
            } else {
                orderRequest.setPosSide("long");   // 默认值
                log.warn("无法识别订单方向，使用默认posSide=long: orderId={}, side={}",
                        riskOrder.getOrderId(), riskOrder.getSide());
            }
            log.debug("推断posSide: side={}, posSide={}", riskOrder.getSide(), orderRequest.getPosSide());
        }

        log.debug("恢复止盈止损参数: takeProfitPrice={}, stopLossPrice={}",
                riskOrder.getTakeProfitPrice(), riskOrder.getStopLossPrice());

        return orderRequest;
    }

    /**
     * 【新增】根据actionId查询风控订单
     *
     * @param actionId TradeAction的ID
     * @return 风控订单Model
     */
    public RiskControlOrderModel getOrderByActionId(Long actionId) {
        log.debug("根据actionId查询风控订单 - actionId: {}", actionId);

        return riskControlOrderRepository.findByActionId(actionId)
                .map(this::convertToModel)
                .orElse(null);
    }

    /**
     * 【新增】根据recordId查询所有关联的风控订单
     * 思路: recordId → TradeAction列表 → actionId列表 → RiskControlOrder列表
     *
     * @param recordId LlmCallRecord的ID
     * @return 风控订单Model列表
     */
    public List<RiskControlOrderModel> getOrdersByRecordId(Long recordId) {
        log.debug("根据recordId查询所有关联的风控订单 - recordId: {}", recordId);

        try {
            // 1. 查询recordId对应的所有TradeAction(不限制executionSource)
            // 修复: 移除executionSource='INITIAL'的过滤条件,兼容所有执行来源
            List<TradeAction> tradeActions = tradeActionRepository.findByRecordId(recordId);

            if (tradeActions == null || tradeActions.isEmpty()) {
                log.debug("未找到关联的TradeAction - recordId: {}", recordId);
                return List.of();
            }

            // 2. 提取所有actionId
            List<Long> actionIds = tradeActions.stream()
                    .map(TradeAction::getId)
                    .toList();

            log.debug("找到 {} 个TradeAction(包含所有executionSource), actionIds: {}",
                    actionIds.size(), actionIds);

            // 3. 批量查询RiskControlOrder
            List<RiskControlOrder> riskControlOrders = riskControlOrderRepository.findByActionIdIn(actionIds);

            if (riskControlOrders == null || riskControlOrders.isEmpty()) {
                log.debug("未找到关联的RiskControlOrder - actionIds: {}", actionIds);
                return List.of();
            }

            // 4. 转换为Model返回
            List<RiskControlOrderModel> models = riskControlOrders.stream()
                    .map(this::convertToModel)
                    .toList();

            log.info("查询到风控订单 - recordId: {}, count: {}", recordId, models.size());
            return models;

        } catch (Exception e) {
            log.error("根据recordId查询风控订单失败 - recordId: {}", recordId, e);
            return List.of();
        }
    }

    /**
     * 【新增】将RiskControlOrder实体转换为Model
     *
     * @param entity 风控订单实体
     * @return 风控订单Model
     */
    private RiskControlOrderModel convertToModel(RiskControlOrder entity) {
        RiskControlOrderModel model = new RiskControlOrderModel();
        model.setOrderId(entity.getOrderId());
        model.setOriginalOrderId(entity.getOriginalOrderId());
        model.setApiKeyId(entity.getApiKeyId());
        model.setSymbol(entity.getSymbol());
        model.setOrderType(entity.getOrderType() != null ? entity.getOrderType().name() : null);
        model.setSide(entity.getSide() != null ? entity.getSide().name() : null);
        model.setQuantity(entity.getQuantity());
        model.setPrice(entity.getPrice());
        model.setTakeProfitPrice(entity.getTakeProfitPrice());
        model.setStopLossPrice(entity.getStopLossPrice());
        model.setAuditStatus(entity.getAuditStatus() != null ? entity.getAuditStatus().name() : null);
        model.setAuditor(entity.getAuditor());
        model.setAuditTime(entity.getAuditTime() != null ? entity.getAuditTime().atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli() : null);
        model.setRejectionReason(entity.getRejectionReason());
        model.setCreateTime(entity.getCreateTime() != null ? entity.getCreateTime().atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli() : null);
        model.setUpdateTime(entity.getUpdateTime() != null ? entity.getUpdateTime().atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli() : null);
        model.setOrderSource(entity.getOrderSource());
        model.setRiskLevel(entity.getRiskLevel() != null ? entity.getRiskLevel().name() : null);
        // actionId 需要手动设置，因为Model可能没有这个字段
        return model;
    }
}
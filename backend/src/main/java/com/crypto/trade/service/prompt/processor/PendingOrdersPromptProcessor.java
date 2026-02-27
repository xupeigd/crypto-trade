package com.crypto.trade.service.prompt.processor;

import com.crypto.trade.model.OrderModel;
import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.service.CapitalCalculatorService;
import com.crypto.trade.service.TradingOrderService;
import com.crypto.trade.service.prompt.AbstractPromptProcessor;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.service.prompt.PromptProcessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PendingOrdersPromptProcessor
 * 处理器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOrdersPromptProcessor
        extends AbstractPromptProcessor {

    private final TradingOrderService tradingOrderService;
    private final CapitalCalculatorService capitalCalculatorService;

    @Override
    public String getName() {
        return "PendingOrdersPromptProcessor";
    }

    @Override
    public int getPriority() {
        return 35; // 略低于持仓信息(30)
    }

    @Override
    public boolean shouldExecute(PromptContext context) {
        return getBooleanParameter("enablePendingOrders", true);
    }

    @Override
    public SegmentModel process(PromptContext context) throws PromptProcessException {
        return safeProcess(context, () -> {
            // 检查是否启用委托订单信息
            boolean enablePendingOrders = getBooleanParameter("enablePendingOrders", true);
            if (!enablePendingOrders) {
                return SegmentModel.builder()
                        .content("")
                        .showTitle(false)
                        .build();
            }

            // 获取委托订单数据
            List<OrderModel> pendingOrders = getPendingOrders(context);

            String content;
            if (pendingOrders == null || pendingOrders.isEmpty()) {
                content = "\n\n当前无委托订单";
                log.debug("无委托订单数据");
            } else {
                content = formatPendingOrdersAsTable(pendingOrders, context);
                log.debug("格式化委托订单信息成功，数量: {}", pendingOrders.size());
            }

            return SegmentModel.builder()
                    .title("等待成交订单")
                    .content(content)
                    .category(SegmentModel.Category.DATA)
                    .priority(SegmentModel.Priority.MEDIUM)
                    .showTitle(true)
                    .addMetadata("processor", getName())
                    .addMetadata("processorPriority", getPriority())
                    .addMetadata("enablePendingOrders", enablePendingOrders)
                    .addMetadata("orderCount", pendingOrders != null ? pendingOrders.size() : 0)
                    .addMetadata("dataStatus", pendingOrders != null ? "SUCCESS" : "UNAVAILABLE")
                    .build();
        });
    }

    /**
     * 获取委托订单数据
     */
    private List<OrderModel> getPendingOrders(PromptContext context) {
        try {
            Long apiKeyId = context.getApiKeyId();
            if (apiKeyId == null) {
                log.warn("PromptContext缺少apiKeyId，无法获取委托订单");
                return null;
            }
            return tradingOrderService.getPendingOrders(apiKeyId, false);
        } catch (Exception e) {
            log.error("获取委托订单失败", e);
            return null;
        }
    }

    /**
     * 格式化委托订单为表格
     */
    private String formatPendingOrdersAsTable(List<OrderModel> orders, PromptContext context) {
        StringBuilder tableContent = new StringBuilder();

        // 表格头（包含"订单ID"和"预计资金"列）
        tableContent.append("| 订单ID | 合约 | 方向 | 类型 | 价格 | 数量 | 杠杆 | 预计资金 | 状态 | 时间 |\n");
        tableContent.append("|--------|------|------|------|------|------|------|----------|------|------|\n");

        for (OrderModel order : orders) {
            tableContent.append(formatOrderAsTableRow(order, context)).append("\n");
        }

        return tableContent.toString();
    }

    /**
     * 格式化单个订单为表格行
     */
    private String formatOrderAsTableRow(OrderModel order, PromptContext context) {
        try {
            // 获取订单ID（新增）
            String orderId = order.getOrdId();
            String orderIdDisplay = (orderId != null && !orderId.isEmpty()) ? orderId : "-";

            String contractName = order.getInstId();
            String direction = getChineseDirection(order.getSide(), order.getPosSide());
            String type = getChineseOrderType(order.getOrdType());
            String price = formatDecimalValue(order.getPx());
            String quantity = formatDecimalValue(order.getSz());
            String leverage = formatLeverage(order.getLever());
            String estimatedCapital = calculateEstimatedCapital(order, context);  // 新增资金计算
            String state = getChineseOrderState(order.getState());
            String time = formatTime(order.getCTime());

            return String.format("| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |",
                    orderIdDisplay,  // 新增订单ID列
                    contractName, direction, type, price, quantity, leverage,
                    estimatedCapital,  // 资金列
                    state, time);

        } catch (Exception e) {
            log.debug("格式化订单行失败: {}", order.getOrdId(), e);
            return "| - | - | - | - | - | - | - | - | - | - |";
        }
    }

    /**
     * 获取中文买卖方向
     */
    private String getChineseDirection(String side, String posSide) {
        if (!StringUtils.hasText(side)) return "未知";

        String sideText = side.toLowerCase();
        String posSideText = StringUtils.hasText(posSide) ? posSide.toLowerCase() : "";

        if ("buy".equals(sideText)) {
            if ("long".equals(posSideText)) return "开多";
            if ("short".equals(posSideText)) return "平空";
            return "买入";
        } else if ("sell".equals(sideText)) {
            if ("long".equals(posSideText)) return "平多";
            if ("short".equals(posSideText)) return "开空";
            return "卖出";
        }
        return side;
    }

    /**
     * 获取中文订单类型
     */
    private String getChineseOrderType(String ordType) {
        if (!StringUtils.hasText(ordType)) return "未知";
        switch (ordType.toLowerCase()) {
            case "market":
                return "市价";
            case "limit":
                return "限价";
            case "post_only":
                return "只做maker";
            case "fok":
                return "全部成交或撤销";
            case "ioc":
                return "立即成交并撤销";
            case "optimal_limit_ioc":
                return "市价委托";
            default:
                return ordType;
        }
    }

    /**
     * 获取中文订单状态
     */
    private String getChineseOrderState(String state) {
        if (!StringUtils.hasText(state)) return "未知";
        switch (state.toLowerCase()) {
            case "live":
                return "等待成交";
            case "partially_filled":
                return "部分成交";
            case "filled":
                return "完全成交";
            case "canceled":
                return "已撤销";
            default:
                return state;
        }
    }

    /**
     * 格式化数值
     */
    private String formatDecimalValue(BigDecimal value) {
        if (value == null) return "-";
        return value.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /**
     * 格式化杠杆
     */
    private String formatLeverage(BigDecimal leverage) {
        if (leverage == null) return "-";
        return leverage.setScale(0, RoundingMode.DOWN).toPlainString() + "x";
    }

    /**
     * 格式化时间
     */
    private String formatTime(Long timestamp) {
        if (timestamp == null) return "-";
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("Asia/Shanghai"))
                    .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return String.valueOf(timestamp);
        }
    }

    /**
     * 计算订单的预计占用资金
     * <p>
     * 调用 CapitalCalculatorService 计算预估总占用资金（保证金+手续费）
     * </p>
     *
     * @param order   订单信息
     * @param context Prompt上下文（包含 apiKeyId）
     * @return 格式化的资金字符串，如 "123.45 USDT" 或 "N/A"
     */
    private String calculateEstimatedCapital(OrderModel order, PromptContext context) {
        try {
            // 获取 apiKeyId
            Long apiKeyId = context.getApiKeyId();
            if (apiKeyId == null) {
                log.warn("PromptContext 缺少 apiKeyId，无法计算预计资金");
                return "N/A";
            }

            // 验证必要参数
            if (order.getInstId() == null || order.getSz() == null || order.getLever() == null) {
                log.warn("订单缺少必要参数，无法计算预计资金 - instId: {}", order.getInstId());
                return "N/A";
            }

            // 市价单的 price 为 null，需要特殊处理
            BigDecimal price = order.getPx();
            if (price == null) {
                // 市价单无法准确预估，返回特殊标记
                return "市价单";
            }

            // 调用资金计算服务
            BigDecimal estimatedCapital = capitalCalculatorService.calculateEstimatedTotalCapital(
                    apiKeyId,
                    order.getInstId(),
                    order.getSz(),
                    order.getLever(),
                    price
            );

            // 格式化输出（保留2位小数）
            return estimatedCapital.setScale(2, RoundingMode.HALF_UP).toPlainString() + " USDT";

        } catch (Exception e) {
            log.warn("计算预计资金失败 - instId: {}, ordId: {}", order.getInstId(), order.getOrdId(), e);
            return "N/A";
        }
    }
}

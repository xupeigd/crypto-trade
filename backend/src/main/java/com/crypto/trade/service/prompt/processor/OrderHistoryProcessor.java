package com.crypto.trade.service.prompt.processor;

import com.crypto.trade.entity.TradingOrder;
import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.service.TradingOrderService;
import com.crypto.trade.service.prompt.AbstractPromptProcessor;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.service.prompt.PromptProcessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * OrderHistoryProcessor
 * 处理器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class OrderHistoryProcessor
        extends AbstractPromptProcessor {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    @Autowired
    TradingOrderService tradingOrderService;

    @Override
    public String getName() {
        return "OrderHistoryProcessor";
    }

    @Override
    public int getPriority() {
        return 29; // 在仓位历史之前执行
    }

    @Override
    public boolean shouldExecute(PromptContext context) {
        return getBooleanParameter("enableOrderHistory", true);
    }

    @Override
    public SegmentModel process(PromptContext context) throws PromptProcessException {
        return safeProcess(context, () -> {
            // 检查是否启用订单历史
            boolean enableOrderHistory = getBooleanParameter("enableOrderHistory", true);
            if (!enableOrderHistory) {
                return SegmentModel.builder()
                        .content("")
                        .showTitle(false)
                        .build();
            }

            // 获取最近10个历史订单（最近30天的已完成订单）
            List<TradingOrder> history = tradingOrderService.getHistoryOrders(context.getApiKeyId(), 30);

            // 限制为最近10条
            if (history != null && history.size() > 10) {
                history = history.subList(0, 10);
            }

            if (history == null || history.isEmpty()) {
                return SegmentModel.builder()
                        .title("订单历史")
                        .content("暂无历史订单数据")
                        .category(SegmentModel.Category.DATA)
                        .priority(SegmentModel.Priority.MEDIUM)
                        .showTitle(true)
                        .addMetadata("processor", getName())
                        .addMetadata("processorPriority", getPriority())
                        .addMetadata("enableOrderHistory", enableOrderHistory)
                        .addMetadata("dataStatus", "EMPTY")
                        .build();
            }

            // 格式化为表格
            String content = formatAsTable(history);

            return SegmentModel.builder()
                    .title("订单历史")
                    .content(content)
                    .category(SegmentModel.Category.DATA)
                    .priority(SegmentModel.Priority.MEDIUM)
                    .showTitle(true)
                    .addMetadata("processor", getName())
                    .addMetadata("processorPriority", getPriority())
                    .addMetadata("enableOrderHistory", enableOrderHistory)
                    .addMetadata("dataStatus", "SUCCESS")
                    .addMetadata("count", history.size())
                    .build();
        });
    }

    /**
     * 格式化为表格形式
     */
    private String formatAsTable(List<TradingOrder> history) {
        StringBuilder tableContent = new StringBuilder();
        tableContent.append("### 订单历史（最近10条）\n\n");
        tableContent.append("| 合约 | 方向 | 数量 | 状态 |\n");
        tableContent.append("|------|------|------|------|\n");

        for (TradingOrder order : history) {
            String instId = order.getInstId();
            String direction = formatDirection(order.getSide(), order.getPosSide());
            String quantity = formatQuantity(order.getSz());
            String status = formatStatus(order.getOrderStatus());

            tableContent.append(String.format("| %s | %s | %s | %s |\n",
                    instId, direction, quantity, status));
        }

        return tableContent.toString();
    }

    /**
     * 格式化方向
     */
    private String formatDirection(String side, String posSide) {
        if (side == null) {
            return "-";
        }

        // side: buy(买入)/sell(卖出)
        // posSide: long(做多)/short(做空)
        // 组合后：
        // buy + long = 开多
        // buy + short = 平空
        // sell + long = 平多
        // sell + short = 开空
        if (posSide == null) {
            // 如果没有持仓方向，直接使用side
            return switch (side.toLowerCase()) {
                case "buy" -> "开多";
                case "sell" -> "开空";
                default -> side;
            };
        }

        String lowerSide = side.toLowerCase();
        String lowerPosSide = posSide.toLowerCase();

        return switch (lowerSide) {
            case "buy" -> {
                if ("long".equals(lowerPosSide)) {
                    yield "开多";
                } else if ("short".equals(lowerPosSide)) {
                    yield "平空";
                } else {
                    yield side;
                }
            }
            case "sell" -> {
                if ("long".equals(lowerPosSide)) {
                    yield "平多";
                } else if ("short".equals(lowerPosSide)) {
                    yield "开空";
                } else {
                    yield side;
                }
            }
            default -> side;
        };
    }

    /**
     * 格式化数量
     */
    private String formatQuantity(java.math.BigDecimal quantity) {
        if (quantity == null) {
            return "-";
        }
        return quantity.abs().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 格式化状态
     */
    private String formatStatus(String orderStatus) {
        if (orderStatus == null) {
            return "-";
        }
        return switch (orderStatus.toLowerCase()) {
            case "success" -> "已成交";
            case "canceled" -> "已撤销";
            case "submitted" -> "委托中";
            case "failed" -> "委托失败";
            case "pending" -> "准备中";
            case "canceling" -> "撤销中";
            default -> orderStatus;
        };
    }
}

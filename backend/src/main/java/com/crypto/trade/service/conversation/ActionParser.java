package com.crypto.trade.service.conversation;

import com.crypto.trade.dto.AiResponseParseResult;
import com.crypto.trade.entity.OrderType;
import com.crypto.trade.entity.TradeAction;
import com.crypto.trade.enums.OpenCloseType;
import com.crypto.trade.util.JsonUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * ActionParser
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class ActionParser {

    /**
     * 解析AI响应中的工具调用动作
     * 支持新的actions数组格式和原有的单个action格式
     *
     * @param aiResponse AI响应文本
     * @return 解析出的动作列表
     */
    public static ActionPack parseActionPack(String aiResponse) {
        if (!StringUtils.hasText(aiResponse)) {
            return null;
        }
        return JsonUtils.parseTo(aiResponse, ActionPack.class);
    }

    public static boolean containsToolCall(ActionPack actionPack) {
        return null != actionPack && !CollectionUtils.isEmpty(actionPack.getActions())
                && actionPack.getActions().stream()
                .anyMatch(v -> Objects.equals(ActionType.QUERY, v.getAction()));
    }

    /**
     * 检查响应是否包含工具调用
     */
    public static boolean containsToolCall(AiResponseParseResult aiResponse) {
        ActionPack actionPack = parseActionPack(aiResponse.getJsonContent());
        return containsToolCall(actionPack);
    }

    /**
     * 将TradeAction转换为ParsedAction（包含数据库生成的id）
     * 用于将保存后的TradeAction重新转换为ActionPack，以便更新aiResponse
     *
     * @param tradeAction 保存后的TradeAction（含id）
     * @return ParsedAction对象
     */
    public static ActionParser.ParsedAction convertTradeActionToParsedAction(TradeAction tradeAction) {
        return ActionParser.ParsedAction.builder()
                .id(tradeAction.getId())
                .recordId(tradeAction.getRecordId())  // ✅ 新增：设置recordId
                .action(ActionParser.ActionType.valueOf(tradeAction.getActionType()))
                .instId(tradeAction.getInstId())
                .posSide(tradeAction.getPosSide())
                .price(tradeAction.getPrice())
                .lever(tradeAction.getLever())
                .amount(tradeAction.getAmount())
                .orderType(tradeAction.getOrderType())
                .quantity(tradeAction.getQuantity())
                .takeProfit(tradeAction.getTakeProfit())
                .stopLoss(tradeAction.getStopLoss())
                .confidence(tradeAction.getConfidence())
                .reasoning(tradeAction.getReasoning())
                .timeframe(tradeAction.getTimeframe())
                .limit(tradeAction.getQueryLimit())
                .priority(tradeAction.getPriority())
                .orderId(tradeAction.getOrderId())
                // 只有BUY/SELL动作才设置openClose字段
                .openClose(("BUY".equals(tradeAction.getActionType()) || "SELL".equals(tradeAction.getActionType()))
                        ? tradeAction.getOpenClose() : null)
                .build();
    }

    public enum ActionType {
        QUERY,
        BUY,
        SELL,
        HOLD,
        ATTENTION,
        CANCEL_ORDER,  // 取消订单操作
        CANCEL,

    }

    /**
     * 解析后的动作对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedAction {

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long id;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long recordId;  // 调用记录ID

        @JsonInclude(JsonInclude.Include.NON_NULL)
        ActionType action;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer priority;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String instId;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String posSide;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String timeframe;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer limit;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        OrderType orderType;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal price;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer lever;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal amount;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal quantity;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal takeProfit;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal stopLoss;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer confidence;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String reasoning;

        /**
         * 开平仓类型 (默认开仓)
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        OpenCloseType openClose = null;

        /**
         * 订单ID
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String orderId;

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionPack {
        List<ParsedAction> actions;
    }

}
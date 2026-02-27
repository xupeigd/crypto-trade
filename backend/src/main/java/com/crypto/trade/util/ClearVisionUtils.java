package com.crypto.trade.util;

import com.crypto.trade.enums.OpenCloseType;
import com.crypto.trade.model.PositionModel;
import com.crypto.trade.service.conversation.ActionParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ClearVisionUtils
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
public class ClearVisionUtils {

    /**
     * 清除所有的json幻觉
     *
     * @param original 原始的ActionPack
     * @return 清理幻觉的ActionPack
     */
    public static ActionParser.ActionPack clearingIllusions(ActionParser.ActionPack original, List<PositionModel> positions) {
        if (null == original || CollectionUtils.isEmpty(original.getActions())) {
            return original;
        }
        List<ActionParser.ParsedAction> withoutIllusions = new ArrayList<>();
        Map<String, PositionModel> positionModelMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(positions)) {
            for (PositionModel position : positions) {
                positionModelMap.put(String.format("%s:%s", position.getPosSide(), position.getInstId()), position);
            }
        }

        for (ActionParser.ParsedAction parsedAction : original.getActions()) {
            switch (parsedAction.getAction()) {
                case QUERY:
                    withoutIllusions.add(ActionParser.ParsedAction.builder()
                            .instId(parsedAction.getInstId())
                            .action(ActionParser.ActionType.QUERY)
                            .timeframe(parsedAction.getTimeframe())
                            .limit(parsedAction.getLimit())
                            .confidence(parsedAction.getConfidence())
                            .reasoning(parsedAction.getReasoning())
                            .build());
                    break;
                case BUY, SELL:
                    ActionParser.ParsedAction transform = JsonUtils.transform(parsedAction, ActionParser.ParsedAction.class);
                    assert transform != null;
                    boolean isClose = OpenCloseType.CLOSE.equals(transform.getOpenClose());
                    if (isClose) {
                        // 平仓不需要杠杠倍数和金额
                        transform.setLever(null);
                        transform.setAmount(null);
                    }
                    withoutIllusions.add(transform);
                    break;
                case HOLD:
                    parsedAction.setOpenClose(null);
                    parsedAction.setLever(null);

                    if (StringUtils.hasText(parsedAction.getInstId()) && !"ALL".equalsIgnoreCase(parsedAction.getInstId())) {
                        // 场景1和2：有具体的instId
                        if (StringUtils.hasText(parsedAction.getPosSide())) {
                            // 场景1：有instId + 有posSide
                            PositionModel positionModel = positionModelMap.get(
                                    String.format("%s:%s", parsedAction.getPosSide(), parsedAction.getInstId())
                            );

                            if (null == positionModel) {
                                // 仓位不存在：转换为ATTENTION
                                log.info("【HOLD处理】仓位不存在，转换为ATTENTION - instId: {}, posSide: {}",
                                        parsedAction.getInstId(), parsedAction.getPosSide());

                                withoutIllusions.add(ActionParser.ParsedAction.builder()
                                        .instId(parsedAction.getInstId())
                                        .posSide(parsedAction.getPosSide())
                                        .priority(parsedAction.getPriority())
                                        .action(ActionParser.ActionType.ATTENTION)
                                        .confidence(parsedAction.getConfidence())
                                        .build());
                            } else {
                                // 仓位存在：保留完整HOLD
                                withoutIllusions.add(parsedAction);
                            }
                        } else {
                            // 场景2：有instId + 无posSide
                            // 无法判断仓位是否存在，转换为ATTENTION
                            log.info("【HOLD处理】缺少posSide，转换为ATTENTION - instId: {}", parsedAction.getInstId());

                            withoutIllusions.add(ActionParser.ParsedAction.builder()
                                    .instId(parsedAction.getInstId())
                                    // 不设置posSide（因为为null）
                                    .priority(parsedAction.getPriority())
                                    .action(ActionParser.ActionType.ATTENTION)
                                    .confidence(parsedAction.getConfidence())
                                    .build());
                        }
                    } else {
                        // 场景3：无instId或instId="ALL"
                        // 简化HOLD，只保留action、confidence、reasoning
                        log.info("【HOLD处理】无具体instId，简化HOLD - instId: {}", parsedAction.getInstId());

                        withoutIllusions.add(ActionParser.ParsedAction.builder()
                                .action(ActionParser.ActionType.HOLD)
                                .confidence(parsedAction.getConfidence())
                                .reasoning(parsedAction.getReasoning())
                                .build());
                    }
                    break;
                case ATTENTION:
                    // 清除openClose字段（ATTENTION不需要开平仓信息）
                    parsedAction.setOpenClose(null);

                    if (StringUtils.hasText(parsedAction.getTimeframe()) && null != parsedAction.getLimit() && 0 < parsedAction.getLimit()) {
                        // 转换为QUERY，保留confidence和reasoning
                        withoutIllusions.add(ActionParser.ParsedAction.builder()
                                .instId(parsedAction.getInstId())
                                .action(ActionParser.ActionType.QUERY)
                                .timeframe(parsedAction.getTimeframe())
                                .limit(parsedAction.getLimit())
                                .confidence(parsedAction.getConfidence())
                                .reasoning(parsedAction.getReasoning())
                                .build());
                    } else {
                        // 保留ATTENTION，保留confidence和priority
                        parsedAction.setOpenClose(null);
                        withoutIllusions.add(parsedAction);
                    }
                    break;
                case CANCEL, CANCEL_ORDER:
                    // 清除openClose字段（CANCEL不需要开平仓信息）
                    parsedAction.setOpenClose(null);

                    withoutIllusions.add(ActionParser.ParsedAction.builder()
                            .instId(parsedAction.getInstId())
                            .action(ActionParser.ActionType.CANCEL_ORDER)
                            .orderId(parsedAction.getOrderId())
                            .confidence(parsedAction.getConfidence())
                            .reasoning(parsedAction.getReasoning())
                            .build());
                    break;
                default:
                    break;
            }
        }

        return ActionParser.ActionPack.builder()
                .actions(withoutIllusions)
                .build();
    }

}

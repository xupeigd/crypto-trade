package com.crypto.trade.service.prompt.processor;

import com.crypto.trade.model.OrderModel;
import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.service.prompt.AbstractPromptProcessor;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.service.prompt.PromptProcessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PendingOrdersKlinePromptProcessor
 * 委托订单K线数据处理器
 * 用于生成委托订单涉及的合约的K线数据和技术指标
 *
 * @author page
 * @date 2026-03-03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOrdersKlinePromptProcessor
        extends AbstractPromptProcessor {

    private final TechnicalIndicatorProcessor technicalIndicatorProcessor;

    @Override
    public String getName() {
        return "PendingOrdersKlineProcessor";
    }

    @Override
    public int getPriority() {
        // 在TechnicalIndicatorProcessor(40)之前执行
        return 36;
    }

    @Override
    public boolean shouldExecute(PromptContext context) {
        // 从context获取委托订单列表
        List<OrderModel> pendingOrders = context.getCustomData("pendingOrders");
        return pendingOrders != null && !pendingOrders.isEmpty();
    }

    @Override
    public SegmentModel process(PromptContext context) throws PromptProcessException {
        // 从context获取委托订单列表
        List<OrderModel> pendingOrders = context.getCustomData("pendingOrders");
        if (pendingOrders == null || pendingOrders.isEmpty()) {
            return SegmentModel.builder().content("").build();
        }

        // 去重提取instId
        Set<String> instrumentIds = pendingOrders.stream()
                .map(OrderModel::getInstId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (instrumentIds.isEmpty()) {
            return SegmentModel.builder().content("").build();
        }

        StringBuilder content = new StringBuilder();
        content.append("\n## 委托订单合约数据\n");
        content.append("以下是需要关注的委托订单合约的技术指标数据：\n\n");

        // 与持仓数据保持一致：5m、1h、4h各30条
        String[] timeframes = {"5m", "1H", "4H"};
        int limit = 30;

        for (String instId : instrumentIds) {
            for (String timeframe : timeframes) {
                try {
                    log.debug("【委托订单K线】获取数据 - instId: {}, timeframe: {}, limit: {}", instId, timeframe, limit);
                    // 传入context参数，支持全局去重
                    String klineData = technicalIndicatorProcessor.processQuery(instId, timeframe, limit, context.getApiKeyId(), context);
                    // 如果返回空字符串，说明已处理过，静默跳过
                    if (klineData == null || klineData.isEmpty()) {
                        continue;
                    }
                    content.append(klineData).append("\n\n");
                } catch (Exception e) {
                    log.error("【委托订单K线】获取数据失败 - instId: {}", instId, e);
                    content.append(String.format("### %s (%s) (数据获取失败)\n%s\n\n",
                            instId, timeframe, e.getMessage()));
                }
            }
        }

        return SegmentModel.builder()
                .title("委托订单K线")
                .content(content.toString())
                .build();
    }
}

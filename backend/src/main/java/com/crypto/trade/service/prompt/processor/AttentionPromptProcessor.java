package com.crypto.trade.service.prompt.processor;

import com.crypto.trade.dto.AttentionInfo;
import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.service.prompt.PromptProcessException;
import com.crypto.trade.service.prompt.PromptProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AttentionPromptProcessor
 * ATTENTION Prompt处理器
 * 用于在prompt中添加ATTENTION合约的K线图数据
 *
 * @author page
 * @date 2026-03-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttentionPromptProcessor
        implements PromptProcessor {

    private final TechnicalIndicatorProcessor technicalIndicatorProcessor;

    @Override
    public String getName() {
        return "AttentionProcessor";
    }

    @Override
    public int getPriority() {
        // 在TechnicalIndicatorProcessor(40)之前执行，获取K线数据
        return 35;
    }

    @Override
    public boolean shouldExecute(PromptContext context) {
        // 检查是否有ATTENTION数据
        List<AttentionInfo> attentions = context.getCustomData("attentions");
        log.info("【ATTENTION】shouldExecute检查 - attentions: {}", attentions);
        return attentions != null && !attentions.isEmpty();
    }

    @Override
    public SegmentModel process(PromptContext context) throws PromptProcessException {
        List<AttentionInfo> attentions = context.getCustomData("attentions");
        if (attentions == null || attentions.isEmpty()) {
            return SegmentModel.builder().content("").build();
        }

        StringBuilder content = new StringBuilder();
        content.append("\n## 关注合约数据(ATTENTION)\n");
        content.append("以下是需要关注的合约的技术指标数据：\n\n");

        // 与持仓数据保持一致：5m、1h、4h各30条
        String[] timeframes = {"5m", "1H", "4H"};
        int limit = 30;

        for (AttentionInfo attention : attentions) {
            String instId = attention.getInstId();
            for (String timeframe : timeframes) {
                try {
                    log.debug("【ATTENTION】获取K线数据 - instId: {}, timeframe: {}, limit: {}", instId, timeframe, limit);
                    // 传入context参数，支持全局去重
                    String klineData = technicalIndicatorProcessor.processQuery(instId, timeframe, limit, context.getApiKeyId(), context);
                    // 如果返回空字符串，说明已处理过，静默跳过
                    if (klineData == null || klineData.isEmpty()) {
                        continue;
                    }
                    content.append(klineData).append("\n\n");
                } catch (Exception e) {
                    log.error("【ATTENTION】获取K线数据失败 - instId: {}", instId, e);
                    content.append(String.format("### %s (%s) (数据获取失败)\n%s\n\n",
                            instId, timeframe, e.getMessage()));
                }
            }
        }

        return SegmentModel.builder()
                .title("Attentions")
                .content(content.toString())
                .build();
    }
}

package com.crypto.trade.service.prompt.processor;

import com.crypto.trade.config.AiTradingRiskControlConfig;
import com.crypto.trade.entity.TradingStyle;
import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.service.conversation.TradingConfigProperties;
import com.crypto.trade.service.prompt.AbstractPromptProcessor;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.service.prompt.PromptProcessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TradeRulePromptProcessor
 * 处理器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeRulePromptProcessor
        extends AbstractPromptProcessor {

    private final TradingConfigProperties tradingConfig;
    private final AiTradingRiskControlConfig riskControlConfig;

    @Override
    public String getName() {
        return "TradeRulePromptProcessor";
    }

    @Override
    public int getPriority() {
        return SegmentModel.Priority.HIGH; // 高优先级 (20)，确保规则在数据之前被设定
    }

    @Override
    public boolean shouldExecute(PromptContext context) {
        return true;
    }

    @Override
    public SegmentModel process(PromptContext context) throws PromptProcessException {
        return safeProcess(context, () -> {
            StringBuilder sb = new StringBuilder();

            // 1. 交易风格
            TradingStyle style = riskControlConfig.getCurrentTradingStyle();
            sb.append(String.format("- **交易风格**: %s\n", style.getDescription()));
            switch (style) {
                case C1_CONSERVATIVE:
                    sb.append("  (优先考虑资本保护，只在确定性极高时交易，严格控制止损，资金使用率目标40%, 最大亏损3%，目标盈利5%)\n");
                    break;
                case C2_CAUTIOUS:
                    sb.append("  (注重风险控制，选择性参与高确定性机会，资金使用率目标50%，最大亏损5%，目标盈利10%)\n");
                    break;
                case C3_MODERATE:
                    sb.append("  (平衡风险与收益，寻找稳健的交易机会，资金使用率目标60%，最大亏损8%，目标盈利15%)\n");
                    break;
                case C4_ACTIVE:
                    sb.append("  (追求更高收益，愿意承担适度风险，资金使用率目标70%，对市场波动反应更灵敏，最大亏损12%，目标盈利20%)\n");
                    break;
                case C5_AGGRESSIVE:
                    sb.append("  (追求高收益机会，承担较高风险以获取更大回报，资金使用率目标80%，最大亏损18%，目标盈利30%)\n");
                    break;
            }
            sb.append(" - 最短持仓时间0.5H，置信度低于80时，不得对小于最短持仓时间的仓位进行平仓。\n");
            sb.append(" - 决策开仓时，必须估算预期的持仓时间及持仓止盈止损的点位。\n");

            // 2. 风控规则
            sb.append("- **风控规则**:\n");
            sb.append(String.format("  - 最低置信度要求: %d%%\n", tradingConfig.getMinConfidence()));
            sb.append("  - 置信度低于95时不得使用市场价下单，必须使用限价下单。\n");
            sb.append(String.format("  - 单笔最大资金比例: %.0f%%\n", tradingConfig.getMaxAmountRatio() * 100));
            sb.append(String.format("  - 单笔金额范围: %.1f - %.1f USDT\n", tradingConfig.getMinAmount(), tradingConfig.getMaxAmount()));
            sb.append(String.format("  - 默认杠杆倍数: %dx\n", tradingConfig.getDefaultLeverage()));
            sb.append("  - 当前是双向持仓模式，注意操作的方向。\n");
            // 3. 执行模式
            sb.append(String.format("- **执行模式**: %s\n", tradingConfig.getExecutionMode()));

            return SegmentModel.builder()
                    .title("规则与约束")
                    .content(sb.toString())
                    .category(SegmentModel.Category.INSTRUCTION)
                    .priority(getPriority())
                    .showTitle(true)
                    .addMetadata("processor", getName())
                    .addMetadata("tradingStyle", style)
                    .addMetadata("executionMode", tradingConfig.getExecutionMode())
                    .build();
        });
    }
}

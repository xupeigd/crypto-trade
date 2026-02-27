package com.crypto.trade.service.prompt.processor;

import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.service.prompt.AbstractPromptProcessor;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.service.prompt.PromptProcessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ThinkingModeProcessor
 * 处理器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class ThinkingModeProcessor
        extends AbstractPromptProcessor {

    @Override
    public String getName() {
        return "ThinkingModeProcessor";
    }

    @Override
    public int getPriority() {
        return 60; // 较低优先级，在数据处理后执行
    }

    @Override
    public boolean shouldExecute(PromptContext context) {
        return context.isThinkingModeEnabled();
    }

    @Override
    public SegmentModel process(PromptContext context) throws PromptProcessException {
        return safeProcess(context, () -> {
            // 获取思考模式配置
            boolean enableThinkingMode = getBooleanParameter("enableThinkingMode", true);
            if (!enableThinkingMode) {
                return SegmentModel.builder()
                        .content("")
                        .showTitle(false)
                        .build();
            }

            // 获取思考步骤配置
            int thinkingSteps = getIntParameter("thinkingSteps", 6);

            String content = generateThinkingModeContent(thinkingSteps);

            return SegmentModel.builder()
                    .title("思考模式要求")
                    .content(content)
                    .category(SegmentModel.Category.THINKING)
                    .priority(SegmentModel.Priority.LOW)
                    .showTitle(true)
                    .addMetadata("processor", getName())
                    .addMetadata("processorPriority", getPriority())
                    .addMetadata("thinkingSteps", thinkingSteps)
                    .addMetadata("enableThinkingMode", enableThinkingMode)
                    .build();
        });
    }

    /**
     * 生成思考模式内容
     *
     * @param steps 思考步骤数量
     * @return 思考模式内容
     */
    private String generateThinkingModeContent(int steps) {
        StringBuilder content = new StringBuilder();

        content.append("\n");
        content.append("- **提供交易决策前，按照以下步骤进行深入分析**：\n");
        content.append("\n");
        // 根据步骤数量生成相应的内容
        for (int i = 0; i <= Math.min(steps, 6); i++) {
            content.append(String.format("  - %s\n", getThinkingStepContent(i)));
        }
        content.append("\n");

        content.append("- **BUY/SELL决策要求**:\n");
        content.append("  - 当你决定开仓时，需要给出预计持仓时间和预计收益率，预计亏损率(吃单手续费0.05%)");
        content.append("  - 当你决定平仓时，需要给出明确的决策依据");
        content.append("\n");

        content.append("请先输出你的详细思考过程（用<thinking>标签包围），然后提供最终的交易决策JSON。\n");
        content.append("\n");
        content.append("<thinking>\n");
        content.append("[在这里详细展示你的思考过程，按照上述步骤进行分析]\n");
        content.append("</thinking>\n");

        return content.toString();
    }

    /**
     * 获取思考步骤内容
     *
     * @param step 步骤编号
     * @return 步骤内容
     */
    private String getThinkingStepContent(int step) {
        switch (step) {
            case 0:
                return "复盘：结合历史仓位及历史订单，对当前市场环境和你的历史决策进行复盘。";
            case 1:
                return "市场环境分析：分析当前整体市场环境，包括Top15合约的涨跌情况、交易量活跃度等，必须基于市场结构、趋势和关键支撑/阻力位进行分析。";
            case 2:
                return "持仓风险评估：分析当前持仓的风险状况，包括保证金使用率、未实现盈亏、集中度等，结合提供的技术指标(RSI, BOLL等)进行确认，避免单一指标决策。注意寻找其中的金叉死叉信号，以及其他关键点位的出现。";
            case 3:
                return "技术指标解读：分析当前持仓合约的技术指标信号，判断趋势和可能的转折点，必须为每个决策提供清晰的逻辑推理(Reasoning)。";
            case 4:
                return "资金管理策略：基于当前账户状况，评估交易风险，确定合理的仓位大小，如果市场形势不明朗或风险过高，请果断选择 HOLD。";
            case 5:
                return "不懂就问：如果需要更多数据，请使用QUERY进行数据查询。最多可以进行5轮查询。";
            case 6:
                return "决策逻辑推理：综合以上分析，形成最终的交易决策逻辑，严格遵守风控规则，特别是金额和杠杆限制。";
            default:
                return "分析步骤";
        }
    }
}
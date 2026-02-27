package com.crypto.trade.service.prompt.processor;

import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.service.prompt.AbstractPromptProcessor;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.service.prompt.PromptProcessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DecisionRequirementProcessor
 * 处理器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class DecisionRequirementProcessor
        extends AbstractPromptProcessor {

    @Override
    public String getName() {
        return "DecisionRequirementProcessor";
    }

    @Override
    public int getPriority() {
        return 90; // 最低优先级，最后执行以确保完整性
    }

    @Override
    public boolean shouldExecute(PromptContext context) {
        return true; // 决策要求总是需要的
    }

    @Override
    public SegmentModel process(PromptContext context) throws PromptProcessException {
        return safeProcess(context, () -> {
            // 获取决策要求配置
            String outputFormat = getStringParameter("outputFormat", "JSON");
            boolean includeToolUsage = getBooleanParameter("includeToolUsage", false);
            StringBuilder content = new StringBuilder();
            content.append("基于以上分析，做出交易决策。\n");
            if (includeToolUsage) {
                content.append("如果需要更多信息，请继续提出工具调用请求。\n");
                content.append("工具调用格式如下：\n");
                content.append("```json\n");
                content.append("{\n");
                content.append("   \"actions\": [\n");
                content.append("      {\n");
                content.append("        \"action\": \"QUERY/ATTENTION\", // 必须的，为QUERY/ATTENTION \n");
                content.append("        \"priority\": 5, //关注的优先级，action为ATTENTION时必须有 \n");
                content.append("        \"instId\": \"目标合约代码\", // 必须的，目标合约代码\n");
                content.append("        \"timeframe\": \"1m/3m/5m/1H/4H/1D\", // 必须的，时间周期\n");
                content.append("        \"limit\": 100 // 可选的，查询条数，默认为100，最高300\n");
                content.append("      }\n");
                content.append("   ]\n");
                content.append("}\n");
                content.append("```\n");
                content.append("否则，请提供最终的决策JSON响应。\n");
            }

            content.append("\n");

            // 根据输出格式生成相应的要求
            if ("JSON".equalsIgnoreCase(outputFormat)) {
                content.append("决策的JSON格式如下：\n");
                content.append("```json\n");
                content.append("{\n");
                content.append("   \"actions\": [\n");
                content.append("      {\n");
                content.append("         \"action\": \"BUY/SELL/HOLD/CANCEL_ORDER\", // 交易动作 取消委托订单必须使用CANCEL_ORDER\n");

                content.append("         \"orderId\": \"12345\", // 订单Id，取消订单时，必须使用orderId\n");
                content.append("         \"instId\": \"目标合约ID\", // 目标合约ID\n");
                content.append("         \"posSide\": \"long/short\", // 方向 多/空\n");
                content.append("         \"openClose\": \"open/close\", // 开仓/平仓 开仓open 平仓 close action=BUY/SELL时有值\n");

                content.append("         \"orderType\": \"limit/market\", // 委托类型，limit/ market\n");
                content.append("         \"price\": 105, // 决策价格, ordType==limit时有值 \n");
                content.append("         \"lever\": 3, //杠杆, 使用杠杆倍数 openClose=open时有值\n");
                content.append("         \"amount\": 30, //决策成本金额 开仓时必填(单位:USDT,例如:100)\n");

                content.append("         \"quantity\": 0.25, //决策数量，平仓时必填(单位:张/合约数,例如:10)\n");
                content.append("         \"takeProfit\": 100.25, // 止盈价格，BUY必须有，HOLD变更止盈止损时有\n");
                content.append("         \"stopLoss\": 92.5, // 止损价格，BUY必须有，HOLD变更止盈止损时有\n");
                content.append("         \"confidence\": 70, // 置信度 1-100\n");
                content.append("         \"reasoning\": \"决策推理过程\", // 决策推理过程\n");
                content.append("      }\n");
                content.append("      ,... // 更多action item对象\n ");
                content.append("   ]\n");
                content.append("}\n");
                content.append("```\n");
            } else if ("MARKDOWN".equalsIgnoreCase(outputFormat)) {
                content.append("请以Markdown格式提供详细的交易决策分析。\n");
            }

            content.append("\n");
            content.append("请确保决策合理，风险可控。\n");

            return SegmentModel.builder()
                    .title("决策要求")
                    .content(content.toString())
                    .category(SegmentModel.Category.INSTRUCTION)
                    .priority(SegmentModel.Priority.LOWEST)
                    .showTitle(true)
                    .addMetadata("processor", getName())
                    .addMetadata("processorPriority", getPriority())
                    .addMetadata("outputFormat", outputFormat)
                    .addMetadata("includeToolUsage", includeToolUsage)
                    .build();
        });
    }
}
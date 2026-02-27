package com.crypto.trade.service.prompt;

import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.service.conversation.ToolExecutionResult;
import com.crypto.trade.service.prompt.processor.DecisionRequirementProcessor;
import com.crypto.trade.service.prompt.processor.ThinkingModeProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MultiTurnPromptTemplate
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class MultiTurnPromptTemplate {

    @Autowired
    PromptBuilder promptBuilder;
    @Autowired
    ThinkingModeProcessor thinkingModeProcessor;
    @Autowired
    DecisionRequirementProcessor decisionRequirementProcessor;

    /**
     * 构建第N轮的prompt
     *
     * @param historySummary    历史对话摘要
     * @param currentAiResponse 当前AI响应
     * @param recentToolResults 最近工具执行结果
     * @param currentRound      当前轮次
     * @param context           Prompt上下文（用于获取业务数据）
     * @return 下一轮的prompt
     */
    public String buildNextTurnPrompt(String historySummary, String currentAiResponse, List<ToolExecutionResult> recentToolResults,
                                      int currentRound, PromptContext context) {
        StringBuilder prompt = new StringBuilder();

        // 1. 历史摘要
        prompt.append(historySummary).append("\n\n");

        // 2. 【核心业务上下文】账户、持仓、委托订单、交易规则
        String businessContext = buildBusinessContext(context);
        prompt.append(businessContext).append("\n");

        // 3. 当前轮次说明
        prompt.append("=== 当前轮次分析 ===\n");
        prompt.append(String.format("你正在执行第%d轮分析(最多10轮)。\n\n", currentRound));

        // 4. 当前AI响应
        prompt.append("=== 上轮响应 ===\n");
        prompt.append(currentAiResponse).append("\n\n");

        // 5. 工具执行结果
        if (null != recentToolResults && !recentToolResults.isEmpty()) {
            prompt.append("=== 工具执行结果 ===\n");
            for (int i = 0; i < recentToolResults.size(); i++) {
                ToolExecutionResult result = recentToolResults.get(i);

                // 获取工具友好名称
                String toolDisplayName = getToolDisplayName(result.getToolName());

                prompt.append(String.format("工具 [%s]: %s\n", toolDisplayName,
                        result.getSuccess() ? "成功" : "失败"));

                if (result.getSuccess()) {
                    prompt.append("数据: ").append(formatResultData(result)).append("\n");
                    prompt.append(String.format("处理时间: %dms\n", result.getProcessingTimeMs()));
                } else {
                    prompt.append("错误: ").append(result.getErrorMessage()).append("\n");
                }
                prompt.append("\n");
            }
        }

        // 6. 思考模式要求（使用ThinkingModeProcessor生成）
        try {
            SegmentModel thinkingSegment = thinkingModeProcessor.process(context);
            prompt.append("=== ").append(thinkingSegment.getTitle()).append(" ===\n");
            prompt.append(thinkingSegment.getContent()).append("\n");
        } catch (com.crypto.trade.service.prompt.PromptProcessException e) {
            log.error("思考模式处理器执行失败", e);
            prompt.append("=== 思考模式要求 ===\n");
            prompt.append("(思考模式生成失败)\n");
        }

        // 7. 决策要求（使用DecisionRequirementProcessor生成，需要启用工具调用支持）
        try {
            // 设置参数以启用工具调用格式
            decisionRequirementProcessor.setParameters(Map.of("includeToolUsage", true));
            SegmentModel decisionSegment = decisionRequirementProcessor.process(context);
            prompt.append("=== ").append(decisionSegment.getTitle()).append(" ===\n");
            prompt.append(decisionSegment.getContent()).append("\n");
        } catch (com.crypto.trade.service.prompt.PromptProcessException e) {
            log.error("决策要求处理器执行失败", e);
            prompt.append("=== 决策要求 ===\n");
            prompt.append("(决策要求生成失败)\n");
        }

        String finalPrompt = prompt.toString();
        log.debug("构建第{}轮prompt完成 - 长度: {}", currentRound, finalPrompt.length());
        return finalPrompt;
    }

    /**
     * 格式化工具执行结果数据
     * <p>
     * 格式化策略：
     * 1. 如果是技术指标表格（包含"###"和"技术指标表格"），不截断，完整展示
     * 2. 如果是普通JSON数据，超过500字符则截断
     * 3. 其他数据直接转字符串返回
     * </p>
     *
     * @param result 工具执行结果
     * @return 格式化后的数据字符串
     */
    private String formatResultData(ToolExecutionResult result) {
        if (null == result.getData()) {
            return "[无数据]";
        }

        try {
            String dataStr = result.getData().toString();

            // 判断是否为技术指标表格
            // 技术指标表格的特征：包含"###"和"技术指标表格"关键字
            if (isTechnicalIndicatorTable(dataStr)) {
                // 技术指标表格不截断，完整展示
                log.debug("识别到技术指标表格，完整展示 - 长度: {}", dataStr.length());
                return dataStr;
            }

            // 普通数据，超过500字符则截断
            if (dataStr.length() > 500) {
                log.debug("数据过长，截断展示 - 原始长度: {}", dataStr.length());
                return dataStr.substring(0, 500) + "...(已截断)";
            }

            return dataStr;
        } catch (Exception e) {
            log.warn("格式化工具结果数据失败", e);
            return "[数据格式化失败: " + e.getMessage() + "]";
        }
    }

    /**
     * 判断是否为技术指标表格
     * <p>
     * 技术指标表格的特征：
     * 1. 包含"###"标记（Markdown标题）
     * 2. 包含"技术指标表格"关键字
     * </p>
     *
     * @param dataStr 数据字符串
     * @return 是否为技术指标表格
     */
    private boolean isTechnicalIndicatorTable(String dataStr) {
        if (dataStr == null || dataStr.trim().isEmpty()) {
            return false;
        }

        // 检查是否包含技术指标表格的特征标记
        return dataStr.contains("###") &&
                dataStr.contains("技术指标表格") &&
                dataStr.contains("|") && // 包含表格分隔符
                dataStr.contains("EMA(") && // 包含EMA指标
                dataStr.contains("RSI("); // 包含RSI指标
    }

    /**
     * 构建第一轮prompt(用于多轮对话的起始)
     *
     * @param originalPrompt 原始用户prompt
     * @return 第一轮prompt
     */
    public String buildFirstTurnPrompt(String originalPrompt) {
        return originalPrompt;
    }

    /**
     * 构建完整业务上下文
     * <p>
     * 包含账户信息、持仓、委托订单、交易规则等核心业务数据。
     * 确保每一轮对话都包含最新的业务状态。
     * </p>
     *
     * @param context Prompt上下文
     * @return 业务上下文字符串
     */
    private String buildBusinessContext(PromptContext context) {
        try {
            // 使用 PromptBuilder 构建完整的业务内容
            PromptBuilder.PromptBuildResult result = promptBuilder.buildPromptWithSegments(context);

            // 提取核心业务段落
            List<SegmentModel> segments = result.getSegments();
            StringBuilder businessContext = new StringBuilder();

            for (SegmentModel segment : segments) {
                // 只包含业务相关的段落
                if (isBusinessSegment(segment)) {
                    String formattedSegment = formatSegment(segment);
                    businessContext.append(formattedSegment).append("\n");
                }
            }

            log.debug("构建业务上下文完成 - 包含 {} 个业务段落", segments.size());
            return businessContext.toString();

        } catch (Exception e) {
            log.error("构建业务上下文失败 - apiKeyId: {}", context.getApiKeyId(), e);
            return "（业务上下文获取失败，请重试）\n";
        }
    }

    /**
     * 判断是否为业务相关的段落
     * <p>
     * 业务段落包括：账户信息、持仓、委托订单、交易规则
     * </p>
     *
     * @param segment 段落模型
     * @return 是否为业务段落
     */
    private boolean isBusinessSegment(SegmentModel segment) {
        if (segment == null || segment.getTitle() == null) {
            return false;
        }

        String title = segment.getTitle();
        return title.contains("账户") ||
                title.contains("持仓") ||
                title.contains("委托订单") ||
                title.contains("等待成交") ||  // 新增:匹配"等待成交订单"
                title.contains("交易规则");
    }

    /**
     * 格式化段落为字符串
     *
     * @param segment 段落模型
     * @return 格式化后的段落字符串
     */
    private String formatSegment(SegmentModel segment) {
        StringBuilder sb = new StringBuilder();

        if (segment.getTitle() != null) {
            sb.append("=== ").append(segment.getTitle()).append(" ===\n");
        }

        if (segment.getContent() != null) {
            sb.append(segment.getContent());
        }

        return sb.toString();
    }

    /**
     * 获取工具友好显示名称
     * 将工具代码名称映射为用户友好的中文名称
     *
     * @param toolName 工具代码名称（如 "k_line"）
     * @return 友好显示名称（如 "K线查询工具"）
     */
    private String getToolDisplayName(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return "未知工具";
        }

        // 工具名称映射表
        switch (toolName) {
            case "k_line":
                return "K线查询工具";
            // 未来可以添加更多工具的映射
            // case "account_info":
            //     return "账户信息工具";
            // case "position_query":
            //     return "持仓查询工具";
            default:
                // 如果没有预定义的友好名称，返回工具名称本身
                return toolName;
        }
    }
}

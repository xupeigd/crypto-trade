package com.crypto.trade.service.decision;

import com.crypto.trade.dto.response.BotCallModelResponse;
import com.crypto.trade.model.TradingContext;
import com.crypto.trade.service.UnifiedModelFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AIModelManager
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIModelManager {

    private final UnifiedModelFactory unifiedModelFactory;

    /**
     * 执行AI决策
     *
     * @param tradingContext 交易上下文
     * @return AI响应结果
     */
    public BotCallModelResponse makeDecision(TradingContext tradingContext) {
        try {
            log.debug("开始AI决策: userId={}, strategy={}",
                    tradingContext.getUserId(), tradingContext.getStrategy());

            // 格式化输入
            String prompt = formatDecisionPrompt(tradingContext);

            // 使用真实大模型调用
            long startTime = System.currentTimeMillis();

            // 获取默认模型配置(遵循系统设置,可以是本地ollama或远端)
            var modelConfig = unifiedModelFactory.getDefaultModelConfig();

            if (null == modelConfig) {
                log.error("未找到默认模型配置");
                return createErrorResponse("未找到默认模型配置");
            }

            log.info("使用真实大模型进行决策: model={}, type={}, isLocal={}",
                    modelConfig.getModelId(),
                    modelConfig.getModelType(),
                    "LOCAL".equals(modelConfig.getModelType()));

            // 调用真实大模型,返回JSON字符串
            String jsonResponse = unifiedModelFactory.callWithConfig(prompt, modelConfig);

            long processingTime = System.currentTimeMillis() - startTime;

            // 解析JSON响应为BotCallModelResponse
            BotCallModelResponse response = parseResponse(jsonResponse, startTime, processingTime, modelConfig.getModelId());

            log.debug("AI决策完成: userId={}, response={}",
                    tradingContext.getUserId(), response.getAiResponse());

            return response;

        } catch (Exception e) {
            log.error("AI决策失败: userId={}", tradingContext.getUserId(), e);
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * 解析大模型返回的JSON字符串
     */
    private BotCallModelResponse parseResponse(String jsonResponse, long callTime, long processingTime, String modelName) {
        // 这里需要解析JSON字符串并转换为BotCallModelResponse对象
        // 简化处理:直接将JSON作为aiResponse,设置其他默认值
        return BotCallModelResponse.builder()
                .taskId(UUID.randomUUID().toString())
                .status("SUCCESS")
                .aiResponse(jsonResponse)
                .callTime(callTime)
                .processingTimeMs(processingTime)
                .success(true)
                .modelName(modelName)
                .decisionId(UUID.randomUUID().toString())
                .tradeDecision("HOLD") // 默认值,实际应从JSON中解析
                .build();
    }

    /**
     * 格式化决策提示词
     */
    private String formatDecisionPrompt(TradingContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的加密货币交易AI助手。\n\n");
        prompt.append("用户信息：\n");
        prompt.append("- 用户ID: ").append(context.getUserId()).append("\n");
        prompt.append("- 策略: ").append(context.getStrategy()).append("\n");
        prompt.append("- 风险等级: ").append(context.getRiskLevel().getDescription()).append("\n\n");

        prompt.append("市场状况：\n");
        if (context.getMarketContext() != null) {
            prompt.append("- 市场状况: ").append(context.getMarketContext().getMarketCondition().getDescription()).append("\n");
            prompt.append("- 市场情绪: ").append(context.getMarketContext().getSentiment().getDescription()).append("\n");
            prompt.append("- 市场波动性: ").append(context.getMarketContext().getVolatility().getDescription()).append("\n");
            prompt.append("- 可用合约数: ").append(context.getMarketContext().getAvailableSymbols().size()).append("\n");
        }

        prompt.append("\n请基于以上信息做出交易决策。");
        prompt.append("以JSON格式回复，包含：action(BUY/SELL/HOLD)、reasoning(决策理由)、confidence(置信度0-1)、recommendations(推荐合约)。");

        return prompt.toString();
    }

    /**
     * 创建错误响应
     */
    private BotCallModelResponse createErrorResponse(String errorMessage) {
        return BotCallModelResponse.builder()
                .taskId(UUID.randomUUID().toString())
                .status("ERROR")
                .aiResponse("AI模型调用失败: " + errorMessage + "，建议持有观望")
                .callTime(System.currentTimeMillis())
                .processingTimeMs(0L)
                .success(false)
                .errorMessage(errorMessage)
                .modelName("Error-AI-Model")
                .decisionId(UUID.randomUUID().toString())
                .tradeDecision("HOLD")
                .build();
    }

    /**
     * 获取模型统计信息
     */
    public ModelStats getModelStats() {
        return ModelStats.builder()
                .totalCalls(0) // TODO: 实现实际统计
                .successCalls(0)
                .errorCalls(0)
                .averageResponseTime(0.0)
                .lastCallTime(LocalDateTime.now())
                .build();
    }

    /**
     * 模型统计信息
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ModelStats {
        private long totalCalls;
        private long successCalls;
        private long errorCalls;
        private double averageResponseTime;
        private LocalDateTime lastCallTime;

        public double getSuccessRate() {
            return totalCalls > 0 ? (double) successCalls / totalCalls : 0.0;
        }

        public double getErrorRate() {
            return totalCalls > 0 ? (double) errorCalls / totalCalls : 0.0;
        }
    }
}
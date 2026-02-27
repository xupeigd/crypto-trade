package com.crypto.trade.service.decision;

import com.crypto.trade.dto.response.BotCallModelResponse;
import com.crypto.trade.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * TradeDecisionEngine
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class TradeDecisionEngine {

    @Autowired
    private MarketDataCollector marketDataCollector;

    @Autowired
    private RiskCalculator riskCalculator;

    @Autowired
    private AIModelManager aiModelManager;

    /**
     * 执行交易决策（默认风险等级）
     *
     * @param userId   用户ID
     * @param strategy 策略名称
     * @return 决策结果
     */
    public DecisionResult makeDecision(String userId, String strategy) {
        return makeDecisionWithRisk(userId, strategy, RiskLevel.NORMAL);
    }

    /**
     * 执行交易决策（指定风险等级）
     *
     * @param userId    用户ID
     * @param strategy  策略名称
     * @param riskLevel 风险等级
     * @return 决策结果
     */
    public DecisionResult makeDecisionWithRisk(String userId, String strategy, RiskLevel riskLevel) {
        try {
            log.info("开始交易决策: userId={}, strategy={}, riskLevel={}", userId, strategy, riskLevel);

            // 1. 收集市场数据
            MarketContext marketContext = marketDataCollector.collectMarketData(userId);
            log.debug("市场数据收集完成: symbols={}", marketContext.getAvailableSymbols().size());

            // 2. 计算风险评估
            RiskAssessment riskAssessment = riskCalculator.calculateRisk(marketContext, riskLevel);
            log.debug("风险评估完成: riskScore={}, riskLevel={}",
                    riskAssessment.getRiskScore(), riskAssessment.getRiskLevel());

            // 3. 构建交易上下文
            TradingContext tradingContext = TradingContext.builder()
                    .userId(userId)
                    .strategy(strategy)
                    .riskLevel(riskLevel)
                    .marketContext(marketContext)
                    .riskAssessment(riskAssessment)
                    .decisionTime(LocalDateTime.now())
                    .build();

            // 4. AI模型决策
            BotCallModelResponse aiResponse = aiModelManager.makeDecision(tradingContext);

            // 5. 从AI响应中提取决策信息
            String decision = "HOLD"; // 默认值
            if (aiResponse.getTradeDecision() instanceof String) {
                decision = (String) aiResponse.getTradeDecision();
            }

            // 6. 生成最终决策结果
            DecisionResult result = DecisionResult.builder()
                    .userId(userId)
                    .strategy(strategy)
                    .riskLevel(riskLevel)
                    .decision(decision)
                    .confidence(BigDecimal.valueOf(0.7)) // 默认置信度
                    .reasoning(aiResponse.getAiResponse())
                    .recommendedInstruments(Arrays.asList("BTC-USDT-SWAP", "ETH-USDT-SWAP")) // 默认推荐
                    .riskScore(riskAssessment.getRiskScore())
                    .marketCondition(marketContext.getMarketCondition().name())
                    .decisionTime(LocalDateTime.now())
                    .success(aiResponse.getSuccess() != null ? aiResponse.getSuccess() : false)
                    .build();

            log.info("交易决策完成: userId={}, action={}, confidence={}",
                    userId, result.getDecision(), result.getConfidence());

            return result;

        } catch (Exception e) {
            log.error("交易决策失败: userId={}, strategy={}", userId, strategy, e);
            return DecisionResult.error("决策执行失败: " + e.getMessage());
        }
    }

    /**
     * 快速决策（适用于高频场景）
     *
     * @param userId     用户ID
     * @param instrument 合约代码
     * @return 快速决策结果
     */
    public DecisionResult makeQuickDecision(String userId, String instrument) {
        try {
            log.debug("开始快速决策: userId={}, instrument={}", userId, instrument);

            // 收集特定合约数据
            MarketContext marketContext = marketDataCollector.collectInstrumentData(userId, instrument);

            // 快速风险评估
            RiskAssessment riskAssessment = riskCalculator.quickRiskAssessment(marketContext);

            // 使用真实大模型进行决策
            TradingContext tradingContext = TradingContext.builder()
                    .userId(userId)
                    .strategy("QUICK_DECISION")
                    .riskLevel(RiskLevel.NORMAL)
                    .marketContext(marketContext)
                    .build();

            BotCallModelResponse aiResponse = aiModelManager.makeDecision(tradingContext);

            // 从AI响应中提取决策信息
            String decision = "HOLD"; // 默认值
            if (aiResponse.getTradeDecision() instanceof String) {
                decision = (String) aiResponse.getTradeDecision();
            }

            DecisionResult result = DecisionResult.builder()
                    .userId(userId)
                    .strategy("QUICK_DECISION")
                    .riskLevel(RiskLevel.NORMAL)
                    .decision(decision)
                    .confidence(BigDecimal.valueOf(0.7)) // 默认置信度
                    .reasoning(aiResponse.getAiResponse())
                    .recommendedInstruments(List.of("BTC-USDT-SWAP")) // 快速决策推荐较少
                    .riskScore(riskAssessment.getRiskScore())
                    .decisionTime(LocalDateTime.now())
                    .success(aiResponse.getSuccess() != null ? aiResponse.getSuccess() : false)
                    .build();

            log.debug("快速决策完成: userId={}, action={}", userId, result.getDecision());

            return result;

        } catch (Exception e) {
            log.error("快速决策失败: userId={}, instrument={}", userId, instrument, e);
            return DecisionResult.error("快速决策失败: " + e.getMessage());
        }
    }

    /**
     * 验证决策结果的合理性
     *
     * @param result 决策结果
     * @return 验证结果
     */
    public boolean validateDecision(DecisionResult result) {
        if (result == null || !result.isSuccess()) {
            return false;
        }

        // 检查决策的合理性
        if (result.getDecision() == null || result.getDecision().isEmpty()) {
            log.warn("决策结果为空: userId={}", result.getUserId());
            return false;
        }

        // 检查置信度
        if (result.getConfidence() != null && result.getConfidence().compareTo(BigDecimal.valueOf(0.3)) < 0) {
            log.warn("决策置信度过低: userId={}, confidence={}",
                    result.getUserId(), result.getConfidence());
            return false;
        }

        // 检查风险评分
        if (result.getRiskScore() > 0.9) {
            log.warn("决策风险过高: userId={}, riskScore={}",
                    result.getUserId(), result.getRiskScore());
            return false;
        }

        return true;
    }
}
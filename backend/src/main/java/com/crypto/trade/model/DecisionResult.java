package com.crypto.trade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DecisionResult
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionResult {

    private String userId;
    private String strategy;
    private RiskLevel riskLevel;
    private String decision; // BUY/SELL/HOLD
    private BigDecimal confidence; // 0-1之间的置信度
    private String reasoning; // 决策理由
    private List<String> recommendedInstruments; // 推荐的合约
    private double riskScore; // 风险评分 0-1
    private String marketCondition; // 市场状况
    private LocalDateTime decisionTime;
    private boolean success;
    private String errorMessage;
    private String orderId; // 如果执行了订单

    /**
     * 创建成功的决策结果
     */
    public static DecisionResult success(String userId, String decision) {
        return DecisionResult.builder()
                .userId(userId)
                .decision(decision)
                .success(true)
                .decisionTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建失败的决策结果
     */
    public static DecisionResult error(String errorMessage) {
        return DecisionResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .decisionTime(LocalDateTime.now())
                .build();
    }

    /**
     * 是否建议买入
     */
    public boolean shouldBuy() {
        return success && "BUY".equalsIgnoreCase(decision);
    }

    /**
     * 是否建议卖出
     */
    public boolean shouldSell() {
        return success && "SELL".equalsIgnoreCase(decision);
    }

    /**
     * 是否建议持有
     */
    public boolean shouldHold() {
        return success && "HOLD".equalsIgnoreCase(decision);
    }

    /**
     * 是否有推荐的合约
     */
    public boolean hasRecommendations() {
        return success && recommendedInstruments != null && !recommendedInstruments.isEmpty();
    }
}
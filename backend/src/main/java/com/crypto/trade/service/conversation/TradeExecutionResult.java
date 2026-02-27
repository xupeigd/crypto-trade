package com.crypto.trade.service.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * TradeExecutionResult
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeExecutionResult {

    /**
     * 合约代码
     */
    private String instId;

    /**
     * 交易动作（BUY/SELL）
     */
    private String action;

    /**
     * 执行是否成功
     */
    private Boolean success;

    /**
     * 订单ID（成功时返回）
     */
    private String orderId;

    /**
     * 成交价格（成功时返回）
     */
    private BigDecimal executedPrice;

    /**
     * 成交数量（成功时返回）
     */
    private BigDecimal executedSize;

    /**
     * 错误信息（失败时返回）
     */
    private String errorMessage;

    /**
     * 执行耗时（毫秒）
     */
    private Long executionTimeMs;

    /**
     * 是否为模拟执行
     */
    private Boolean dryRun;

    /**
     * 创建成功结果
     */
    public static TradeExecutionResult success(String instId, String action, String orderId,
                                               BigDecimal executedPrice, BigDecimal executedSize,
                                               Long executionTimeMs, Boolean dryRun) {
        return TradeExecutionResult.builder()
                .instId(instId)
                .action(action)
                .success(true)
                .orderId(orderId)
                .executedPrice(executedPrice)
                .executedSize(executedSize)
                .executionTimeMs(executionTimeMs)
                .dryRun(dryRun)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static TradeExecutionResult failure(String instId, String action, String errorMessage,
                                               Long executionTimeMs) {
        return TradeExecutionResult.builder()
                .instId(instId)
                .action(action)
                .success(false)
                .errorMessage(errorMessage)
                .executionTimeMs(executionTimeMs)
                .dryRun(false)
                .build();
    }

    /**
     * 创建模拟执行结果
     */
    public static TradeExecutionResult dryRun(String instId, String action, String message) {
        return TradeExecutionResult.builder()
                .instId(instId)
                .action(action)
                .success(true)
                .dryRun(true)
                .errorMessage(message)
                .executionTimeMs(0L)
                .build();
    }

    /**
     * 创建跳过执行结果（用于HOLD等不执行交易的动作）
     */
    public static TradeExecutionResult skip(String instId, String action, String reason) {
        return TradeExecutionResult.builder()
                .instId(instId)
                .action(action)
                .success(true)
                .dryRun(false)
                .errorMessage(reason)
                .executionTimeMs(0L)
                .build();
    }
}

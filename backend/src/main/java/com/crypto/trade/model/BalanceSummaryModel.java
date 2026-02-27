package com.crypto.trade.model;

import com.crypto.trade.service.BalanceService;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * BalanceSummaryModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceSummaryModel {

    /**
     * CEX名称
     */
    private String cexName;

    /**
     * 总资产估值（USD）
     */
    private BigDecimal totalUsdValue;

    /**
     * 总可用余额
     */
    private BigDecimal totalAvailableBalance;

    /**
     * 总锁定余额
     */
    private BigDecimal totalLockedBalance;

    /**
     * 币种数量
     */
    private int currencyCount;

    /**
     * 币种分布（按USD价值）
     */
    private Map<String, BigDecimal> currencyDistribution;

    /**
     * 最新更新时间
     */
    private LocalDateTime latestUpdateTime;

    /**
     * 从Service内嵌类转换为Model
     *
     * @param summary BalanceSummary内嵌类实例
     * @return BalanceSummaryModel模型
     */
    public static BalanceSummaryModel fromServiceClass(
            BalanceService.BalanceSummary summary) {
        if (null == summary) {
            return BalanceSummaryModel.builder()
                    .totalUsdValue(BigDecimal.ZERO)
                    .totalAvailableBalance(BigDecimal.ZERO)
                    .totalLockedBalance(BigDecimal.ZERO)
                    .currencyCount(0)
                    .currencyDistribution(Map.of())
                    .latestUpdateTime(LocalDateTime.now())
                    .build();
        }

        return BalanceSummaryModel.builder()
                .cexName(summary.getCexName())
                .totalUsdValue(summary.getTotalUsdValue())
                .totalAvailableBalance(summary.getTotalAvailableBalance())
                .totalLockedBalance(summary.getTotalLockedBalance())
                .currencyCount(summary.getCurrencyCount())
                .currencyDistribution(summary.getCurrencyDistribution())
                .latestUpdateTime(summary.getLatestUpdateTime())
                .build();
    }

    /**
     * 获取锁定余额占比
     *
     * @return 锁定余额占比百分比
     */
    public BigDecimal getLockedBalancePercentage() {
        BigDecimal totalBalance = totalAvailableBalance.add(totalLockedBalance);
        if (totalBalance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalLockedBalance.divide(totalBalance, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    /**
     * 获取可用余额占比
     *
     * @return 可用余额占比百分比
     */
    public BigDecimal getAvailableBalancePercentage() {
        BigDecimal totalBalance = totalAvailableBalance.add(totalLockedBalance);
        if (totalBalance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalAvailableBalance.divide(totalBalance, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    /**
     * 判断是否有余额数据
     *
     * @return true如果总资产大于0
     */
    public boolean hasBalance() {
        return null != totalUsdValue && totalUsdValue.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 获取总余额
     *
     * @return 可用余额 + 锁定余额
     */
    public BigDecimal getTotalBalance() {
        return totalAvailableBalance.add(totalLockedBalance);
    }
}
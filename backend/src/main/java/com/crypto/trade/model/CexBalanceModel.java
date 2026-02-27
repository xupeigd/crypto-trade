package com.crypto.trade.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * CexBalanceModel
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
public class CexBalanceModel {

    /**
     * CEX名称
     */
    String cexName;

    /**
     * 币种
     */
    String currency;

    /**
     * 总余额
     */
    BigDecimal totalBalance;

    /**
     * 可用余额
     */
    BigDecimal availableBalance;

    /**
     * 锁定余额
     */
    BigDecimal lockedBalance;

    /**
     * USD估值
     */
    BigDecimal usdValue;

    /**
     * 数据摄取时间
     */
    LocalDateTime dataIngestionTime;

    /**
     * 从Entity转换为Model
     *
     * @param entity CexBalance实体
     * @return CexBalanceModel模型
     */
    public static CexBalanceModel fromEntity(com.crypto.trade.entity.CexBalance entity) {
        if (null == entity) {
            return null;
        }

        return CexBalanceModel.builder()
                .cexName(entity.getCexName())
                .currency(entity.getCurrency())
                .totalBalance(entity.getTotalBalance())
                .availableBalance(entity.getAvailableBalance())
                .lockedBalance(entity.getLockedBalance())
                .usdValue(entity.getUsdValue())
                .dataIngestionTime(entity.getDataIngestionTime())
                .build();
    }

    /**
     * 判断余额是否为空
     *
     * @return true如果余额为0或null
     */
    public boolean isEmpty() {
        return null == totalBalance || totalBalance.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * 获取可用余额占比
     *
     * @return 可用余额占比百分比
     */
    public BigDecimal getAvailableBalancePercentage() {
        if (isEmpty() || totalBalance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return availableBalance.divide(totalBalance, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }
}
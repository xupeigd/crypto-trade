package com.crypto.trade.rest.controller.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * PositionStopLossStrategyModel
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
public class PositionStopLossStrategyModel {

    /**
     * 算法订单ID
     */
    String algoId;

    /**
     * 合约品种
     */
    String instId;

    /**
     * 持仓方向 (long/short)
     */
    String posSide;

    /**
     * 策略数量
     */
    String sz;

    /**
     * 止盈触发价格
     */
    String tpTriggerPx;

    /**
     * 止盈比例
     */
    String tpRatio;

    /**
     * 止损触发价格
     */
    String slTriggerPx;

    /**
     * 止损比例
     */
    String slRatio;

    /**
     * 止盈触发进度
     */
    Double tpProgress;

    /**
     * 止损触发进度
     */
    Double slProgress;

    /**
     * 获取止盈触发价格的BigDecimal值
     *
     * @return 止盈触发价格BigDecimal值
     */
    public BigDecimal getTpTriggerPxValue() {
        if (null == tpTriggerPx || tpTriggerPx.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(tpTriggerPx);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取止损触发价格的BigDecimal值
     *
     * @return 止损触发价格BigDecimal值
     */
    public BigDecimal getSlTriggerPxValue() {
        if (null == slTriggerPx || slTriggerPx.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(slTriggerPx);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断是否设置了止盈
     *
     * @return true如果设置了止盈
     */
    public boolean hasTakeProfit() {
        return null != tpTriggerPx && !tpTriggerPx.trim().isEmpty() &&
                !"0".equals(tpTriggerPx) && !"null".equals(tpTriggerPx);
    }

    /**
     * 判断是否设置了止损
     *
     * @return true如果设置了止损
     */
    public boolean hasStopLoss() {
        return null != slTriggerPx && !slTriggerPx.trim().isEmpty() &&
                !"0".equals(slTriggerPx) && !"null".equals(slTriggerPx);
    }

    /**
     * 判断策略是否有效
     *
     * @return true如果策略有效
     */
    public boolean isValid() {
        return null != algoId && !algoId.trim().isEmpty() &&
                null != instId && !instId.trim().isEmpty() &&
                null != posSide && !posSide.trim().isEmpty() &&
                (hasTakeProfit() || hasStopLoss());
    }

}
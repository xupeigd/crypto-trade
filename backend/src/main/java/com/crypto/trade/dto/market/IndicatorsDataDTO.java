package com.crypto.trade.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * IndicatorsDataDTO
 * 数据传输对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicatorsDataDTO {
    /**
     * 指标名称（如 "EMA", "RSI", "BOLL", "MACD"）
     */
    private String metricName;

    /**
     * 指标数据点列表（按时间倒序）
     */
    private List<IndicatorDataPoint> values;

    /**
     * 指标数据点
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndicatorDataPoint {
        /**
         * 时间戳
         */
        private Long timestamp;

        /**
         * 多周期值
         * key格式: "ema_5", "ema_20", "rsi_14"
         * value: 数值（BigDecimal）或复杂对象（BollValue/MacdValue）
         */
        private Map<String, Object> multiPeriodValues;
    }
}

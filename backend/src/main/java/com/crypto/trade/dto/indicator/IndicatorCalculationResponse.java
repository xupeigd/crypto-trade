package com.crypto.trade.dto.indicator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * IndicatorCalculationResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicatorCalculationResponse {

    /**
     * Map of Indicator Name (e.g., "SMA", "MACD") to a list of calculated results for different periods.
     */
    private Map<String, List<IndicatorResult>> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SingleValue {
        private Long timestamp;
        private BigDecimal value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndicatorResult {
        /**
         * The period parameters used (e.g., "5", "12,26,9")
         */
        private String period;

        /**
         * Values for single-line indicators (SMA, EMA, WMA, RSI)
         * Only populated for single-line indicators.
         */
        private List<SingleValue> values;

        /**
         * Values for MACD
         * Only populated for MACD.
         */
        private List<MacdValue> macdValues;

        /**
         * Values for BOLL
         * Only populated for BOLL.
         */
        private List<BollValue> bollValues;

        /**
         * Values for KDJ
         * Only populated for KDJ.
         */
        private List<KdjValue> kdjValues;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MacdValue {
        private Long timestamp;
        private BigDecimal diff; // Fast - Slow (DIF)
        private BigDecimal dea;  // Signal line (DEA)
        private BigDecimal macd; // Histogram (Bar)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BollValue {
        private Long timestamp;
        private BigDecimal upper;
        private BigDecimal middle;
        private BigDecimal lower;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KdjValue {
        private Long timestamp;
        private BigDecimal k;
        private BigDecimal d;
        private BigDecimal j;
    }
}

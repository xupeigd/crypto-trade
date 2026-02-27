package com.crypto.trade.dto.indicator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * IndicatorCalculationRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicatorCalculationRequest {

    /**
     * OHLC data list, should be sorted by time ascending (oldest first) or we will sort it.
     */
    private List<OhlcItem> ohlcData;

    /**
     * List of indicators to calculate
     */
    private List<IndicatorConfig> indicators;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OhlcItem {
        private Long timestamp;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private BigDecimal volume;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndicatorConfig {
        /**
         * Indicator name: EMA, SMA, WMA, MACD, BOLL, RSI
         */
        private String name;

        /**
         * Periods for calculation.
         * For SMA, EMA, WMA, RSI, BOLL: each integer is a separate period to calculate (e.g., [5, 10, 20]).
         * For MACD: expects [fast, slow, signal] (e.g., [12, 26, 9]). If multiple MACDs are needed, provide multiple IndicatorConfig objects.
         */
        private List<Integer> periods;
    }
}

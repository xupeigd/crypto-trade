package com.crypto.trade.service.market;

import com.crypto.trade.dto.indicator.IndicatorCalculationRequest;
import com.crypto.trade.dto.indicator.IndicatorCalculationResponse;
import com.crypto.trade.dto.market.IndicatorsDataDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UnifiedPriceDataServiceKdjParseTest {

    @Test
    void parseIndicatorResponse_shouldPreferKdjValuesOverValues() {
        UnifiedPriceDataService service = new UnifiedPriceDataService();

        List<IndicatorCalculationRequest.OhlcItem> ohlc = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            long ts = 1000L * (i + 1);
            BigDecimal base = BigDecimal.valueOf(100 + i);
            ohlc.add(IndicatorCalculationRequest.OhlcItem.builder()
                    .timestamp(ts)
                    .open(base)
                    .high(base.add(BigDecimal.ONE))
                    .low(base.subtract(BigDecimal.ONE))
                    .close(base)
                    .volume(BigDecimal.TEN)
                    .build());
        }

        List<IndicatorCalculationResponse.KdjValue> kdjValues = new ArrayList<>();
        List<IndicatorCalculationResponse.SingleValue> values = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            long ts = 1000L * (i + 1);
            kdjValues.add(IndicatorCalculationResponse.KdjValue.builder()
                    .timestamp(ts)
                    .k(new BigDecimal("10.00000000"))
                    .d(new BigDecimal("20.00000000"))
                    .j(new BigDecimal("30.00000000"))
                    .build());
            values.add(IndicatorCalculationResponse.SingleValue.builder()
                    .timestamp(ts)
                    .value(new BigDecimal("999.00000000"))
                    .build());
        }

        List<IndicatorCalculationResponse.IndicatorResult> kdjResults = List.of(
                IndicatorCalculationResponse.IndicatorResult.builder()
                        .period("9")
                        .values(values)
                        .kdjValues(kdjValues)
                        .build()
        );

        Map<String, List<IndicatorCalculationResponse.IndicatorResult>> results = new HashMap<>();
        results.put("KDJ", kdjResults);

        IndicatorCalculationResponse resp = IndicatorCalculationResponse.builder()
                .results(results)
                .build();

        Map<String, IndicatorsDataDTO> parsed = service.parseIndicatorResponse(resp, ohlc);
        assertTrue(parsed.containsKey("KDJ"));

        IndicatorsDataDTO dto = parsed.get("KDJ");
        assertNotNull(dto);
        assertNotNull(dto.getValues());
        assertEquals(30, dto.getValues().size());

        IndicatorsDataDTO.IndicatorDataPoint first = dto.getValues().get(0);
        assertNotNull(first.getMultiPeriodValues());
        Object v = first.getMultiPeriodValues().get("kdj_9");
        assertNotNull(v);
        assertTrue(v instanceof Map || v instanceof IndicatorCalculationResponse.KdjValue);

        if (v instanceof IndicatorCalculationResponse.KdjValue kv) {
            assertEquals(new BigDecimal("10.00000000"), kv.getK());
            assertEquals(new BigDecimal("20.00000000"), kv.getD());
            assertEquals(new BigDecimal("30.00000000"), kv.getJ());
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) v;
            assertTrue(m.containsKey("k"));
            assertTrue(m.containsKey("d"));
            assertTrue(m.containsKey("j"));
        }
    }
}

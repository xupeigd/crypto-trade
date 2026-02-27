package com.crypto.trade.service;

import com.crypto.trade.dto.indicator.IndicatorCalculationRequest;
import com.crypto.trade.dto.indicator.IndicatorCalculationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CommonTechnicalIndicatorServiceTest {

    private CommonTechnicalIndicatorService service;

    @BeforeEach
    void setUp() {
        service = new CommonTechnicalIndicatorService();
    }

    @Test
    void testCalculateSMA() {
        List<IndicatorCalculationRequest.OhlcItem> ohlcData = createOhlcData(1, 2, 3, 4, 5);
        IndicatorCalculationRequest.IndicatorConfig config = IndicatorCalculationRequest.IndicatorConfig.builder()
                .name("SMA")
                .periods(Collections.singletonList(3))
                .build();

        IndicatorCalculationRequest request = IndicatorCalculationRequest.builder()
                .ohlcData(ohlcData)
                .indicators(Collections.singletonList(config))
                .build();

        IndicatorCalculationResponse response = service.calculate(request);

        assertNotNull(response);
        assertTrue(response.getResults().containsKey("SMA"));
        assertEquals(1, response.getResults().get("SMA").size());

        List<BigDecimal> values = response.getResults().get("SMA").get(0).getValues().stream()
                .map(v -> v != null ? v.getValue() : null)
                .collect(Collectors.toList());
        assertEquals(5, values.size());
        assertNull(values.get(0));
        assertNull(values.get(1));
        assertEquals(new BigDecimal("2.00000000"), values.get(2)); // (1+2+3)/3 = 2
        assertEquals(new BigDecimal("3.00000000"), values.get(3)); // (2+3+4)/3 = 3
        assertEquals(new BigDecimal("4.00000000"), values.get(4)); // (3+4+5)/3 = 4
    }

    @Test
    void testCalculateMultipleIndicators() {
        // Create 30 data points for MACD/BOLL
        List<IndicatorCalculationRequest.OhlcItem> ohlcData = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            ohlcData.add(createItem(i * 1000L, i + 1));
        }

        IndicatorCalculationRequest.IndicatorConfig smaConfig = IndicatorCalculationRequest.IndicatorConfig.builder()
                .name("SMA")
                .periods(Arrays.asList(5, 10))
                .build();

        IndicatorCalculationRequest.IndicatorConfig macdConfig = IndicatorCalculationRequest.IndicatorConfig.builder()
                .name("MACD")
                .periods(Arrays.asList(12, 26, 9))
                .build();

        IndicatorCalculationRequest request = IndicatorCalculationRequest.builder()
                .ohlcData(ohlcData)
                .indicators(Arrays.asList(smaConfig, macdConfig))
                .build();

        IndicatorCalculationResponse response = service.calculate(request);

        assertNotNull(response);
        assertTrue(response.getResults().containsKey("SMA"));
        assertEquals(2, response.getResults().get("SMA").size()); // SMA 5 and SMA 10

        assertTrue(response.getResults().containsKey("MACD"));
        assertEquals(1, response.getResults().get("MACD").size());

        // MACD 12, 26, 9 requires at least 26 data points for slow EMA, and then some for signal
        // Slow EMA starts at index 25 (26th item).
        // DIF is calculated from 25.
        // DEA (Signal 9) needs 9 DIF values. DIF starts at 25. 25 + 8 = 33.
        // So with 30 items, DEA might be null?
        // Let's check logic:
        // Slow EMA calc starts at index 25 (period-1).
        // DIF valid from 25.
        // DEA calc: calculateEmaOfValues(difList, 9, 25).
        // It needs 9 values starting from 25. 25, 26, ..., 33.
        // So with 30 items (index 0-29), DEA will be null.
        // MACD will be null.

        List<IndicatorCalculationResponse.MacdValue> macdValues = response.getResults().get("MACD").get(0).getMacdValues();
        assertEquals(30, macdValues.size());
        assertNull(macdValues.get(29));
    }

    @Test
    void testCalculateMultiPeriodAtrCciObvKdj() {
        List<IndicatorCalculationRequest.OhlcItem> ohlcData = createOhlcData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        IndicatorCalculationRequest.IndicatorConfig atr = IndicatorCalculationRequest.IndicatorConfig.builder()
                .name("ATR")
                .periods(Arrays.asList(3, 5))
                .build();
        IndicatorCalculationRequest.IndicatorConfig cci = IndicatorCalculationRequest.IndicatorConfig.builder()
                .name("CCI")
                .periods(Arrays.asList(3, 5))
                .build();
        IndicatorCalculationRequest.IndicatorConfig obv = IndicatorCalculationRequest.IndicatorConfig.builder()
                .name("OBV")
                .periods(Arrays.asList(3, 5))
                .build();
        IndicatorCalculationRequest.IndicatorConfig kdj = IndicatorCalculationRequest.IndicatorConfig.builder()
                .name("KDJ")
                .periods(Arrays.asList(3, 5))
                .build();

        IndicatorCalculationRequest request = IndicatorCalculationRequest.builder()
                .ohlcData(ohlcData)
                .indicators(Arrays.asList(atr, cci, obv, kdj))
                .build();

        IndicatorCalculationResponse response = service.calculate(request);

        assertNotNull(response);
        assertTrue(response.getResults().containsKey("ATR"));
        assertTrue(response.getResults().containsKey("CCI"));
        assertTrue(response.getResults().containsKey("OBV"));
        assertTrue(response.getResults().containsKey("KDJ"));

        assertEquals(2, response.getResults().get("ATR").size());
        assertEquals(2, response.getResults().get("CCI").size());
        assertEquals(2, response.getResults().get("OBV").size());
        assertEquals(2, response.getResults().get("KDJ").size());

        IndicatorCalculationResponse.IndicatorResult atr3 = response.getResults().get("ATR").stream()
                .filter(r -> "3".equals(r.getPeriod()))
                .findFirst()
                .orElseThrow();
        assertEquals(10, atr3.getValues().size());
        assertNull(atr3.getValues().get(0));
        assertNull(atr3.getValues().get(1));
        assertNotNull(atr3.getValues().get(2));

        IndicatorCalculationResponse.IndicatorResult cci3 = response.getResults().get("CCI").stream()
                .filter(r -> "3".equals(r.getPeriod()))
                .findFirst()
                .orElseThrow();
        assertEquals(10, cci3.getValues().size());
        assertNull(cci3.getValues().get(0));
        assertNull(cci3.getValues().get(1));
        assertNotNull(cci3.getValues().get(2));

        IndicatorCalculationResponse.IndicatorResult obv3 = response.getResults().get("OBV").stream()
                .filter(r -> "3".equals(r.getPeriod()))
                .findFirst()
                .orElseThrow();
        assertEquals(10, obv3.getValues().size());
        assertNull(obv3.getValues().get(0));
        assertNull(obv3.getValues().get(1));
        assertNotNull(obv3.getValues().get(2));

        IndicatorCalculationResponse.IndicatorResult kdj3 = response.getResults().get("KDJ").stream()
                .filter(r -> "3".equals(r.getPeriod()))
                .findFirst()
                .orElseThrow();
        assertEquals(10, kdj3.getValues().size());
        assertNull(kdj3.getValues().get(0));
        assertNull(kdj3.getValues().get(1));
        assertNotNull(kdj3.getValues().get(2));

        assertNotNull(kdj3.getKdjValues());
        assertEquals(10, kdj3.getKdjValues().size());
        assertNull(kdj3.getKdjValues().get(0));
        assertNull(kdj3.getKdjValues().get(1));
        assertNotNull(kdj3.getKdjValues().get(2));
        assertNotNull(kdj3.getKdjValues().get(2).getK());
        assertNotNull(kdj3.getKdjValues().get(2).getD());
        assertNotNull(kdj3.getKdjValues().get(2).getJ());
    }

    @Test
    void testKdjDenomZeroConvergesTo50() {
        List<IndicatorCalculationRequest.OhlcItem> ohlcData = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long ts = (i + 1L) * 1000L;
            ohlcData.add(IndicatorCalculationRequest.OhlcItem.builder()
                    .timestamp(ts)
                    .open(new BigDecimal("100"))
                    .high(new BigDecimal("100"))
                    .low(new BigDecimal("100"))
                    .close(new BigDecimal("100"))
                    .volume(BigDecimal.TEN)
                    .build());
        }

        IndicatorCalculationRequest.IndicatorConfig kdj = IndicatorCalculationRequest.IndicatorConfig.builder()
                .name("KDJ")
                .periods(Collections.singletonList(3))
                .build();

        IndicatorCalculationRequest request = IndicatorCalculationRequest.builder()
                .ohlcData(ohlcData)
                .indicators(Collections.singletonList(kdj))
                .build();

        IndicatorCalculationResponse response = service.calculate(request);
        IndicatorCalculationResponse.IndicatorResult kdj3 = response.getResults().get("KDJ").get(0);
        assertEquals("3", kdj3.getPeriod());

        for (int i = 0; i < 2; i++) {
            assertNull(kdj3.getKdjValues().get(i));
        }
        for (int i = 2; i < 10; i++) {
            assertNotNull(kdj3.getKdjValues().get(i));
            assertEquals(new BigDecimal("50.00000000"), kdj3.getKdjValues().get(i).getK());
            assertEquals(new BigDecimal("50.00000000"), kdj3.getKdjValues().get(i).getD());
            assertEquals(new BigDecimal("50.00000000"), kdj3.getKdjValues().get(i).getJ());
        }
    }

    @Test
    void testKdjMultiPeriodAvailability() {
        List<IndicatorCalculationRequest.OhlcItem> ohlcData = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            long ts = (i + 1L) * 1000L;
            BigDecimal price = BigDecimal.valueOf(100 + i);
            ohlcData.add(IndicatorCalculationRequest.OhlcItem.builder()
                    .timestamp(ts)
                    .open(price)
                    .high(price.add(BigDecimal.ONE))
                    .low(price.subtract(BigDecimal.ONE))
                    .close(price)
                    .volume(BigDecimal.TEN)
                    .build());
        }

        IndicatorCalculationRequest.IndicatorConfig kdj = IndicatorCalculationRequest.IndicatorConfig.builder()
                .name("KDJ")
                .periods(Arrays.asList(9, 14, 21))
                .build();

        IndicatorCalculationRequest request = IndicatorCalculationRequest.builder()
                .ohlcData(ohlcData)
                .indicators(Collections.singletonList(kdj))
                .build();

        IndicatorCalculationResponse response = service.calculate(request);
        assertEquals(3, response.getResults().get("KDJ").size());

        IndicatorCalculationResponse.IndicatorResult kdj9 = response.getResults().get("KDJ").stream()
                .filter(r -> "9".equals(r.getPeriod()))
                .findFirst()
                .orElseThrow();
        assertNull(kdj9.getKdjValues().get(7));
        assertNotNull(kdj9.getKdjValues().get(8));

        IndicatorCalculationResponse.IndicatorResult kdj14 = response.getResults().get("KDJ").stream()
                .filter(r -> "14".equals(r.getPeriod()))
                .findFirst()
                .orElseThrow();
        assertNull(kdj14.getKdjValues().get(12));
        assertNotNull(kdj14.getKdjValues().get(13));

        IndicatorCalculationResponse.IndicatorResult kdj21 = response.getResults().get("KDJ").stream()
                .filter(r -> "21".equals(r.getPeriod()))
                .findFirst()
                .orElseThrow();
        assertNull(kdj21.getKdjValues().get(19));
        assertNotNull(kdj21.getKdjValues().get(20));
    }

    private List<IndicatorCalculationRequest.OhlcItem> createOhlcData(double... prices) {
        List<IndicatorCalculationRequest.OhlcItem> list = new ArrayList<>();
        long time = 1000;
        for (double p : prices) {
            list.add(createItem(time, p));
            time += 1000;
        }
        return list;
    }

    private IndicatorCalculationRequest.OhlcItem createItem(long time, double close) {
        return IndicatorCalculationRequest.OhlcItem.builder()
                .timestamp(time)
                .close(BigDecimal.valueOf(close))
                .open(BigDecimal.valueOf(close))
                .high(BigDecimal.valueOf(close))
                .low(BigDecimal.valueOf(close))
                .volume(BigDecimal.TEN)
                .build();
    }
}

package com.crypto.trade.service;

import com.crypto.trade.dto.cex.model.CexMarketCandle;
import com.crypto.trade.dto.cex.model.CexPosition;
import com.crypto.trade.model.TechnicalIndicators;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TechnicalIndicatorServiceTest {

    @Mock
    private UnifiedCexApiService unifiedCexApiService;

    @Mock
    private CommonTechnicalIndicatorService commonIndicatorService;

    @InjectMocks
    private TechnicalIndicatorService service;

    @BeforeEach
    void setUp() {
        // 初始化测试环境
    }

    // 测试1: 验证输入参数
    @Test
    void testGetIndicatorsForPositions_withNullPositions() {
        Map<String, TechnicalIndicators> result = service.getIndicatorsForPositions(null, 1L);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetIndicatorsForPositions_withEmptyPositions() {
        Map<String, TechnicalIndicators> result = service.getIndicatorsForPositions(Collections.emptyList(), 1L);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetIndicatorsForPositions_withInvalidApiKeyId() {
        CexPosition position = createMockPosition("BTC-USDT-SWAP", BigDecimal.ONE);
        Map<String, TechnicalIndicators> result = service.getIndicatorsForPositions(
                Collections.singletonList(position), null);
        assertTrue(result.isEmpty());
    }

    // 测试2: 验证数据过滤
    @Test
    void testGetIndicatorsForPositions_withInvalidPositions() {
        List<CexPosition> positions = Arrays.asList(
                null,  // null持仓
                createMockPosition("", BigDecimal.ONE),  // 空instId
                createMockPosition("BTC-USDT-SWAP", BigDecimal.ZERO)  // 零持仓
        );

        Map<String, TechnicalIndicators> result = service.getIndicatorsForPositions(positions, 1L);
        assertTrue(result.isEmpty());
    }

    // 测试3: 验证去重逻辑
    @Test
    void testGetIndicatorsForPositions_withDuplicateInstIds() {
        CexPosition position1 = createMockPosition("BTC-USDT-SWAP", BigDecimal.ONE);
        CexPosition position2 = createMockPosition("BTC-USDT-SWAP", BigDecimal.ONE);
        CexPosition position3 = createMockPosition("ETH-USDT-SWAP", BigDecimal.ONE);

        List<CexMarketCandle> candles = createMockCandles(120);
        when(unifiedCexApiService.getMarketCandles(any(), anyString(), anyString(), anyInt()))
                .thenReturn(candles);

        Map<String, TechnicalIndicators> result = service.getIndicatorsForPositions(
                Arrays.asList(position1, position2, position3), 1L);

        // 应该只有2个唯一合约的结果
        assertEquals(2, result.size());
        // 应该只调用1次BTC的API（去重）
        verify(unifiedCexApiService, times(1)).getMarketCandles(any(), eq("BTC-USDT-SWAP"), anyString(), anyInt());
        verify(unifiedCexApiService, times(1)).getMarketCandles(any(), eq("ETH-USDT-SWAP"), anyString(), anyInt());
    }

    // 测试4: 验证K线数据量检查（Bug 3修复验证）
    @Test
    void testGetTechnicalIndicators_withInsufficientCandles() {
        List<CexMarketCandle> candles = createMockCandles(25); // 少于31条
        when(unifiedCexApiService.getMarketCandles(any(), anyString(), anyString(), anyInt()))
                .thenReturn(candles);

        TechnicalIndicators result = service.getTechnicalIndicators("BTC-USDT-SWAP", "5m", 1L);
        assertNull(result);
    }

    @Test
    void testGetTechnicalIndicators_withSufficientCandles() {
        List<CexMarketCandle> candles = createMockCandles(120); // 足够31条
        when(unifiedCexApiService.getMarketCandles(any(), anyString(), anyString(), anyInt()))
                .thenReturn(candles);

        TechnicalIndicators result = service.getTechnicalIndicators("BTC-USDT-SWAP", "5m", 1L);
        assertNotNull(result);
    }

    // 测试5: 验证缓存失效（Bug 7修复验证）
    @Test
    void testInvalidateCacheByInstId() {
        // 先填充缓存
        List<CexMarketCandle> candles = createMockCandles(120);
        when(unifiedCexApiService.getMarketCandles(any(), anyString(), anyString(), anyInt()))
                .thenReturn(candles);

        service.getTechnicalIndicators("BTC-USDT-SWAP", "5m", 1L);
        String statsBefore = service.getCacheStats();

        // 失效缓存
        service.invalidateCacheByInstId("BTC-USDT-SWAP");

        String statsAfter = service.getCacheStats();
        // 验证缓存大小减少
        assertNotEquals(statsBefore, statsAfter);
    }

    // 测试6: 验证缓存统计（Bug 6修复验证）
    @Test
    void testGetCacheStats() {
        String stats = service.getCacheStats();
        assertNotNull(stats);
        // 验证包含正确的缓存大小（200和100）
        assertTrue(stats.contains("200"));  // 技术指标缓存最大大小
        assertTrue(stats.contains("100"));  // K线缓存最大大小
    }

    // 测试7: 验证并发异常处理（Bug 4修复验证）
    @Test
    void testGetIndicatorsForPositions_withApiException() {
        CexPosition position1 = createMockPosition("BTC-USDT-SWAP", BigDecimal.ONE);
        CexPosition position2 = createMockPosition("ETH-USDT-SWAP", BigDecimal.ONE);

        // 模拟API异常
        when(unifiedCexApiService.getMarketCandles(any(), anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("API调用失败"));

        // 不应该抛出异常，而是返回空结果或部分结果
        Map<String, TechnicalIndicators> result = service.getIndicatorsForPositions(
                Arrays.asList(position1, position2), 1L);

        // 异常应该被捕获，不会传播到调用方
        assertNotNull(result);
    }

    // 测试8: 验证资源清理（Bug 1修复验证）
    @Test
    void testShutdown() {
        // 验证shutdown方法可以正常调用而不抛出异常
        assertDoesNotThrow(() -> service.shutdown());
    }

    // 辅助方法
    private CexPosition createMockPosition(String instId, BigDecimal quantity) {
        CexPosition position = mock(CexPosition.class);
        lenient().when(position.getSymbol()).thenReturn(instId);
        lenient().when(position.getQuantity()).thenReturn(quantity);
        return position;
    }

    private List<CexMarketCandle> createMockCandles(int count) {
        List<CexMarketCandle> candles = new ArrayList<>();
        long baseTimestamp = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            final int idx = i;
            final long ts = baseTimestamp + (long) idx * 300000L;

            CexMarketCandle candle = mock(CexMarketCandle.class);
            lenient().when(candle.getTimestamp()).thenReturn(ts);
            lenient().when(candle.getOpen()).thenReturn(BigDecimal.valueOf(50000 + idx * 10));
            lenient().when(candle.getHigh()).thenReturn(BigDecimal.valueOf(50000 + idx * 10 + 20));
            lenient().when(candle.getLow()).thenReturn(BigDecimal.valueOf(50000 + idx * 10 - 10));
            lenient().when(candle.getClose()).thenReturn(BigDecimal.valueOf(50000 + idx * 10 + 5));
            lenient().when(candle.getVolume()).thenReturn(BigDecimal.valueOf(100));
            lenient().when(candle.getVolumeCcy()).thenReturn(BigDecimal.valueOf(1000000));
            lenient().when(candle.isConfirmed()).thenReturn(true);

            candles.add(candle);
        }
        return candles;
    }
}

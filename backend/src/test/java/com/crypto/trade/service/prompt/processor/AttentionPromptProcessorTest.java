package com.crypto.trade.service.prompt.processor;

import com.crypto.trade.dto.AttentionInfo;
import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.service.prompt.PromptProcessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * AttentionPromptProcessor单元测试
 *
 * @author page
 * @date 2026-03-02
 */
@ExtendWith(MockitoExtension.class)
class AttentionPromptProcessorTest {

    @Mock
    private TechnicalIndicatorProcessor technicalIndicatorProcessor;

    @Mock
    private Logger log;

    @InjectMocks
    private AttentionPromptProcessor attentionPromptProcessor;

    private PromptContext createPromptContext() {
        return PromptContext.builder()
                .apiKeyId(1L)
                .build();
    }

    private AttentionInfo createAttentionInfo() {
        return AttentionInfo.builder()
                .queueId(1L)
                .instId("BTC-USDT-SWAP")
                .priority(1)
                .timeframe("1H")
                .limit(100)
                .expectedTriggerTime(LocalDateTime.now())
                .build();
    }

    @Test
    void testGetName() {
        String name = attentionPromptProcessor.getName();
        assertEquals("AttentionProcessor", name);
    }

    @Test
    void testGetPriority() {
        int priority = attentionPromptProcessor.getPriority();
        assertEquals(35, priority);
    }

    @Test
    void testShouldExecuteWithAttentions() {
        PromptContext context = createPromptContext();
        List<AttentionInfo> attentions = Collections.singletonList(createAttentionInfo());
        context.setCustomData("attentions", attentions);

        boolean result = attentionPromptProcessor.shouldExecute(context);

        assertTrue(result);
    }

    @Test
    void testShouldExecuteWithoutAttentions() {
        PromptContext context = createPromptContext();

        boolean result = attentionPromptProcessor.shouldExecute(context);

        assertFalse(result);
    }

    @Test
    void testShouldExecuteWithEmptyAttentions() {
        PromptContext context = createPromptContext();
        context.setCustomData("attentions", Collections.emptyList());

        boolean result = attentionPromptProcessor.shouldExecute(context);

        assertFalse(result);
    }

    @Test
    void testShouldExecuteWithNullAttentions() {
        PromptContext context = createPromptContext();
        context.setCustomData("attentions", null);

        boolean result = attentionPromptProcessor.shouldExecute(context);

        assertFalse(result);
    }

    @Test
    void testProcess() throws PromptProcessException {
        PromptContext context = createPromptContext();
        List<AttentionInfo> attentions = Collections.singletonList(createAttentionInfo());
        context.setCustomData("attentions", attentions);

        when(technicalIndicatorProcessor.processQuery(
                eq("BTC-USDT-SWAP"),
                eq("1H"),
                eq(100),
                eq(1L)))
                .thenReturn("K线数据: [收盘价: 50000, 成交量: 1000]");

        SegmentModel result = attentionPromptProcessor.process(context);

        assertNotNull(result);
        assertTrue(result.getContent().contains("## 关注合约数据(ATTENTION)"));
        assertTrue(result.getContent().contains("BTC-USDT-SWAP"));
    }

    @Test
    void testProcessWithDefaultTimeframe() throws PromptProcessException {
        PromptContext context = createPromptContext();
        AttentionInfo attentionInfo = AttentionInfo.builder()
                .queueId(1L)
                .instId("ETH-USDT-SWAP")
                .priority(2)
                .timeframe(null)
                .limit(100)
                .expectedTriggerTime(LocalDateTime.now())
                .build();
        context.setCustomData("attentions", Collections.singletonList(attentionInfo));

        when(technicalIndicatorProcessor.processQuery(
                eq("ETH-USDT-SWAP"),
                eq("1H"),
                eq(100),
                eq(1L)))
                .thenReturn("K线数据: [收盘价: 3000, 成交量: 500]");

        SegmentModel result = attentionPromptProcessor.process(context);

        assertNotNull(result);
        assertTrue(result.getContent().contains("ETH-USDT-SWAP"));
    }

    @Test
    void testProcessWithDefaultLimit() throws PromptProcessException {
        PromptContext context = createPromptContext();
        AttentionInfo attentionInfo = AttentionInfo.builder()
                .queueId(1L)
                .instId("BNB-USDT-SWAP")
                .priority(3)
                .timeframe("5m")
                .limit(null)
                .expectedTriggerTime(LocalDateTime.now())
                .build();
        context.setCustomData("attentions", Collections.singletonList(attentionInfo));

        when(technicalIndicatorProcessor.processQuery(
                eq("BNB-USDT-SWAP"),
                eq("5m"),
                eq(100),
                eq(1L)))
                .thenReturn("K线数据: [收盘价: 400, 成交量: 200]");

        SegmentModel result = attentionPromptProcessor.process(context);

        assertNotNull(result);
        assertTrue(result.getContent().contains("BNB-USDT-SWAP"));
    }

    @Test
    void testProcessEmptyAttentions() throws PromptProcessException {
        PromptContext context = createPromptContext();
        context.setCustomData("attentions", Collections.emptyList());

        SegmentModel result = attentionPromptProcessor.process(context);

        assertNotNull(result);
        assertEquals("", result.getContent());
    }

    @Test
    void testProcessNullAttentions() throws PromptProcessException {
        PromptContext context = createPromptContext();
        context.setCustomData("attentions", null);

        SegmentModel result = attentionPromptProcessor.process(context);

        assertNotNull(result);
        assertEquals("", result.getContent());
    }

    @Test
    void testProcessWithException() throws PromptProcessException {
        PromptContext context = createPromptContext();
        List<AttentionInfo> attentions = Collections.singletonList(createAttentionInfo());
        context.setCustomData("attentions", attentions);

        when(technicalIndicatorProcessor.processQuery(
                anyString(),
                anyString(),
                anyInt(),
                anyLong()))
                .thenThrow(new RuntimeException("获取K线数据失败"));

        SegmentModel result = attentionPromptProcessor.process(context);

        assertNotNull(result);
        assertTrue(result.getContent().contains("数据获取失败"));
        assertTrue(result.getContent().contains("获取K线数据失败"));
    }

    @Test
    void testProcessMultipleAttentions() throws PromptProcessException {
        PromptContext context = createPromptContext();
        AttentionInfo attention1 = createAttentionInfo();
        AttentionInfo attention2 = AttentionInfo.builder()
                .queueId(2L)
                .instId("ETH-USDT-SWAP")
                .priority(2)
                .timeframe("5m")
                .limit(50)
                .expectedTriggerTime(LocalDateTime.now())
                .build();
        context.setCustomData("attentions", Arrays.asList(attention1, attention2));

        when(technicalIndicatorProcessor.processQuery(
                eq("BTC-USDT-SWAP"),
                eq("1H"),
                eq(100),
                eq(1L)))
                .thenReturn("BTC K线数据");

        when(technicalIndicatorProcessor.processQuery(
                eq("ETH-USDT-SWAP"),
                eq("5m"),
                eq(50),
                eq(1L)))
                .thenReturn("ETH K线数据");

        SegmentModel result = attentionPromptProcessor.process(context);

        assertNotNull(result);
        assertTrue(result.getContent().contains("BTC-USDT-SWAP"));
        assertTrue(result.getContent().contains("ETH-USDT-SWAP"));
        assertTrue(result.getContent().contains("BTC K线数据"));
        assertTrue(result.getContent().contains("ETH K线数据"));
    }
}

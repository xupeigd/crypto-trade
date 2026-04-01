package com.crypto.trade.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AttentionInfo单元测试
 *
 * @author page
 * @date 2026-03-02
 */
class AttentionInfoTest {

    @Test
    void testBuilder() {
        LocalDateTime now = LocalDateTime.now();
        AttentionInfo attentionInfo = AttentionInfo.builder()
                .queueId(1L)
                .instId("BTC-USDT-SWAP")
                .priority(1)
                .timeframe("1H")
                .limit(100)
                .expectedTriggerTime(now)
                .build();

        assertEquals(1L, attentionInfo.getQueueId());
        assertEquals("BTC-USDT-SWAP", attentionInfo.getInstId());
        assertEquals(1, attentionInfo.getPriority());
        assertEquals("1H", attentionInfo.getTimeframe());
        assertEquals(100, attentionInfo.getLimit());
        assertEquals(now, attentionInfo.getExpectedTriggerTime());
    }

    @Test
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        AttentionInfo attentionInfo = new AttentionInfo(
                1L,
                "BTC-USDT-SWAP",
                2,
                "5m",
                50,
                now
        );

        assertEquals(1L, attentionInfo.getQueueId());
        assertEquals("BTC-USDT-SWAP", attentionInfo.getInstId());
        assertEquals(2, attentionInfo.getPriority());
        assertEquals("5m", attentionInfo.getTimeframe());
        assertEquals(50, attentionInfo.getLimit());
        assertEquals(now, attentionInfo.getExpectedTriggerTime());
    }

    @Test
    void testNoArgsConstructor() {
        AttentionInfo attentionInfo = new AttentionInfo();
        assertNull(attentionInfo.getQueueId());
        assertNull(attentionInfo.getInstId());
        assertNull(attentionInfo.getPriority());
        assertNull(attentionInfo.getTimeframe());
        assertNull(attentionInfo.getLimit());
        assertNull(attentionInfo.getExpectedTriggerTime());
    }

    @Test
    void testSetterAndGetter() {
        AttentionInfo attentionInfo = new AttentionInfo();
        LocalDateTime now = LocalDateTime.now();

        attentionInfo.setQueueId(1L);
        attentionInfo.setInstId("ETH-USDT-SWAP");
        attentionInfo.setPriority(3);
        attentionInfo.setTimeframe("15m");
        attentionInfo.setLimit(200);
        attentionInfo.setExpectedTriggerTime(now);

        assertEquals(1L, attentionInfo.getQueueId());
        assertEquals("ETH-USDT-SWAP", attentionInfo.getInstId());
        assertEquals(3, attentionInfo.getPriority());
        assertEquals("15m", attentionInfo.getTimeframe());
        assertEquals(200, attentionInfo.getLimit());
        assertEquals(now, attentionInfo.getExpectedTriggerTime());
    }

    @Test
    void testDefaultValues() {
        AttentionInfo attentionInfo = new AttentionInfo();
        assertNull(attentionInfo.getQueueId());
        assertNull(attentionInfo.getInstId());
        assertNull(attentionInfo.getPriority());
        assertNull(attentionInfo.getTimeframe());
        assertNull(attentionInfo.getLimit());
        assertNull(attentionInfo.getExpectedTriggerTime());
    }
}

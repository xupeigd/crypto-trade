package com.crypto.trade.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AttentionQueue单元测试
 *
 * @author page
 * @date 2026-03-02
 */
class AttentionQueueTest {

    @Test
    void testBuilder() {
        LocalDateTime now = LocalDateTime.now();
        AttentionQueue attentionQueue = AttentionQueue.builder()
                .id(1L)
                .recordId(100L)
                .apiKeyId(200L)
                .instId("BTC-USDT-SWAP")
                .priority(1)
                .timeframe("1H")
                .queryLimit(100)
                .expectedTriggerTime(now.plusMinutes(5))
                .actualTriggerTime(null)
                .status("PENDING")
                .createTime(now)
                .updateTime(now)
                .remark("测试备注")
                .build();

        assertEquals(1L, attentionQueue.getId());
        assertEquals(100L, attentionQueue.getRecordId());
        assertEquals(200L, attentionQueue.getApiKeyId());
        assertEquals("BTC-USDT-SWAP", attentionQueue.getInstId());
        assertEquals(1, attentionQueue.getPriority());
        assertEquals("1H", attentionQueue.getTimeframe());
        assertEquals(100, attentionQueue.getQueryLimit());
        assertEquals(now.plusMinutes(5), attentionQueue.getExpectedTriggerTime());
        assertNull(attentionQueue.getActualTriggerTime());
        assertEquals("PENDING", attentionQueue.getStatus());
        assertEquals(now, attentionQueue.getCreateTime());
        assertEquals(now, attentionQueue.getUpdateTime());
        assertEquals("测试备注", attentionQueue.getRemark());
    }

    @Test
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        AttentionQueue attentionQueue = new AttentionQueue(
                1L,
                100L,
                200L,
                "ETH-USDT-SWAP",
                2,
                "5m",
                50,
                now.plusMinutes(5),
                now,
                "EXPIRED",
                now,
                now,
                "测试"
        );

        assertEquals(1L, attentionQueue.getId());
        assertEquals(100L, attentionQueue.getRecordId());
        assertEquals(200L, attentionQueue.getApiKeyId());
        assertEquals("ETH-USDT-SWAP", attentionQueue.getInstId());
        assertEquals(2, attentionQueue.getPriority());
        assertEquals("5m", attentionQueue.getTimeframe());
        assertEquals(50, attentionQueue.getQueryLimit());
        assertEquals(now.plusMinutes(5), attentionQueue.getExpectedTriggerTime());
        assertEquals(now, attentionQueue.getActualTriggerTime());
        assertEquals("EXPIRED", attentionQueue.getStatus());
        assertEquals(now, attentionQueue.getCreateTime());
        assertEquals(now, attentionQueue.getUpdateTime());
        assertEquals("测试", attentionQueue.getRemark());
    }

    @Test
    void testNoArgsConstructor() {
        AttentionQueue attentionQueue = new AttentionQueue();
        assertNull(attentionQueue.getId());
        assertNull(attentionQueue.getRecordId());
        assertNull(attentionQueue.getApiKeyId());
        assertNull(attentionQueue.getInstId());
        assertNull(attentionQueue.getPriority());
        assertNull(attentionQueue.getTimeframe());
        assertNull(attentionQueue.getQueryLimit());
        assertNull(attentionQueue.getExpectedTriggerTime());
        assertNull(attentionQueue.getActualTriggerTime());
        assertNull(attentionQueue.getCreateTime());
        assertNull(attentionQueue.getUpdateTime());
        assertNull(attentionQueue.getRemark());
    }

    @Test
    void testDefaultStatus() {
        AttentionQueue attentionQueue = AttentionQueue.builder()
                .recordId(100L)
                .apiKeyId(200L)
                .instId("BTC-USDT-SWAP")
                .expectedTriggerTime(LocalDateTime.now().plusMinutes(5))
                .build();

        assertEquals("PENDING", attentionQueue.getStatus());
    }

    @Test
    void testSetterAndGetter() {
        AttentionQueue attentionQueue = new AttentionQueue();
        LocalDateTime now = LocalDateTime.now();

        attentionQueue.setId(1L);
        attentionQueue.setRecordId(100L);
        attentionQueue.setApiKeyId(200L);
        attentionQueue.setInstId("BNB-USDT-SWAP");
        attentionQueue.setPriority(3);
        attentionQueue.setTimeframe("15m");
        attentionQueue.setQueryLimit(200);
        attentionQueue.setExpectedTriggerTime(now.plusMinutes(10));
        attentionQueue.setActualTriggerTime(now);
        attentionQueue.setStatus("EXECUTED");
        attentionQueue.setCreateTime(now);
        attentionQueue.setUpdateTime(now);
        attentionQueue.setRemark("备注信息");

        assertEquals(1L, attentionQueue.getId());
        assertEquals(100L, attentionQueue.getRecordId());
        assertEquals(200L, attentionQueue.getApiKeyId());
        assertEquals("BNB-USDT-SWAP", attentionQueue.getInstId());
        assertEquals(3, attentionQueue.getPriority());
        assertEquals("15m", attentionQueue.getTimeframe());
        assertEquals(200, attentionQueue.getQueryLimit());
        assertEquals(now.plusMinutes(10), attentionQueue.getExpectedTriggerTime());
        assertEquals(now, attentionQueue.getActualTriggerTime());
        assertEquals("EXECUTED", attentionQueue.getStatus());
        assertEquals(now, attentionQueue.getCreateTime());
        assertEquals(now, attentionQueue.getUpdateTime());
        assertEquals("备注信息", attentionQueue.getRemark());
    }

    @Test
    void testPrePersist() {
        AttentionQueue attentionQueue = AttentionQueue.builder()
                .recordId(100L)
                .apiKeyId(200L)
                .instId("BTC-USDT-SWAP")
                .expectedTriggerTime(LocalDateTime.now().plusMinutes(5))
                .status("PENDING")
                .build();

        attentionQueue.prePersist();

        assertNotNull(attentionQueue.getCreateTime());
        assertNotNull(attentionQueue.getUpdateTime());
        assertEquals("PENDING", attentionQueue.getStatus());
    }

    @Test
    void testPrePersistWithNullStatus() {
        AttentionQueue attentionQueue = AttentionQueue.builder()
                .recordId(100L)
                .apiKeyId(200L)
                .instId("BTC-USDT-SWAP")
                .expectedTriggerTime(LocalDateTime.now().plusMinutes(5))
                .status(null)
                .build();

        attentionQueue.prePersist();

        assertEquals("PENDING", attentionQueue.getStatus());
    }

    @Test
    void testPreUpdate() {
        LocalDateTime originalTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0);
        AttentionQueue attentionQueue = AttentionQueue.builder()
                .recordId(100L)
                .apiKeyId(200L)
                .instId("BTC-USDT-SWAP")
                .expectedTriggerTime(LocalDateTime.now().plusMinutes(5))
                .status("PENDING")
                .createTime(originalTime)
                .updateTime(originalTime)
                .build();

        attentionQueue.preUpdate();

        assertEquals(originalTime, attentionQueue.getCreateTime());
        assertNotNull(attentionQueue.getUpdateTime());
        assertTrue(attentionQueue.getUpdateTime().isAfter(originalTime) ||
                attentionQueue.getUpdateTime().isEqual(originalTime));
    }
}

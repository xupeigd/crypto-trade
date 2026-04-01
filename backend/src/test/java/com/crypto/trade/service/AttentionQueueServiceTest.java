package com.crypto.trade.service;

import com.crypto.trade.entity.AttentionQueue;
import com.crypto.trade.repository.AttentionQueueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * AttentionQueueService单元测试
 *
 * @author page
 * @date 2026-03-02
 */
@ExtendWith(MockitoExtension.class)
class AttentionQueueServiceTest {

    @Mock
    private AttentionQueueRepository attentionQueueRepository;

    @InjectMocks
    private AttentionQueueService attentionQueueService;

    private AttentionQueue createTestAttentionQueue() {
        return AttentionQueue.builder()
                .id(1L)
                .recordId(100L)
                .apiKeyId(200L)
                .instId("BTC-USDT-SWAP")
                .priority(1)
                .timeframe("1H")
                .queryLimit(100)
                .expectedTriggerTime(LocalDateTime.now().plusMinutes(5))
                .status("PENDING")
                .build();
    }

    @Test
    void testSave() {
        AttentionQueue attentionQueue = createTestAttentionQueue();
        attentionQueue.setExpectedTriggerTime(null);

        when(attentionQueueRepository.save(any(AttentionQueue.class))).thenAnswer(invocation -> {
            AttentionQueue saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        AttentionQueue result = attentionQueueService.save(attentionQueue);

        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        assertNotNull(result.getExpectedTriggerTime());
        verify(attentionQueueRepository, times(1)).save(attentionQueue);
    }

    @Test
    void testSaveWithCustomTriggerTime() {
        LocalDateTime customTime = LocalDateTime.now().plusHours(1);
        AttentionQueue attentionQueue = createTestAttentionQueue();
        attentionQueue.setExpectedTriggerTime(customTime);

        when(attentionQueueRepository.save(any(AttentionQueue.class))).thenAnswer(invocation -> {
            AttentionQueue saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        AttentionQueue result = attentionQueueService.save(attentionQueue);

        assertNotNull(result);
        assertEquals(customTime, result.getExpectedTriggerTime());
    }

    @Test
    void testGetByRecordId() {
        List<AttentionQueue> mockList = Arrays.asList(createTestAttentionQueue());
        when(attentionQueueRepository.findByRecordId(100L)).thenReturn(mockList);

        List<AttentionQueue> result = attentionQueueService.getByRecordId(100L);

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getRecordId());
    }

    @Test
    void testGetByRecordIdEmpty() {
        when(attentionQueueRepository.findByRecordId(999L)).thenReturn(Collections.emptyList());

        List<AttentionQueue> result = attentionQueueService.getByRecordId(999L);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetPendingAttentions() {
        List<AttentionQueue> mockList = Arrays.asList(createTestAttentionQueue());
        when(attentionQueueRepository.findPendingAttentions(any(LocalDateTime.class))).thenReturn(mockList);

        List<AttentionQueue> result = attentionQueueService.getPendingAttentions();

        assertEquals(1, result.size());
        assertEquals("PENDING", result.get(0).getStatus());
    }

    @Test
    void testGetPendingAttentionsEmpty() {
        when(attentionQueueRepository.findPendingAttentions(any(LocalDateTime.class))).thenReturn(Collections.emptyList());

        List<AttentionQueue> result = attentionQueueService.getPendingAttentions();

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetPendingAttentionsByApiKeyId() {
        List<AttentionQueue> mockList = Arrays.asList(createTestAttentionQueue());
        when(attentionQueueRepository.findPendingAttentionsByApiKeyId(any(LocalDateTime.class), anyLong()))
                .thenReturn(mockList);

        List<AttentionQueue> result = attentionQueueService.getPendingAttentionsByApiKeyId(200L);

        assertEquals(1, result.size());
        assertEquals(200L, result.get(0).getApiKeyId());
    }

    @Test
    void testGetPendingAttentionsByApiKeyIdEmpty() {
        when(attentionQueueRepository.findPendingAttentionsByApiKeyId(any(LocalDateTime.class), anyLong()))
                .thenReturn(Collections.emptyList());

        List<AttentionQueue> result = attentionQueueService.getPendingAttentionsByApiKeyId(999L);

        assertTrue(result.isEmpty());
    }

    @Test
    void testUpdateStatus() {
        doNothing().when(attentionQueueRepository).updateStatus(anyLong(), anyString(), any(), any());

        attentionQueueService.updateStatus(1L, "EXECUTED");

        verify(attentionQueueRepository, times(1)).updateStatus(
                eq(1L), eq("EXECUTED"), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void testTriggerPendingAttentions() {
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1);
        AttentionQueue attentionQueue = createTestAttentionQueue();
        attentionQueue.setExpectedTriggerTime(pastTime);

        when(attentionQueueRepository.findPendingAttentions(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(attentionQueue));
        doNothing().when(attentionQueueRepository).updateStatus(anyLong(), anyString(), any(), any());

        attentionQueueService.triggerPendingAttentions();

        verify(attentionQueueRepository, times(1)).updateStatus(
                eq(1L), eq("EXECUTED"), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void testTriggerPendingAttentionsEmpty() {
        when(attentionQueueRepository.findPendingAttentions(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        attentionQueueService.triggerPendingAttentions();

        verify(attentionQueueRepository, never()).updateStatus(anyLong(), anyString(), any(), any());
    }

    @Test
    void testCalculateNextTriggerTimePriority1() {
        LocalDateTime result = attentionQueueService.calculateNextTriggerTime(1);
        LocalDateTime expected = LocalDateTime.now().plusMinutes(3);

        assertTrue(result.isAfter(expected.minusSeconds(2)));
        assertTrue(result.isBefore(expected.plusSeconds(2)));
    }

    @Test
    void testCalculateNextTriggerTimePriority2() {
        LocalDateTime result = attentionQueueService.calculateNextTriggerTime(2);
        LocalDateTime expected = LocalDateTime.now().plusMinutes(5);

        assertTrue(result.isAfter(expected.minusSeconds(2)));
        assertTrue(result.isBefore(expected.plusSeconds(2)));
    }

    @Test
    void testCalculateNextTriggerTimePriority3() {
        LocalDateTime result = attentionQueueService.calculateNextTriggerTime(3);
        LocalDateTime expected = LocalDateTime.now().plusMinutes(10);

        assertTrue(result.isAfter(expected.minusSeconds(2)));
        assertTrue(result.isBefore(expected.plusSeconds(2)));
    }

    @Test
    void testCalculateNextTriggerTimeDefault() {
        LocalDateTime result = attentionQueueService.calculateNextTriggerTime(99);
        LocalDateTime expected = LocalDateTime.now().plusMinutes(5);

        assertTrue(result.isAfter(expected.minusSeconds(2)));
        assertTrue(result.isBefore(expected.plusSeconds(2)));
    }

    @Test
    void testCalculateNextTriggerTimeNull() {
        LocalDateTime result = attentionQueueService.calculateNextTriggerTime(null);
        LocalDateTime expected = LocalDateTime.now().plusMinutes(5);

        assertTrue(result.isAfter(expected.minusSeconds(2)));
        assertTrue(result.isBefore(expected.plusSeconds(2)));
    }
}

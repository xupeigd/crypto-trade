package com.crypto.trade.scheduler;

import com.crypto.trade.entity.AttentionQueue;
import com.crypto.trade.service.AttentionQueueService;
import com.crypto.trade.service.AsyncTradingTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AttentionQueueScheduler单元测试
 *
 * @author page
 * @date 2026-03-02
 */
@ExtendWith(MockitoExtension.class)
class AttentionQueueSchedulerTest {

    @Mock
    private AttentionQueueService attentionQueueService;

    @Mock
    private AsyncTradingTaskService asyncTradingTaskService;

    @InjectMocks
    private AttentionQueueScheduler attentionQueueScheduler;

    private AttentionQueue createTestAttentionQueue() {
        return AttentionQueue.builder()
                .id(1L)
                .recordId(100L)
                .apiKeyId(200L)
                .instId("BTC-USDT-SWAP")
                .priority(1)
                .timeframe("1H")
                .queryLimit(100)
                .expectedTriggerTime(LocalDateTime.now().minusMinutes(1))
                .status("PENDING")
                .build();
    }

    @Test
    void testTriggerPendingAttentionsWithTasks() {
        List<AttentionQueue> pendingList = Collections.singletonList(createTestAttentionQueue());
        when(attentionQueueService.getPendingAttentions()).thenReturn(pendingList);
        doNothing().when(attentionQueueService).updateStatus(any(), any());

        attentionQueueScheduler.triggerPendingAttentions();

        verify(attentionQueueService, times(1)).getPendingAttentions();
        verify(attentionQueueService, times(1)).updateStatus(1L, "EXECUTED");
        verify(asyncTradingTaskService, times(1)).executeAsyncTradingTask(any());
    }

    @Test
    void testTriggerPendingAttentionsWithoutTasks() {
        when(attentionQueueService.getPendingAttentions()).thenReturn(Collections.emptyList());

        attentionQueueScheduler.triggerPendingAttentions();

        verify(attentionQueueService, times(1)).getPendingAttentions();
        verify(attentionQueueService, never()).updateStatus(any(), any());
        verify(asyncTradingTaskService, never()).executeAsyncTradingTask(any());
    }

    @Test
    void testTriggerPendingAttentionsWithException() {
        List<AttentionQueue> pendingList = Collections.singletonList(createTestAttentionQueue());
        when(attentionQueueService.getPendingAttentions()).thenReturn(pendingList);
        doThrow(new RuntimeException("触发异常")).when(asyncTradingTaskService).executeAsyncTradingTask(any());

        attentionQueueScheduler.triggerPendingAttentions();

        verify(attentionQueueService, times(1)).getPendingAttentions();
        verify(attentionQueueService, never()).updateStatus(any(), any());
        verify(asyncTradingTaskService, times(1)).executeAsyncTradingTask(any());
    }

    @Test
    void testTriggerPendingAttentionsMultipleTasks() {
        AttentionQueue attention1 = createTestAttentionQueue();
        attention1.setId(1L);
        attention1.setInstId("BTC-USDT-SWAP");

        AttentionQueue attention2 = AttentionQueue.builder()
                .id(2L)
                .recordId(101L)
                .apiKeyId(201L)
                .instId("ETH-USDT-SWAP")
                .priority(2)
                .timeframe("5m")
                .queryLimit(50)
                .expectedTriggerTime(LocalDateTime.now().minusMinutes(1))
                .status("PENDING")
                .build();

        List<AttentionQueue> pendingList = List.of(attention1, attention2);
        when(attentionQueueService.getPendingAttentions()).thenReturn(pendingList);

        attentionQueueScheduler.triggerPendingAttentions();

        verify(attentionQueueService, times(1)).getPendingAttentions();
        verify(attentionQueueService, times(2)).updateStatus(any(), any());
        verify(asyncTradingTaskService, times(2)).executeAsyncTradingTask(any());
    }
}

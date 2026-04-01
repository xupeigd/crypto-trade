package com.crypto.trade.repository;

import com.crypto.trade.entity.AttentionQueue;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AttentionQueueRepository单元测试
 *
 * @author page
 * @date 2026-03-02
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class AttentionQueueRepositoryTest {

    @Autowired
    private AttentionQueueRepository attentionQueueRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        attentionQueueRepository.deleteAll();
    }

    @Test
    void testFindByRecordId() {
        AttentionQueue attentionQueue = AttentionQueue.builder()
                .recordId(100L)
                .apiKeyId(200L)
                .instId("BTC-USDT-SWAP")
                .priority(1)
                .timeframe("1H")
                .queryLimit(100)
                .expectedTriggerTime(LocalDateTime.now().plusMinutes(5))
                .status("PENDING")
                .build();
        attentionQueue.prePersist();
        attentionQueueRepository.save(attentionQueue);

        List<AttentionQueue> result = attentionQueueRepository.findByRecordId(100L);

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getRecordId());
        assertEquals("BTC-USDT-SWAP", result.get(0).getInstId());
    }

    @Test
    void testFindByRecordIdNotFound() {
        List<AttentionQueue> result = attentionQueueRepository.findByRecordId(999L);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindPendingAttentions() {
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1);
        AttentionQueue attentionQueue1 = AttentionQueue.builder()
                .recordId(100L)
                .apiKeyId(200L)
                .instId("BTC-USDT-SWAP")
                .priority(1)
                .expectedTriggerTime(pastTime)
                .status("PENDING")
                .build();
        attentionQueue1.prePersist();
        attentionQueueRepository.save(attentionQueue1);

        AttentionQueue attentionQueue2 = AttentionQueue.builder()
                .recordId(101L)
                .apiKeyId(201L)
                .instId("ETH-USDT-SWAP")
                .priority(2)
                .expectedTriggerTime(pastTime)
                .status("PENDING")
                .build();
        attentionQueue2.prePersist();
        attentionQueueRepository.save(attentionQueue2);

        AttentionQueue attentionQueue3 = AttentionQueue.builder()
                .recordId(102L)
                .apiKeyId(202L)
                .instId("BNB-USDT-SWAP")
                .priority(3)
                .expectedTriggerTime(LocalDateTime.now().plusMinutes(10))
                .status("PENDING")
                .build();
        attentionQueue3.prePersist();
        attentionQueueRepository.save(attentionQueue3);

        List<AttentionQueue> result = attentionQueueRepository.findPendingAttentions(LocalDateTime.now());

        assertEquals(2, result.size());
    }

    @Test
    void testFindPendingAttentionsEmpty() {
        List<AttentionQueue> result = attentionQueueRepository.findPendingAttentions(LocalDateTime.now());
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindPendingAttentionsByApiKeyId() {
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1);
        AttentionQueue attentionQueue1 = AttentionQueue.builder()
                .recordId(100L)
                .apiKeyId(200L)
                .instId("BTC-USDT-SWAP")
                .priority(1)
                .expectedTriggerTime(pastTime)
                .status("PENDING")
                .build();
        attentionQueue1.prePersist();
        attentionQueueRepository.save(attentionQueue1);

        AttentionQueue attentionQueue2 = AttentionQueue.builder()
                .recordId(101L)
                .apiKeyId(201L)
                .instId("ETH-USDT-SWAP")
                .priority(2)
                .expectedTriggerTime(pastTime)
                .status("PENDING")
                .build();
        attentionQueue2.prePersist();
        attentionQueueRepository.save(attentionQueue2);

        List<AttentionQueue> result = attentionQueueRepository.findPendingAttentionsByApiKeyId(LocalDateTime.now(), 200L);

        assertEquals(1, result.size());
        assertEquals(200L, result.get(0).getApiKeyId());
    }

    @Test
    void testFindPendingAttentionsByApiKeyIdNotFound() {
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1);
        AttentionQueue attentionQueue = AttentionQueue.builder()
                .recordId(100L)
                .apiKeyId(200L)
                .instId("BTC-USDT-SWAP")
                .priority(1)
                .expectedTriggerTime(pastTime)
                .status("PENDING")
                .build();
        attentionQueue.prePersist();
        attentionQueueRepository.save(attentionQueue);

        List<AttentionQueue> result = attentionQueueRepository.findPendingAttentionsByApiKeyId(LocalDateTime.now(), 999L);
        assertTrue(result.isEmpty());
    }

    @Test
    void testUpdateStatus() {
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1);
        AttentionQueue attentionQueue = AttentionQueue.builder()
                .recordId(100L)
                .apiKeyId(200L)
                .instId("BTC-USDT-SWAP")
                .priority(1)
                .expectedTriggerTime(pastTime)
                .status("PENDING")
                .build();
        attentionQueue.prePersist();
        AttentionQueue saved = attentionQueueRepository.save(attentionQueue);
        entityManager.flush();
        entityManager.clear();

        LocalDateTime now = LocalDateTime.now();
        attentionQueueRepository.updateStatus(saved.getId(), "EXECUTED", now, now);
        entityManager.flush();
        entityManager.clear();

        AttentionQueue updated = attentionQueueRepository.findById(saved.getId()).orElse(null);
        assertNotNull(updated);
        assertEquals("EXECUTED", updated.getStatus());
        assertNotNull(updated.getActualTriggerTime());
    }
}

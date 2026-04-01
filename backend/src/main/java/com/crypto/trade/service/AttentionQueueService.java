package com.crypto.trade.service;

import com.crypto.trade.entity.AttentionQueue;
import com.crypto.trade.repository.AttentionQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AttentionQueueService
 * ATTENTION队列服务
 *
 * @author page
 * @date 2026-03-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttentionQueueService {

    private final AttentionQueueRepository attentionQueueRepository;

    /**
     * 保存ATTENTION到队列
     */
    @Transactional
    public AttentionQueue save(AttentionQueue attentionQueue) {
        // 计算触发时间：当前时间 + 5分钟
        if (attentionQueue.getExpectedTriggerTime() == null) {
            attentionQueue.setExpectedTriggerTime(LocalDateTime.now().plusMinutes(5));
        }
        attentionQueue.setStatus("PENDING");
        AttentionQueue saved = attentionQueueRepository.save(attentionQueue);
        log.info("【ATTENTION队列】保存成功 - id: {}, recordId: {}, instId: {}, expectedTriggerTime: {}",
                saved.getId(), saved.getRecordId(), saved.getInstId(), saved.getExpectedTriggerTime());
        return saved;
    }

    /**
     * 根据recordId查询ATTENTION列表
     */
    public List<AttentionQueue> getByRecordId(Long recordId) {
        return attentionQueueRepository.findByRecordId(recordId);
    }

    /**
     * 查询待执行的ATTENTION
     */
    public List<AttentionQueue> getPendingAttentions() {
        return attentionQueueRepository.findPendingAttentions(LocalDateTime.now());
    }

    /**
     * 根据API Key ID查询待执行的ATTENTION
     */
    public List<AttentionQueue> getPendingAttentionsByApiKeyId(Long apiKeyId) {
        return attentionQueueRepository.findPendingAttentionsByApiKeyId(LocalDateTime.now(), apiKeyId);
    }

    /**
     * 更新状态
     */
    @Transactional
    public void updateStatus(Long id, String status) {
        LocalDateTime now = LocalDateTime.now();
        attentionQueueRepository.updateStatus(id, status, now, now);
        log.info("【ATTENTION队列】状态更新 - id: {}, status: {}", id, status);
    }

    /**
     * 触发到期的ATTENTION
     */
    @Transactional
    public void triggerPendingAttentions() {
        List<AttentionQueue> pendingList = getPendingAttentions();
        log.info("【ATTENTION队列】待触发数量: {}", pendingList.size());

        for (AttentionQueue attention : pendingList) {
            try {
                // 检查是否到达触发时间
                if (attention.getExpectedTriggerTime().isBefore(LocalDateTime.now()) ||
                        attention.getExpectedTriggerTime().isEqual(LocalDateTime.now())) {
                    // 更新状态为EXECUTED
                    updateStatus(attention.getId(), "EXECUTED");
                    log.info("【ATTENTION队列】触发执行 - id: {}, instId: {}", attention.getId(), attention.getInstId());
                }
            } catch (Exception e) {
                log.error("【ATTENTION队列】触发失败 - id: {}", attention.getId(), e);
            }
        }
    }

    /**
     * 根据优先级计算下次触发时间
     * 优先级越高1），（如触发时间越短
     */
    public LocalDateTime calculateNextTriggerTime(Integer priority) {
        // 默认5分钟
        int minutes = 5;
        if (priority != null) {
            // 优先级1: 3分钟, 优先级2: 5分钟, 优先级3: 10分钟
            minutes = switch (priority) {
                case 1 -> 3;
                case 2 -> 5;
                case 3 -> 10;
                default -> 5;
            };
        }
        return LocalDateTime.now().plusMinutes(minutes);
    }
}

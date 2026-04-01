package com.crypto.trade.repository;

import com.crypto.trade.entity.AttentionQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AttentionQueueRepository
 * ATTENTION队列数据访问层
 *
 * @author page
 * @date 2026-03-02
 */
@Repository
public interface AttentionQueueRepository
        extends JpaRepository<AttentionQueue, Long> {

    /**
     * 根据recordId查询ATTENTION列表
     */
    List<AttentionQueue> findByRecordId(Long recordId);

    /**
     * 查询待执行的ATTENTION（状态为PENDING且到达触发时间）
     */
    @Query("SELECT a FROM AttentionQueue a WHERE a.status = 'PENDING' AND a.expectedTriggerTime <= :currentTime")
    List<AttentionQueue> findPendingAttentions(@Param("currentTime") LocalDateTime currentTime);

    /**
     * 根据API Key ID查询待执行的ATTENTION
     */
    @Query("SELECT a FROM AttentionQueue a WHERE a.status = 'PENDING' AND a.expectedTriggerTime <= :currentTime AND a.apiKeyId = :apiKeyId")
    List<AttentionQueue> findPendingAttentionsByApiKeyId(@Param("currentTime") LocalDateTime currentTime, @Param("apiKeyId") Long apiKeyId);

    /**
     * 更新状态
     */
    @Modifying
    @Query("UPDATE AttentionQueue a SET a.status = :status, a.actualTriggerTime = :actualTriggerTime, a.updateTime = :updateTime WHERE a.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") String status, @Param("actualTriggerTime") LocalDateTime actualTriggerTime, @Param("updateTime") LocalDateTime updateTime);
}

package com.crypto.trade.repository;

import com.crypto.trade.entity.AccountEquitySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AccountEquitySnapshotRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface AccountEquitySnapshotRepository extends JpaRepository<AccountEquitySnapshot, Long> {

    /**
     * 根据API密钥ID查找快照
     */
    Optional<AccountEquitySnapshot> findByApiKeyId(Long apiKeyId);

    /**
     * 根据API密钥ID列表查找快照
     */
    List<AccountEquitySnapshot> findByApiKeyIdIn(List<Long> apiKeyIds);

    /**
     * 查找所有快照，按更新时间降序排列
     */
    List<AccountEquitySnapshot> findAllByOrderByUpdateTimeDesc();

    /**
     * 查找指定时间之后更新的快照
     */
    List<AccountEquitySnapshot> findByUpdateTimeAfterOrderByUpdateTimeDesc(LocalDateTime after);

    /**
     * 计算所有活跃API密钥的总权益
     */
    @Query("SELECT COALESCE(SUM(e.totalEquityUsdt), 0) FROM AccountEquitySnapshot e " +
            "WHERE e.apiKeyId IN :activeApiKeyIds")
    BigDecimal getTotalEquityByActiveKeys(@Param("activeApiKeyIds") List<Long> activeApiKeyIds);

    /**
     * 计算所有API密钥的总权益
     */
    @Query("SELECT COALESCE(SUM(e.totalEquityUsdt), 0) FROM AccountEquitySnapshot e")
    BigDecimal getTotalEquityAll();

    /**
     * 计算所有活跃API密钥的可用权益
     */
    @Query("SELECT COALESCE(SUM(e.availableEquityUsdt), 0) FROM AccountEquitySnapshot e " +
            "WHERE e.apiKeyId IN :activeApiKeyIds")
    BigDecimal getAvailableEquityByActiveKeys(@Param("activeApiKeyIds") List<Long> activeApiKeyIds);

    /**
     * 计算所有API密钥的可用权益
     */
    @Query("SELECT COALESCE(SUM(e.availableEquityUsdt), 0) FROM AccountEquitySnapshot e")
    BigDecimal getAvailableEquityAll();

    /**
     * 保存或更新快照（基于API密钥ID的唯一约束）
     * 使用JPA标准方法实现upsert操作
     */
    @Transactional
    default AccountEquitySnapshot saveOrUpdateByApiKeyId(Long apiKeyId,
                                                         BigDecimal totalEquityUsdt,
                                                         BigDecimal availableEquityUsdt,
                                                         BigDecimal frozenEquityUsdt,
                                                         BigDecimal marginEquityUsdt,
                                                         LocalDateTime updateTime) {
        Optional<AccountEquitySnapshot> existing = findByApiKeyId(apiKeyId);
        AccountEquitySnapshot entity;

        if (existing.isPresent()) {
            entity = existing.get();
            // 更新现有记录的字段
            entity.setTotalEquityUsdt(totalEquityUsdt);
            entity.setAvailableEquityUsdt(availableEquityUsdt);
            entity.setFrozenEquityUsdt(frozenEquityUsdt);
            entity.setMarginEquityUsdt(marginEquityUsdt);
            entity.setUpdateTime(updateTime);
        } else {
            entity = new AccountEquitySnapshot();
            entity.setApiKeyId(apiKeyId);
            entity.setTotalEquityUsdt(totalEquityUsdt);
            entity.setAvailableEquityUsdt(availableEquityUsdt);
            entity.setFrozenEquityUsdt(frozenEquityUsdt);
            entity.setMarginEquityUsdt(marginEquityUsdt);
            entity.setUpdateTime(updateTime);
        }

        return save(entity);
    }

    /**
     * 删除指定时间之前的快照数据
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AccountEquitySnapshot e WHERE e.updateTime < :before")
    int deleteOldSnapshots(@Param("before") LocalDateTime before);

    /**
     * 检查是否存在指定API密钥的快照
     */
    boolean existsByApiKeyId(Long apiKeyId);
}
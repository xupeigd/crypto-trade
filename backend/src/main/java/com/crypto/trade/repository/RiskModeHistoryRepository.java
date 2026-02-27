package com.crypto.trade.repository;

import com.crypto.trade.entity.RiskMode;
import com.crypto.trade.entity.RiskModeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RiskModeHistoryRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface RiskModeHistoryRepository extends JpaRepository<RiskModeHistory, Long> {

    /**
     * 根据旧模式查询历史记录
     */
    List<RiskModeHistory> findByOldMode(RiskMode oldMode);

    /**
     * 根据新模式查询历史记录
     */
    List<RiskModeHistory> findByNewMode(RiskMode newMode);

    /**
     * 根据时间范围查询历史记录
     */
    @Query("SELECT h FROM RiskModeHistory h WHERE h.createdTime >= :startTime AND h.createdTime <= :endTime ORDER BY h.createdTime DESC")
    List<RiskModeHistory> findByCreatedTimeBetween(@Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 查询最近的历史记录
     */
    @Query("SELECT h FROM RiskModeHistory h ORDER BY h.createdTime DESC")
    List<RiskModeHistory> findRecentHistory();

    /**
     * 查询最近N条历史记录
     */
    @Query("SELECT h FROM RiskModeHistory h ORDER BY h.createdTime DESC LIMIT :limit")
    List<RiskModeHistory> findRecentHistoryWithLimit(@Param("limit") int limit);

    /**
     * 查询最近一次的模式变更
     */
    @Query("SELECT h FROM RiskModeHistory h ORDER BY h.createdTime DESC LIMIT 1")
    RiskModeHistory findLastChange();

    /**
     * 统计指定时间范围内的变更次数
     */
    @Query("SELECT COUNT(h) FROM RiskModeHistory h WHERE h.createdTime >= :startTime AND h.createdTime <= :endTime")
    long countByCreatedTimeBetween(@Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 根据操作者信息查询历史记录
     */
    List<RiskModeHistory> findByOperatorInfoContaining(String operatorInfo);
}
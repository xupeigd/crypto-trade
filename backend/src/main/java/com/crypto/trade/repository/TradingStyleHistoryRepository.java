package com.crypto.trade.repository;

import com.crypto.trade.entity.TradingStyle;
import com.crypto.trade.entity.TradingStyleHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TradingStyleHistoryRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface TradingStyleHistoryRepository extends JpaRepository<TradingStyleHistory, Long> {

    /**
     * 根据旧风格查询历史记录
     */
    List<TradingStyleHistory> findByOldStyle(TradingStyle oldStyle);

    /**
     * 根据新风格查询历史记录
     */
    List<TradingStyleHistory> findByNewStyle(TradingStyle newStyle);

    /**
     * 根据时间范围查询历史记录
     */
    @Query("SELECT h FROM TradingStyleHistory h WHERE h.createdTime >= :startTime AND h.createdTime <= :endTime ORDER BY h.createdTime DESC")
    List<TradingStyleHistory> findByCreatedTimeBetween(@Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);

    /**
     * 查询最近的历史记录
     */
    @Query("SELECT h FROM TradingStyleHistory h ORDER BY h.createdTime DESC")
    List<TradingStyleHistory> findRecentHistory();

    /**
     * 查询最近N条历史记录
     */
    @Query("SELECT h FROM TradingStyleHistory h ORDER BY h.createdTime DESC LIMIT :limit")
    List<TradingStyleHistory> findRecentHistoryWithLimit(@Param("limit") int limit);

    /**
     * 查询最近一次的风格变更
     */
    @Query("SELECT h FROM TradingStyleHistory h ORDER BY h.createdTime DESC LIMIT 1")
    TradingStyleHistory findLastChange();

    /**
     * 统计指定时间范围内的变更次数
     */
    @Query("SELECT COUNT(h) FROM TradingStyleHistory h WHERE h.createdTime >= :startTime AND h.createdTime <= :endTime")
    long countByCreatedTimeBetween(@Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 根据操作者信息查询历史记录
     */
    List<TradingStyleHistory> findByOperatorInfoContaining(String operatorInfo);
}
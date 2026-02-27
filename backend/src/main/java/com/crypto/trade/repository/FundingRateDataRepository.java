package com.crypto.trade.repository;

import com.crypto.trade.entity.FundingRateData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * FundingRateDataRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface FundingRateDataRepository extends JpaRepository<FundingRateData, Long> {

    /**
     * 获取指定合约的最新资金费率
     */
    Optional<FundingRateData> findTopByInstIdOrderByDataIngestionTimeDesc(String instId);

    /**
     * 获取指定合约类型的资金费率列表
     */
    List<FundingRateData> findByInstTypeOrderByDataIngestionTimeDesc(String instType);

    /**
     * 获取指定时间后的数据
     */
    List<FundingRateData> findByDataIngestionTimeAfter(LocalDateTime afterTime);

    /**
     * 获取指定交易所和时间范围的数据
     */
    @Query("SELECT f FROM FundingRateData f WHERE f.vendor = :vendor " +
            "AND f.dataIngestionTime BETWEEN :startTime AND :endTime " +
            "ORDER BY f.dataIngestionTime DESC")
    List<FundingRateData> findByVendorAndTimeRange(@Param("vendor") String vendor,
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 删除指定时间之前的数据
     */
    void deleteByDataIngestionTimeBefore(LocalDateTime cutoffTime);

    /**
     * 检查指定合约在指定时间范围内是否已存在数据
     */
    @Query("SELECT COUNT(f) > 0 FROM FundingRateData f " +
            "WHERE f.instId = :instId AND f.dataIngestionTime >= :sinceTime")
    boolean existsByInstIdAndSinceTime(@Param("instId") String instId,
                                       @Param("sinceTime") LocalDateTime sinceTime);
}
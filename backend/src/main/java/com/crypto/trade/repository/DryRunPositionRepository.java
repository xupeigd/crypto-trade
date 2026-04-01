package com.crypto.trade.repository;

import com.crypto.trade.entity.DryRunPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * DryRunPositionRepository
 * 模拟持仓Repository
 *
 * @author page
 * @date 2026-03-16
 */
@Repository
public interface DryRunPositionRepository
        extends JpaRepository<DryRunPosition, Long> {

    /**
     * 根据API Key ID、合约品种、持仓方向查找模拟持仓
     */
    @Query("SELECT d FROM DryRunPosition d WHERE d.apiKeyId = :apiKeyId AND d.instId = :instId AND d.posSide = :posSide")
    Optional<DryRunPosition> findByApiKeyIdAndInstIdAndPosSide(
            @Param("apiKeyId") Long apiKeyId,
            @Param("instId") String instId,
            @Param("posSide") String posSide);

    /**
     * 根据API Key ID、合约品种、持仓方向、状态查找模拟持仓
     */
    @Query("SELECT d FROM DryRunPosition d WHERE d.apiKeyId = :apiKeyId AND d.instId = :instId AND d.posSide = :posSide AND d.status = :status")
    List<DryRunPosition> findByApiKeyIdAndInstIdAndPosSideAndStatus(
            @Param("apiKeyId") Long apiKeyId,
            @Param("instId") String instId,
            @Param("posSide") String posSide,
            @Param("status") String status);

    /**
     * 根据API Key ID查找所有模拟持仓
     */
    @Query("SELECT d FROM DryRunPosition d WHERE d.apiKeyId = :apiKeyId")
    List<DryRunPosition> findByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 根据API Key ID、合约品种、持仓方向删除模拟持仓
     */
    @Modifying
    @Query("DELETE FROM DryRunPosition d WHERE d.apiKeyId = :apiKeyId AND d.instId = :instId AND d.posSide = :posSide")
    void deleteByApiKeyIdAndInstIdAndPosSide(
            @Param("apiKeyId") Long apiKeyId,
            @Param("instId") String instId,
            @Param("posSide") String posSide);

    /**
     * 根据API Key ID删除所有模拟持仓
     */
    @Modifying
    @Query("DELETE FROM DryRunPosition d WHERE d.apiKeyId = :apiKeyId")
    void deleteByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 获取所有持仓中的模拟持仓（不包括委托中和已平仓）
     */
    @Query("SELECT d FROM DryRunPosition d WHERE d.status = 'open'")
    List<DryRunPosition> findAllOpenPositions();

    /**
     * 获取指定API Key的持仓中模拟持仓
     */
    @Query("SELECT d FROM DryRunPosition d WHERE d.apiKeyId = :apiKeyId AND d.status = 'open'")
    List<DryRunPosition> findOpenPositionsByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 获取所有委托中的模拟订单
     */
    @Query("SELECT d FROM DryRunPosition d WHERE d.status = 'pending'")
    List<DryRunPosition> findAllPendingPositions();

    /**
     * 获取指定API Key的委托中模拟订单
     */
    @Query("SELECT d FROM DryRunPosition d WHERE d.apiKeyId = :apiKeyId AND d.status = 'pending'")
    List<DryRunPosition> findPendingPositionsByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 获取指定API Key的持仓中和委托中的模拟数据
     */
    @Query("SELECT d FROM DryRunPosition d WHERE d.apiKeyId = :apiKeyId AND d.status IN ('pending', 'open')")
    List<DryRunPosition> findActivePositionsByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 获取指定API Key的已关闭模拟持仓（用于计算已实现盈亏）
     */
    @Query("SELECT d FROM DryRunPosition d WHERE d.apiKeyId = :apiKeyId AND d.status = 'closed'")
    List<DryRunPosition> findClosedPositionsByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 计算指定API Key的已实现盈亏总和
     */
    @Query("SELECT COALESCE(SUM(d.realizedPnl), 0) FROM DryRunPosition d WHERE d.apiKeyId = :apiKeyId AND d.status = 'closed'")
    BigDecimal sumRealizedPnlByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 统计所有活跃（持仓中或委托中）的模拟持仓数量
     */
    @Query("SELECT COUNT(d) FROM DryRunPosition d WHERE d.status IN ('open', 'pending')")
    long countActivePositions();
}

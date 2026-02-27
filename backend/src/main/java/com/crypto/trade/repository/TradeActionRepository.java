package com.crypto.trade.repository;

import com.crypto.trade.entity.TradeAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * TradeActionRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface TradeActionRepository
        extends JpaRepository<TradeAction, Long> {

    /**
     * 根据调用记录ID查询动作列表
     */
    List<TradeAction> findByRecordId(Long recordId);

    /**
     * 根据API Key ID查询动作列表
     */
    List<TradeAction> findByApiKeyId(Long apiKeyId);

    /**
     * 根据recordId和执行来源查询动作列表
     */
    List<TradeAction> findByRecordIdAndExecutionSource(Long recordId, String executionSource);

    /**
     * 查询某个动作的所有重放记录
     */
    List<TradeAction> findByParentActionIdOrderByCreateTimeDesc(Long parentActionId);

    /**
     * 根据recordId、actionType和instId批量更新首次执行动作的执行结果
     * 优化版本：使用子查询定位最小ID，提高性能
     */
    @Modifying
    @Query("UPDATE TradeAction t SET " +
            "t.status = :status, " +
            "t.executedTime = :executedTime, " +
            "t.executionTimeMs = :executionTimeMs, " +
            "t.orderId = :orderId, " +
            "t.executedPrice = :executedPrice, " +
            "t.executedSize = :executedSize, " +
            "t.errorMessage = :errorMessage " +
            "WHERE t.id = (" +
            "    SELECT MIN(t2.id) FROM TradeAction t2 " +
            "    WHERE t2.recordId = :recordId " +
            "    AND t2.actionType = :actionType " +
            "    AND t2.instId = :instId " +
            "    AND t2.executionSource = 'INITIAL'" +
            ")")
    void updateInitialExecutionResult(
            @Param("recordId") Long recordId,
            @Param("actionType") String actionType,
            @Param("instId") String instId,
            @Param("status") String status,
            @Param("executedTime") LocalDateTime executedTime,
            @Param("executionTimeMs") Long executionTimeMs,
            @Param("orderId") String orderId,
            @Param("executedPrice") BigDecimal executedPrice,
            @Param("executedSize") BigDecimal executedSize,
            @Param("errorMessage") String errorMessage
    );
}

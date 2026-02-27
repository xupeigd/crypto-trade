package com.crypto.trade.repository;

import com.crypto.trade.entity.OrderExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OrderExecutionRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface OrderExecutionRepository extends JpaRepository<OrderExecution, Long> {

    /**
     * 根据订单ID查找执行记录
     */
    List<OrderExecution> findByOrderId(String orderId);

    /**
     * 根据订单ID查找执行记录，按时间倒序
     */
    List<OrderExecution> findByOrderIdOrderByExecTsDesc(String orderId);

    /**
     * 根据交易ID查找执行记录
     */
    List<OrderExecution> findByTradeId(String tradeId);

    /**
     * 查找指定时间范围内的执行记录
     */
    @Query("SELECT e FROM OrderExecution e WHERE e.execTs BETWEEN :startTime AND :endTime " +
            "ORDER BY e.execTs DESC")
    List<OrderExecution> findByExecTsBetween(@Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    /**
     * 统计订单的执行记录数量
     */
    @Query("SELECT COUNT(e) FROM OrderExecution e WHERE e.orderId = :orderId")
    Long countByOrderId(@Param("orderId") String orderId);

    /**
     * 删除指定时间之前的执行记录
     */
    @Query("DELETE FROM OrderExecution e WHERE e.createdTime < :before")
    int deleteOldExecutions(@Param("before") LocalDateTime before);

    /**
     * 根据执行类型查找执行记录
     */
    List<OrderExecution> findByExecTypeOrderByExecTsDesc(String execType);
}
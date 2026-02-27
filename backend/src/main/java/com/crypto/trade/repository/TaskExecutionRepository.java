package com.crypto.trade.repository;

import com.crypto.trade.entity.TaskExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TaskExecutionRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {

    List<TaskExecution> findByTaskId(Long taskId);

    List<TaskExecution> findByExecutionStatus(String executionStatus);

    List<TaskExecution> findByTriggerType(String triggerType);

    @Query("SELECT e FROM TaskExecution e ORDER BY e.triggerTime DESC")
    List<TaskExecution> findAllOrderByTriggerTimeDesc();

    @Query("SELECT e FROM TaskExecution e WHERE e.taskId = :taskId AND e.parentTaskName = :parentTaskName " +
            "AND e.executionStatus IN ('pending', 'running')")
    List<TaskExecution> findPendingOrRunningExecutions(@Param("taskId") Long taskId,
                                                       @Param("parentTaskName") String parentTaskName);

    @Query("SELECT e FROM TaskExecution e WHERE e.taskId = :taskId " +
            "ORDER BY e.triggerTime DESC LIMIT :limit")
    List<TaskExecution> findRecentExecutions(@Param("taskId") Long taskId,
                                             @Param("limit") int limit);

    @Query("SELECT COUNT(e) FROM TaskExecution e WHERE e.taskId = :taskId AND e.executionStatus = 'success' " +
            "AND e.createdTime >= :startTime")
    Long countSuccessfulExecutionsSince(@Param("taskId") Long taskId,
                                        @Param("startTime") LocalDateTime startTime);

    @Query("SELECT COUNT(e) FROM TaskExecution e WHERE e.executionStatus = :status " +
            "AND e.createdTime BETWEEN :startTime AND :endTime")
    Long countByExecutionStatusAndCreatedTimeBetween(@Param("status") String status,
                                                     @Param("startTime") LocalDateTime startTime,
                                                     @Param("endTime") LocalDateTime endTime);

    @Query("SELECT e FROM TaskExecution e WHERE e.createdTime >= :startTime " +
            "ORDER BY e.triggerTime DESC")
    List<TaskExecution> findRecentExecutionsSince(@Param("startTime") LocalDateTime startTime);

    @Query("SELECT COUNT(e) FROM TaskExecution e WHERE e.createdTime BETWEEN :startTime AND :endTime")
    Long countByCreatedTimeBetween(@Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 查找待执行或运行中的执行记录，排除指定的执行ID
     */
    @Query("SELECT e FROM TaskExecution e WHERE e.taskId = :taskId AND e.parentTaskName = :parentTaskName " +
            "AND e.executionStatus IN ('pending', 'running') AND e.executionId != :excludeExecutionId")
    List<TaskExecution> findPendingOrRunningExecutionsExcluding(@Param("taskId") Long taskId,
                                                                @Param("parentTaskName") String parentTaskName,
                                                                @Param("excludeExecutionId") Long excludeExecutionId);

    /**
     * 查找指定时间窗口内的待执行或运行中的执行记录，排除指定的执行ID
     */
    @Query("SELECT e FROM TaskExecution e WHERE e.taskId = :taskId AND e.parentTaskName = :parentTaskName " +
            "AND e.executionStatus IN ('pending', 'running') AND e.executionId != :excludeExecutionId " +
            "AND e.createdTime >= :cutoffTime")
    List<TaskExecution> findPendingOrRunningExecutionsInTimeWindow(@Param("taskId") Long taskId,
                                                                   @Param("parentTaskName") String parentTaskName,
                                                                   @Param("excludeExecutionId") Long excludeExecutionId,
                                                                   @Param("cutoffTime") LocalDateTime cutoffTime);
}
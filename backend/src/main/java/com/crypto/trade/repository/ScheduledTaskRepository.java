package com.crypto.trade.repository;

import com.crypto.trade.entity.ScheduledTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ScheduledTaskRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Repository
public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, Long> {

    ScheduledTask findByTaskName(String taskName);

    List<ScheduledTask> findByTaskType(String taskType);

    List<ScheduledTask> findByStatus(String status);

    List<ScheduledTask> findByParentTaskId(Long parentTaskId);

    @Query("SELECT t FROM ScheduledTask t WHERE t.cronExpression IS NOT NULL AND t.status = 'active'")
    List<ScheduledTask> findActiveCronTasks();

    @Query("SELECT COUNT(t) FROM ScheduledTask t WHERE t.cronExpression IS NOT NULL AND t.status = 'active'")
    long countActiveCronTasks();

    @Query("SELECT t FROM ScheduledTask t WHERE t.parentTaskId = :parentTaskId AND t.status = 'active'")
    List<ScheduledTask> findActiveChildTasks(@Param("parentTaskId") Long parentTaskId);
}
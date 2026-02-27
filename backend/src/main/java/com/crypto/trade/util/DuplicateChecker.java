package com.crypto.trade.util;

import com.crypto.trade.entity.TaskExecution;
import com.crypto.trade.repository.TaskExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DuplicateChecker
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Slf4j
@Component
public class DuplicateChecker {

    @Autowired
    TaskExecutionRepository taskExecutionRepository;

    /**
     * 检查是否存在重复的任务执行
     *
     * @param taskId         任务ID
     * @param parentTaskName 父任务名称（cron触发时为"st"）
     * @return 如果存在重复返回true，否则返回false
     */
    public boolean hasDuplicateExecution(Long taskId, String parentTaskName) {
        List<TaskExecution> pendingOrRunning = taskExecutionRepository
                .findPendingOrRunningExecutions(taskId, parentTaskName);
        return !pendingOrRunning.isEmpty();
    }

    /**
     * 检查是否存在重复的cron任务执行
     *
     * @param taskId 任务ID
     * @return 如果存在重复返回true，否则返回false
     */
    public boolean hasDuplicateCronExecution(Long taskId) {
        return hasDuplicateExecution(taskId, "st");
    }

    /**
     * 检查是否存在重复的父任务触发执行
     *
     * @param taskId         任务ID
     * @param parentTaskName 父任务名称
     * @return 如果存在重复返回true，否则返回false
     */
    public boolean hasDuplicateParentExecution(Long taskId, String parentTaskName) {
        return hasDuplicateExecution(taskId, parentTaskName);
    }

    /**
     * 获取重复执行的数量
     *
     * @param taskId         任务ID
     * @param parentTaskName 父任务名称
     * @return 重复执行的数量
     */
    public int getDuplicateCount(Long taskId, String parentTaskName) {
        List<TaskExecution> pendingOrRunning = taskExecutionRepository
                .findPendingOrRunningExecutions(taskId, parentTaskName);
        return pendingOrRunning.size();
    }

    /**
     * 检查是否存在重复的任务执行（排除指定的执行ID）
     *
     * @param taskId             任务ID
     * @param parentTaskName     父任务名称
     * @param excludeExecutionId 要排除的执行ID
     * @return 如果存在重复返回true，否则返回false
     */
    public boolean hasDuplicateExecution(Long taskId, String parentTaskName, Long excludeExecutionId) {
        List<TaskExecution> pendingOrRunning = taskExecutionRepository
                .findPendingOrRunningExecutionsExcluding(taskId, parentTaskName, excludeExecutionId);
        return !pendingOrRunning.isEmpty();
    }

    /**
     * 检查指定时间窗口内是否存在重复执行
     *
     * @param taskId             任务ID
     * @param parentTaskName     父任务名称
     * @param excludeExecutionId 要排除的执行ID
     * @param timeWindowMinutes  时间窗口（分钟）
     * @return 如果存在重复返回true，否则返回false
     */
    public boolean hasDuplicateExecutionInTimeWindow(Long taskId, String parentTaskName,
                                                     Long excludeExecutionId, int timeWindowMinutes) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(timeWindowMinutes);
        List<TaskExecution> pendingOrRunning = taskExecutionRepository
                .findPendingOrRunningExecutionsInTimeWindow(taskId, parentTaskName, excludeExecutionId, cutoffTime);
        return !pendingOrRunning.isEmpty();
    }

    /**
     * 根据触发类型获取合理的时间窗口（已适配秒级调度精度）
     *
     * @param triggerType 触发类型
     * @return 时间窗口（分钟）
     */
    public int getTimeWindowByTriggerType(String triggerType) {
        switch (triggerType) {
            case "cron":
                return 1; // cron任务1分钟时间窗口（适配秒级调度，原5分钟太长）
            case "parent":
                return 1; // 父任务触发1分钟时间窗口（原2分钟）
            case "manual":
                return 1; // 手动触发1分钟时间窗口（保持不变）
            default:
                return 1; // 默认1分钟时间窗口
        }
    }

}
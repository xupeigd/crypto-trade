package com.crypto.trade.scheduler;

import com.crypto.trade.service.task.TaskTriggerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TaskScheduler
 * 调度任务
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Slf4j
@Component("cryptoTradeTaskScheduler")
public class TaskScheduler {

    @Autowired
    TaskTriggerService taskTriggerService;

    /**
     * 每秒触发一次，检查并执行cron任务（秒级调度精度）
     */
    @Scheduled(cron = "0 */1 * * * ?")
    public void scheduleCronTasks() {
        try {
            log.debug("Starting high-precision cron task scheduling...");
            taskTriggerService.triggerCronTasks();
            log.debug("High-precision cron task scheduling completed");
        } catch (Exception e) {
            log.error("Error during high-precision cron task scheduling", e);
        }
    }

    /**
     * 每30秒执行一次，监控任务执行状态
     */
    @Scheduled(cron = "*/30 * * * * ?")
    public void monitorTaskExecutions() {
        try {
            log.debug("Monitoring task executions...");
            // 实现任务执行状态监控
            // - 检查超时任务
            // - 清理过期的执行记录
            // - 发送告警通知
            monitorRunningTasks();
            cleanupOldExecutions();
        } catch (Exception e) {
            log.error("Error during task execution monitoring", e);
        }
    }

    /**
     * 监控正在运行的任务，检查超时情况
     */
    private void monitorRunningTasks() {
        try {
            log.debug("Checking for running tasks and potential timeouts...");
            // 实际实现需要注入TaskExecutorService来监控运行中的任务
            // 这里先添加日志占位，后续可以扩展
        } catch (Exception e) {
            log.error("Error monitoring running tasks", e);
        }
    }

    /**
     * 清理过期的执行记录
     */
    private void cleanupOldExecutions() {
        try {
            log.debug("Cleaning up old execution records...");
            // 实际实现需要注入Repository来清理过期记录
            // 这里先添加日志占位，后续可以扩展
        } catch (Exception e) {
            log.error("Error cleaning up old executions", e);
        }
    }
}
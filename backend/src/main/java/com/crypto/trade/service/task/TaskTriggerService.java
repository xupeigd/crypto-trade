package com.crypto.trade.service.task;

import com.crypto.trade.entity.ScheduledTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TaskTriggerService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Slf4j
@Service
public class TaskTriggerService {

//    private static final Logger logger = LoggerFactory.getLogger(TaskTriggerService.class);

    private final TaskManagerService taskManagerService;
    private final TaskExecutorService taskExecutorService;

    @Autowired
    public TaskTriggerService(TaskManagerService taskManagerService,
                              @Lazy TaskExecutorService taskExecutorService) {
        this.taskManagerService = taskManagerService;
        this.taskExecutorService = taskExecutorService;
    }

    /**
     * 触发cron任务
     */
    public void triggerCronTasks() {
        log.debug("开始检查cron任务触发 - 时间: {}", LocalDateTime.now());

        List<ScheduledTask> cronTasks = taskManagerService.getActiveCronTasks();
        log.debug("获取到活跃的cron任务数量: {}", cronTasks.size());

        LocalDateTime now = LocalDateTime.now();
        int triggeredCount = 0;

        for (ScheduledTask task : cronTasks) {
            // 验证cron表达式并检查是否应该当前执行
            if (shouldTriggerNow(task.getCronExpression(), now)) {
                log.debug("触发cron任务 - 任务ID: {}, 任务名称: {}, Cron表达式: {}",
                        task.getTaskId(), task.getTaskName(), task.getCronExpression());
                taskExecutorService.executeTask(task, "cron", "st");
                triggeredCount++;
            }
        }
        log.debug("cron任务检查完成 - 共触发 {} 个任务", triggeredCount);
    }

    /**
     * 检查任务是否应该当前执行
     *
     * @param cronExpression cron表达式
     * @param currentTime    当前时间
     * @return 如果应该执行返回true，否则返回false
     */
    private boolean shouldTriggerNow(String cronExpression, LocalDateTime currentTime) {
        if (null == cronExpression || cronExpression.trim().isEmpty()) {
            return false;
        }

        try {
            CronExpression cron = CronExpression.parse(cronExpression);

            // 获取下一个执行时间
            LocalDateTime nextExecution = cron.next(currentTime);

            if (null == nextExecution) {
                return false;
            }

            // 如果下一个执行时间在当前时间的同一秒内，则认为应该执行
            // 这里使用1秒的容差，因为调度器每秒执行一次（高精度调度）
            long timeDiff = Math.abs(java.time.Duration.between(currentTime, nextExecution).toMillis());
            return timeDiff <= 1000; // 1秒容差，支持秒级调度精度

        } catch (IllegalArgumentException e) {
            // cron表达式无效，记录错误但不中断其他任务
            log.error("无效的cron表达式: {}, 错误信息: {}", cronExpression, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 触发父任务的子任务
     *
     * @param parentTaskId 父任务ID
     */
    public void triggerChildTasks(Long parentTaskId) {
        log.info("开始触发父任务的子任务 - 父任务ID: {}", parentTaskId);

        ScheduledTask parentTask = taskManagerService.getTaskById(parentTaskId)
                .orElseThrow(() -> new IllegalArgumentException("Parent task not found with id: " + parentTaskId));

        List<ScheduledTask> childTasks = taskManagerService.getActiveChildTasks(parentTaskId);
        log.info("找到活跃的子任务数量: {} - 父任务ID: {}", childTasks.size(), parentTaskId);

        for (ScheduledTask childTask : childTasks) {
            log.info("触发子任务 - 子任务ID: {}, 子任务名称: {}, 父任务: {}",
                    childTask.getTaskId(), childTask.getTaskName(), parentTask.getTaskName());
            taskExecutorService.executeTask(childTask, "parent", parentTask.getTaskName());
        }

        log.info("子任务触发完成 - 父任务ID: {}, 共触发 {} 个子任务", parentTaskId, childTasks.size());
    }

    /**
     * 触发指定任务
     *
     * @param taskId 任务ID
     */
    public void triggerTask(Long taskId) {
        log.info("手动触发任务 - 任务ID: {}", taskId);

        ScheduledTask task = taskManagerService.getTaskById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with id: " + taskId));

        if (!"active".equals(task.getStatus())) {
            log.error("任务未激活，无法触发 - 任务ID: {}, 状态: {}", taskId, task.getStatus());
            throw new IllegalArgumentException("Task is not active");
        }

        log.info("手动触发任务成功 - 任务ID: {}, 任务名称: {}", taskId, task.getTaskName());
        taskExecutorService.executeTask(task, "manual", "st");
    }

    /**
     * 触发指定任务（指定父任务）
     *
     * @param taskId         任务ID
     * @param parentTaskName 父任务名称
     */
    public void triggerTaskWithParent(Long taskId, String parentTaskName) {
        log.info("触发任务（指定父任务） - 任务ID: {}, 父任务: {}", taskId, parentTaskName);

        ScheduledTask task = taskManagerService.getTaskById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with id: " + taskId));

        if (!"active".equals(task.getStatus())) {
            log.error("任务未激活，无法触发 - 任务ID: {}, 状态: {}", taskId, task.getStatus());
            throw new IllegalArgumentException("Task is not active");
        }

        log.info("触发任务成功 - 任务ID: {}, 任务名称: {}, 父任务: {}",
                taskId, task.getTaskName(), parentTaskName);
        taskExecutorService.executeTask(task, "parent", parentTaskName);
    }
}
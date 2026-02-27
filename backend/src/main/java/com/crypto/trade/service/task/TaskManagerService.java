package com.crypto.trade.service.task;

import com.crypto.trade.entity.ScheduledTask;
import com.crypto.trade.repository.ScheduledTaskRepository;
import com.crypto.trade.util.CycleDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * TaskManagerService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Slf4j
@Service
public class TaskManagerService {

//    private static final Logger logger = LoggerFactory.getLogger(TaskManagerService.class);

    @Autowired
    private ScheduledTaskRepository scheduledTaskRepository;

    @Autowired
    private CycleDetector cycleDetector;

    public List<ScheduledTask> getAllTasks() {
        return scheduledTaskRepository.findAll();
    }

    public Optional<ScheduledTask> getTaskById(Long taskId) {
        return scheduledTaskRepository.findById(taskId);
    }

    public ScheduledTask getTaskByName(String taskName) {
        return scheduledTaskRepository.findByTaskName(taskName);
    }

    public List<ScheduledTask> getTasksByType(String taskType) {
        return scheduledTaskRepository.findByTaskType(taskType);
    }

    public List<ScheduledTask> getActiveTasks() {
        return scheduledTaskRepository.findByStatus("active");
    }

    public List<ScheduledTask> getActiveCronTasks() {
        return scheduledTaskRepository.findActiveCronTasks();
    }

    public List<ScheduledTask> getChildTasks(Long parentTaskId) {
        return scheduledTaskRepository.findByParentTaskId(parentTaskId);
    }

    public List<ScheduledTask> getActiveChildTasks(Long parentTaskId) {
        return scheduledTaskRepository.findActiveChildTasks(parentTaskId);
    }

    public ScheduledTask createTask(ScheduledTask task) {
        log.info("创建新任务 - 任务名称: {}, 任务类型: {}, Cron表达式: {}, 父任务ID: {}",
                task.getTaskName(), task.getTaskType(), task.getCronExpression(), task.getParentTaskId());

        // 检查任务名称是否已存在
        ScheduledTask existingTask = scheduledTaskRepository.findByTaskName(task.getTaskName());
        if (null != existingTask) {
            log.error("任务名称已存在 - 任务名称: {}", task.getTaskName());
            throw new IllegalArgumentException("Task with name '" + task.getTaskName() + "' already exists");
        }

        // 验证任务类型
        if (!isValidTaskType(task.getTaskType())) {
            log.error("无效的任务类型 - 任务类型: {}", task.getTaskType());
            throw new IllegalArgumentException("Invalid task type: " + task.getTaskType());
        }

        // 验证cron表达式（如果有）
        if (task.getCronExpression() != null && !task.getCronExpression().trim().isEmpty()) {
            if (!isValidCronExpression(task.getCronExpression())) {
                log.error("无效的cron表达式 - Cron表达式: {}", task.getCronExpression());
                throw new IllegalArgumentException("Invalid cron expression: " + task.getCronExpression());
            }
        }

        // 检测循环触发关系（仅检查活跃任务）
        try {
            cycleDetector.validateNoCycle(null, task.getParentTaskId());
            log.debug("循环依赖检测通过 - 父任务ID: {}", task.getParentTaskId());
        } catch (Exception e) {
            log.error("循环依赖检测失败 - 父任务ID: {}, 错误: {}", task.getParentTaskId(), e.getMessage());
            throw e;
        }

        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());

        ScheduledTask savedTask = scheduledTaskRepository.save(task);
        log.info("任务创建成功 - 任务ID: {}, 任务名称: {}", savedTask.getTaskId(), savedTask.getTaskName());
        return savedTask;
    }

    public ScheduledTask updateTask(Long taskId, ScheduledTask updatedTask) {
        log.info("更新任务 - 任务ID: {}, 新任务名称: {}, 新任务类型: {}, 新Cron表达式: {}, 新父任务ID: {}",
                taskId, updatedTask.getTaskName(), updatedTask.getTaskType(),
                updatedTask.getCronExpression(), updatedTask.getParentTaskId());

        return scheduledTaskRepository.findById(taskId)
                .map(existingTask -> {
                    log.debug("找到现有任务 - 任务ID: {}, 当前名称: {}", taskId, existingTask.getTaskName());

                    // 检查任务名称是否被其他任务使用
                    if (!existingTask.getTaskName().equals(updatedTask.getTaskName())) {
                        ScheduledTask taskWithSameName = scheduledTaskRepository.findByTaskName(updatedTask.getTaskName());
                        if (null != taskWithSameName && !taskWithSameName.getTaskId().equals(taskId)) {
                            log.error("任务名称已被其他任务使用 - 任务名称: {}", updatedTask.getTaskName());
                            throw new IllegalArgumentException("Task with name '" + updatedTask.getTaskName() + "' already exists");
                        }
                    }

                    // 验证任务类型
                    if (!isValidTaskType(updatedTask.getTaskType())) {
                        log.error("无效的任务类型 - 任务类型: {}", updatedTask.getTaskType());
                        throw new IllegalArgumentException("Invalid task type: " + updatedTask.getTaskType());
                    }

                    // 验证cron表达式（如果有）
                    if (updatedTask.getCronExpression() != null && !updatedTask.getCronExpression().trim().isEmpty()) {
                        if (!isValidCronExpression(updatedTask.getCronExpression())) {
                            log.error("无效的cron表达式 - Cron表达式: {}", updatedTask.getCronExpression());
                            throw new IllegalArgumentException("Invalid cron expression: " + updatedTask.getCronExpression());
                        }
                    }

                    // 检测循环触发关系（仅检查活跃任务）
                    // 只有当parentTaskId发生变化时才需要检测
                    if (!isSameParentTaskId(existingTask.getParentTaskId(), updatedTask.getParentTaskId())) {
                        try {
                            cycleDetector.validateNoCycle(taskId, updatedTask.getParentTaskId());
                            log.debug("循环依赖检测通过 - 任务ID: {}, 新父任务ID: {}", taskId, updatedTask.getParentTaskId());
                        } catch (Exception e) {
                            log.error("循环依赖检测失败 - 任务ID: {}, 新父任务ID: {}, 错误: {}",
                                    taskId, updatedTask.getParentTaskId(), e.getMessage());
                            throw e;
                        }
                    }

                    // 更新任务信息
                    existingTask.setTaskName(updatedTask.getTaskName());
                    existingTask.setTaskType(updatedTask.getTaskType());
                    existingTask.setCronExpression(updatedTask.getCronExpression());
                    existingTask.setTimeoutSeconds(updatedTask.getTimeoutSeconds());
                    existingTask.setStatus(updatedTask.getStatus());
                    existingTask.setParentTaskId(updatedTask.getParentTaskId());
                    existingTask.setParameters(updatedTask.getParameters());
                    existingTask.setDescription(updatedTask.getDescription());
                    existingTask.setUpdatedTime(LocalDateTime.now());

                    ScheduledTask savedTask = scheduledTaskRepository.save(existingTask);
                    log.info("任务更新成功 - 任务ID: {}, 任务名称: {}", savedTask.getTaskId(), savedTask.getTaskName());
                    return savedTask;
                })
                .orElseThrow(() -> {
                    log.error("未找到要更新的任务 - 任务ID: {}", taskId);
                    return new IllegalArgumentException("Task not found with id: " + taskId);
                });
    }

    public void deleteTask(Long taskId) {
        log.info("删除任务 - 任务ID: {}", taskId);

        if (!scheduledTaskRepository.existsById(taskId)) {
            log.error("未找到要删除的任务 - 任务ID: {}", taskId);
            throw new IllegalArgumentException("Task not found with id: " + taskId);
        }

        scheduledTaskRepository.deleteById(taskId);
        log.info("任务删除成功 - 任务ID: {}", taskId);
    }

    public ScheduledTask updateTaskStatus(Long taskId, String status) {
        log.info("更新任务状态 - 任务ID: {}, 新状态: {}", taskId, status);

        return scheduledTaskRepository.findById(taskId)
                .map(task -> {
                    if (!isValidStatus(status)) {
                        log.error("无效的任务状态 - 状态: {}", status);
                        throw new IllegalArgumentException("Invalid status: " + status);
                    }
                    String oldStatus = task.getStatus();
                    task.setStatus(status);
                    task.setUpdatedTime(LocalDateTime.now());

                    ScheduledTask savedTask = scheduledTaskRepository.save(task);
                    log.info("任务状态更新成功 - 任务ID: {}, 任务名称: {}, 状态: {} -> {}",
                            taskId, savedTask.getTaskName(), oldStatus, status);
                    return savedTask;
                })
                .orElseThrow(() -> {
                    log.error("未找到要更新状态的任务 - 任务ID: {}", taskId);
                    return new IllegalArgumentException("Task not found with id: " + taskId);
                });
    }

    private boolean isValidTaskType(String taskType) {
        return "data_fetch".equals(taskType) || "data_calculation".equals(taskType);
    }

    private boolean isValidStatus(String status) {
        return "active".equals(status) || "inactive".equals(status);
    }

    private boolean isValidCronExpression(String cronExpression) {
        // 使用Spring的CronExpression进行验证
        if (null == cronExpression || cronExpression.trim().isEmpty()) {
            return false;
        }

        try {
            CronExpression.parse(cronExpression);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 比较两个parentTaskId是否相同
     * 处理null值的情况
     */
    private boolean isSameParentTaskId(Long oldParentTaskId, Long newParentTaskId) {
        if (null == oldParentTaskId && null == newParentTaskId) {
            return true;
        }
        if (null == oldParentTaskId || null == newParentTaskId) {
            return false;
        }
        return oldParentTaskId.equals(newParentTaskId);
    }
}
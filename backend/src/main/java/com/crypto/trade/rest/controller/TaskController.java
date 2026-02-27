package com.crypto.trade.rest.controller;

import com.crypto.trade.dto.task.ScheduledTaskResponse;
import com.crypto.trade.dto.task.TaskExecutionResponse;
import com.crypto.trade.entity.ScheduledTask;
import com.crypto.trade.entity.TaskExecution;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.model.request.CreateTaskReq;
import com.crypto.trade.model.request.UpdateTaskReq;
import com.crypto.trade.model.request.UpdateTaskStatusReq;
import com.crypto.trade.service.task.TaskExecutorService;
import com.crypto.trade.service.task.TaskManagerService;
import com.crypto.trade.service.task.TaskTriggerService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * TaskController
 * REST控制器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskManagerService taskManagerService;
    private final TaskExecutorService taskExecutorService;
    private final TaskTriggerService taskTriggerService;

    @Autowired
    public TaskController(TaskManagerService taskManagerService,
                          TaskExecutorService taskExecutorService,
                          TaskTriggerService taskTriggerService) {
        this.taskManagerService = taskManagerService;
        this.taskExecutorService = taskExecutorService;
        this.taskTriggerService = taskTriggerService;
    }

    /**
     * 获取所有任务
     *
     * @return ApiResponse<List < ScheduledTaskResponse>> 任务列表的统一响应格式
     */
    @GetMapping
    public ApiResponse<List<ScheduledTaskResponse>> getAllTasks() {
        try {
            List<ScheduledTask> tasks = taskManagerService.getAllTasks();
            List<ScheduledTaskResponse> taskResponses = tasks.stream()
                    .map(this::convertToScheduledTaskResponse)
                    .collect(Collectors.toList());
            log.debug("获取所有任务成功，数量: {}", taskResponses.size());
            return ApiResponse.ok(taskResponses);
        } catch (Exception e) {
            log.error("获取所有任务失败", e);
            return ApiResponse.fail("获取任务列表失败: " + e.getMessage());
        }
    }

    /**
     * 按ID查询任务
     *
     * @param id 任务ID
     * @return ApiResponse<ScheduledTaskResponse> 任务详情的统一响应格式
     */
    @GetMapping("/{id}")
    public ApiResponse<ScheduledTaskResponse> getTaskById(@PathVariable Long id) {
        try {
            ScheduledTask task = taskManagerService.getTaskById(id)
                    .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
            ScheduledTaskResponse response = convertToScheduledTaskResponse(task);
            log.debug("按ID查询任务成功，任务ID: {}", id);
            return ApiResponse.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("按ID查询任务失败，任务不存在: {}", id);
            return ApiResponse.fail("任务不存在");
        } catch (Exception e) {
            log.error("按ID查询任务失败，任务ID: {}", id, e);
            return ApiResponse.fail("查询任务失败: " + e.getMessage());
        }
    }

    /**
     * 按名称查询任务
     *
     * @param name 任务名称
     * @return ApiResponse<ScheduledTaskResponse> 任务详情的统一响应格式
     */
    @GetMapping("/name/{name}")
    public ApiResponse<ScheduledTaskResponse> getTaskByName(@PathVariable String name) {
        try {
            ScheduledTask task = taskManagerService.getTaskByName(name);
            ScheduledTaskResponse response = convertToScheduledTaskResponse(task);
            log.debug("按名称查询任务成功，任务名称: {}", name);
            return ApiResponse.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("按名称查询任务失败，任务不存在: {}", name);
            return ApiResponse.fail("任务不存在");
        } catch (Exception e) {
            log.error("按名称查询任务失败，任务名称: {}", name, e);
            return ApiResponse.fail("查询任务失败: " + e.getMessage());
        }
    }

    /**
     * 按类型查询任务
     *
     * @param type 任务类型
     * @return ApiResponse<List < ScheduledTaskResponse>> 任务列表的统一响应格式
     */
    @GetMapping("/type/{type}")
    public ApiResponse<List<ScheduledTaskResponse>> getTasksByType(@PathVariable String type) {
        try {
            List<ScheduledTask> tasks = taskManagerService.getTasksByType(type);
            List<ScheduledTaskResponse> taskResponses = tasks.stream()
                    .map(this::convertToScheduledTaskResponse)
                    .collect(Collectors.toList());
            log.debug("按类型查询任务成功，类型: {}, 数量: {}", type, taskResponses.size());
            return ApiResponse.ok(taskResponses);
        } catch (Exception e) {
            log.error("按类型查询任务失败，类型: {}", type, e);
            return ApiResponse.fail("查询任务失败: " + e.getMessage());
        }
    }

    /**
     * 获取活跃任务
     *
     * @return ApiResponse<List < ScheduledTaskResponse>> 活跃任务列表的统一响应格式
     */
    @GetMapping("/active")
    public ApiResponse<List<ScheduledTaskResponse>> getActiveTasks() {
        try {
            List<ScheduledTask> tasks = taskManagerService.getActiveTasks();
            List<ScheduledTaskResponse> taskResponses = tasks.stream()
                    .map(this::convertToScheduledTaskResponse)
                    .collect(Collectors.toList());
            log.debug("获取活跃任务成功，数量: {}", taskResponses.size());
            return ApiResponse.ok(taskResponses);
        } catch (Exception e) {
            log.error("获取活跃任务失败", e);
            return ApiResponse.fail("获取活跃任务失败: " + e.getMessage());
        }
    }

    /**
     * 获取子任务
     *
     * @param parentTaskId 父任务ID
     * @return ApiResponse<List < ScheduledTaskResponse>> 子任务列表的统一响应格式
     */
    @GetMapping("/{parentTaskId}/children")
    public ApiResponse<List<ScheduledTaskResponse>> getChildTasks(@PathVariable Long parentTaskId) {
        try {
            List<ScheduledTask> tasks = taskManagerService.getChildTasks(parentTaskId);
            List<ScheduledTaskResponse> taskResponses = tasks.stream()
                    .map(this::convertToScheduledTaskResponse)
                    .collect(Collectors.toList());
            log.debug("获取子任务成功，父任务ID: {}, 数量: {}", parentTaskId, taskResponses.size());
            return ApiResponse.ok(taskResponses);
        } catch (Exception e) {
            log.error("获取子任务失败，父任务ID: {}", parentTaskId, e);
            return ApiResponse.fail("获取子任务失败: " + e.getMessage());
        }
    }

    /**
     * 创建任务
     *
     * @param request 创建任务请求
     * @return ApiResponse<ScheduledTaskResponse> 创建任务的统一响应格式
     */
    @PostMapping
    public ApiResponse<ScheduledTaskResponse> createTask(@Valid @RequestBody CreateTaskReq request) {
        try {
            // 转换请求为Entity
            ScheduledTask task = convertToScheduledTask(request);
            ScheduledTask createdTask = taskManagerService.createTask(task);
            ScheduledTaskResponse response = convertToScheduledTaskResponse(createdTask);
            log.debug("创建任务成功，任务ID: {}, 任务名称: {}", response.getTaskId(), response.getTaskName());
            return ApiResponse.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("创建任务失败: {}", e.getMessage());
            return ApiResponse.fail("创建任务失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("创建任务失败", e);
            return ApiResponse.fail("创建任务失败: " + e.getMessage());
        }
    }

    /**
     * 更新任务
     *
     * @param id      任务ID
     * @param request 更新任务请求
     * @return ApiResponse<ScheduledTaskResponse> 更新任务的统一响应格式
     */
    @PutMapping("/{id}")
    public ApiResponse<ScheduledTaskResponse> updateTask(@PathVariable Long id,
                                                         @Valid @RequestBody UpdateTaskReq request) {
        try {
            // 转换请求为Entity
            ScheduledTask task = convertToScheduledTask(request);
            ScheduledTask updatedTask = taskManagerService.updateTask(id, task);
            ScheduledTaskResponse response = convertToScheduledTaskResponse(updatedTask);
            log.debug("更新任务成功，任务ID: {}", id);
            return ApiResponse.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("更新任务失败，任务ID: {}", id, e);
            return ApiResponse.fail("更新任务失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("更新任务失败，任务ID: {}", id, e);
            return ApiResponse.fail("更新任务失败: " + e.getMessage());
        }
    }

    /**
     * 删除任务
     *
     * @param id 任务ID
     * @return ApiResponse<Void> 删除结果的统一响应格式
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        try {
            taskManagerService.deleteTask(id);
            log.debug("删除任务成功，任务ID: {}", id);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            log.warn("删除任务失败，任务ID: {}", id, e);
            return ApiResponse.fail("删除任务失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("删除任务失败，任务ID: {}", id, e);
            return ApiResponse.fail("删除任务失败: " + e.getMessage());
        }
    }

    /**
     * 更新任务状态
     *
     * @param id      任务ID
     * @param request 更新状态请求
     * @return ApiResponse<ScheduledTaskResponse> 更新结果的统一响应格式
     */
    @PutMapping("/{id}/status")
    public ApiResponse<ScheduledTaskResponse> updateTaskStatus(@PathVariable Long id,
                                                               @Valid @RequestBody UpdateTaskStatusReq request) {
        try {
            ScheduledTask updatedTask = taskManagerService.updateTaskStatus(id, request.getStatus());
            ScheduledTaskResponse response = convertToScheduledTaskResponse(updatedTask);
            log.debug("更新任务状态成功，任务ID: {}, 新状态: {}", id, request.getStatus());
            return ApiResponse.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("更新任务状态失败，任务ID: {}, 状态: {}", id, request.getStatus(), e);
            return ApiResponse.fail("更新任务状态失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("更新任务状态失败，任务ID: {}, 状态: {}", id, request.getStatus(), e);
            return ApiResponse.fail("更新任务状态失败: " + e.getMessage());
        }
    }

    /**
     * 手动执行任务
     *
     * @param id 任务ID
     * @return ApiResponse<TaskExecutionResponse> 执行结果的统一响应格式
     */
    @PostMapping("/{id}/execute")
    public ApiResponse<TaskExecutionResponse> executeTask(@PathVariable Long id) {
        try {
            TaskExecution execution = taskExecutorService.executeTaskManually(id);
            TaskExecutionResponse response = convertToTaskExecutionResponse(execution);
            log.debug("手动执行任务成功，任务ID: {}, 执行ID: {}", id, response.getExecutionId());
            return ApiResponse.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("手动执行任务失败，任务ID: {}", id, e);
            return ApiResponse.fail("执行任务失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("手动执行任务失败，任务ID: {}", id, e);
            return ApiResponse.fail("执行任务失败: " + e.getMessage());
        }
    }

    /**
     * 触发任务
     *
     * @param id 任务ID
     * @return ApiResponse<Void> 触发结果的统一响应格式
     */
    @PostMapping("/{id}/trigger")
    public ApiResponse<Void> triggerTask(@PathVariable Long id) {
        try {
            taskTriggerService.triggerTask(id);
            log.debug("触发任务成功，任务ID: {}", id);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            log.warn("触发任务失败，任务ID: {}", id, e);
            return ApiResponse.fail("触发任务失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("触发任务失败，任务ID: {}", id, e);
            return ApiResponse.fail("触发任务失败: " + e.getMessage());
        }
    }

    // ==================== 私有转换方法 ====================

    /**
     * 将Entity转换为ScheduledTaskResponse
     */
    private ScheduledTaskResponse convertToScheduledTaskResponse(ScheduledTask task) {
        return ScheduledTaskResponse.builder()
                .taskId(task.getTaskId())
                .taskName(task.getTaskName())
                .taskType(task.getTaskType())
                .cronExpression(task.getCronExpression())
                .timeoutSeconds(task.getTimeoutSeconds())
                .status(task.getStatus())
                .parentTaskId(task.getParentTaskId())
                .parameters(task.getParameters())
                .description(task.getDescription())
                .createdTime(task.getCreatedTime())
                .updatedTime(task.getUpdatedTime())
                .build();
    }

    /**
     * 将CreateTaskRequest转换为ScheduledTask Entity
     */
    private ScheduledTask convertToScheduledTask(CreateTaskReq request) {
        ScheduledTask task = new ScheduledTask();
        task.setTaskName(request.getTaskName());
        task.setTaskType(request.getTaskType());
        task.setCronExpression(request.getCronExpression());
        task.setTimeoutSeconds(request.getTimeoutSeconds());
        task.setStatus(request.getStatus() != null ? request.getStatus() : "active");
        task.setParentTaskId(request.getParentTaskId());
        task.setParameters(request.getParameters());
        task.setDescription(request.getDescription());
        return task;
    }

    /**
     * 将UpdateTaskRequest转换为ScheduledTask Entity
     */
    private ScheduledTask convertToScheduledTask(UpdateTaskReq request) {
        ScheduledTask task = new ScheduledTask();
        task.setTaskName(request.getTaskName());
        task.setTaskType(request.getTaskType());
        task.setCronExpression(request.getCronExpression());
        task.setTimeoutSeconds(request.getTimeoutSeconds());
        task.setStatus(request.getStatus());
        task.setParentTaskId(request.getParentTaskId());
        task.setParameters(request.getParameters());
        task.setDescription(request.getDescription());
        return task;
    }

    /**
     * 将TaskExecution Entity转换为TaskExecutionResponse
     */
    private TaskExecutionResponse convertToTaskExecutionResponse(TaskExecution execution) {
        // 计算执行时长
        Long durationMillis = null;
        if (execution.getActualExecuteTime() != null && execution.getFinishTime() != null) {
            durationMillis = java.time.Duration.between(execution.getActualExecuteTime(), execution.getFinishTime()).toMillis();
        }
        return TaskExecutionResponse.builder()
                .executionId(execution.getExecutionId())
                .taskId(execution.getTaskId())
                .triggerType(execution.getTriggerType())
                .parentTaskName(execution.getParentTaskName())
                .triggerTime(execution.getTriggerTime())
                .actualExecuteTime(execution.getActualExecuteTime())
                .finishTime(execution.getFinishTime())
                .executionStatus(execution.getExecutionStatus())
                .executionResult(execution.getExecutionResult())
                .errorMessage(execution.getErrorMessage())
                .durationMillis(durationMillis)
                .build();
    }
}
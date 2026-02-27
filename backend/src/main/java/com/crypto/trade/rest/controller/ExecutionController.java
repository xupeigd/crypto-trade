package com.crypto.trade.rest.controller;

import com.crypto.trade.model.ExecutionModel;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.service.ExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * ExecutionController
 * REST控制器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@RestController
@RequestMapping("/executions")
public class ExecutionController {

    @Autowired
    ExecutionService executionService;

    /**
     * 获取所有执行记录
     *
     * @return ApiResponse<List < ExecutionModel>> 所有执行记录的统一响应格式
     */
    @GetMapping
    public ApiResponse<List<ExecutionModel>> getAllExecutions() {
        try {
            log.debug("获取所有执行记录");
            List<ExecutionModel> executions = executionService.getAllExecutions();
            log.debug("获取所有执行记录成功 - 记录数量: {}", executions.size());
            return ApiResponse.ok(executions);
        } catch (Exception e) {
            log.error("获取所有执行记录失败", e);
            return ApiResponse.fail("获取所有执行记录失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取执行记录
     *
     * @param id 执行ID
     * @return ApiResponse<ExecutionModel> 执行记录的统一响应格式
     */
    @GetMapping("/{id}")
    public ApiResponse<ExecutionModel> getExecutionById(@PathVariable Long id) {
        try {
            log.debug("根据ID获取执行记录 - ID: {}", id);
            Optional<ExecutionModel> execution = executionService.getExecutionById(id);
            if (execution.isPresent()) {
                log.debug("根据ID获取执行记录成功 - ID: {}", id);
                return ApiResponse.ok(execution.get());
            } else {
                log.warn("未找到ID为 {} 的执行记录", id);
                return ApiResponse.fail("未找到执行记录: " + id);
            }
        } catch (Exception e) {
            log.error("根据ID获取执行记录失败 - ID: {}", id, e);
            return ApiResponse.fail("获取执行记录失败: " + e.getMessage());
        }
    }

    /**
     * 根据任务ID获取执行记录
     *
     * @param taskId 任务ID
     * @return ApiResponse<List < ExecutionModel>> 执行记录列表的统一响应格式
     */
    @GetMapping("/task/{taskId}")
    public ApiResponse<List<ExecutionModel>> getExecutionsByTaskId(@PathVariable Long taskId) {
        try {
            log.debug("根据任务ID获取执行记录 - 任务ID: {}", taskId);
            List<ExecutionModel> executions = executionService.getExecutionsByTaskId(taskId);
            log.debug("根据任务ID获取执行记录成功 - 任务ID: {}, 记录数量: {}", taskId, executions.size());
            return ApiResponse.ok(executions);
        } catch (Exception e) {
            log.error("根据任务ID获取执行记录失败 - 任务ID: {}", taskId, e);
            return ApiResponse.fail("获取执行记录失败: " + e.getMessage());
        }
    }

    /**
     * 根据执行状态获取执行记录
     *
     * @param status 执行状态
     * @return ApiResponse<List < ExecutionModel>> 执行记录列表的统一响应格式
     */
    @GetMapping("/status/{status}")
    public ApiResponse<List<ExecutionModel>> getExecutionsByStatus(@PathVariable String status) {
        try {
            log.debug("根据状态获取执行记录 - 状态: {}", status);
            List<ExecutionModel> executions = executionService.getExecutionsByStatus(status);
            log.debug("根据状态获取执行记录成功 - 状态: {}, 记录数量: {}", status, executions.size());
            return ApiResponse.ok(executions);
        } catch (Exception e) {
            log.error("根据状态获取执行记录失败 - 状态: {}", status, e);
            return ApiResponse.fail("获取执行记录失败: " + e.getMessage());
        }
    }

    /**
     * 根据触发类型获取执行记录
     *
     * @param triggerType 触发类型
     * @return ApiResponse<List < ExecutionModel>> 执行记录列表的统一响应格式
     */
    @GetMapping("/trigger/{triggerType}")
    public ApiResponse<List<ExecutionModel>> getExecutionsByTriggerType(@PathVariable String triggerType) {
        try {
            log.debug("根据触发类型获取执行记录 - 触发类型: {}", triggerType);
            List<ExecutionModel> executions = executionService.getExecutionsByTriggerType(triggerType);
            log.debug("根据触发类型获取执行记录成功 - 触发类型: {}, 记录数量: {}", triggerType, executions.size());
            return ApiResponse.ok(executions);
        } catch (Exception e) {
            log.error("根据触发类型获取执行记录失败 - 触发类型: {}", triggerType, e);
            return ApiResponse.fail("获取执行记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定任务的最近执行记录
     *
     * @param taskId 任务ID
     * @param limit  限制数量（默认10）
     * @return ApiResponse<List < ExecutionModel>> 执行记录列表的统一响应格式
     */
    @GetMapping("/task/{taskId}/recent")
    public ApiResponse<List<ExecutionModel>> getRecentExecutions(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            log.debug("获取最近执行记录 - 任务ID: {}, 限制数量: {}", taskId, limit);
            List<ExecutionModel> executions = executionService.getRecentExecutions(taskId, limit);
            log.debug("获取最近执行记录成功 - 任务ID: {}, 记录数量: {}", taskId, executions.size());
            return ApiResponse.ok(executions);
        } catch (Exception e) {
            log.error("获取最近执行记录失败 - 任务ID: {}, 限制数量: {}", taskId, limit, e);
            return ApiResponse.fail("获取执行记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定时间范围内的执行记录
     *
     * @param hours 时间范围（小时，默认1小时）
     * @return ApiResponse<List < ExecutionModel>> 执行记录列表的统一响应格式
     */
    @GetMapping("/recent")
    public ApiResponse<List<ExecutionModel>> getRecentExecutionsSince(@RequestParam(defaultValue = "1") int hours) {
        try {
            log.debug("获取指定时间范围内的执行记录 - 小时数: {}", hours);
            List<ExecutionModel> executions = executionService.getRecentExecutionsSince(hours);
            log.debug("获取指定时间范围内的执行记录成功 - 小时数: {}, 记录数量: {}", hours, executions.size());
            return ApiResponse.ok(executions);
        } catch (Exception e) {
            log.error("获取指定时间范围内的执行记录失败 - 小时数: {}", hours, e);
            return ApiResponse.fail("获取执行记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定任务的成功执行次数
     *
     * @param taskId 任务ID
     * @param hours  时间范围（小时，默认24小时）
     * @return ApiResponse<Long> 成功执行次数的统一响应格式
     */
    @GetMapping("/task/{taskId}/success-count")
    public ApiResponse<Long> getSuccessfulExecutionCount(@PathVariable Long taskId,
                                                         @RequestParam(defaultValue = "24") int hours) {
        try {
            log.debug("获取成功执行次数 - 任务ID: {}, 小时数: {}", taskId, hours);
            long count = executionService.getSuccessfulExecutionCount(taskId, hours);
            log.debug("获取成功执行次数成功 - 任务ID: {}, 小时数: {}, 成功次数: {}", taskId, hours, count);
            return ApiResponse.ok(count);
        } catch (Exception e) {
            log.error("获取成功执行次数失败 - 任务ID: {}, 小时数: {}", taskId, hours, e);
            return ApiResponse.fail("获取执行次数失败: " + e.getMessage());
        }
    }

    /**
     * 删除执行记录
     *
     * @param id 执行ID
     * @return ApiResponse<String> 删除结果的统一响应格式
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteExecution(@PathVariable Long id) {
        try {
            log.debug("删除执行记录 - ID: {}", id);
            boolean success = executionService.deleteExecution(id);
            if (success) {
                log.debug("删除执行记录成功 - ID: {}", id);
                return ApiResponse.ok("删除执行记录成功");
            } else {
                log.warn("未找到ID为 {} 的执行记录，删除失败", id);
                return ApiResponse.fail("未找到执行记录: " + id);
            }
        } catch (Exception e) {
            log.error("删除执行记录失败 - ID: {}", id, e);
            return ApiResponse.fail("删除执行记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取执行记录统计信息
     *
     * @param taskId 任务ID（可选）
     * @return ApiResponse<ExecutionService.ExecutionStats> 统计信息的统一响应格式
     */
    @GetMapping("/stats")
    public ApiResponse<ExecutionService.ExecutionStats> getExecutionStats(@RequestParam(required = false) Long taskId) {
        try {
            log.debug("获取执行记录统计信息 - 任务ID: {}", taskId);
            ExecutionService.ExecutionStats stats = executionService.getExecutionStats(taskId);
            log.debug("获取执行记录统计信息成功 - 任务ID: {}, 总数: {}", taskId, stats.getTotal());
            return ApiResponse.ok(stats);
        } catch (Exception e) {
            log.error("获取执行记录统计信息失败 - 任务ID: {}", taskId, e);
            return ApiResponse.fail("获取统计信息失败: " + e.getMessage());
        }
    }
}
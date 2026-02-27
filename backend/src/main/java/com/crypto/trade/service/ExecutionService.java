package com.crypto.trade.service;

import com.crypto.trade.entity.TaskExecution;
import com.crypto.trade.model.ExecutionModel;
import com.crypto.trade.repository.TaskExecutionRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ExecutionService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class ExecutionService {

    @Autowired
    TaskExecutionRepository taskExecutionRepository;

    /**
     * 获取所有执行记录
     *
     * @return 执行记录Model列表
     */
    public List<ExecutionModel> getAllExecutions() {
        log.debug("获取所有执行记录");
        List<TaskExecution> executions = taskExecutionRepository.findAllOrderByTriggerTimeDesc();
        List<ExecutionModel> models = ExecutionModel.fromEntities(executions);
        log.debug("获取所有执行记录成功 - 记录数量: {}", models.size());
        return models;
    }

    /**
     * 根据任务ID获取执行记录
     *
     * @param taskId 任务ID
     * @return 执行记录Model列表
     */
    public List<ExecutionModel> getExecutionsByTaskId(Long taskId) {
        log.debug("根据任务ID获取执行记录 - 任务ID: {}", taskId);
        List<TaskExecution> executions = taskExecutionRepository.findByTaskId(taskId);
        List<ExecutionModel> models = ExecutionModel.fromEntities(executions);
        log.debug("根据任务ID获取执行记录成功 - 任务ID: {}, 记录数量: {}", taskId, models.size());
        return models;
    }

    /**
     * 根据执行ID获取执行记录
     *
     * @param executionId 执行ID
     * @return 执行记录Model
     */
    public Optional<ExecutionModel> getExecutionById(Long executionId) {
        log.debug("根据执行ID获取执行记录 - 执行ID: {}", executionId);
        Optional<TaskExecution> execution = taskExecutionRepository.findById(executionId);
        Optional<ExecutionModel> model = execution.map(ExecutionModel::fromEntity);

        if (model.isPresent()) {
            log.debug("根据执行ID获取执行记录成功 - 执行ID: {}", executionId);
        } else {
            log.warn("未找到执行ID为 {} 的执行记录", executionId);
        }

        return model;
    }

    /**
     * 根据执行状态获取执行记录
     *
     * @param status 执行状态
     * @return 执行记录Model列表
     */
    public List<ExecutionModel> getExecutionsByStatus(String status) {
        log.debug("根据状态获取执行记录 - 状态: {}", status);
        List<TaskExecution> executions = taskExecutionRepository.findByExecutionStatus(status);
        List<ExecutionModel> models = ExecutionModel.fromEntities(executions);
        log.debug("根据状态获取执行记录成功 - 状态: {}, 记录数量: {}", status, models.size());
        return models;
    }

    /**
     * 根据触发类型获取执行记录
     *
     * @param triggerType 触发类型
     * @return 执行记录Model列表
     */
    public List<ExecutionModel> getExecutionsByTriggerType(String triggerType) {
        log.debug("根据触发类型获取执行记录 - 触发类型: {}", triggerType);
        List<TaskExecution> executions = taskExecutionRepository.findByTriggerType(triggerType);
        List<ExecutionModel> models = ExecutionModel.fromEntities(executions);
        log.debug("根据触发类型获取执行记录成功 - 触发类型: {}, 记录数量: {}", triggerType, models.size());
        return models;
    }

    /**
     * 获取指定任务的最近执行记录
     *
     * @param taskId 任务ID
     * @param limit  限制数量
     * @return 执行记录Model列表
     */
    public List<ExecutionModel> getRecentExecutions(Long taskId, int limit) {
        log.debug("获取最近执行记录 - 任务ID: {}, 限制数量: {}", taskId, limit);
        List<TaskExecution> executions = taskExecutionRepository.findRecentExecutions(taskId, limit);
        List<ExecutionModel> models = ExecutionModel.fromEntities(executions);
        log.debug("获取最近执行记录成功 - 任务ID: {}, 记录数量: {}", taskId, models.size());
        return models;
    }

    /**
     * 获取指定时间范围内的执行记录
     *
     * @param hours 时间范围（小时）
     * @return 执行记录Model列表
     */
    public List<ExecutionModel> getRecentExecutionsSince(int hours) {
        log.debug("获取指定时间范围内的执行记录 - 小时数: {}", hours);
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<TaskExecution> executions = taskExecutionRepository.findRecentExecutionsSince(since);
        List<ExecutionModel> models = ExecutionModel.fromEntities(executions);
        log.debug("获取指定时间范围内的执行记录成功 - 小时数: {}, 记录数量: {}", hours, models.size());
        return models;
    }

    /**
     * 获取指定任务的成功执行次数
     *
     * @param taskId 任务ID
     * @param hours  时间范围（小时）
     * @return 成功执行次数
     */
    public long getSuccessfulExecutionCount(Long taskId, int hours) {
        log.debug("获取成功执行次数 - 任务ID: {}, 小时数: {}", taskId, hours);
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        long count = taskExecutionRepository.countSuccessfulExecutionsSince(taskId, since);
        log.debug("获取成功执行次数成功 - 任务ID: {}, 小时数: {}, 成功次数: {}", taskId, hours, count);
        return count;
    }

    /**
     * 删除执行记录
     *
     * @param executionId 执行ID
     * @return 是否删除成功
     */
    public boolean deleteExecution(Long executionId) {
        log.debug("删除执行记录 - 执行ID: {}", executionId);
        if (!taskExecutionRepository.existsById(executionId)) {
            log.warn("未找到执行ID为 {} 的执行记录，无法删除", executionId);
            return false;
        }
        try {
            taskExecutionRepository.deleteById(executionId);
            log.info("删除执行记录成功 - 执行ID: {}", executionId);
            return true;
        } catch (Exception e) {
            log.error("删除执行记录失败 - 执行ID: {}", executionId, e);
            return false;
        }
    }

    /**
     * 根据任务ID删除执行记录
     *
     * @param taskId 任务ID
     * @return 删除的记录数量
     */
    public int deleteExecutionsByTaskId(Long taskId) {
        log.debug("根据任务ID删除执行记录 - 任务ID: {}", taskId);
        List<TaskExecution> executions = taskExecutionRepository.findByTaskId(taskId);
        int count = executions.size();
        try {
            taskExecutionRepository.deleteAll(executions);
            log.info("根据任务ID删除执行记录成功 - 任务ID: {}, 删除数量: {}", taskId, count);
            return count;
        } catch (Exception e) {
            log.error("根据任务ID删除执行记录失败 - 任务ID: {}", taskId, e);
            return 0;
        }
    }

    /**
     * 获取执行记录统计信息
     *
     * @param taskId 任务ID（可选）
     * @return 执行统计信息
     */
    public ExecutionStats getExecutionStats(Long taskId) {
        log.debug("获取执行记录统计信息 - 任务ID: {}", taskId);
        List<TaskExecution> executions;
        if (null != taskId) {
            executions = taskExecutionRepository.findByTaskId(taskId);
        } else {
            executions = taskExecutionRepository.findAll();
        }
        ExecutionStats stats = ExecutionStats.builder()
                .total(executions.size())
                .success((int) executions.stream().filter(e -> "success".equalsIgnoreCase(e.getExecutionStatus())).count())
                .failed((int) executions.stream().filter(e -> "failed".equalsIgnoreCase(e.getExecutionStatus())).count())
                .running((int) executions.stream().filter(e -> "running".equalsIgnoreCase(e.getExecutionStatus())).count())
                .pending((int) executions.stream().filter(e -> "pending".equalsIgnoreCase(e.getExecutionStatus())).count())
                .build();
        log.debug("获取执行记录统计信息成功 - 任务ID: {}, 总数: {}, 成功: {}, 失败: {}, 运行中: {}, 等待中: {}",
                taskId, stats.getTotal(), stats.getSuccess(), stats.getFailed(), stats.getRunning(), stats.getPending());
        return stats;
    }

    /**
     * 执行记录统计信息Model
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ExecutionStats {
        int total;
        int success;
        int failed;
        int running;
        int pending;

        /**
         * 获取成功率
         *
         * @return 成功率（百分比）
         */
        public double getSuccessRate() {
            if (total == 0) {
                return 0.0;
            }
            return (double) success / total * 100;
        }

        /**
         * 获取失败率
         *
         * @return 失败率（百分比）
         */
        public double getFailureRate() {
            if (total == 0) {
                return 0.0;
            }
            return (double) failed / total * 100;
        }
    }
}
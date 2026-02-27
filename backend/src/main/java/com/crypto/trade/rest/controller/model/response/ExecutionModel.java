//package com.crypto.trade.rest.controller.model.response;
//
//import com.crypto.trade.entity.TaskExecution;
//import lombok.AllArgsConstructor;
//import lombok.Builder;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.stream.Collectors;
//
/// **
// * 任务执行记录Model
// * 用于替代Entity直接返回前端，避免敏感数据泄露
// */
//@Data
//@Builder
//@AllArgsConstructor
//@NoArgsConstructor
/**
 * ExecutionModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */
//public class ExecutionModel {
//
//    /**
//     * 执行ID
//     */
//    Long executionId;
//
//    /**
//     * 任务ID
//     */
//    Long taskId;
//
//    /**
//     * 触发类型
//     */
//    String triggerType;
//
//    /**
//     * 父任务名称
//     */
//    String parentTaskName;
//
//    /**
//     * 触发时间
//     */
//    LocalDateTime triggerTime;
//
//    /**
//     * 实际执行时间
//     */
//    LocalDateTime actualExecuteTime;
//
//    /**
//     * 完成时间
//     */
//    LocalDateTime finishTime;
//
//    /**
//     * 执行状态
//     */
//    String executionStatus;
//
//    /**
//     * 执行结果
//     */
//    String executionResult;
//
//    /**
//     * 错误信息
//     */
//    String errorMessage;
//
//    /**
//     * 创建时间
//     */
//    LocalDateTime createdTime;
//
//    /**
//     * 从Entity转换为Model
//     *
//     * @param entity TaskExecution实体
//     * @return ExecutionModel
//     */
//    public static ExecutionModel fromEntity(TaskExecution entity) {
//        if (null == entity) {
//            return null;
//        }
//        return ExecutionModel.builder()
//                .executionId(entity.getExecutionId())
//                .taskId(entity.getTaskId())
//                .triggerType(entity.getTriggerType())
//                .parentTaskName(entity.getParentTaskName())
//                .triggerTime(entity.getTriggerTime())
//                .actualExecuteTime(entity.getActualExecuteTime())
//                .finishTime(entity.getFinishTime())
//                .executionStatus(entity.getExecutionStatus())
//                .executionResult(entity.getExecutionResult())
//                .errorMessage(entity.getErrorMessage())
//                .createdTime(entity.getCreatedTime())
//                .build();
//    }
//
/// /    /**
/// /     * 从Entity列表转换为Model列表
/// /     *
/// /     * @param entities TaskExecution实体列表
/// /     * @return ExecutionModel列表
/// /     */
/// /    public static List<ExecutionModel> fromEntities(List<TaskExecution> entities) {
/// /        if (null == entities || entities.isEmpty()) {
/// /            return new ArrayList<>();
/// /        }
/// /        return entities.stream()
/// /                .map(ExecutionModel::fromEntity)
/// /                .collect(Collectors.toList());
/// /    }
//
//    /**
//     * 状态显示文本
//     *
//     * @return 状态的中文描述
//     */
//    public String getStatusText() {
//        if (null == executionStatus) {
//            return "";
//        }
//        return switch (executionStatus.toLowerCase()) {
//            case "pending" -> "等待中";
//            case "running" -> "运行中";
//            case "success" -> "成功";
//            case "failed" -> "失败";
//            case "timeout" -> "超时";
//            case "skipped" -> "跳过";
//            default -> executionStatus;
//        };
//    }
//
//    /**
//     * 触发类型显示文本
//     *
//     * @return 触发类型的中文描述
//     */
//    public String getTriggerTypeText() {
//        if (null == triggerType) {
//            return "";
//        }
//        return switch (triggerType.toLowerCase()) {
//            case "cron" -> "定时触发";
//            case "parent" -> "父任务触发";
//            case "manual" -> "手动触发";
//            default -> triggerType;
//        };
//    }
//
//    /**
//     * 判断是否为成功状态
//     *
//     * @return true如果执行成功
//     */
//    public boolean isSuccess() {
//        return "success".equalsIgnoreCase(executionStatus);
//    }
//
//    /**
//     * 判断是否为失败状态
//     *
//     * @return true如果执行失败
//     */
//    public boolean isFailed() {
//        return "failed".equalsIgnoreCase(executionStatus);
//    }
//
//    /**
//     * 判断是否正在运行
//     *
//     * @return true如果正在运行
//     */
//    public boolean isRunning() {
//        return "running".equalsIgnoreCase(executionStatus);
//    }
//
//    /**
//     * 判断是否等待中
//     *
//     * @return true如果等待执行
//     */
//    public boolean isPending() {
//        return "pending".equalsIgnoreCase(executionStatus);
//    }
//
//    /**
//     * 获取执行持续时间（毫秒）
//     *
//     * @return 持续时间，如果未完成返回null
//     */
//    public Long getDurationMillis() {
//        if (null == actualExecuteTime || null == finishTime) {
//            return null;
//        }
//        return java.time.Duration.between(actualExecuteTime, finishTime).toMillis();
//    }
//
//    /**
//     * 获取格式化的执行持续时间
//     *
//     * @return 格式化的持续时间字符串
//     */
//    public String getFormattedDuration() {
//        Long duration = getDurationMillis();
//        if (null == duration) {
//            return "";
//        }
//        long seconds = duration / 1000;
//        long minutes = seconds / 60;
//        long hours = minutes / 60;
//        if (hours > 0) {
//            return String.format("%d小时%d分钟%d秒", hours, minutes % 60, seconds % 60);
//        } else if (minutes > 0) {
//            return String.format("%d分钟%d秒", minutes, seconds % 60);
//        } else {
//            return String.format("%d秒", seconds);
//        }
//    }
//}
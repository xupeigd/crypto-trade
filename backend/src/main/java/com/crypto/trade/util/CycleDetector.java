package com.crypto.trade.util;

import com.crypto.trade.entity.ScheduledTask;
import com.crypto.trade.repository.ScheduledTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CycleDetector
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class CycleDetector {

    @Autowired
    ScheduledTaskRepository scheduledTaskRepository;

    /**
     * 检测任务之间是否存在循环触发关系
     *
     * @param taskId       当前任务ID
     * @param parentTaskId 父任务ID
     * @return 如果存在循环返回true，否则返回false
     */
    public boolean hasCycle(Long taskId, Long parentTaskId) {
        if (null == parentTaskId) {
            return false; // 没有父任务，不可能形成循环
        }
        // 检查自引用
        if (null != taskId && taskId.equals(parentTaskId)) {
            return true; // 自引用循环
        }
        Set<Long> visited = new HashSet<>();
        if (null != taskId) {
            visited.add(taskId);
        }
        return detectCycleDFS(parentTaskId, visited);
    }

    /**
     * 深度优先搜索检测循环
     *
     * @param currentTaskId 当前遍历的任务ID
     * @param visited       已访问的任务ID集合
     * @return 如果存在循环返回true，否则返回false
     */
    private boolean detectCycleDFS(Long currentTaskId, Set<Long> visited) {
        // 如果已经访问过当前任务，说明存在循环
        if (visited.contains(currentTaskId)) {
            return true;
        }
        // 标记当前任务为已访问
        visited.add(currentTaskId);
        // 获取当前任务的所有活跃子任务
        List<ScheduledTask> childTasks = scheduledTaskRepository.findActiveChildTasks(currentTaskId);
        // 递归检查所有子任务
        for (ScheduledTask childTask : childTasks) {
            if (detectCycleDFS(childTask.getTaskId(), visited)) {
                return true;
            }
        }
        // 回溯：移除当前任务
        visited.remove(currentTaskId);
        return false;
    }

    /**
     * 获取循环路径（用于错误信息）
     *
     * @param taskId       当前任务ID
     * @param parentTaskId 父任务ID
     * @return 循环路径字符串，如果没有循环返回null
     */
    public String getCyclePath(Long taskId, Long parentTaskId) {
        if (null == parentTaskId) {
            return null;
        }
        Set<Long> visited = new HashSet<>();
        if (null != taskId) {
            visited.add(taskId);
        }
        StringBuilder path = new StringBuilder();
        if (null != taskId) {
            path.append(taskId);
        }
        boolean hasCycle = findCyclePathDFS(parentTaskId, visited, path);
        return hasCycle ? path.toString() : null;
    }

    /**
     * 深度优先搜索查找循环路径
     */
    private boolean findCyclePathDFS(Long currentTaskId, Set<Long> visited, StringBuilder path) {
        path.append(" -> ").append(currentTaskId);
        if (visited.contains(currentTaskId)) {
            path.append(" [CYCLE]");
            return true;
        }
        visited.add(currentTaskId);
        List<ScheduledTask> childTasks = scheduledTaskRepository.findActiveChildTasks(currentTaskId);
        for (ScheduledTask childTask : childTasks) {
            if (findCyclePathDFS(childTask.getTaskId(), visited, path)) {
                return true;
            }
        }
        // 回溯
        visited.remove(currentTaskId);
        // 移除当前任务从路径中
        int lastArrowIndex = path.lastIndexOf(" -> ");
        if (lastArrowIndex != -1) {
            path.setLength(lastArrowIndex);
        }
        return false;
    }

    /**
     * 验证任务配置是否安全（无循环）
     *
     * @param taskId       当前任务ID（创建时为null）
     * @param parentTaskId 父任务ID
     * @throws IllegalArgumentException 如果存在循环
     */
    public void validateNoCycle(Long taskId, Long parentTaskId) {
        if (hasCycle(taskId, parentTaskId)) {
            String cyclePath = getCyclePath(taskId, parentTaskId);
            throw new IllegalArgumentException(
                    "任务配置存在循环触发关系。循环路径: " + cyclePath +
                            "。请检查任务的父子关系配置。"
            );
        }
    }
}
package com.crypto.trade.service;

import com.crypto.trade.model.DashboardStatisticsModel;
import com.crypto.trade.repository.ApiKeyRepository;
import com.crypto.trade.repository.DataFetchConfigRepository;
import com.crypto.trade.repository.ScheduledTaskRepository;
import com.crypto.trade.repository.TaskExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DashboardService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class DashboardService {

    @Autowired
    ScheduledTaskRepository scheduledTaskRepository;
    @Autowired
    ApiKeyRepository apiKeyRepository;
    @Autowired
    DataFetchConfigRepository dataFetchConfigRepository;
    @Autowired
    TaskExecutionRepository taskExecutionRepository;

    /**
     * 获取仪表板统计数据
     *
     * @return DashboardStatisticsModel 仪表板统计数据模型
     */
    public DashboardStatisticsModel getDashboardStatistics() {
        log.debug("获取仪表板统计数据");
        try {
            // 活跃任务数量 - 统计活跃的定时任务数量（有cron表达式且状态为active）
            long activeTasks = scheduledTaskRepository.countActiveCronTasks();
            // API Key数量 - 只统计活跃状态的API Key
            long totalApiKeys = apiKeyRepository.countActiveApiKeys();
            // 数据获取配置数量
            long dataFetchConfigs = dataFetchConfigRepository.count();
            // 今日执行统计
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);
            // 今日成功执行数量
            Long todaySuccessExecutions = taskExecutionRepository.countByExecutionStatusAndCreatedTimeBetween(
                    "success", startOfDay, endOfDay);
            // 今日总执行数量
            Long todayTotalExecutions = taskExecutionRepository.countByCreatedTimeBetween(startOfDay, endOfDay);
            // 今日执行成功率（百分比）
            double successRate = 0.0;
            if (null != todayTotalExecutions && todayTotalExecutions > 0) {
                successRate = (double) (null != todaySuccessExecutions ? todaySuccessExecutions : 0) / todayTotalExecutions * 100;
            }
            DashboardStatisticsModel statistics = DashboardStatisticsModel.builder()
                    .activeTasks(activeTasks)
                    .totalApiKeys(totalApiKeys)
                    .dataFetchConfigs(dataFetchConfigs)
                    .todaySuccessExecutions(null != todaySuccessExecutions ? todaySuccessExecutions : 0L)
                    .todayTotalExecutions(null != todayTotalExecutions ? todayTotalExecutions : 0L)
                    .todaySuccessRate(Math.round(successRate * 100.0) / 100.0) // 保留两位小数
                    .build();
            log.debug("仪表板统计数据获取成功 - 活跃任务: {}, API Key: {}, 成功率: {}%",
                    activeTasks, totalApiKeys, statistics.getTodaySuccessRate());
            return statistics;
        } catch (Exception e) {
            log.error("获取仪表板统计数据失败", e);
            // 返回默认统计数据
            return DashboardStatisticsModel.builder()
                    .activeTasks(0L)
                    .totalApiKeys(0L)
                    .dataFetchConfigs(0L)
                    .todaySuccessExecutions(0L)
                    .todayTotalExecutions(0L)
                    .todaySuccessRate(0.0)
                    .build();
        }
    }
}
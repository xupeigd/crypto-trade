package com.crypto.trade.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DashboardStatisticsModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardStatisticsModel {

    /**
     * 活跃任务数量 - 统计活跃的定时任务数量（有cron表达式且状态为active）
     */
    Long activeTasks;

    /**
     * API Key数量 - 只统计活跃状态的API Key
     */
    Long totalApiKeys;

    /**
     * 数据获取配置数量
     */
    Long dataFetchConfigs;

    /**
     * 今日成功执行数量
     */
    Long todaySuccessExecutions;

    /**
     * 今日总执行数量
     */
    Long todayTotalExecutions;

    /**
     * 今日执行成功率（百分比），保留两位小数
     */
    Double todaySuccessRate;

    /**
     * 获取成功率显示文本
     *
     * @return 成功率的字符串表示，如"85.67%"
     */
    public String getSuccessRateText() {
        if (null == todaySuccessRate) {
            return "0.00%";
        }
        return String.format("%.2f%%", todaySuccessRate);
    }

    /**
     * 判断今日是否有执行记录
     *
     * @return true如果今日总执行数量大于0
     */
    public boolean hasTodayExecutions() {
        return null != todayTotalExecutions && todayTotalExecutions > 0;
    }

    /**
     * 判断今日执行成功率是否良好（>=90%）
     *
     * @return true如果成功率>=90%
     */
    public boolean hasGoodSuccessRate() {
        return null != todaySuccessRate && todaySuccessRate >= 90.0;
    }
}
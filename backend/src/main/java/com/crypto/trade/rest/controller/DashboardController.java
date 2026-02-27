package com.crypto.trade.rest.controller;

import com.crypto.trade.model.DashboardStatisticsModel;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.service.DashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DashboardController
 * REST控制器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    DashboardService dashboardService;

    /**
     * 获取仪表板统计数据
     *
     * @return ApiResponse<DashboardStatisticsModel> 仪表板统计数据的统一响应格式
     */
    @GetMapping("/statistics")
    public ApiResponse<DashboardStatisticsModel> getDashboardStatistics() {
        try {
            log.debug("获取仪表板统计数据");
            DashboardStatisticsModel statistics = dashboardService.getDashboardStatistics();
            log.debug("获取仪表板统计数据成功 - 活跃任务: {}, API Key: {}, 成功率: {}%",
                    statistics.getActiveTasks(), statistics.getTotalApiKeys(), statistics.getTodaySuccessRate());
            return ApiResponse.ok(statistics);
        } catch (Exception e) {
            log.error("获取仪表板统计数据失败", e);
            return ApiResponse.fail("获取仪表板统计数据失败: " + e.getMessage());
        }
    }
}
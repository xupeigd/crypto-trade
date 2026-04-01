package com.crypto.trade.rest.controller;

import com.crypto.trade.entity.FreqtradeConfig;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.service.FreqtradeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FreqtradeConfigController
 * Freqtrade配置REST控制器
 *
 * @author page
 * @date 2026-03-22
 */
@Slf4j
@RestController
@RequestMapping("/freqtrade-configs")
public class FreqtradeConfigController {

    @Autowired
    private FreqtradeConfigService freqtradeConfigService;

    /**
     * 获取所有配置
     */
    @GetMapping
    public ApiResponse<List<FreqtradeConfig>> getAllConfigs() {
        try {
            List<FreqtradeConfig> configs = freqtradeConfigService.getAllConfigs();
            return ApiResponse.ok(configs);
        } catch (Exception e) {
            log.error("获取Freqtrade配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有启用的配置
     */
    @GetMapping("/active")
    public ApiResponse<List<FreqtradeConfig>> getActiveConfigs() {
        try {
            List<FreqtradeConfig> configs = freqtradeConfigService.getActiveConfigs();
            return ApiResponse.ok(configs);
        } catch (Exception e) {
            log.error("获取启用的Freqtrade配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取配置
     */
    @GetMapping("/{id}")
    public ApiResponse<FreqtradeConfig> getConfigById(@PathVariable Long id) {
        try {
            FreqtradeConfig config = freqtradeConfigService.getConfigById(id);
            if (config == null) {
                return ApiResponse.fail("配置不存在");
            }
            return ApiResponse.ok(config);
        } catch (Exception e) {
            log.error("获取Freqtrade配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取配置失败: " + e.getMessage());
        }
    }

    /**
     * 创建配置
     */
    @PostMapping
    public ApiResponse<FreqtradeConfig> createConfig(@RequestBody FreqtradeConfig config) {
        try {
            FreqtradeConfig created = freqtradeConfigService.createConfig(config);
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            log.warn("创建Freqtrade配置失败: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("创建Freqtrade配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("创建配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新配置
     */
    @PutMapping("/{id}")
    public ApiResponse<FreqtradeConfig> updateConfig(@PathVariable Long id, @RequestBody FreqtradeConfig config) {
        try {
            FreqtradeConfig updated = freqtradeConfigService.updateConfig(id, config);
            return ApiResponse.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("更新Freqtrade配置失败: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("更新Freqtrade配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("更新配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConfig(@PathVariable Long id) {
        try {
            freqtradeConfigService.deleteConfig(id);
            return ApiResponse.ok(null);
        } catch (Exception e) {
            log.error("删除Freqtrade配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("删除配置失败: " + e.getMessage());
        }
    }

    /**
     * 切换启用状态
     */
    @PutMapping("/{id}/toggle-status")
    public ApiResponse<FreqtradeConfig> toggleStatus(@PathVariable Long id) {
        try {
            FreqtradeConfig config = freqtradeConfigService.toggleStatus(id);
            return ApiResponse.ok(config);
        } catch (Exception e) {
            log.error("切换状态失败: {}", e.getMessage(), e);
            return ApiResponse.fail("切换状态失败: " + e.getMessage());
        }
    }
}

package com.crypto.trade.rest.controller;

import com.crypto.trade.entity.StrategyConfig;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.service.StrategyConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * StrategyConfigController
 * 策略配置REST控制器
 *
 * @author page
 * @date 2026-03-23
 */
@Slf4j
@RestController
@RequestMapping("/strategy-configs")
public class StrategyConfigController {

    @Autowired
    private StrategyConfigService strategyConfigService;

    /**
     * 获取所有配置
     */
    @GetMapping
    public ApiResponse<List<StrategyConfig>> getAllConfigs() {
        try {
            List<StrategyConfig> configs = strategyConfigService.getAllConfigs();
            return ApiResponse.ok(configs);
        } catch (Exception e) {
            log.error("获取策略配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有启用的配置
     */
    @GetMapping("/active")
    public ApiResponse<List<StrategyConfig>> getActiveConfigs() {
        try {
            List<StrategyConfig> configs = strategyConfigService.getActiveConfigs();
            return ApiResponse.ok(configs);
        } catch (Exception e) {
            log.error("获取启用的策略配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取配置
     */
    @GetMapping("/{id}")
    public ApiResponse<StrategyConfig> getConfigById(@PathVariable Long id) {
        try {
            StrategyConfig config = strategyConfigService.getConfigById(id);
            if (config == null) {
                return ApiResponse.fail("配置不存在");
            }
            return ApiResponse.ok(config);
        } catch (Exception e) {
            log.error("获取策略配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取配置失败: " + e.getMessage());
        }
    }

    /**
     * 创建配置
     */
    @PostMapping
    public ApiResponse<StrategyConfig> createConfig(@RequestBody StrategyConfig config) {
        try {
            StrategyConfig created = strategyConfigService.createConfig(config);
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            log.warn("创建策略配置失败: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("创建策略配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("创建配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新配置
     */
    @PutMapping("/{id}")
    public ApiResponse<StrategyConfig> updateConfig(@PathVariable Long id, @RequestBody StrategyConfig config) {
        try {
            StrategyConfig updated = strategyConfigService.updateConfig(id, config);
            return ApiResponse.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("更新策略配置失败: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("更新策略配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("更新配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConfig(@PathVariable Long id) {
        try {
            strategyConfigService.deleteConfig(id);
            return ApiResponse.ok(null);
        } catch (Exception e) {
            log.error("删除策略配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("删除配置失败: " + e.getMessage());
        }
    }

    /**
     * 切换启用状态
     */
    @PutMapping("/{id}/toggle-status")
    public ApiResponse<StrategyConfig> toggleStatus(@PathVariable Long id) {
        try {
            StrategyConfig config = strategyConfigService.toggleStatus(id);
            return ApiResponse.ok(config);
        } catch (Exception e) {
            log.error("切换状态失败: {}", e.getMessage(), e);
            return ApiResponse.fail("切换状态失败: " + e.getMessage());
        }
    }
}

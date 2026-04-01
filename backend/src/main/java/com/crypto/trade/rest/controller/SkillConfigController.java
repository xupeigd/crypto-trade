package com.crypto.trade.rest.controller;

import com.crypto.trade.entity.SkillConfig;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.service.AgentConfigService;
import com.crypto.trade.service.SkillConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * SkillConfigController
 * 技能配置REST控制器
 *
 * @author page
 * @date 2026-03-16
 */
@Slf4j
@RestController
@RequestMapping("/skill-configs")
public class SkillConfigController {

    @Autowired
    SkillConfigService skillConfigService;
    @Autowired
    AgentConfigService agentConfigService;

    /**
     * 获取所有技能配置
     */
    @GetMapping
    public ApiResponse<List<SkillConfig>> getAllSkillConfigs() {
        try {
            log.debug("获取所有技能配置");
            List<SkillConfig> configs = skillConfigService.getAllSkillConfigs();
            log.debug("获取所有技能配置成功，数量: {}", configs.size());
            return ApiResponse.ok(configs);
        } catch (Exception e) {
            log.error("获取所有技能配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取技能配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有活跃的技能配置
     */
    @GetMapping("/active")
    public ApiResponse<List<SkillConfig>> getActiveSkillConfigs() {
        try {
            log.debug("获取所有活跃的技能配置");
            List<SkillConfig> configs = skillConfigService.getActiveSkillConfigs();
            log.debug("获取所有活跃的技能配置成功，数量: {}", configs.size());
            return ApiResponse.ok(configs);
        } catch (Exception e) {
            log.error("获取所有活跃的技能配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取活跃技能配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取技能配置
     */
    @GetMapping("/{id}")
    public ApiResponse<SkillConfig> getSkillConfigById(@PathVariable Long id) {
        try {
            log.debug("根据ID获取技能配置: {}", id);
            return skillConfigService.getSkillConfigById(id)
                    .map(config -> {
                        log.debug("根据ID获取技能配置成功: {}", id);
                        return ApiResponse.ok(config);
                    })
                    .orElseGet(() -> {
                        log.warn("未找到技能配置: {}", id);
                        return ApiResponse.fail("技能配置不存在: " + id);
                    });
        } catch (Exception e) {
            log.error("根据ID获取技能配置失败: {}, error: {}", id, e.getMessage(), e);
            return ApiResponse.fail("获取技能配置失败: " + e.getMessage());
        }
    }

    /**
     * 创建技能配置
     */
    @PostMapping
    public ApiResponse<SkillConfig> createSkillConfig(@RequestBody SkillConfig config) {
        try {
            log.debug("创建技能配置: {}", config.getName());

            // 基础参数校验
            if (config.getName() == null || config.getName().trim().isEmpty()) {
                return ApiResponse.fail("技能名称不能为空");
            }

            if (config.getSkillPrompt() == null || config.getSkillPrompt().trim().isEmpty()) {
                return ApiResponse.fail("技能Prompt不能为空");
            }

            SkillConfig created = skillConfigService.createSkillConfig(config);
            log.info("创建技能配置成功: id={}, name={}", created.getId(), created.getName());
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            log.warn("创建技能配置失败 - 参数错误: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("创建技能配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("创建技能配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新技能配置
     */
    @PutMapping("/{id}")
    public ApiResponse<SkillConfig> updateSkillConfig(@PathVariable Long id, @RequestBody SkillConfig config) {
        try {
            log.debug("更新技能配置: id={}, name={}", id, config.getName());

            // 基础参数校验
            if (config.getName() == null || config.getName().trim().isEmpty()) {
                return ApiResponse.fail("技能名称不能为空");
            }

            if (config.getSkillPrompt() == null || config.getSkillPrompt().trim().isEmpty()) {
                return ApiResponse.fail("技能Prompt不能为空");
            }

            SkillConfig updated = skillConfigService.updateSkillConfig(id, config);
            log.info("更新技能配置成功: id={}, name={}", updated.getId(), updated.getName());
            return ApiResponse.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("更新技能配置失败 - 参数错误: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("更新技能配置失败: id={}, error: {}", id, e.getMessage(), e);
            return ApiResponse.fail("更新技能配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除技能配置
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteSkillConfig(@PathVariable Long id) {
        try {
            log.debug("删除技能配置: {}", id);
            skillConfigService.deleteSkillConfig(id);
            log.info("删除技能配置成功: {}", id);
            return ApiResponse.ok("删除成功");
        } catch (IllegalArgumentException e) {
            log.warn("删除技能配置失败 - 参数错误: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("删除技能配置失败: {}, error: {}", id, e.getMessage(), e);
            return ApiResponse.fail("删除技能配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取可用的工具列表（用于选择技能依赖的工具）
     */
    @GetMapping("/tools")
    public ApiResponse<List<Map<String, String>>> getAvailableTools() {
        try {
            log.debug("获取可用的工具列表");
            List<Map<String, String>> tools = agentConfigService.getAvailableTools();
            log.debug("获取可用的工具列表成功，数量: {}", tools.size());
            return ApiResponse.ok(tools);
        } catch (Exception e) {
            log.error("获取可用的工具列表失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取工具列表失败: " + e.getMessage());
        }
    }
}

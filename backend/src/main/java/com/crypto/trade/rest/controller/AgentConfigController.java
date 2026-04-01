package com.crypto.trade.rest.controller;

import com.crypto.trade.entity.AgentConfig;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.service.AgentConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AgentConfigController
 * 智能体配置REST控制器
 *
 * @author page
 * @date 2026-03-09
 */
@Slf4j
@RestController
@RequestMapping("/agent-configs")
public class AgentConfigController {

    @Autowired
    private AgentConfigService agentConfigService;

    /**
     * 获取所有智能体配置
     */
    @GetMapping
    public ApiResponse<List<AgentConfig>> getAllAgentConfigs() {
        try {
            log.debug("获取所有智能体配置");
            List<AgentConfig> configs = agentConfigService.getAllAgentConfigs();
            log.debug("获取所有智能体配置成功，数量: {}", configs.size());
            return ApiResponse.ok(configs);
        } catch (Exception e) {
            log.error("获取所有智能体配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取智能体配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有活跃的智能体配置
     */
    @GetMapping("/active")
    public ApiResponse<List<AgentConfig>> getActiveAgentConfigs() {
        try {
            log.debug("获取所有活跃的智能体配置");
            List<AgentConfig> configs = agentConfigService.getActiveAgentConfigs();
            log.debug("获取所有活跃的智能体配置成功，数量: {}", configs.size());
            return ApiResponse.ok(configs);
        } catch (Exception e) {
            log.error("获取所有活跃的智能体配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取活跃智能体配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取智能体配置
     */
    @GetMapping("/{id}")
    public ApiResponse<AgentConfig> getAgentConfigById(@PathVariable Long id) {
        try {
            log.debug("根据ID获取智能体配置: {}", id);
            return agentConfigService.getAgentConfigById(id)
                    .map(config -> {
                        log.debug("根据ID获取智能体配置成功: {}", id);
                        return ApiResponse.ok(config);
                    })
                    .orElseGet(() -> {
                        log.warn("未找到智能体配置: {}", id);
                        return ApiResponse.fail("智能体配置不存在: " + id);
                    });
        } catch (Exception e) {
            log.error("根据ID获取智能体配置失败: {}, error: {}", id, e.getMessage(), e);
            return ApiResponse.fail("获取智能体配置失败: " + e.getMessage());
        }
    }

    /**
     * 创建智能体配置
     */
    @PostMapping
    public ApiResponse<AgentConfig> createAgentConfig(@RequestBody AgentConfig config) {
        try {
            log.debug("创建智能体配置: {}", config.getName());

            // 基础参数校验
            if (config.getName() == null || config.getName().trim().isEmpty()) {
                return ApiResponse.fail("智能体名称不能为空");
            }

            AgentConfig created = agentConfigService.createAgentConfig(config);
            log.info("创建智能体配置成功: id={}, name={}", created.getId(), created.getName());
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            log.warn("创建智能体配置失败 - 参数错误: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("创建智能体配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("创建智能体配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新智能体配置
     */
    @PutMapping("/{id}")
    public ApiResponse<AgentConfig> updateAgentConfig(@PathVariable Long id, @RequestBody AgentConfig config) {
        try {
            log.debug("更新智能体配置: id={}, name={}", id, config.getName());

            // 基础参数校验
            if (config.getName() == null || config.getName().trim().isEmpty()) {
                return ApiResponse.fail("智能体名称不能为空");
            }

            AgentConfig updated = agentConfigService.updateAgentConfig(id, config);
            log.info("更新智能体配置成功: id={}, name={}", updated.getId(), updated.getName());
            return ApiResponse.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("更新智能体配置失败 - 参数错误: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("更新智能体配置失败: id={}, error: {}", id, e.getMessage(), e);
            return ApiResponse.fail("更新智能体配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除智能体配置
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteAgentConfig(@PathVariable Long id) {
        try {
            log.debug("删除智能体配置: {}", id);
            agentConfigService.deleteAgentConfig(id);
            log.info("删除智能体配置成功: {}", id);
            return ApiResponse.ok("删除成功");
        } catch (IllegalArgumentException e) {
            log.warn("删除智能体配置失败 - 参数错误: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("删除智能体配置失败: {}, error: {}", id, e.getMessage(), e);
            return ApiResponse.fail("删除智能体配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取可用的工具列表
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

    /**
     * 获取可绑定的模型列表
     */
    @GetMapping("/models")
    public ApiResponse<List<Map<String, Object>>> getAvailableModels() {
        try {
            log.debug("获取可绑定的模型列表");
            List<Map<String, Object>> models = agentConfigService.getAvailableModels();
            log.debug("获取可绑定的模型列表成功，数量: {}", models.size());
            return ApiResponse.ok(models);
        } catch (Exception e) {
            log.error("获取可绑定的模型列表失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取模型列表失败: " + e.getMessage());
        }
    }
}

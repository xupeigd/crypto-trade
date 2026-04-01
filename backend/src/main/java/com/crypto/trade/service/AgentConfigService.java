package com.crypto.trade.service;

import com.crypto.trade.entity.AgentConfig;
import com.crypto.trade.entity.AIModelConfig;
import com.crypto.trade.repository.AgentConfigRepository;
import com.crypto.trade.repository.AIModelConfigRepository;
import com.crypto.trade.service.conversation.ToolExecutionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AgentConfigService
 * 智能体配置服务类
 *
 * @author page
 * @date 2026-03-09
 */
@Slf4j
@Service
@Transactional
public class AgentConfigService {

    @Autowired
    private AgentConfigRepository repository;

    @Autowired
    private AIModelConfigRepository modelConfigRepository;

    @Autowired
    private ToolExecutionManager toolExecutionManager;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取所有智能体配置
     */
    @Transactional(readOnly = true)
    public List<AgentConfig> getAllAgentConfigs() {
        log.debug("获取所有智能体配置");
        return repository.findAll();
    }

    /**
     * 获取所有活跃的智能体配置
     */
    @Transactional(readOnly = true)
    public List<AgentConfig> getActiveAgentConfigs() {
        log.debug("获取所有活跃的智能体配置");
        return repository.findByIsActiveTrue();
    }

    /**
     * 根据ID获取智能体配置
     */
    @Transactional(readOnly = true)
    public Optional<AgentConfig> getAgentConfigById(Long id) {
        log.debug("根据ID获取智能体配置: {}", id);
        return repository.findById(id);
    }

    /**
     * 根据名称获取智能体配置
     */
    @Transactional(readOnly = true)
    public Optional<AgentConfig> getAgentConfigByName(String name) {
        log.debug("根据名称获取智能体配置: {}", name);
        return repository.findByName(name);
    }

    /**
     * 创建智能体配置
     */
    public AgentConfig createAgentConfig(AgentConfig config) {
        log.info("创建智能体配置: {}", config.getName());

        // 检查名称是否已存在
        if (repository.existsByName(config.getName())) {
            throw new IllegalArgumentException("智能体名称已存在: " + config.getName());
        }

        // 验证模型配置是否存在
        if (config.getModelConfigId() != null) {
            modelConfigRepository.findById(config.getModelConfigId())
                    .orElseThrow(() -> new IllegalArgumentException("模型配置不存在: " + config.getModelConfigId()));
        }

        AgentConfig saved = repository.save(config);
        log.info("智能体配置创建成功: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * 更新智能体配置
     */
    public AgentConfig updateAgentConfig(Long id, AgentConfig config) {
        log.info("更新智能体配置: id={}, name={}", id, config.getName());

        AgentConfig existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("智能体配置不存在: " + id));

        // 检查名称是否被其他配置使用
        if (!existing.getName().equals(config.getName()) &&
                repository.existsByNameAndIdNot(config.getName(), id)) {
            throw new IllegalArgumentException("智能体名称已存在: " + config.getName());
        }

        // 验证模型配置是否存在
        if (config.getModelConfigId() != null) {
            modelConfigRepository.findById(config.getModelConfigId())
                    .orElseThrow(() -> new IllegalArgumentException("模型配置不存在: " + config.getModelConfigId()));
        }

        existing.setName(config.getName());
        existing.setSystemPrompt(config.getSystemPrompt());
        existing.setTools(config.getTools());
        existing.setSkills(config.getSkills());
        existing.setModelConfigId(config.getModelConfigId());
        existing.setDescription(config.getDescription());
        existing.setIsActive(config.getIsActive() != null ? config.getIsActive() : existing.getIsActive());

        AgentConfig saved = repository.save(existing);
        log.info("智能体配置更新成功: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * 删除智能体配置
     */
    public void deleteAgentConfig(Long id) {
        log.info("删除智能体配置: {}", id);

        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("智能体配置不存在: " + id);
        }

        repository.deleteById(id);
        log.info("智能体配置删除成功: {}", id);
    }

    /**
     * 获取可用的工具列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, String>> getAvailableTools() {
        log.debug("获取可用的工具列表");
        List<String> registeredTools = toolExecutionManager.getRegisteredTools();

        return registeredTools.stream()
                .map(toolName -> Map.of(
                        "name", toolName,
                        "description", getToolDescription(toolName)
                ))
                .collect(Collectors.toList());
    }

    /**
     * 获取工具描述
     */
    private String getToolDescription(String toolName) {
        // 根据工具名称返回描述
        if ("k_line".equals(toolName)) {
            return "K线数据查询";
        }
        if ("position_info".equals(toolName)) {
            return "仓位查询";
        }
        if ("balance_info".equals(toolName)) {
            return "账户余额查询";
        }
        return "工具: " + toolName;
    }

    /**
     * 获取可绑定的模型列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAvailableModels() {
        log.debug("获取可绑定的模型列表");
        List<AIModelConfig> models = modelConfigRepository.findByIsActiveTrue();

        return models.stream()
                .map(model -> Map.<String, Object>of(
                        "configId", model.getConfigId(),
                        "modelId", model.getModelId(),
                        "displayName", model.getDisplayName(),
                        "provider", model.getProvider(),
                        "modelType", model.getModelType()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 解析工具列表JSON
     */
    public List<String> parseTools(String toolsJson) {
        if (toolsJson == null || toolsJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(toolsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("解析工具列表失败: {}", toolsJson, e);
            return new ArrayList<>();
        }
    }

    /**
     * 转换为工具JSON
     */
    public String toToolsJson(List<String> tools) {
        if (tools == null || tools.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(tools);
        } catch (JsonProcessingException e) {
            log.error("转换工具列表为JSON失败", e);
            return "[]";
        }
    }
}

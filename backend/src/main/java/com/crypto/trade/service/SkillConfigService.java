package com.crypto.trade.service;

import com.crypto.trade.entity.SkillConfig;
import com.crypto.trade.repository.SkillConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SkillConfigService
 * 技能配置服务类
 *
 * @author page
 * @date 2026-03-16
 */
@Slf4j
@Service
@Transactional
public class SkillConfigService {

    @Autowired
    SkillConfigRepository repository;
    @Autowired
    ObjectMapper objectMapper;

    /**
     * 获取所有技能配置
     */
    @Transactional(readOnly = true)
    public List<SkillConfig> getAllSkillConfigs() {
        log.debug("获取所有技能配置");
        return repository.findAll();
    }

    /**
     * 获取所有活跃的技能配置
     */
    @Transactional(readOnly = true)
    public List<SkillConfig> getActiveSkillConfigs() {
        log.debug("获取所有活跃的技能配置");
        return repository.findByIsActiveTrue();
    }

    /**
     * 根据ID获取技能配置
     */
    @Transactional(readOnly = true)
    public Optional<SkillConfig> getSkillConfigById(Long id) {
        log.debug("根据ID获取技能配置: {}", id);
        return repository.findById(id);
    }

    /**
     * 根据ID列表获取技能配置
     */
    @Transactional(readOnly = true)
    public List<SkillConfig> getSkillConfigsByIds(List<Long> ids) {
        log.debug("根据ID列表获取技能配置: {}", ids);
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        return repository.findAllById(ids);
    }

    /**
     * 根据名称获取技能配置
     */
    @Transactional(readOnly = true)
    public Optional<SkillConfig> getSkillConfigByName(String name) {
        log.debug("根据名称获取技能配置: {}", name);
        return repository.findByName(name);
    }

    /**
     * 创建技能配置
     */
    public SkillConfig createSkillConfig(SkillConfig config) {
        log.info("创建技能配置: {}", config.getName());

        // 检查名称是否已存在
        if (repository.existsByName(config.getName())) {
            throw new IllegalArgumentException("技能名称已存在: " + config.getName());
        }

        SkillConfig saved = repository.save(config);
        log.info("技能配置创建成功: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * 更新技能配置
     */
    public SkillConfig updateSkillConfig(Long id, SkillConfig config) {
        log.info("更新技能配置: id={}, name={}", id, config.getName());

        SkillConfig existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("技能配置不存在: " + id));

        // 检查名称是否被其他配置使用
        if (!existing.getName().equals(config.getName()) &&
                repository.existsByNameAndIdNot(config.getName(), id)) {
            throw new IllegalArgumentException("技能名称已存在: " + config.getName());
        }

        existing.setName(config.getName());
        existing.setDescription(config.getDescription());
        existing.setSkillPrompt(config.getSkillPrompt());
        existing.setOutputFormat(config.getOutputFormat());
        existing.setRequiredTools(config.getRequiredTools());
        existing.setExecutionHint(config.getExecutionHint());
        existing.setIsActive(config.getIsActive() != null ? config.getIsActive() : existing.getIsActive());

        SkillConfig saved = repository.save(existing);
        log.info("技能配置更新成功: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * 删除技能配置
     */
    public void deleteSkillConfig(Long id) {
        log.info("删除技能配置: {}", id);

        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("技能配置不存在: " + id);
        }

        repository.deleteById(id);
        log.info("技能配置删除成功: {}", id);
    }

    /**
     * 解析技能ID列表JSON
     */
    public List<Long> parseSkillIds(String skillsJson) {
        if (skillsJson == null || skillsJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(skillsJson, new TypeReference<List<Long>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("解析技能ID列表失败: {}", skillsJson, e);
            return new ArrayList<>();
        }
    }

    /**
     * 转换为技能ID JSON
     */
    public String toSkillsJson(List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(skillIds);
        } catch (JsonProcessingException e) {
            log.error("转换技能ID列表为JSON失败", e);
            return "[]";
        }
    }

    /**
     * 解析工具列表JSON
     */
    public List<String> parseTools(String toolsJson) {
        if (toolsJson == null || toolsJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(toolsJson, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("解析工具列表失败: {}", toolsJson, e);
            return new ArrayList<>();
        }
    }

    /**
     * 解析技能的requiredTools JSON
     */
    public List<String> parseRequiredTools(String requiredToolsJson) {
        return parseTools(requiredToolsJson);
    }

    /**
     * 获取技能的完整Prompt（包含输出格式和执行流程）
     */
    public String buildFullPrompt(SkillConfig skill) {
        StringBuilder sb = new StringBuilder();

        // 添加技能Prompt
        if (skill.getSkillPrompt() != null && !skill.getSkillPrompt().isEmpty()) {
            sb.append(skill.getSkillPrompt());
        }

        // 添加执行流程提示
        if (skill.getExecutionHint() != null && !skill.getExecutionHint().isEmpty()) {
            sb.append("\n\n## 执行流程\n");
            sb.append(skill.getExecutionHint());
        }

        // 添加输出格式要求
        if (skill.getOutputFormat() != null && !skill.getOutputFormat().isEmpty()) {
            sb.append("\n\n## 输出格式要求\n");
            sb.append("请按照以下格式输出结果:\n");
            sb.append(skill.getOutputFormat());
        }

        return sb.toString();
    }

    /**
     * 获取技能所需的所有工具（合并多个技能的工具）
     */
    public List<String> getRequiredTools(List<SkillConfig> skills) {
        return skills.stream()
                .filter(skill -> skill.getRequiredTools() != null)
                .flatMap(skill -> parseTools(skill.getRequiredTools()).stream())
                .distinct()
                .collect(Collectors.toList());
    }

}
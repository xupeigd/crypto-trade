package com.crypto.trade.service;

import com.crypto.trade.entity.AIInfoModel;
import com.crypto.trade.entity.AIModelConfig;
import com.crypto.trade.enums.ApiFormat;
import com.crypto.trade.enums.ModelType;
import com.crypto.trade.repository.AIModelConfigRepository;
import com.crypto.trade.util.ApiKeyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AIModelConfigService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
@Transactional
public class AIModelConfigService {

    @Autowired
    private AIModelConfigRepository repository;

    @Autowired
    private UnifiedModelFactory unifiedModelFactory;

    @Autowired
    private ApiKeyUtils apiKeyUtils;

    /**
     * 获取所有AI模型配置
     */
    @Transactional(readOnly = true)
    public List<AIInfoModel> getAllModels() {
        log.debug("获取所有AI模型配置");
        List<AIModelConfig> configs = repository.findByIsActiveTrue();
        return configs.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有活跃的AI模型配置
     */
    @Transactional(readOnly = true)
    public List<AIInfoModel> getActiveModels() {
        log.debug("获取所有活跃的AI模型配置");
        List<AIModelConfig> configs = repository.findByIsActiveTrue();
        return configs.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据modelId获取模型配置
     */
    @Transactional(readOnly = true)
    public Optional<AIInfoModel> getModelByModelId(String modelId) {
        log.debug("根据modelId获取模型配置: {}", modelId);
        return repository.findByModelId(modelId)
                .map(this::convertToDTO);
    }

    /**
     * 获取默认模型配置
     */
    @Transactional(readOnly = true)
    public Optional<AIInfoModel> getDefaultModel() {
        log.debug("获取默认AI模型配置");
        return repository.findByDefaultModelTrue()
                .map(this::convertToDTO);
    }

    /**
     * 创建新的AI模型配置
     */
    @Transactional
    public AIInfoModel createModel(AIInfoModel modelInfo) throws UnifiedModelFactory.ModelCallException {
        log.info("创建新的AI模型配置: {}", modelInfo.getModelId());

        // 检查modelId是否已存在
        if (repository.existsByModelId(modelInfo.getModelId())) {
            throw new IllegalArgumentException("模型ID已存在: " + modelInfo.getModelId());
        }

        AIModelConfig config = convertToEntity(modelInfo);

        // 验证模型配置的完整性
        validateModelConfig(config);

        // 如果设置为默认模型，先取消所有其他默认模型
        if (Boolean.TRUE.equals(modelInfo.getDefaultModel())) {
            repository.unsetAllDefaultModels();
        }

        AIModelConfig savedConfig = repository.save(config);

        // 创建时不再强制验证模型可用性，允许用户预先配置模型
        // 用户可以通过独立的测试连接功能来验证模型可用性
        log.info("AI模型配置创建成功: {}, 类型: {}", savedConfig.getModelId(), savedConfig.getModelType());

        return convertToDTO(savedConfig);
    }

    /**
     * 更新AI模型配置
     */
    @Transactional
    public AIInfoModel updateModel(Long configId, AIInfoModel modelInfo) throws UnifiedModelFactory.ModelCallException {
        log.info("更新AI模型配置: configId={}, modelId={}", configId, modelInfo.getModelId());

        AIModelConfig existingConfig = repository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("模型配置不存在: " + configId));

        // 检查modelId是否被其他配置使用
        if (!existingConfig.getModelId().equals(modelInfo.getModelId()) &&
                repository.existsByModelIdAndConfigIdNot(modelInfo.getModelId(), configId)) {
            throw new IllegalArgumentException("模型ID已存在: " + modelInfo.getModelId());
        }

        // 如果设置为默认模型，先取消所有其他默认模型
        // 注意：只有当模型从非默认变为默认时才需要执行
        if (Boolean.TRUE.equals(modelInfo.getDefaultModel())
                && !Boolean.TRUE.equals(existingConfig.getDefaultModel())) {
            repository.unsetAllDefaultModels();
        }
        // 更新字段
        updateEntityFromDTO(existingConfig, modelInfo);
        // 验证模型配置的完整性
        validateModelConfig(existingConfig);
        AIModelConfig savedConfig = repository.save(existingConfig);
        // 验证模型可用性（可选）
        if (Boolean.TRUE.equals(savedConfig.getIsActive())) {
            validateModelAvailability(savedConfig.getModelId());
        }
        return convertToDTO(savedConfig);
    }

    /**
     * 删除AI模型配置（软删除：设为不活跃）
     */
    public void deleteModel(Long configId) {
        log.info("删除AI模型配置: {}", configId);

        AIModelConfig config = repository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("模型配置不存在: " + configId));

        // 如果是默认模型，不允许删除
        if (Boolean.TRUE.equals(config.getDefaultModel())) {
            throw new IllegalArgumentException("不能删除默认模型");
        }

        repository.deactivateModel(configId);
    }

    /**
     * 设置默认模型
     * 实现"检查-取消-设置"的完整流程，确保只有一个默认模型
     */
    @Transactional
    public AIInfoModel setDefaultModel(Long configId) {
        log.info("设置默认模型: {}", configId);

        // 1. 检查配置是否存在且活跃
        AIModelConfig config = repository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("模型配置不存在: " + configId));

        if (!config.getIsActive()) {
            throw new IllegalArgumentException("不能将不活跃的模型设为默认模型");
        }

        // 2. 检查是否已经是默认模型
        if (Boolean.TRUE.equals(config.getDefaultModel())) {
            log.debug("模型已经是默认模型: {}", configId);
            return convertToDTO(config);
        }

        // 3. 取消所有其他默认模型（确保原子性）
        log.debug("取消所有现有默认模型");
        repository.unsetAllDefaultModels();

        // 4. 设置当前模型为默认
        log.debug("设置新的默认模型: {}", configId);
        config.setDefaultModel(true);
        AIModelConfig savedConfig = repository.save(config);

        log.info("成功设置默认模型: configId={}, modelId={}", configId, savedConfig.getModelId());
        return convertToDTO(savedConfig);
    }

    /**
     * 切换模型活跃状态
     */
    public AIInfoModel toggleModelStatus(Long configId) throws UnifiedModelFactory.ModelCallException {
        log.info("切换模型状态: {}", configId);

        AIModelConfig config = repository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("模型配置不存在: " + configId));

        // 如果要激活模型，验证其可用性
        if (!config.getIsActive()) {
            validateModelAvailability(config.getModelId());
        }

        // 如果要取消激活默认模型，需要先取消其默认状态
        if (config.getIsActive() && config.getDefaultModel()) {
            config.setDefaultModel(false);
        }

        config.setIsActive(!config.getIsActive());
        AIModelConfig savedConfig = repository.save(config);

        return convertToDTO(savedConfig);
    }

    /**
     * 验证模型可用性
     */
    private void validateModelAvailability(String modelId) throws UnifiedModelFactory.ModelCallException {
        // 使用UnifiedModelFactory验证模型可用性
        if (unifiedModelFactory.isModelAvailable(modelId)) {
            log.debug("模型验证成功: {}", modelId);
        } else {
            log.warn("模型不可用: {}", modelId);
            throw new UnifiedModelFactory.ModelCallException("模型不可用: " + modelId,
                    UnifiedModelFactory.ModelCallException.ErrorCodes.MODEL_NOT_FOUND, modelId);
        }
    }

    /**
     * 验证模型配置的完整性
     */
    private void validateModelConfig(AIModelConfig config) {
        if (config.isLocalModel()) {
            // 本地模型验证
            if (config.getApiUrl() == null || config.getApiUrl().trim().isEmpty()) {
                log.warn("本地模型缺少API URL: {}", config.getModelId());
            }
        } else if (config.isRemoteModel()) {
            // 远端模型验证
            if (config.getApiUrl() == null || config.getApiUrl().trim().isEmpty()) {
                throw new IllegalArgumentException("远端模型必须配置API URL");
            }
            if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
                throw new IllegalArgumentException("远端模型必须配置API密钥");
            }
            if (config.getApiFormat() == null) {
                throw new IllegalArgumentException("远端模型必须配置API格式");
            }
        }
    }

    /**
     * 获取本地模型列表
     */
    @Transactional(readOnly = true)
    public List<AIInfoModel> getLocalModels() {
        log.debug("获取所有本地模型配置");
        List<AIModelConfig> configs = repository.findByModelType(ModelType.LOCAL.getCode());
        return configs.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取远端模型列表
     */
    @Transactional(readOnly = true)
    public List<AIInfoModel> getRemoteModels() {
        log.debug("获取所有远端模型配置");
        List<AIModelConfig> configs = repository.findByModelType(ModelType.REMOTE.getCode());
        return configs.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 测试模型连接
     */
    public ModelConnectionResult testModelConnection(Long configId) {
        log.info("测试模型连接: {}", configId);

        AIModelConfig config = repository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("模型配置不存在: " + configId));

        boolean isAvailable = unifiedModelFactory.isModelAvailable(config);
        String diagnosticInfo = getDiagnosticInfo(config);

        return new ModelConnectionResult(isAvailable, diagnosticInfo);
    }

    /**
     * 获取模型诊断信息
     */
    private String getDiagnosticInfo(AIModelConfig config) {
        StringBuilder info = new StringBuilder();
        info.append(String.format("模型ID: %s, 类型: %s", config.getModelId(), config.getModelType()));

        if (config.isLocalModel()) {
            info.append(String.format(", API地址: %s",
                    config.getApiUrl() != null ? config.getApiUrl() : "http://localhost:11434"));
            info.append(", 建议命令: 'ollama pull ").append(config.getModelId()).append("'");
        } else {
            info.append(String.format(", API地址: %s, 格式: %s",
                    config.getApiUrl(), config.getApiFormat()));
            if (config.getApiKey() != null) {
                info.append(", API密钥已配置");
            } else {
                info.append(", API密钥未配置");
            }
        }

        info.append(String.format(", 超时: %ds, 重试: %d次",
                config.getTimeoutSeconds(), config.getRetryCount()));

        return info.toString();
    }

    /**
     * 实体转DTO
     */
    private AIInfoModel convertToDTO(AIModelConfig entity) {
        return AIInfoModel.builder()
                .configId(entity.getConfigId())
                .modelId(entity.getModelId())
                .displayName(entity.getDisplayName())
                .provider(entity.getProvider())
                .parameterSize(entity.getParameterSize())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .maxTokens(entity.getMaxTokens())
                .costPerToken(entity.getCostPerToken() != null ? entity.getCostPerToken().doubleValue() : null)
                .defaultModel(entity.getDefaultModel())
                .modelType(entity.getModelType())
                .apiUrl(entity.getApiUrl())
                .apiKey(entity.getApiKey() != null ? apiKeyUtils.maskApiKey(entity.getApiKey()) : null)
                .apiFormat(entity.getApiFormat())
                .timeoutSeconds(entity.getTimeoutSeconds())
                .retryCount(entity.getRetryCount())
                .maxConcurrent(entity.getMaxConcurrent())
                .extraBody(entity.getExtraBody())
                .build();
    }

    /**
     * DTO转实体
     */
    private AIModelConfig convertToEntity(AIInfoModel dto) {
        return AIModelConfig.builder()
                .modelId(dto.getModelId())
                .displayName(dto.getDisplayName())
                .provider(dto.getProvider())
                .parameterSize(dto.getParameterSize())
                .description(dto.getDescription())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .maxTokens(dto.getMaxTokens())
                .costPerToken(dto.getCostPerToken() != null ? java.math.BigDecimal.valueOf(dto.getCostPerToken()) : null)
                .defaultModel(dto.getDefaultModel() != null ? dto.getDefaultModel() : false)
                .modelType(dto.getModelType() != null ? dto.getModelType() : ModelType.LOCAL.getCode())
                .apiUrl(dto.getApiUrl())
                .apiKey(dto.getApiKey() != null && !dto.getApiKey().contains("****") ?
                        apiKeyUtils.encrypt(dto.getApiKey()) : null)
                .apiFormat(dto.getApiFormat())
                .timeoutSeconds(dto.getTimeoutSeconds())
                .retryCount(dto.getRetryCount())
                .maxConcurrent(dto.getMaxConcurrent())
                .extraBody(dto.getExtraBody())
                .build();
    }

    /**
     * 从DTO更新实体
     */
    private void updateEntityFromDTO(AIModelConfig entity, AIInfoModel dto) {
        entity.setModelId(dto.getModelId());
        entity.setDisplayName(dto.getDisplayName());
        entity.setProvider(dto.getProvider());
        entity.setParameterSize(dto.getParameterSize());
        entity.setDescription(dto.getDescription());
        entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : entity.getIsActive());
        entity.setMaxTokens(dto.getMaxTokens());
        entity.setCostPerToken(dto.getCostPerToken() != null ? java.math.BigDecimal.valueOf(dto.getCostPerToken()) : null);
        entity.setDefaultModel(dto.getDefaultModel() != null ? dto.getDefaultModel() : entity.getDefaultModel());
        if (dto.getModelType() != null) {
            entity.setModelType(ModelType.fromCode(dto.getModelType()));
        }
        entity.setApiUrl(dto.getApiUrl());
        // 只有在API密钥发生变化且不是脱敏格式时才更新
        if (dto.getApiKey() != null && !dto.getApiKey().contains("****")) {
            entity.setApiKey(apiKeyUtils.encrypt(dto.getApiKey()));
        }
        entity.setApiFormat(dto.getApiFormat() != null ? ApiFormat.fromCode(dto.getApiFormat()) : null);
        entity.setTimeoutSeconds(dto.getTimeoutSeconds());
        entity.setRetryCount(dto.getRetryCount());
        entity.setMaxConcurrent(dto.getMaxConcurrent());
        entity.setExtraBody(dto.getExtraBody());
    }

    /**
     * 模型连接测试结果
     */
    public static class ModelConnectionResult {
        private final boolean success;
        private final String diagnosticInfo;

        public ModelConnectionResult(boolean success, String diagnosticInfo) {
            this.success = success;
            this.diagnosticInfo = diagnosticInfo;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getDiagnosticInfo() {
            return diagnosticInfo;
        }
    }
}
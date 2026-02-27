package com.crypto.trade.service.data;

import com.crypto.trade.entity.DataFetchConfig;
import com.crypto.trade.model.DataFetchConfigModel;
import com.crypto.trade.model.request.CreateDataFetchConfigReq;
import com.crypto.trade.model.request.UpdateDataFetchConfigReq;
import com.crypto.trade.repository.DataFetchConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ApiConfigService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class ApiConfigService {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000; // 5分钟缓存过期时间

    // 配置缓存，提高频繁查询的性能
    private final Map<Long, DataFetchConfig> configCache = new ConcurrentHashMap<>();

    @Autowired
    DataFetchConfigRepository dataFetchConfigRepository;

    private volatile long lastCacheUpdate = 0;

    public List<DataFetchConfig> getAllConfigs() {
        return dataFetchConfigRepository.findAll();
    }

    public Optional<DataFetchConfig> getConfigById(Long configId) {
        return dataFetchConfigRepository.findById(configId);
    }

    public DataFetchConfig getConfigByTaskId(Long taskId) {
        // 检查缓存
        DataFetchConfig cachedConfig = configCache.get(taskId);
        long currentTime = System.currentTimeMillis();

        if (null != cachedConfig && (currentTime - lastCacheUpdate) < CACHE_EXPIRY_MS) {
            log.debug("从缓存获取配置 - 任务ID: {}", taskId);
            return cachedConfig;
        }

        // 缓存过期或不存在，从数据库获取
        DataFetchConfig config = dataFetchConfigRepository.findByTaskId(taskId);

        if (null != config) {
            // 更新缓存
            configCache.put(taskId, config);
            lastCacheUpdate = currentTime;
            log.debug("从数据库获取配置并更新缓存 - 任务ID: {}", taskId);
        } else {
            log.warn("未找到任务ID {} 的配置", taskId);
        }

        return config;
    }

    public List<DataFetchConfig> getConfigsByCexName(String cexName) {
        return dataFetchConfigRepository.findByCexName(cexName);
    }

    public List<DataFetchConfig> getAuthRequiredConfigs() {
        return dataFetchConfigRepository.findAuthRequiredConfigs();
    }

    public List<DataFetchConfig> getActiveConfigs() {
        return dataFetchConfigRepository.findActiveConfigs();
    }

    public DataFetchConfig createConfig(DataFetchConfig config) {
        // 验证任务ID是否已存在配置
        DataFetchConfig existingConfig = dataFetchConfigRepository.findByTaskId(config.getTaskId());
        if (null != existingConfig) {
            throw new IllegalArgumentException("Config already exists for task id: " + config.getTaskId());
        }

        // 验证必要的字段
        validateConfig(config);

        DataFetchConfig savedConfig = dataFetchConfigRepository.save(config);

        // 清除相关缓存
        clearCacheByTaskId(config.getTaskId());

        log.info("创建配置成功 - 任务ID: {}, 配置ID: {}", config.getTaskId(), savedConfig.getConfigId());
        return savedConfig;
    }

    public DataFetchConfig updateConfig(Long configId, DataFetchConfig updatedConfig) {
        return dataFetchConfigRepository.findById(configId)
                .map(existingConfig -> {
                    // 验证必要的字段
                    validateConfig(updatedConfig);

                    // 更新配置信息
                    existingConfig.setCexBaseUrl(updatedConfig.getCexBaseUrl());
                    existingConfig.setApiPath(updatedConfig.getApiPath());
                    existingConfig.setHttpMethod(updatedConfig.getHttpMethod());
                    existingConfig.setRequestParams(updatedConfig.getRequestParams());
                    existingConfig.setRequiresAuth(updatedConfig.getRequiresAuth());
                    existingConfig.setAuthKeyId(updatedConfig.getAuthKeyId());
                    existingConfig.setSignatureClass(updatedConfig.getSignatureClass());
                    existingConfig.setDataProcessorClass(updatedConfig.getDataProcessorClass());
                    existingConfig.setTargetDuckdbTable(updatedConfig.getTargetDuckdbTable());
                    existingConfig.setResponseMapping(updatedConfig.getResponseMapping());

                    DataFetchConfig savedConfig = dataFetchConfigRepository.save(existingConfig);

                    // 清除相关缓存
                    clearCacheByTaskId(existingConfig.getTaskId());

                    log.info("更新配置成功 - 配置ID: {}, 任务ID: {}", configId, existingConfig.getTaskId());
                    return savedConfig;
                })
                .orElseThrow(() -> new IllegalArgumentException("Config not found with id: " + configId));
    }

    public void deleteConfig(Long configId) {
        if (!dataFetchConfigRepository.existsById(configId)) {
            throw new IllegalArgumentException("Config not found with id: " + configId);
        }

        // 获取配置以清除缓存
        DataFetchConfig config = dataFetchConfigRepository.findById(configId).orElse(null);
        if (null != config) {
            dataFetchConfigRepository.deleteById(configId);
            clearCacheByTaskId(config.getTaskId());
            log.info("删除配置成功 - 配置ID: {}, 任务ID: {}", configId, config.getTaskId());
        }
    }

    public void deleteConfigByTaskId(Long taskId) {
        DataFetchConfig config = dataFetchConfigRepository.findByTaskId(taskId);
        if (null != config) {
            dataFetchConfigRepository.delete(config);
            clearCacheByTaskId(taskId);
            log.info("根据任务ID删除配置成功 - 任务ID: {}", taskId);
        } else {
            log.warn("未找到任务ID {} 的配置进行删除", taskId);
        }
    }

    public List<DataFetchConfig> getConfigsByAuthKeyId(Long keyId) {
        return dataFetchConfigRepository.findByAuthKeyId(keyId);
    }

    private void validateConfig(DataFetchConfig config) {
        log.debug("验证配置 - 任务ID: {}", config.getTaskId());

        if (config.getCexBaseUrl() == null || config.getCexBaseUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("CEX Base URL is required");
        }

        if (config.getApiPath() == null || config.getApiPath().trim().isEmpty()) {
            throw new IllegalArgumentException("API Path is required");
        }

        if (config.getHttpMethod() == null || config.getHttpMethod().trim().isEmpty()) {
            throw new IllegalArgumentException("HTTP Method is required");
        }

        if (config.getDataProcessorClass() == null || config.getDataProcessorClass().trim().isEmpty()) {
            throw new IllegalArgumentException("Data Processor Class is required");
        }

        if (config.getTargetDuckdbTable() == null || config.getTargetDuckdbTable().trim().isEmpty()) {
            throw new IllegalArgumentException("Target DuckDB Table is required");
        }

        // 验证HTTP方法
        String httpMethod = config.getHttpMethod().toUpperCase();
        if (!httpMethod.equals("GET") && !httpMethod.equals("POST") &&
                !httpMethod.equals("PUT") && !httpMethod.equals("DELETE")) {
            throw new IllegalArgumentException("Invalid HTTP Method: " + config.getHttpMethod());
        }

        // 如果需要鉴权，必须提供authKeyId
        if (Boolean.TRUE.equals(config.getRequiresAuth()) && config.getAuthKeyId() == null) {
            throw new IllegalArgumentException("Auth Key ID is required when authentication is enabled");
        }

        // 验证请求参数JSON格式
        validateRequestParams(config.getRequestParams(), config.getHttpMethod());

        // 验证响应映射JSON格式
        validateResponseMapping(config.getResponseMapping());

        log.debug("配置验证通过 - 任务ID: {}", config.getTaskId());
    }

    /**
     * 验证请求参数JSON格式
     */
    private void validateRequestParams(String requestParams, String httpMethod) {
        if (null == requestParams || requestParams.trim().isEmpty()) {
            // 空参数是允许的
            return;
        }

        try {
            Map<String, Object> params = objectMapper.readValue(requestParams, Map.class);
            log.debug("请求参数JSON格式验证通过 - 参数数量: {}, HTTP方法: {}", params.size(), httpMethod);

            // 针对GET请求，建议使用查询参数而不是请求体
            if ("GET".equalsIgnoreCase(httpMethod) && !params.isEmpty()) {
                log.info("GET请求包含参数，将作为查询字符串处理 - 任务配置: {}", requestParams);
            }

        } catch (JsonProcessingException e) {
            log.error("请求参数JSON格式无效: {}", requestParams);
            throw new IllegalArgumentException("Invalid request parameters JSON format: " + e.getMessage());
        }
    }

    /**
     * 验证响应映射JSON格式
     */
    private void validateResponseMapping(String responseMapping) {
        if (null == responseMapping || responseMapping.trim().isEmpty()) {
            // 空响应映射是允许的
            return;
        }

        try {
            Map<String, Object> mapping = objectMapper.readValue(responseMapping, Map.class);
            log.debug("响应映射JSON格式验证通过 - 映射字段数量: {}", mapping.size());

        } catch (JsonProcessingException e) {
            log.error("响应映射JSON格式无效: {}", responseMapping);
            throw new IllegalArgumentException("Invalid response mapping JSON format: " + e.getMessage());
        }
    }

    /**
     * 清除配置缓存
     */
    public void clearCache() {
        configCache.clear();
        lastCacheUpdate = 0;
        log.info("配置缓存已清除");
    }

    /**
     * 清除指定任务的缓存
     */
    public void clearCacheByTaskId(Long taskId) {
        configCache.remove(taskId);
        log.debug("已清除任务ID {} 的配置缓存", taskId);
    }

    // ==================== Model转换方法 ====================

    /**
     * 获取所有配置的Model列表
     *
     * @return DataFetchConfigModel列表
     */
    public List<DataFetchConfigModel> getAllConfigsModel() {
        List<DataFetchConfig> configs = getAllConfigs();
        return configs.stream()
                .map(DataFetchConfigModel::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 根据ID获取配置的Model
     *
     * @param configId 配置ID
     * @return DataFetchConfigModel
     */
    public DataFetchConfigModel getConfigByIdModel(Long configId) {
        Optional<DataFetchConfig> configOpt = getConfigById(configId);
        return configOpt.map(DataFetchConfigModel::fromEntity).orElse(null);
    }

    /**
     * 根据任务ID获取配置的Model
     *
     * @param taskId 任务ID
     * @return DataFetchConfigModel
     */
    public DataFetchConfigModel getConfigByTaskIdModel(Long taskId) {
        DataFetchConfig config = getConfigByTaskId(taskId);
        return DataFetchConfigModel.fromEntity(config);
    }

    /**
     * 创建配置并返回Model
     *
     * @param config 数据获取配置
     * @return DataFetchConfigModel
     */
    public DataFetchConfigModel createConfigModel(DataFetchConfig config) {
        DataFetchConfig createdConfig = createConfig(config);
        return DataFetchConfigModel.fromEntity(createdConfig);
    }

    /**
     * 更新配置并返回Model
     *
     * @param configId      配置ID
     * @param updatedConfig 更新的配置
     * @return DataFetchConfigModel
     */
    public DataFetchConfigModel updateConfigModel(Long configId, DataFetchConfig updatedConfig) {
        DataFetchConfig config = updateConfig(configId, updatedConfig);
        return DataFetchConfigModel.fromEntity(config);
    }

    // ==================== Request Model处理方法 ====================

    /**
     * 从CreateRequest创建Entity并保存配置
     *
     * @param request 创建配置请求
     * @return DataFetchConfigModel
     */
    public DataFetchConfigModel createConfigFromRequest(CreateDataFetchConfigReq request) {
        log.debug("从Request创建数据获取配置 - 任务ID: {}", request.getTaskId());

        // 验证请求参数
        if (!request.isValid()) {
            throw new IllegalArgumentException("请求参数验证失败");
        }

        // 转换为Entity
        DataFetchConfig config = convertCreateRequestToEntity(request);

        // 使用现有的createConfig方法
        DataFetchConfig createdConfig = createConfig(config);

        log.info("从Request创建配置成功 - 配置ID: {}, 任务ID: {}", createdConfig.getConfigId(), createdConfig.getTaskId());
        return DataFetchConfigModel.fromEntity(createdConfig);
    }

    /**
     * 从UpdateRequest更新配置
     *
     * @param configId 配置ID
     * @param request  更新配置请求
     * @return DataFetchConfigModel
     */
    public DataFetchConfigModel updateConfigFromRequest(Long configId, UpdateDataFetchConfigReq request) {
        log.debug("从Request更新数据获取配置 - ID: {}", configId);

        // 验证请求参数
        if (null == request || !request.hasUpdateFields()) {
            throw new IllegalArgumentException("更新请求不能为空且必须包含更新字段");
        }

        if (!request.isValid()) {
            throw new IllegalArgumentException("请求参数验证失败");
        }

        // 获取现有配置
        DataFetchConfig existingConfig = dataFetchConfigRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("配置不存在: " + configId));

        // 更新字段
        updateEntityFromRequest(existingConfig, request);

        // 使用现有的updateConfig方法
        DataFetchConfig updatedConfig = updateConfig(configId, existingConfig);

        log.info("从Request更新配置成功 - 配置ID: {}, 任务ID: {}", updatedConfig.getConfigId(), updatedConfig.getTaskId());
        return DataFetchConfigModel.fromEntity(updatedConfig);
    }

    /**
     * 将CreateRequest转换为Entity
     *
     * @param request 创建请求
     * @return DataFetchConfig Entity
     */
    private DataFetchConfig convertCreateRequestToEntity(CreateDataFetchConfigReq request) {
        DataFetchConfig config = new DataFetchConfig();
        config.setTaskId(request.getTaskId());
        config.setCexBaseUrl(request.getCexBaseUrl());
        config.setApiPath(request.getApiPath());
        config.setHttpMethod(request.getHttpMethod());
        config.setRequestParams(request.getRequestParams());
        config.setRequiresAuth(request.getRequiresAuth());
        config.setAuthKeyId(request.getAuthKeyId());
        config.setSignatureClass(request.getSignatureClass());
        config.setDataProcessorClass(request.getDataProcessorClass());
        config.setTargetDuckdbTable(request.getTargetDuckdbTable());
        config.setResponseMapping(request.getResponseMapping());
        config.setRequiresProxy(request.getRequiresProxy());
        config.setProxyId(request.getProxyId());
        return config;
    }

    /**
     * 使用UpdateRequest更新Entity
     *
     * @param entity  现有的Entity
     * @param request 更新请求
     */
    private void updateEntityFromRequest(DataFetchConfig entity, UpdateDataFetchConfigReq request) {
        if (request.getCexBaseUrl() != null) {
            entity.setCexBaseUrl(request.getCexBaseUrl());
        }
        if (request.getApiPath() != null) {
            entity.setApiPath(request.getApiPath());
        }
        if (request.getHttpMethod() != null) {
            entity.setHttpMethod(request.getHttpMethod());
        }
        if (request.getRequestParams() != null) {
            entity.setRequestParams(request.getRequestParams());
        }
        if (request.getRequiresAuth() != null) {
            entity.setRequiresAuth(request.getRequiresAuth());
        }
        if (request.getAuthKeyId() != null) {
            entity.setAuthKeyId(request.getAuthKeyId());
        }
        if (request.getSignatureClass() != null) {
            entity.setSignatureClass(request.getSignatureClass());
        }
        if (request.getDataProcessorClass() != null) {
            entity.setDataProcessorClass(request.getDataProcessorClass());
        }
        if (request.getTargetDuckdbTable() != null) {
            entity.setTargetDuckdbTable(request.getTargetDuckdbTable());
        }
        if (request.getResponseMapping() != null) {
            entity.setResponseMapping(request.getResponseMapping());
        }
        if (request.getRequiresProxy() != null) {
            entity.setRequiresProxy(request.getRequiresProxy());
        }
        if (request.getProxyId() != null) {
            entity.setProxyId(request.getProxyId());
        }
    }
}
package com.crypto.trade.rest.controller;

import com.crypto.trade.entity.AIInfoModel;
import com.crypto.trade.enums.ApiFormat;
import com.crypto.trade.enums.ModelType;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.service.AIModelConfigService;
import com.crypto.trade.service.UnifiedModelFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AIModelConfigController
 * REST控制器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@RestController
@RequestMapping("/ai-model-configs")
public class AIModelConfigController {

    @Autowired
    private AIModelConfigService aiModelConfigService;

    /**
     * 获取所有AI模型配置
     */
    @GetMapping
    public ApiResponse<List<AIInfoModel>> getAllModels() {
        try {
            log.debug("获取所有AI模型配置");
            List<AIInfoModel> models = aiModelConfigService.getAllModels();
            log.debug("获取所有AI模型配置成功，数量: {}", models.size());
            return ApiResponse.ok(models);
        } catch (Exception e) {
            log.error("获取所有AI模型配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取模型配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有活跃的AI模型配置
     */
    @GetMapping("/active")
    public ApiResponse<List<AIInfoModel>> getActiveModels() {
        try {
            log.debug("获取所有活跃的AI模型配置");
            List<AIInfoModel> models = aiModelConfigService.getActiveModels();
            log.debug("获取所有活跃的AI模型配置成功，数量: {}", models.size());
            return ApiResponse.ok(models);
        } catch (Exception e) {
            log.error("获取所有活跃的AI模型配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取活跃模型配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据modelId获取模型配置
     */
    @GetMapping("/model/{modelId}")
    public ApiResponse<AIInfoModel> getModelByModelId(@PathVariable String modelId) {
        try {
            log.debug("根据modelId获取模型配置: {}", modelId);
            return aiModelConfigService.getModelByModelId(modelId)
                    .map(model -> {
                        log.debug("根据modelId获取模型配置成功: {}", modelId);
                        return ApiResponse.ok(model);
                    })
                    .orElseGet(() -> {
                        log.warn("未找到模型配置: {}", modelId);
                        return ApiResponse.fail("模型配置不存在: " + modelId);
                    });
        } catch (Exception e) {
            log.error("根据modelId获取模型配置失败: {}, error: {}", modelId, e.getMessage(), e);
            return ApiResponse.fail("获取模型配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取默认模型配置
     */
    @GetMapping("/default")
    public ApiResponse<AIInfoModel> getDefaultModel() {
        try {
            log.debug("获取默认AI模型配置");
            return aiModelConfigService.getDefaultModel()
                    .map(model -> {
                        log.debug("获取默认AI模型配置成功: {}", model.getModelId());
                        return ApiResponse.ok(model);
                    })
                    .orElseGet(() -> {
                        log.warn("未找到默认模型配置");
                        return ApiResponse.fail("未找到默认模型配置");
                    });
        } catch (Exception e) {
            log.error("获取默认AI模型配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取默认模型配置失败: " + e.getMessage());
        }
    }

    /**
     * 创建新的AI模型配置
     */
    @PostMapping
    public ApiResponse<AIInfoModel> createModel(@RequestBody AIInfoModel modelInfo) {
        try {
            log.debug("创建新的AI模型配置: {}", modelInfo.getModelId());

            // 基础参数校验
            if (modelInfo.getModelId() == null || modelInfo.getModelId().trim().isEmpty()) {
                return ApiResponse.fail("模型ID不能为空");
            }
            if (modelInfo.getDisplayName() == null || modelInfo.getDisplayName().trim().isEmpty()) {
                return ApiResponse.fail("显示名称不能为空");
            }
            if (modelInfo.getProvider() == null || modelInfo.getProvider().trim().isEmpty()) {
                return ApiResponse.fail("提供商不能为空");
            }

            // 模型配置校验
            if (!validateModelConfig(modelInfo)) {
                return ApiResponse.fail("模型配置参数不完整或不正确");
            }

            AIInfoModel createdModel = aiModelConfigService.createModel(modelInfo);
            log.info("创建AI模型配置成功: {}", createdModel.getModelId());
            return ApiResponse.ok(createdModel);
        } catch (UnifiedModelFactory.ModelCallException e) {
            log.error("创建AI模型配置失败 - 模型调用异常: {}", e.getMessage(), e);
            // 提供更友好的错误信息和解决建议
            String userMessage = getUserFriendlyErrorMessage(e, modelInfo.getModelId());
            return ApiResponse.fail(userMessage);
        } catch (IllegalArgumentException e) {
            log.warn("创建AI模型配置失败 - 参数错误: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("创建AI模型配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("创建模型配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新AI模型配置
     */
    @PutMapping("/{configId}")
    public ApiResponse<AIInfoModel> updateModel(@PathVariable Long configId, @RequestBody AIInfoModel modelInfo) {
        try {
            log.debug("更新AI模型配置: configId={}, modelId={}", configId, modelInfo.getModelId());

            // 基础参数校验
            if (modelInfo.getModelId() == null || modelInfo.getModelId().trim().isEmpty()) {
                return ApiResponse.fail("模型ID不能为空");
            }
            if (modelInfo.getDisplayName() == null || modelInfo.getDisplayName().trim().isEmpty()) {
                return ApiResponse.fail("显示名称不能为空");
            }
            if (modelInfo.getProvider() == null || modelInfo.getProvider().trim().isEmpty()) {
                return ApiResponse.fail("提供商不能为空");
            }

            // 模型配置校验
            if (!validateModelConfig(modelInfo)) {
                return ApiResponse.fail("模型配置参数不完整或不正确");
            }

            AIInfoModel updatedModel = aiModelConfigService.updateModel(configId, modelInfo);
            log.info("更新AI模型配置成功: configId={}, modelId={}", configId, updatedModel.getModelId());
            return ApiResponse.ok(updatedModel);
        } catch (UnifiedModelFactory.ModelCallException e) {
            log.error("更新AI模型配置失败 - 模型调用异常: configId={}, error: {}", configId, e.getMessage(), e);
            return ApiResponse.fail("模型不可用: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新AI模型配置失败 - 参数错误: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("更新AI模型配置失败: configId={}, error: {}", configId, e.getMessage(), e);
            return ApiResponse.fail("更新模型配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除AI模型配置（软删除）
     */
    @DeleteMapping("/{configId}")
    public ApiResponse<String> deleteModel(@PathVariable Long configId) {
        try {
            log.debug("删除AI模型配置: {}", configId);
            aiModelConfigService.deleteModel(configId);
            log.info("删除AI模型配置成功: {}", configId);
            return ApiResponse.ok("删除成功");
        } catch (IllegalArgumentException e) {
            log.warn("删除AI模型配置失败 - 参数错误: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("删除AI模型配置失败: {}, error: {}", configId, e.getMessage(), e);
            return ApiResponse.fail("删除模型配置失败: " + e.getMessage());
        }
    }

    /**
     * 设置默认模型
     */
    @PutMapping("/{configId}/set-default")
    public ApiResponse<AIInfoModel> setDefaultModel(@PathVariable Long configId) {
        try {
            log.debug("设置默认模型: {}", configId);
            AIInfoModel model = aiModelConfigService.setDefaultModel(configId);
            log.info("设置默认模型成功: configId={}, modelId={}", configId, model.getModelId());
            return ApiResponse.ok(model);
        } catch (IllegalArgumentException e) {
            log.warn("设置默认模型失败 - 参数错误: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("设置默认模型失败: {}, error: {}", configId, e.getMessage(), e);
            return ApiResponse.fail("设置默认模型失败: " + e.getMessage());
        }
    }

    /**
     * 切换模型活跃状态
     */
    @PutMapping("/{configId}/toggle-status")
    public ApiResponse<AIInfoModel> toggleModelStatus(@PathVariable Long configId) {
        try {
            log.debug("切换模型状态: {}", configId);
            AIInfoModel model = aiModelConfigService.toggleModelStatus(configId);
            log.info("切换模型状态成功: configId={}, modelId={}, active={}",
                    configId, model.getModelId(), model.getIsActive());
            return ApiResponse.ok(model);
        } catch (IllegalArgumentException e) {
            log.warn("切换模型状态失败 - 参数错误: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (UnifiedModelFactory.ModelCallException e) {
            log.error("切换模型状态失败 - 模型调用异常: {}, error: {}", configId, e.getMessage(), e);
            return ApiResponse.fail("模型不可用: " + e.getMessage());
        } catch (Exception e) {
            log.error("切换模型状态失败: {}, error: {}", configId, e.getMessage(), e);
            return ApiResponse.fail("切换模型状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取本地模型列表
     */
    @GetMapping("/local")
    public ApiResponse<List<AIInfoModel>> getLocalModels() {
        try {
            log.debug("获取所有本地模型配置");
            List<AIInfoModel> models = aiModelConfigService.getLocalModels();
            log.debug("获取所有本地模型配置成功，数量: {}", models.size());
            return ApiResponse.ok(models);
        } catch (Exception e) {
            log.error("获取所有本地模型配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取本地模型配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取远端模型列表
     */
    @GetMapping("/remote")
    public ApiResponse<List<AIInfoModel>> getRemoteModels() {
        try {
            log.debug("获取所有远端模型配置");
            List<AIInfoModel> models = aiModelConfigService.getRemoteModels();
            log.debug("获取所有远端模型配置成功，数量: {}", models.size());
            return ApiResponse.ok(models);
        } catch (Exception e) {
            log.error("获取所有远端模型配置失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取远端模型配置失败: " + e.getMessage());
        }
    }

    /**
     * 测试模型连接
     */
    @PostMapping("/{configId}/test-connection")
    public ApiResponse<String> testModelConnection(@PathVariable Long configId) {
        try {
            log.debug("测试模型连接: {}", configId);
            AIModelConfigService.ModelConnectionResult result = aiModelConfigService.testModelConnection(configId);

            if (result.isSuccess()) {
                log.info("模型连接测试成功: {}", configId);
                return ApiResponse.ok("模型连接正常 - " + result.getDiagnosticInfo());
            } else {
                log.warn("模型连接测试失败: {}", configId);
                return ApiResponse.fail("模型连接失败 - " + result.getDiagnosticInfo());
            }
        } catch (IllegalArgumentException e) {
            log.warn("测试模型连接失败 - 参数错误: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("测试模型连接失败: {}, error: {}", configId, e.getMessage(), e);
            return ApiResponse.fail("测试连接失败: " + e.getMessage());
        }
    }

    /**
     * 获取支持的模型类型
     */
    @GetMapping("/model-types")
    public ApiResponse<List<String>> getModelTypes() {
        try {
            log.debug("获取支持的模型类型");
            List<String> modelTypes = List.of(
                    ModelType.LOCAL.getCode(),
                    ModelType.REMOTE.getCode()
            );
            log.debug("获取支持的模型类型成功，数量: {}", modelTypes.size());
            return ApiResponse.ok(modelTypes);
        } catch (Exception e) {
            log.error("获取支持的模型类型失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取模型类型失败: " + e.getMessage());
        }
    }

    /**
     * 获取支持的API格式
     */
    @GetMapping("/api-formats")
    public ApiResponse<List<String>> getApiFormats() {
        try {
            log.debug("获取支持的API格式");
            List<String> apiFormats = List.of(
                    ApiFormat.OLLAMA.getCode(),
                    ApiFormat.OPENAI.getCode(),
                    ApiFormat.CLAUDE.getCode(),
                    ApiFormat.CUSTOM.getCode()
            );
            log.debug("获取支持的API格式成功，数量: {}", apiFormats.size());
            return ApiResponse.ok(apiFormats);
        } catch (Exception e) {
            log.error("获取支持的API格式失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取API格式失败: " + e.getMessage());
        }
    }

    /**
     * 验证远端模型配置参数
     */
    private boolean validateRemoteModelConfig(AIInfoModel modelInfo) {
        // API URL
        if (modelInfo.getApiUrl() == null || modelInfo.getApiUrl().trim().isEmpty()) {
            log.warn("远端模型缺少API URL");
            return false;
        }

        // API Key
        if (modelInfo.getApiKey() == null || modelInfo.getApiKey().trim().isEmpty()) {
            log.warn("远端模型缺少API密钥");
            return false;
        }

        // API Format
        if (modelInfo.getApiFormat() == null || modelInfo.getApiFormat().trim().isEmpty()) {
            log.warn("远端模型缺少API格式");
            return false;
        }

        // 验证API格式是否支持
        try {
            ApiFormat.fromCode(modelInfo.getApiFormat());
        } catch (IllegalArgumentException e) {
            log.warn("不支持的API格式: {}", modelInfo.getApiFormat());
            return false;
        }

        return true;
    }

    /**
     * 验证模型配置参数
     */
    private boolean validateModelConfig(AIInfoModel modelInfo) {
        // 基础验证
        if (modelInfo.getModelType() == null || modelInfo.getModelType().trim().isEmpty()) {
            log.warn("模型类型不能为空");
            return false;
        }

        try {
            ModelType.fromCode(modelInfo.getModelType());
        } catch (IllegalArgumentException e) {
            log.warn("不支持的模型类型: {}", modelInfo.getModelType());
            return false;
        }

        // 远端模型需要额外验证
        if (ModelType.REMOTE.getCode().equals(modelInfo.getModelType())) {
            return validateRemoteModelConfig(modelInfo);
        }

        return true;
    }

    /**
     * 获取用户友好的错误信息和解决建议
     */
    private String getUserFriendlyErrorMessage(UnifiedModelFactory.ModelCallException e, String modelId) {
        String errorCode = e.getErrorCode();
        String modelType = "模型";

        // 根据错误代码提供具体的解决建议
        switch (errorCode) {
            case UnifiedModelFactory.ModelCallException.ErrorCodes.CONNECTION_ERROR:
                return String.format("无法连接到%s服务 '%s'。请检查：%s服务是否正在运行，网络连接是否正常",
                        modelType, modelId, modelType);

            case UnifiedModelFactory.ModelCallException.ErrorCodes.MODEL_NOT_FOUND:
                if (modelId.startsWith("ollama") || isLikelyLocalModel(modelId)) {
                    return String.format("本地模型 '%s' 不存在。请执行命令下载：%s",
                            modelId, getLocalModelDownloadCommand(modelId));
                } else {
                    return String.format("远端模型 '%s' 不存在或无法访问。请检查模型名称和API配置", modelId);
                }

            case UnifiedModelFactory.ModelCallException.ErrorCodes.AUTHENTICATION_ERROR:
                return String.format("API认证失败。请检查 '%s' 的API密钥是否正确", modelId);

            case UnifiedModelFactory.ModelCallException.ErrorCodes.TIMEOUT_ERROR:
                return String.format("调用模型 '%s' 超时。请尝试增加超时时间配置", modelId);

            case UnifiedModelFactory.ModelCallException.ErrorCodes.RATE_LIMIT_ERROR:
                return "调用频率限制。请稍后再试或检查API配额";

            case UnifiedModelFactory.ModelCallException.ErrorCodes.QUOTA_EXCEEDED:
                return "API配额已用完。请检查账户余额或升级套餐";

            default:
                return String.format("模型操作失败：%s。请检查配置和服务状态", e.getMessage());
        }
    }

    /**
     * 判断是否可能是本地模型
     */
    private boolean isLikelyLocalModel(String modelId) {
        return modelId.contains(":") || modelId.toLowerCase().contains("llama") ||
                modelId.toLowerCase().contains("qwen") || modelId.toLowerCase().contains("deepseek");
    }

    /**
     * 获取本地模型下载命令
     */
    private String getLocalModelDownloadCommand(String modelId) {
        return String.format("'ollama pull %s' 或 'ollama run %s'", modelId, modelId);
    }
}
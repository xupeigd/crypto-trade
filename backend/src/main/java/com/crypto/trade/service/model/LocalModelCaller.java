package com.crypto.trade.service.model;

import com.crypto.trade.entity.AIModelConfig;
import com.crypto.trade.enums.ModelType;
import com.crypto.trade.service.UnifiedModelFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * LocalModelCaller
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class LocalModelCaller implements ModelCaller {

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Override
    public String call(String prompt, AIModelConfig config) throws UnifiedModelFactory.ModelCallException {
        if (config == null || !config.isLocalModel()) {
            throw new UnifiedModelFactory.ModelCallException("配置无效或不是本地模型", config != null ? config.getModelId() : "unknown");
        }

        try {
            log.debug("调用本地模型: {}, prompt长度: {}", config.getModelId(), prompt.length());

            // 使用Ollama API直接调用
            OllamaApi ollamaApi = createOllamaApi(config);

            // 创建Ollama聊天请求
            OllamaApi.Message message = new OllamaApi.Message(
                    OllamaApi.Message.Role.USER, prompt, null, null);

            OllamaApi.ChatRequest request = new OllamaApi.ChatRequest(
                    config.getModelId(),
                    List.of(message),
                    false, // stream - 必须明确设置
                    null, // format
                    null, // options
                    null, // tools
                    null  // system
            );

            // 调用Ollama API
            OllamaApi.ChatResponse response = ollamaApi.chat(request);

            String result = response.message() != null ? response.message().content() : "";

            log.debug("本地模型调用成功: {}, 响应长度: {}", config.getModelId(), result.length());
            return result;

        } catch (Exception e) {
            log.error("本地模型调用失败: {}", config.getModelId(), e);

            // 根据异常类型设置不同的错误代码
            String errorCode = determineErrorCode(e);
            throw new UnifiedModelFactory.ModelCallException(
                    "本地模型调用失败: " + e.getMessage(),
                    e,
                    errorCode,
                    config.getModelId()
            );
        }
    }

    @Override
    public String callWithMessages(List<UnifiedModelFactory.Message> messages, AIModelConfig config) throws UnifiedModelFactory.ModelCallException {
        if (config == null || !config.isLocalModel()) {
            throw new UnifiedModelFactory.ModelCallException("配置无效或不是本地模型", config != null ? config.getModelId() : "unknown");
        }

        if (messages == null || messages.isEmpty()) {
            throw new UnifiedModelFactory.ModelCallException("消息数组不能为空", config != null ? config.getModelId() : "unknown");
        }

        try {
            log.debug("调用本地模型(使用messages): {}, 消息数: {}", config.getModelId(), messages.size());

            // 使用Ollama API直接调用
            OllamaApi ollamaApi = createOllamaApi(config);

            // 转换为Ollama消息格式
            List<OllamaApi.Message> ollamaMessages = new ArrayList<>();
            for (UnifiedModelFactory.Message msg : messages) {
                OllamaApi.Message.Role role = convertRole(msg.getRole());
                ollamaMessages.add(new OllamaApi.Message(role, msg.getContent(), null, null));
            }

            // 创建Ollama聊天请求
            OllamaApi.ChatRequest request = new OllamaApi.ChatRequest(
                    config.getModelId(),
                    ollamaMessages,
                    false, // stream - 必须明确设置
                    null, // format
                    null, // options
                    null, // tools
                    null  // system
            );

            // 调用Ollama API
            OllamaApi.ChatResponse response = ollamaApi.chat(request);

            String result = response.message() != null ? response.message().content() : "";

            log.debug("本地模型调用成功(使用messages): {}, 响应长度: {}", config.getModelId(), result.length());
            return result;

        } catch (Exception e) {
            log.error("本地模型调用失败(使用messages): {}", config.getModelId(), e);

            // 根据异常类型设置不同的错误代码
            String errorCode = determineErrorCode(e);
            throw new UnifiedModelFactory.ModelCallException(
                    "本地模型调用失败: " + e.getMessage(),
                    e,
                    errorCode,
                    config.getModelId()
            );
        }
    }

    @Override
    public String callWithMessagesAndTools(List<UnifiedModelFactory.Message> messages, AIModelConfig config,
            List<UnifiedModelFactory.Tool> tools) throws UnifiedModelFactory.ModelCallException {
        // 本地模型（Ollama）暂不支持Function Calling，回退到不带tools的调用
        log.warn("本地模型不支持Function Calling，回退到普通调用");
        return callWithMessages(messages, config);
    }

    @Override
    public CompletableFuture<String> callAsync(String prompt, AIModelConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return call(prompt, config);
            } catch (UnifiedModelFactory.ModelCallException e) {
                throw new RuntimeException(e);
            }
        }, executorService).orTimeout(config.getTimeoutSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public boolean isAvailable(AIModelConfig config) {
        if (config == null || !config.isLocalModel()) {
            return false;
        }

        try {
            log.debug("检查本地模型可用性: {}", config.getModelId());

            OllamaApi ollamaApi = createOllamaApi(config);

            // 尝试获取模型列表来验证连接
            OllamaApi.ListModelResponse response = ollamaApi.listModels();

            // 检查目标模型是否在列表中
            for (OllamaApi.Model model : response.models()) {
                if (config.getModelId().equals(model.name())) {
                    log.debug("本地模型可用: {}", config.getModelId());
                    return true;
                }
            }

            // 模型不存在时提供详细的解决建议
            log.warn("本地模型不存在: {}。请执行以下命令来下载模型: 'ollama pull {}' 或 'ollama run {}'",
                    config.getModelId(), config.getModelId(), config.getModelId());
            return false;

        } catch (Exception e) {
            String errorMessage = e.getMessage().toLowerCase();
            String detailedError = getDetailedErrorMessage(errorMessage, config);
            log.warn("本地模型不可用: {} - {}", config.getModelId(), detailedError);
            return false;
        }
    }

    @Override
    public String getSupportedModelType() {
        return ModelType.LOCAL.getCode();
    }

    @Override
    public String getName() {
        return "LocalModelCaller";
    }

    /**
     * 创建Ollama API客户端
     */
    private OllamaApi createOllamaApi(AIModelConfig config) {
        // 使用默认的Ollama地址，如果配置了api_url则使用配置的地址
        String baseUrl = config.getApiUrl() != null ? config.getApiUrl() : "http://localhost:11434";

        log.debug("创建Ollama API客户端: {}", baseUrl);

        return new OllamaApi(baseUrl);
    }

    /**
     * 获取详细的错误信息和解决建议
     */
    private String getDetailedErrorMessage(String errorMessage, AIModelConfig config) {
        if (errorMessage.contains("connection refused")) {
            return String.format("Ollama服务未启动。请确保Ollama服务正在运行，默认地址: %s",
                    config.getApiUrl() != null ? config.getApiUrl() : "http://localhost:11434");
        } else if (errorMessage.contains("connection timeout")) {
            return "连接Ollama服务超时。请检查网络连接或增加timeout配置";
        } else if (errorMessage.contains("unknown host")) {
            return "无法解析Ollama服务地址。请检查API URL配置是否正确";
        } else {
            return String.format("连接错误: %s。请检查Ollama服务状态和配置", errorMessage);
        }
    }

    /**
     * 根据异常类型确定错误代码
     */
    private String determineErrorCode(Exception e) {
        String errorMessage = e.getMessage().toLowerCase();

        if (errorMessage.contains("connection refused") || errorMessage.contains("connection timeout")) {
            return UnifiedModelFactory.ModelCallException.ErrorCodes.CONNECTION_ERROR;
        } else if (errorMessage.contains("timeout")) {
            return UnifiedModelFactory.ModelCallException.ErrorCodes.TIMEOUT_ERROR;
        } else if (errorMessage.contains("model not found") || errorMessage.contains("not found")) {
            return UnifiedModelFactory.ModelCallException.ErrorCodes.MODEL_NOT_FOUND;
        } else if (errorMessage.contains("invalid request") || errorMessage.contains("bad request")) {
            return UnifiedModelFactory.ModelCallException.ErrorCodes.INVALID_REQUEST;
        } else if (errorMessage.contains("server error") || errorMessage.contains("internal server error")) {
            return UnifiedModelFactory.ModelCallException.ErrorCodes.SERVER_ERROR;
        } else {
            return UnifiedModelFactory.ModelCallException.ErrorCodes.UNKNOWN_ERROR;
        }
    }

    /**
     * 转换消息角色
     */
    private OllamaApi.Message.Role convertRole(String role) {
        if (role == null) {
            return OllamaApi.Message.Role.USER;
        }

        return switch (role.toLowerCase()) {
            case "system" -> OllamaApi.Message.Role.SYSTEM;
            case "assistant" -> OllamaApi.Message.Role.ASSISTANT;
            default -> OllamaApi.Message.Role.USER;
        };
    }

    /**
     * 清理资源
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
package com.crypto.trade.service.model;

import com.crypto.trade.entity.AIModelConfig;
import com.crypto.trade.enums.ApiFormat;
import com.crypto.trade.enums.ModelType;
import com.crypto.trade.service.UnifiedModelFactory;
import com.crypto.trade.service.model.adapter.ClaudeAdapter;
import com.crypto.trade.service.model.adapter.OpenAiAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * RemoteModelCaller
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class RemoteModelCaller
        implements ModelCaller {

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final HttpClient httpClient;

    @Autowired
    OpenAiAdapter openAiAdapter;

    @Autowired
    ClaudeAdapter claudeAdapter;

    public RemoteModelCaller() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String call(String prompt, AIModelConfig config) throws UnifiedModelFactory.ModelCallException {
        if (config == null || !config.isRemoteModel()) {
            throw new UnifiedModelFactory.ModelCallException("配置无效或不是远端模型",
                    config != null ? config.getModelId() : "unknown");
        }

        try {
            log.debug("调用远端模型: {}, API格式: {}, prompt长度: {}",
                    config.getModelId(), config.getApiFormat(), prompt.length());

            // 根据API格式选择适配器
            ApiAdapter adapter = getAdapter(config.getApiFormat());

            // 构建请求
            var request = adapter.buildRequest(prompt, config);

            // 发送请求
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            // 解析响应
            String result = adapter.parseResponse(response);

            log.debug("远端模型调用成功: {}, 响应长度: {}", config.getModelId(), result.length());
            return result;

        } catch (UnifiedModelFactory.ModelCallException e) {
            // 直接重新抛出ModelCallException
            throw e;
        } catch (Exception e) {
            log.error("远端模型调用失败: {}", config.getModelId(), e);

            // 根据异常类型设置不同的错误代码
            String errorCode = determineErrorCode(e);
            throw new UnifiedModelFactory.ModelCallException(
                    "远端模型调用失败: " + e.getMessage(),
                    e,
                    errorCode,
                    config.getModelId()
            );
        }
    }

    @Override
    public String callWithMessages(List<UnifiedModelFactory.Message> messages, AIModelConfig config) throws UnifiedModelFactory.ModelCallException {
        if (config == null || !config.isRemoteModel()) {
            throw new UnifiedModelFactory.ModelCallException("配置无效或不是远端模型",
                    config != null ? config.getModelId() : "unknown");
        }

        if (CollectionUtils.isEmpty(messages)) {
            throw new UnifiedModelFactory.ModelCallException("消息数组不能为空", config.getModelId());
        }

        try {
            log.debug("调用远端模型(使用messages): {}, API格式: {}, 消息数: {}",
                    config.getModelId(), config.getApiFormat(), messages.size());

            // 记录发送的messages内容（用于调试）
            log.debug("【远端模型调用】发送的messages数组:");
            for (int i = 0; i < messages.size(); i++) {
                UnifiedModelFactory.Message msg = messages.get(i);
                String contentPreview = msg.getContent() != null && msg.getContent().length() > 100
                        ? msg.getContent().substring(0, 100) + "..."
                        : (msg.getContent() != null ? msg.getContent() : "null");
                log.debug("【远端模型调用】[{}] role: {}, content.length: {}, preview: {}",
                        i, msg.getRole(), msg.getContent() != null ? msg.getContent().length() : 0, contentPreview);
            }

            // 根据API格式选择适配器
            ApiAdapter adapter = getAdapter(config.getApiFormat());

            // 检查适配器是否支持messages格式
            if (!(adapter instanceof OpenAiAdapter)
                    && !(adapter instanceof ClaudeAdapter)) {
                throw new UnifiedModelFactory.ModelCallException(
                        "当前API格式不支持messages数组调用: " + config.getApiFormat(),
                        config.getModelId()
                );
            }

            // 构建请求
            HttpRequest request = openAiAdapter.buildMessagesRequest(messages, config);

            // 发送请求
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 解析响应
            String result = openAiAdapter.parseResponse(response);

            log.debug("【远端模型调用】响应长度: {}", result.length());
            if (result.isEmpty()) {
                log.error("【远端模型调用】严重错误：模型返回了空响应！");
                log.error("【远端模型调用】HTTP状态码: {}", response.statusCode());
                log.error("【远端模型调用】响应体: {}", response.body());
            }

            log.debug("远端模型调用成功(使用messages): {}, 响应长度: {}", config.getModelId(), result.length());
            return result;

        } catch (UnifiedModelFactory.ModelCallException e) {
            // 直接重新抛出ModelCallException
            throw e;
        } catch (Exception e) {
            log.error("远端模型调用失败(使用messages): {}", config.getModelId(), e);
            // 根据异常类型设置不同的错误代码
            String errorCode = determineErrorCode(e);
            throw new UnifiedModelFactory.ModelCallException("远端模型调用失败: " + e.getMessage(), e, errorCode,
                    config.getModelId());
        }
    }

    @Override
    public String callWithMessagesAndTools(List<UnifiedModelFactory.Message> messages, AIModelConfig config,
                                           List<UnifiedModelFactory.Tool> tools) throws UnifiedModelFactory.ModelCallException {
        if (config == null || !config.isRemoteModel()) {
            throw new UnifiedModelFactory.ModelCallException("配置无效或不是远端模型",
                    config != null ? config.getModelId() : "unknown");
        }

        if (CollectionUtils.isEmpty(messages)) {
            throw new UnifiedModelFactory.ModelCallException("消息数组不能为空", config.getModelId());
        }

        try {
            log.debug("调用远端模型(使用messages和tools): {}, API格式: {}, 消息数: {}, 工具数: {}",
                    config.getModelId(), config.getApiFormat(), messages.size(), tools != null ? tools.size() : 0);

            // 记录发送的messages内容（用于调试）
            log.debug("【远端模型调用】发送的messages数组:");
            for (int i = 0; i < messages.size(); i++) {
                UnifiedModelFactory.Message msg = messages.get(i);
                String contentPreview = msg.getContent() != null && msg.getContent().length() > 100
                        ? msg.getContent().substring(0, 100) + "..."
                        : (msg.getContent() != null ? msg.getContent() : "null");
                log.debug("【远端模型调用】[{}] role: {}, content.length: {}, preview: {}",
                        i, msg.getRole(), msg.getContent() != null ? msg.getContent().length() : 0, contentPreview);
            }

            // 根据API格式选择适配器
            ApiAdapter adapter = getAdapter(config.getApiFormat());

            // 检查适配器是否支持messages格式
            if (!(adapter instanceof OpenAiAdapter)
                    && !(adapter instanceof ClaudeAdapter)) {
                throw new UnifiedModelFactory.ModelCallException(
                        "当前API格式不支持messages数组调用: " + config.getApiFormat(),
                        config.getModelId()
                );
            }

            // 构建请求（带tools）
            HttpRequest request = openAiAdapter.buildMessagesRequest(messages, config, tools);

            // 发送请求
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 解析响应（期望处理function_call）
            String result = openAiAdapter.parseResponse(response, true);

            log.debug("【远端模型调用】响应长度: {}", result.length());
            if (result.isEmpty()) {
                log.error("【远端模型调用】严重错误：模型返回了空响应！");
                log.error("【远端模型调用】HTTP状态码: {}", response.statusCode());
                log.error("【远端模型调用】响应体: {}", response.body());
            }

            log.debug("远端模型调用成功(使用messages和tools): {}, 响应长度: {}", config.getModelId(), result.length());
            return result;

        } catch (UnifiedModelFactory.ModelCallException e) {
            // 直接重新抛出ModelCallException
            throw e;
        } catch (Exception e) {
            log.error("远端模型调用失败(使用messages和tools): {}", config.getModelId(), e);
            // 根据异常类型设置不同的错误代码
            String errorCode = determineErrorCode(e);
            throw new UnifiedModelFactory.ModelCallException("远端模型调用失败: " + e.getMessage(), e, errorCode,
                    config.getModelId());
        }
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
        if (config == null || !config.isRemoteModel()) {
            return false;
        }

        try {
            log.debug("检查远端模型可用性: {}", config.getModelId());

            // 根据API格式选择适配器
            ApiAdapter adapter = getAdapter(config.getApiFormat());

            // 验证配置
            if (!adapter.validateConfig(config)) {
                log.warn("远端模型配置无效: {}", config.getModelId());
                return false;
            }

            // 可以添加简单的连接测试
            // 这里简化处理，只验证配置有效性
            log.debug("远端模型可用: {}", config.getModelId());
            return true;

        } catch (Exception e) {
            log.warn("远端模型不可用: {}, 错误: {}", config.getModelId(), e.getMessage());
            return false;
        }
    }

    @Override
    public String getSupportedModelType() {
        return ModelType.REMOTE.getCode();
    }

    @Override
    public String getName() {
        return "RemoteModelCaller";
    }

    /**
     * 根据API格式获取适配器
     */
    private ApiAdapter getAdapter(String apiFormat) throws UnifiedModelFactory.ModelCallException {
        if (apiFormat == null) {
            throw new UnifiedModelFactory.ModelCallException("API格式不能为空", "unknown");
        }

        ApiFormat format = ApiFormat.fromCode(apiFormat);

        switch (format) {
            case OPENAI:
                return openAiAdapter;
            case CLAUDE:
                return claudeAdapter;
            case CUSTOM:
                // TODO: 实现CustomAdapter
                throw new UnifiedModelFactory.ModelCallException("自定义适配器暂未实现", apiFormat);
            default:
                throw new UnifiedModelFactory.ModelCallException("不支持的API格式: " + apiFormat, apiFormat);
        }
    }

    /**
     * 根据异常类型确定错误代码
     */
    private String determineErrorCode(Exception e) {
        String errorMessage = e.getMessage();
        if (errorMessage == null) {
            return UnifiedModelFactory.ModelCallException.ErrorCodes.UNKNOWN_ERROR;
        }
        errorMessage = errorMessage.toLowerCase();

        if (errorMessage.contains("connection refused") || errorMessage.contains("connection timeout") ||
                errorMessage.contains("chunked") || errorMessage.contains("eof")) {
            return UnifiedModelFactory.ModelCallException.ErrorCodes.CONNECTION_ERROR;
        } else if (errorMessage.contains("timeout")) {
            return UnifiedModelFactory.ModelCallException.ErrorCodes.TIMEOUT_ERROR;
        } else if (errorMessage.contains("401") || errorMessage.contains("unauthorized") ||
                errorMessage.contains("authentication")) {
            return UnifiedModelFactory.ModelCallException.ErrorCodes.AUTHENTICATION_ERROR;
        } else if (errorMessage.contains("429") || errorMessage.contains("rate limit")) {
            return UnifiedModelFactory.ModelCallException.ErrorCodes.RATE_LIMIT_ERROR;
        } else if (errorMessage.contains("404") || errorMessage.contains("not found")) {
            return UnifiedModelFactory.ModelCallException.ErrorCodes.MODEL_NOT_FOUND;
        } else if (errorMessage.contains("400") || errorMessage.contains("bad request")) {
            return UnifiedModelFactory.ModelCallException.ErrorCodes.INVALID_REQUEST;
        } else if (errorMessage.contains("500") || errorMessage.contains("server error")) {
            return UnifiedModelFactory.ModelCallException.ErrorCodes.SERVER_ERROR;
        } else {
            return UnifiedModelFactory.ModelCallException.ErrorCodes.UNKNOWN_ERROR;
        }
    }

    /**
     * 重试机制调用
     */
    public String callWithRetry(String prompt, AIModelConfig config) throws UnifiedModelFactory.ModelCallException {
        UnifiedModelFactory.ModelCallException lastException = null;
        int maxRetries = config.getRetryCount() != null ? config.getRetryCount() : 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return call(prompt, config);
            } catch (UnifiedModelFactory.ModelCallException e) {
                lastException = e;

                // 如果是认证错误或模型不存在错误，不进行重试
                if (UnifiedModelFactory.ModelCallException.ErrorCodes.AUTHENTICATION_ERROR.equals(e.getErrorCode()) ||
                        UnifiedModelFactory.ModelCallException.ErrorCodes.MODEL_NOT_FOUND.equals(e.getErrorCode()) ||
                        UnifiedModelFactory.ModelCallException.ErrorCodes.INVALID_REQUEST.equals(e.getErrorCode())) {
                    throw e;
                }

                if (attempt < maxRetries) {
                    log.warn("远端模型调用失败，第{}次重试: {}", attempt, config.getModelId());
                    try {
                        Thread.sleep(1000L * attempt); // 递增延迟
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new UnifiedModelFactory.ModelCallException("调用被中断", ie,
                                UnifiedModelFactory.ModelCallException.ErrorCodes.UNKNOWN_ERROR, config.getModelId());
                    }
                }
            }
        }

        throw lastException;
    }

    /**
     * 重试机制调用（使用messages数组格式）
     */
    public String callWithMessagesWithRetry(List<UnifiedModelFactory.Message> messages, AIModelConfig config) throws UnifiedModelFactory.ModelCallException {
        UnifiedModelFactory.ModelCallException lastException = null;
        int maxRetries = config.getRetryCount() != null ? config.getRetryCount() : 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return callWithMessages(messages, config);
            } catch (UnifiedModelFactory.ModelCallException e) {
                lastException = e;

                // 如果是认证错误或模型不存在错误，不进行重试
                if (UnifiedModelFactory.ModelCallException.ErrorCodes.AUTHENTICATION_ERROR.equals(e.getErrorCode()) ||
                        UnifiedModelFactory.ModelCallException.ErrorCodes.MODEL_NOT_FOUND.equals(e.getErrorCode()) ||
                        UnifiedModelFactory.ModelCallException.ErrorCodes.INVALID_REQUEST.equals(e.getErrorCode())) {
                    throw e;
                }

                if (attempt < maxRetries) {
                    log.warn("远端模型调用失败(使用messages)，第{}次重试: {}", attempt, config.getModelId());
                    try {
                        Thread.sleep(1000L * attempt); // 递增延迟
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new UnifiedModelFactory.ModelCallException("调用被中断", ie,
                                UnifiedModelFactory.ModelCallException.ErrorCodes.UNKNOWN_ERROR, config.getModelId());
                    }
                }
            }
        }

        throw lastException;
    }

    /**
     * 重试机制调用（使用messages数组和tools格式）
     */
    public String callWithMessagesAndToolsWithRetry(List<UnifiedModelFactory.Message> messages, AIModelConfig config,
                                                    List<UnifiedModelFactory.Tool> tools) throws UnifiedModelFactory.ModelCallException {
        UnifiedModelFactory.ModelCallException lastException = null;
        int maxRetries = config.getRetryCount() != null ? config.getRetryCount() : 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return callWithMessagesAndTools(messages, config, tools);
            } catch (UnifiedModelFactory.ModelCallException e) {
                lastException = e;

                // 如果是认证错误或模型不存在错误，不进行重试
                if (UnifiedModelFactory.ModelCallException.ErrorCodes.AUTHENTICATION_ERROR.equals(e.getErrorCode()) ||
                        UnifiedModelFactory.ModelCallException.ErrorCodes.MODEL_NOT_FOUND.equals(e.getErrorCode()) ||
                        UnifiedModelFactory.ModelCallException.ErrorCodes.INVALID_REQUEST.equals(e.getErrorCode())) {
                    throw e;
                }

                if (attempt < maxRetries) {
                    log.warn("远端模型调用失败(使用messages和tools)，第{}次重试: {}", attempt, config.getModelId());
                    try {
                        Thread.sleep(1000L * attempt); // 递增延迟
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new UnifiedModelFactory.ModelCallException("调用被中断", ie,
                                UnifiedModelFactory.ModelCallException.ErrorCodes.UNKNOWN_ERROR, config.getModelId());
                    }
                }
            }
        }

        throw lastException;
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
package com.crypto.trade.service.model.adapter;

import com.crypto.trade.entity.AIModelConfig;
import com.crypto.trade.enums.ApiFormat;
import com.crypto.trade.service.UnifiedModelFactory;
import com.crypto.trade.service.model.ApiAdapter;
import com.crypto.trade.util.ApiKeyUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAiAdapter
 * 适配器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class OpenAiAdapter implements ApiAdapter {

    private static final String CHAT_COMPLETIONS_ENDPOINT = "/v1/chat/completions";
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ApiKeyUtils apiKeyUtils;

    @Override
    public HttpRequest buildRequest(String prompt, AIModelConfig config) throws ApiAdapterException {
        if (!validateConfig(config)) {
            throw new ApiAdapterException("OpenAI配置验证失败",
                    ApiAdapterException.ErrorCodes.INVALID_REQUEST, "400");
        }

        try {
            String requestBody = buildRequestBody(prompt, config);
            String url = buildApiUrl(config);

            return HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKeyUtils.decrypt(config.getApiKey()))
                    .header("User-Agent", "Crypto-Trade/1.0")
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

        } catch (Exception e) {
            log.error("构建OpenAI请求失败: {}", e.getMessage(), e);
            throw new ApiAdapterException("构建请求失败: " + e.getMessage(),
                    ApiAdapterException.ErrorCodes.INVALID_REQUEST, "400");
        }
    }

    /**
     * 使用messages数组构建OpenAI请求
     *
     * @param messages 消息数组
     * @param config   模型配置
     * @return HTTP请求
     * @throws ApiAdapterException 构建请求失败时抛出
     */
    public HttpRequest buildMessagesRequest(List<UnifiedModelFactory.Message> messages, AIModelConfig config) throws ApiAdapterException {
        if (!validateConfig(config)) {
            throw new ApiAdapterException("OpenAI配置验证失败",
                    ApiAdapterException.ErrorCodes.INVALID_REQUEST, "400");
        }

        try {
            String requestBody = buildMessagesRequestBody(messages, config);
            String url = buildApiUrl(config);

            return HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKeyUtils.decrypt(config.getApiKey()))
                    .header("User-Agent", "Crypto-Trade/1.0")
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

        } catch (Exception e) {
            log.error("构建OpenAI请求(使用messages)失败: {}", e.getMessage(), e);
            throw new ApiAdapterException("构建请求失败: " + e.getMessage(),
                    ApiAdapterException.ErrorCodes.INVALID_REQUEST, "400");
        }
    }

    /**
     * 使用messages数组构建OpenAI请求体
     *
     * @param messages 消息数组
     * @param config   模型配置
     * @return JSON格式的请求体
     */
    public String buildMessagesRequestBody(List<UnifiedModelFactory.Message> messages, AIModelConfig config) {
        try {
            Map<String, Object> requestBody = new HashMap<>(8);

            // 模型配置
            requestBody.put("model", config.getModelId());

            // 转换消息格式
            List<Map<String, String>> openaiMessages = new ArrayList<>();
            for (UnifiedModelFactory.Message msg : messages) {
                Map<String, String> message = new HashMap<>();
                message.put("role", msg.getRole());
                message.put("content", msg.getContent());
                openaiMessages.add(message);
            }
            requestBody.put("messages", openaiMessages);

            // 可选参数
            if (config.getMaxTokens() != null && config.getMaxTokens() > 0) {
                requestBody.put("max_tokens", config.getMaxTokens());
            }

            // 设置温度参数（默认0.7）
            requestBody.put("temperature", 0.7);

            // 设置流式响应为false
            requestBody.put("stream", false);

            // 合并extra_body中的额外参数(会覆盖默认参数)
            if (config.getExtraBody() != null && !config.getExtraBody().trim().isEmpty()) {
                try {
                    // 创建允许注释的ObjectMapper,支持更宽松的JSON格式
                    ObjectMapper commentAwareMapper = objectMapper.copy()
                            .enable(JsonParser.Feature.ALLOW_COMMENTS)
                            .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
                            .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);

                    // 解析JSON格式的额外参数
                    @SuppressWarnings("unchecked")
                    Map<String, Object> extraParams = commentAwareMapper.readValue(
                            config.getExtraBody(),
                            Map.class
                    );
                    // 合并到请求体(extra_body中的参数会覆盖默认参数)
                    requestBody.putAll(extraParams);
                    log.debug("已合并extra_body参数: {}", extraParams.keySet());
                } catch (Exception e) {
                    log.error("解析extra_body失败,跳过额外参数: {}", e.getMessage());
                    // 解析失败不影响主流程,继续使用默认参数
                }
            }

            return objectMapper.writeValueAsString(requestBody);

        } catch (Exception e) {
            log.error("构建OpenAI请求体(使用messages)失败: {}", e.getMessage(), e);
            throw new RuntimeException("构建请求体失败", e);
        }
    }

    @Override
    public String parseResponse(HttpResponse<String> response) throws ApiAdapterException {
        if (!isSuccess(response)) {
            String errorInfo = handleOpenAiErrorResponse(response);
            throw new ApiAdapterException(errorInfo,
                    ApiAdapterException.ErrorCodes.MODEL_NOT_FOUND, String.valueOf(response.statusCode()));
        }

        try {
            String responseBody = response.body();
            JsonNode root = objectMapper.readTree(responseBody);

            // OpenAI格式的响应结构
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.path("message");
                JsonNode content = message.path("content");

                if (!content.isMissingNode()) {
                    return content.asText();
                }
            }

            log.warn("OpenAI响应格式异常，无法提取内容: {}", responseBody);
            throw new ApiAdapterException("响应格式异常，无法提取内容",
                    ApiAdapterException.ErrorCodes.PARSE_ERROR, "500");

        } catch (Exception e) {
            log.error("解析OpenAI响应失败: {}", e.getMessage(), e);
            throw new ApiAdapterException("解析响应失败: " + e.getMessage(),
                    ApiAdapterException.ErrorCodes.PARSE_ERROR, "500");
        }
    }

    @Override
    public String getSupportedFormat() {
        return ApiFormat.OPENAI.getCode();
    }

    @Override
    public boolean validateConfig(AIModelConfig config) {
        if (config == null) {
            return false;
        }

        // 检查必要字段
        if (config.getApiUrl() == null || config.getApiUrl().trim().isEmpty()) {
            log.warn("OpenAI配置缺少API URL");
            return false;
        }

        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            log.warn("OpenAI配置缺少API Key");
            return false;
        }

        if (config.getModelId() == null || config.getModelId().trim().isEmpty()) {
            log.warn("OpenAI配置缺少模型ID");
            return false;
        }

        return true;
    }

    @Override
    public String buildRequestBody(String prompt, AIModelConfig config) {
        try {
            Map<String, Object> requestBody = new HashMap<>();

            // 模型配置
            requestBody.put("model", config.getModelId());
            // 消息配置
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);
            requestBody.put("messages", new Object[]{message});

            // 可选参数
            if (config.getMaxTokens() != null && config.getMaxTokens() > 0) {
                requestBody.put("max_tokens", config.getMaxTokens());
            }

            // 设置温度参数（默认0.7）
            requestBody.put("temperature", 0.7);

            // 设置流式响应为false
            requestBody.put("stream", false);

//            // 合并extra_body中的额外参数(会覆盖默认参数)
            if (config.getExtraBody() != null && !config.getExtraBody().trim().isEmpty()) {
                try {
                    // 创建允许注释的ObjectMapper,支持更宽松的JSON格式
                    ObjectMapper commentAwareMapper = objectMapper.copy()
                            .enable(JsonParser.Feature.ALLOW_COMMENTS)
                            .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
                            .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);

                    // 解析JSON格式的额外参数
                    @SuppressWarnings("unchecked")
                    Map<String, Object> extraParams = commentAwareMapper.readValue(
                            config.getExtraBody(),
                            Map.class
                    );
                    // 合并到请求体(extra_body中的参数会覆盖默认参数)
                    requestBody.putAll(extraParams);
                    log.debug("已合并extra_body参数: {}", extraParams.keySet());
                } catch (Exception e) {
                    log.error("解析extra_body失败,跳过额外参数: {}", e.getMessage());
                    // 解析失败不影响主流程,继续使用默认参数
                }
            }

            return objectMapper.writeValueAsString(requestBody);

        } catch (Exception e) {
            log.error("构建OpenAI请求体失败: {}", e.getMessage(), e);
            throw new RuntimeException("构建请求体失败", e);
        }
    }

    /**
     * 获取OpenAI特有的请求头
     */
    public Map<String, String> getOpenAiHeaders(AIModelConfig config) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "Crypto-Trade/1.0");
        headers.put("Authorization", "Bearer " + apiKeyUtils.decrypt(config.getApiKey()));

        return headers;
    }

    /**
     * 构建API URL
     */
    private String buildApiUrl(AIModelConfig config) {
        String baseUrl = config.getApiUrl().trim();

        // 确保URL以/结尾
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        return baseUrl + CHAT_COMPLETIONS_ENDPOINT;
    }

    /**
     * 处理OpenAI特定的错误响应
     */
    public String handleOpenAiErrorResponse(HttpResponse<String> response) {
        try {
            String body = response.body();

            if (body == null || body.isEmpty()) {
                return handleDefaultErrorResponse(response);
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.path("error");

            if (!error.isMissingNode()) {
                String type = error.path("type").asText("unknown");
                String message = error.path("message").asText("未知错误");
                String code = error.path("code").asText("");

                // 特殊处理Model Not Exist错误
                if ("invalid_request_error".equals(type) &&
                        (message.toLowerCase().contains("model not exist") ||
                                message.toLowerCase().contains("model not found") ||
                                "model_not_found".equals(code))) {
                    return String.format("远端模型调用失败: 模型不存在。请检查模型名称是否正确，或在AI模型配置中添加该模型: %s", message);
                }

                return String.format("远端模型调用失败: %s", message);
            }

        } catch (Exception e) {
            log.debug("解析OpenAI错误响应失败，使用默认处理: {}", e.getMessage());
        }

        return handleDefaultErrorResponse(response);
    }

    /**
     * 处理默认错误响应
     */
    private String handleDefaultErrorResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        String body = response.body();

        switch (statusCode) {
            case 400:
                return "远端模型调用失败: 请求参数错误: " + extractErrorMessage(body);
            case 401:
                return "远端模型调用失败: API密钥无效或已过期，请检查API密钥配置";
            case 403:
                return "远端模型调用失败: API访问被拒绝，请检查权限和配置";
            case 404:
                return "远端模型调用失败: 模型不存在或API端点错误，请检查模型配置";
            case 429:
                return "远端模型调用失败: API调用频率超限，请稍后重试";
            case 500:
                return "远端模型调用失败: 服务器内部错误";
            case 502:
                return "远端模型调用失败: 网关错误";
            case 503:
                return "远端模型调用失败: 服务暂时不可用";
            default:
                return String.format("远端模型调用失败 (HTTP %d): %s", statusCode, extractErrorMessage(body));
        }
    }

    /**
     * 从响应体中提取错误信息
     */
    public String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return "无详细错误信息";
        }

        try {
            // 简单的JSON解析，提取message或error字段
            if (responseBody.contains("\"message\"")) {
                int start = responseBody.indexOf("\"message\":\"") + 10;
                int end = responseBody.indexOf("\"", start);
                if (end > start) {
                    return responseBody.substring(start, end);
                }
            } else if (responseBody.contains("\"error\"")) {
                int start = responseBody.indexOf("\"error\":\"") + 8;
                int end = responseBody.indexOf("\"", start);
                if (end > start) {
                    return responseBody.substring(start, end);
                }
            }
        } catch (Exception e) {
            // 解析失败，返回原始响应
        }

        return responseBody.length() > 100 ? responseBody.substring(0, 100) + "..." : responseBody;
    }

    /**
     * 获取模型信息
     */
    public Map<String, Object> getModelInfo(AIModelConfig config) throws ApiAdapterException {
        if (!validateConfig(config)) {
            throw new ApiAdapterException("配置验证失败",
                    ApiAdapterException.ErrorCodes.INVALID_REQUEST, "400");
        }

        try {
            String url = buildApiUrl(config).replace(CHAT_COMPLETIONS_ENDPOINT, "/v1/models");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiKeyUtils.decrypt(config.getApiKey()))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            // 这里需要发送HTTP请求，但为了简化，我们只返回基本信息
            Map<String, Object> modelInfo = new HashMap<>();
            modelInfo.put("model", config.getModelId());
            modelInfo.put("api_format", getSupportedFormat());
            modelInfo.put("provider", config.getProvider());

            return modelInfo;

        } catch (Exception e) {
            log.error("获取OpenAI模型信息失败: {}", e.getMessage(), e);
            throw new ApiAdapterException("获取模型信息失败: " + e.getMessage(),
                    ApiAdapterException.ErrorCodes.UNKNOWN_ERROR, "500");
        }
    }
}
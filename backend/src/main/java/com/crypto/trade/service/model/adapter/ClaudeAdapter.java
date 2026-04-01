package com.crypto.trade.service.model.adapter;

import com.crypto.trade.entity.AIModelConfig;
import com.crypto.trade.enums.ApiFormat;
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
import java.util.HashMap;
import java.util.Map;

/**
 * ClaudeAdapter
 * Claude API 适配器
 *
 * @author page
 * @date 2026-03-21
 */
@Slf4j
@Component
public class ClaudeAdapter
        implements ApiAdapter {

    private static final String MESSAGES_ENDPOINT = "/anthropic";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiKeyUtils apiKeyUtils;

    @Override
    public HttpRequest buildRequest(String prompt, AIModelConfig config) throws ApiAdapterException {
        if (!validateConfig(config)) {
            throw new ApiAdapterException("Claude配置验证失败",
                    ApiAdapterException.ErrorCodes.INVALID_REQUEST, "400");
        }

        try {
            String requestBody = buildRequestBody(prompt, config);
            String url = buildApiUrl(config);

            return HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKeyUtils.decrypt(config.getApiKey()))
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .header("User-Agent", "Crypto-Trade/1.0")
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

        } catch (Exception e) {
            log.error("构建Claude请求失败: {}", e.getMessage(), e);
            throw new ApiAdapterException("构建请求失败: " + e.getMessage(),
                    ApiAdapterException.ErrorCodes.INVALID_REQUEST, "400");
        }
    }

    @Override
    public String parseResponse(HttpResponse<String> response) throws ApiAdapterException {
        if (!isSuccess(response)) {
            String errorInfo = handleClaudeErrorResponse(response);
            throw new ApiAdapterException(errorInfo,
                    ApiAdapterException.ErrorCodes.MODEL_NOT_FOUND, String.valueOf(response.statusCode()));
        }

        try {
            String responseBody = response.body();
            JsonNode root = objectMapper.readTree(responseBody);

            // Claude 响应格式
            JsonNode content = root.path("content");
            if (content.isArray() && content.size() > 0) {
                JsonNode firstContent = content.get(0);
                if ("text".equals(firstContent.path("type").asText())) {
                    String text = firstContent.path("text").asText();
                    if (!text.isEmpty()) {
                        return text;
                    }
                }
            }

            log.warn("Claude响应格式异常，无法提取内容: {}", responseBody);
            throw new ApiAdapterException("响应格式异常，无法提取内容",
                    ApiAdapterException.ErrorCodes.PARSE_ERROR, "500");

        } catch (ApiAdapterException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析Claude响应失败: {}", e.getMessage(), e);
            throw new ApiAdapterException("解析响应失败: " + e.getMessage(),
                    ApiAdapterException.ErrorCodes.PARSE_ERROR, "500");
        }
    }

    @Override
    public String getSupportedFormat() {
        return ApiFormat.CLAUDE.getCode();
    }

    @Override
    public boolean validateConfig(AIModelConfig config) {
        if (config == null) {
            return false;
        }

        // 检查必要字段
        if (config.getApiUrl() == null || config.getApiUrl().trim().isEmpty()) {
            log.warn("Claude配置缺少API URL");
            return false;
        }

        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            log.warn("Claude配置缺少API Key");
            return false;
        }

        if (config.getModelId() == null || config.getModelId().trim().isEmpty()) {
            log.warn("Claude配置缺少模型ID");
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

            // max_tokens 是必填字段，Claude API 要求
            if (config.getMaxTokens() != null && config.getMaxTokens() > 0) {
                requestBody.put("max_tokens", config.getMaxTokens());
            } else {
                // 使用默认值
                requestBody.put("max_tokens", 4096);
            }

            // 设置温度参数（默认0.7）
            requestBody.put("temperature", 0.7);

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
                }
            }

            return objectMapper.writeValueAsString(requestBody);

        } catch (Exception e) {
            log.error("构建Claude请求体失败: {}", e.getMessage(), e);
            throw new RuntimeException("构建请求体失败", e);
        }
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

        return baseUrl + MESSAGES_ENDPOINT;
    }

    /**
     * 处理Claude特定的错误响应
     */
    public String handleClaudeErrorResponse(HttpResponse<String> response) {
        try {
            String body = response.body();

            if (body == null || body.isEmpty()) {
                return handleDefaultErrorResponse(response);
            }

            JsonNode root = objectMapper.readTree(body);

            // Claude 错误格式: {"error": {"type": "...", "message": "..."}}
            JsonNode error = root.path("error");

            if (!error.isMissingNode()) {
                String type = error.path("type").asText("unknown");
                String message = error.path("message").asText("未知错误");

                return String.format("Claude API调用失败: %s", message);
            }

        } catch (Exception e) {
            log.debug("解析Claude错误响应失败，使用默认处理: {}", e.getMessage());
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
                return "Claude API调用失败: 请求参数错误: " + extractErrorMessage(body);
            case 401:
                return "Claude API调用失败: API密钥无效或已过期，请检查API密钥配置";
            case 403:
                return "Claude API调用失败: API访问被拒绝，请检查权限和配置";
            case 404:
                return "Claude API调用失败: 模型不存在或API端点错误，请检查模型配置";
            case 429:
                return "Claude API调用失败: API调用频率超限，请稍后重试";
            case 500:
            case 529:
                return "Claude API调用失败: 服务器内部错误或服务暂时不可用";
            default:
                return String.format("Claude API调用失败 (HTTP %d): %s", statusCode, extractErrorMessage(body));
        }
    }

    /**
     * 从响应体中提取错误信息
     */
    @Override
    public String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return "无详细错误信息";
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                String message = error.path("message").asText();
                if (!message.isEmpty()) {
                    return message;
                }
            }
        } catch (Exception e) {
            // 解析失败，返回原始响应
        }

        return responseBody.length() > 100 ? responseBody.substring(0, 100) + "..." : responseBody;
    }
}

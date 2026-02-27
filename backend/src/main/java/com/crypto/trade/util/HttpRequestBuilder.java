package com.crypto.trade.util;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.DataFetchConfig;
import com.crypto.trade.service.auth.AuthService;
import com.crypto.trade.service.auth.AuthService.AuthValidationResult;
import com.crypto.trade.service.auth.AuthServiceFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * HttpRequestBuilder
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class HttpRequestBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    AuthServiceFactory authServiceFactory;

    /**
     * 设置HTTP方法
     */
    private static void setHttpMethod(HttpRequest.Builder builder, DataFetchConfig config) {
        switch (config.getHttpMethod().toUpperCase()) {
            case "GET":
                builder.GET();
                break;
            case "POST":
                // 暂时设置为空请求体，后续会在setRequestBodyOrParams中处理
                builder.POST(HttpRequest.BodyPublishers.noBody());
                break;
            case "PUT":
                builder.PUT(HttpRequest.BodyPublishers.noBody());
                break;
            case "DELETE":
                builder.DELETE();
                break;
            default:
                throw new IllegalArgumentException("不支持的HTTP方法: " + config.getHttpMethod());
        }
    }

    /**
     * 设置请求参数或请求体
     */
    private static void setRequestBodyOrParams(HttpRequest.Builder builder, DataFetchConfig config) {
        if (config.getRequestParams() == null || config.getRequestParams().trim().isEmpty()) {
            return;
        }
        try {
            // 解析JSON格式的请求参数
            Map<String, Object> params = parseRequestParams(config.getRequestParams());

            if (params.isEmpty()) {
                return;
            }
            String httpMethod = config.getHttpMethod().toUpperCase();
            switch (httpMethod) {
                case "GET":
                    // GET请求：将参数添加到URL查询字符串
                    setQueryParams(builder, params);
                    break;
                case "POST":
                case "PUT":
                    // POST/PUT请求：将参数作为JSON请求体
                    setJsonRequestBody(builder, params);
                    break;
                default:
                    log.warn("HTTP方法 {} 不支持请求参数", httpMethod);
                    break;
            }
            log.debug("设置请求参数 - 方法: {}, 参数数量: {}", httpMethod, params.size());
        } catch (Exception e) {
            log.error("处理请求参数失败: {}", e.getMessage(), e);
            throw new RuntimeException("处理请求参数失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析请求参数JSON
     */
    private static Map<String, Object> parseRequestParams(String requestParamsJson) {
        try {
            if (null == requestParamsJson || requestParamsJson.trim().isEmpty()) {
                return new HashMap<>();
            }
            return objectMapper.readValue(requestParamsJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("解析请求参数JSON失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 设置查询参数（用于GET请求）
     */
    private static void setQueryParams(HttpRequest.Builder builder, Map<String, Object> params) {
        try {
            StringBuilder queryString = new StringBuilder();
            String uri = builder.build().uri().toString();
            // 检查URL是否已有查询参数
            if (uri.contains("?")) {
                // 如果已有查询参数，需要重新构建URI
                String[] parts = uri.split("\\?", 2);
                String baseUrl = parts[0];
                String existingQuery = parts.length > 1 ? parts[1] : "";
                queryString.append(existingQuery);
                if (!existingQuery.isEmpty()) {
                    queryString.append("&");
                }
            } else {
                // 移除当前URI并重新构建
                String baseUrl = uri.split("\\?", 2)[0];
                queryString = new StringBuilder();
                uri = baseUrl;
            }
            // 添加新参数
            boolean firstParam = queryString.length() == 0 || !queryString.toString().contains("=");
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (!firstParam) {
                    queryString.append("&");
                }
                queryString.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append("=")
                        .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
                firstParam = false;
            }
            // 重新设置URI
            String newUri = uri + "?" + queryString;
            builder.uri(URI.create(newUri));
        } catch (Exception e) {
            log.error("设置查询参数失败: {}", e.getMessage());
            throw new RuntimeException("设置查询参数失败: " + e.getMessage(), e);
        }
    }

    /**
     * 设置JSON请求体（用于POST/PUT请求）
     */
    private static void setJsonRequestBody(HttpRequest.Builder builder, Map<String, Object> params) {
        try {
            String jsonBody = objectMapper.writeValueAsString(params);
            builder.method(builder.build().method(), HttpRequest.BodyPublishers.ofString(jsonBody));
        } catch (JsonProcessingException e) {
            log.error("序列化JSON请求体失败: {}", e.getMessage());
            throw new RuntimeException("序列化JSON请求体失败: " + e.getMessage(), e);
        }
    }

    /**
     * 掩码API Key用于日志输出
     */
    private static String maskApiKey(String apiKey) {
        if (null == apiKey || apiKey.length() <= 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "***" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 构建HTTP请求
     *
     * @param config   数据获取配置
     * @param authInfo 认证信息（可选）
     * @return HttpRequest
     */
    public HttpRequest buildRequest(DataFetchConfig config, ApiKey authInfo) {
        try {
            String url = config.getCexBaseUrl() + config.getApiPath();
            log.debug("构建HTTP请求 - URL: {}, 方法: {}", url, config.getHttpMethod());
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(DEFAULT_TIMEOUT);
            // 设置HTTP方法
            setHttpMethod(builder, config);
            // 设置请求参数/请求体
            setRequestBodyOrParams(builder, config);
            // 设置HTTP头
            setHeaders(builder, config, authInfo);
            return builder.build();
        } catch (Exception e) {
            log.error("构建HTTP请求失败", e);
            throw new RuntimeException("构建HTTP请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 设置HTTP头
     */
    private void setHeaders(HttpRequest.Builder builder, DataFetchConfig config, ApiKey authInfo) {
        // 设置Content-Type
        if ("POST".equalsIgnoreCase(config.getHttpMethod()) ||
                "PUT".equalsIgnoreCase(config.getHttpMethod())) {
            builder.header("Content-Type", "application/json; charset=UTF-8");
        }
        // 设置User-Agent
        builder.header("User-Agent", "CryptoTrade/1.0");
        // 设置认证信息
        if (config.getRequiresAuth() && null != authInfo) {
            setAuthHeadersWithService(builder, config, authInfo);
        }
    }

    /**
     * 使用鉴权服务设置认证头
     */
    private void setAuthHeadersWithService(HttpRequest.Builder builder, DataFetchConfig config, ApiKey authInfo) {
        try {
            // 验证鉴权配置
            AuthService authService = authServiceFactory.getAuthService(config.getSignatureClass());
            AuthValidationResult validationResult = authService.validateAuthConfig(config, authInfo);
            if (!validationResult.isValid()) {
                log.error("鉴权配置验证失败: {}", validationResult.getErrorMessage());
                throw new RuntimeException("鉴权配置验证失败: " + validationResult.getErrorMessage());
            }
            // 添加鉴权头
            authService.addAuthHeaders(builder, config, authInfo);
            log.debug("使用鉴权服务设置认证头完成 - 鉴权类型: {}", authService.getAuthType());
        } catch (Exception e) {
            log.error("设置鉴权头失败: {}", e.getMessage(), e);
            throw new RuntimeException("设置鉴权头失败: " + e.getMessage(), e);
        }
    }
}
package com.crypto.trade.service.model;

import com.crypto.trade.entity.AIModelConfig;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * ApiAdapter
 * 适配器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public interface ApiAdapter {

    /**
     * 构建HTTP请求
     *
     * @param prompt 用户提示词
     * @param config 模型配置
     * @return HTTP请求对象
     * @throws ApiAdapterException 构建异常
     */
    HttpRequest buildRequest(String prompt, AIModelConfig config) throws ApiAdapterException;

    /**
     * 解析HTTP响应
     *
     * @param response HTTP响应对象
     * @return 模型响应文本
     * @throws ApiAdapterException 解析异常
     */
    String parseResponse(HttpResponse<String> response) throws ApiAdapterException;

    /**
     * 获取支持的API格式
     *
     * @return API格式代码
     */
    String getSupportedFormat();

    /**
     * 验证配置是否有效
     *
     * @param config 模型配置
     * @return 是否有效
     */
    boolean validateConfig(AIModelConfig config);

    /**
     * 获取默认请求头
     *
     * @param config 模型配置
     * @return 请求头Map
     */
    default Map<String, String> getDefaultHeaders(AIModelConfig config) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "Crypto-Trade/1.0");
        return headers;
    }

    /**
     * 构建请求体
     *
     * @param prompt 用户提示词
     * @param config 模型配置
     * @return JSON格式的请求体字符串
     */
    String buildRequestBody(String prompt, AIModelConfig config);

    /**
     * 处理API错误响应
     *
     * @param response HTTP响应
     * @return 错误信息
     */
    default String handleErrorResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        String body = response.body();

        switch (statusCode) {
            case 400:
                return "请求参数错误: " + extractErrorMessage(body);
            case 401:
                return "API密钥无效或已过期";
            case 403:
                return "API访问被拒绝，请检查权限";
            case 404:
                return "模型不存在或API端点错误";
            case 429:
                return "API调用频率超限，请稍后重试";
            case 500:
                return "服务器内部错误";
            case 502:
                return "网关错误";
            case 503:
                return "服务暂时不可用";
            default:
                return String.format("API调用失败 (HTTP %d): %s", statusCode, extractErrorMessage(body));
        }
    }

    /**
     * 从响应体中提取错误信息
     *
     * @param responseBody 响应体
     * @return 错误信息
     */
    default String extractErrorMessage(String responseBody) {
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
     * 检查响应是否成功
     *
     * @param response HTTP响应
     * @return 是否成功
     */
    default boolean isSuccess(HttpResponse<String> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    /**
     * API适配器异常类
     */
    class ApiAdapterException extends Exception {
        private final String errorCode;
        private final String statusCode;

        public ApiAdapterException(String message, String errorCode, String statusCode) {
            super(message);
            this.errorCode = errorCode;
            this.statusCode = statusCode;
        }

        public ApiAdapterException(String message, Throwable cause, String errorCode, String statusCode) {
            super(message, cause);
            this.errorCode = errorCode;
            this.statusCode = statusCode;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getStatusCode() {
            return statusCode;
        }

        /**
         * 常用错误代码
         */
        public static class ErrorCodes {
            public static final String INVALID_REQUEST = "INVALID_REQUEST";
            public static final String AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";
            public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
            public static final String MODEL_NOT_FOUND = "MODEL_NOT_FOUND";
            public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
            public static final String SERVER_ERROR = "SERVER_ERROR";
            public static final String NETWORK_ERROR = "NETWORK_ERROR";
            public static final String PARSE_ERROR = "PARSE_ERROR";
            public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
        }
    }
}
package com.crypto.trade.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ResponseValidator
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class ResponseValidator {

    private static final Logger logger = LoggerFactory.getLogger(ResponseValidator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 检查响应是否为有效JSON格式
     *
     * @param response API响应内容
     * @return true如果是有效JSON，false如果不是
     */
    public static boolean isValidJson(String response) {
        if (null == response || response.trim().isEmpty()) {
            return false;
        }
        String trimmedResponse = response.trim();
        // 如果响应以<开头，可能是HTML错误页面
        if (trimmedResponse.startsWith("<")) {
            logger.debug("检测到HTML响应，非JSON格式: {}",
                    trimmedResponse.length() > 100 ? trimmedResponse.substring(0, 100) + "..." : trimmedResponse);
            return false;
        }
        // 检查是否以{或[开头，这是JSON的基本特征
        if (!(trimmedResponse.startsWith("{") || trimmedResponse.startsWith("["))) {
            logger.debug("响应不是以JSON对象或数组开头: {}",
                    trimmedResponse.length() > 50 ? trimmedResponse.substring(0, 50) + "..." : trimmedResponse);
            return false;
        }
        try {
            objectMapper.readTree(trimmedResponse);
            return true;
        } catch (JsonProcessingException e) {
            logger.debug("JSON解析失败，响应不是有效JSON格式: {}",
                    trimmedResponse.length() > 200 ? trimmedResponse.substring(0, 200) + "..." : trimmedResponse);
            return false;
        }
    }

    /**
     * 安全解析JSON响应
     * 如果响应不是有效JSON，返回null而不是抛出异常
     *
     * @param response API响应内容
     * @return 解析后的JsonNode，如果不是有效JSON则返回null
     */
    public static JsonNode safeParseJson(String response) {
        if (!isValidJson(response)) {
            return null;
        }
        try {
            return objectMapper.readTree(response);
        } catch (JsonProcessingException e) {
            logger.error("安全解析JSON失败，响应内容: {}",
                    response.length() > 500 ? response.substring(0, 500) + "..." : response, e);
            return null;
        }
    }

    /**
     * 从响应中提取错误消息
     * 如果响应是JSON格式，尝试提取msg字段；否则返回原始响应
     *
     * @param response API响应内容
     * @return 错误消息
     */
    public static String extractErrorMessage(String response) {
        if (null == response || response.trim().isEmpty()) {
            return "空响应";
        }
        JsonNode jsonNode = safeParseJson(response);
        if (null != jsonNode) {
            // 如果是JSON，提取错误消息
            String code = jsonNode.path("code").asText();
            String msg = jsonNode.path("msg").asText();
            if (!"0".equals(code) && !msg.isEmpty()) {
                return msg;
            }
        }
        // 如果不是JSON或没有错误消息，返回截断的原始响应
        String trimmedResponse = response.trim();
        if (trimmedResponse.length() > 100) {
            return trimmedResponse.substring(0, 100) + "...";
        }
        return trimmedResponse;
    }

    /**
     * 检查响应是否表示成功状态
     * 只有在响应是有效JSON且code为"0"时才返回true
     *
     * @param response API响应内容
     * @return true如果表示成功，false否则
     */
    public static boolean isSuccessResponse(String response) {
        JsonNode jsonNode = safeParseJson(response);
        if (null != jsonNode) {
            return "0".equals(jsonNode.path("code").asText());
        }
        return false;
    }

    /**
     * 从JSON响应中获取特定字段的值
     * 如果响应不是有效JSON或字段不存在，返回null
     *
     * @param response  API响应内容
     * @param fieldName 字段名
     * @return 字段值，如果不存在则返回null
     */
    public static String getFieldFromResponse(String response, String fieldName) {
        JsonNode jsonNode = safeParseJson(response);
        if (null != jsonNode) {
            return jsonNode.path(fieldName).asText(null);
        }
        return null;
    }
}
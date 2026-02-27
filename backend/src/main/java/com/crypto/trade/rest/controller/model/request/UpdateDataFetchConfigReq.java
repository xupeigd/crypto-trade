package com.crypto.trade.rest.controller.model.request;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UpdateDataFetchConfigReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateDataFetchConfigReq {

    /**
     * CEX基础URL
     */
    @Pattern(regexp = "^https?://.*", message = "CEX基础URL必须是有效的HTTP/HTTPS地址")
    String cexBaseUrl;

    /**
     * API路径
     */
    String apiPath;

    /**
     * HTTP方法
     */
    @Pattern(regexp = "^(GET|POST|PUT|DELETE)$", message = "HTTP方法必须是GET、POST、PUT或DELETE")
    String httpMethod;

    /**
     * 请求参数（JSON格式）
     */
    String requestParams;

    /**
     * 是否需要鉴权
     */
    Boolean requiresAuth;

    /**
     * 鉴权密钥ID
     */
    Long authKeyId;

    /**
     * 签名类全限定名
     */
    String signatureClass;

    /**
     * 数据处理类全限定名
     */
    String dataProcessorClass;

    /**
     * 目标DuckDB表名
     */
    @Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_]*$", message = "表名必须以字母或下划线开头，只能包含字母、数字和下划线")
    String targetDuckdbTable;

    /**
     * 响应字段映射（JSON格式）
     */
    String responseMapping;

    /**
     * 是否需要代理
     */
    Boolean requiresProxy;

    /**
     * 代理ID
     */
    Long proxyId;

    /**
     * 获取HTTP方法显示文本
     *
     * @return HTTP方法的大写形式
     */
    public String getHttpMethodText() {
        return null != httpMethod ? httpMethod.toUpperCase() : "";
    }

    /**
     * 判断是否需要鉴权
     *
     * @return true如果需要鉴权
     */
    public boolean needsAuthentication() {
        return Boolean.TRUE.equals(requiresAuth);
    }

    /**
     * 判断是否需要代理
     *
     * @return true如果需要代理
     */
    public boolean needsProxy() {
        return Boolean.TRUE.equals(requiresProxy);
    }

    /**
     * 获取完整的API URL
     *
     * @return 完整的API URL
     */
    public String getFullApiUrl() {
        if (null == cexBaseUrl || null == apiPath) {
            return "";
        }
        String baseUrl = cexBaseUrl.endsWith("/") ? cexBaseUrl.substring(0, cexBaseUrl.length() - 1) : cexBaseUrl;
        String path = apiPath.startsWith("/") ? apiPath : "/" + apiPath;
        return baseUrl + path;
    }

    /**
     * 验证更新参数逻辑
     *
     * @return 验证结果
     */
    public boolean isValid() {
        // 如果需要鉴权，必须提供authKeyId
        if (needsAuthentication() && null == authKeyId) {
            return false;
        }
        // 如果需要代理，必须提供proxyId
        return !needsProxy() || null != proxyId;
    }

    /**
     * 判断是否有更新内容
     *
     * @return true如果至少有一个字段不为null
     */
    public boolean hasUpdateFields() {
        return null != cexBaseUrl ||
                null != apiPath ||
                null != httpMethod ||
                null != requestParams ||
                null != requiresAuth ||
                null != authKeyId ||
                null != signatureClass ||
                null != dataProcessorClass ||
                null != targetDuckdbTable ||
                null != responseMapping ||
                null != requiresProxy ||
                null != proxyId;
    }
}
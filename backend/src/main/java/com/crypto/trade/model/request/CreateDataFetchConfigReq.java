package com.crypto.trade.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CreateDataFetchConfigReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateDataFetchConfigReq {

    /**
     * 任务ID
     */
    @NotNull(message = "任务ID不能为空")
    Long taskId;

    /**
     * CEX基础URL
     */
    @NotBlank(message = "CEX基础URL不能为空")
    @Pattern(regexp = "^https?://.*", message = "CEX基础URL必须是有效的HTTP/HTTPS地址")
    String cexBaseUrl;

    /**
     * API路径
     */
    @NotBlank(message = "API路径不能为空")
    String apiPath;

    /**
     * HTTP方法
     */
    @NotBlank(message = "HTTP方法不能为空")
    @Pattern(regexp = "^(GET|POST|PUT|DELETE)$", message = "HTTP方法必须是GET、POST、PUT或DELETE")
    String httpMethod;

    /**
     * 请求参数（JSON格式）
     */
    String requestParams;

    /**
     * 是否需要鉴权
     */
    @NotNull(message = "鉴权标识不能为空")
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
    @NotBlank(message = "数据处理类不能为空")
    String dataProcessorClass;

    /**
     * 目标DuckDB表名
     */
    @NotBlank(message = "目标DuckDB表名不能为空")
    @Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_]*$", message = "表名必须以字母或下划线开头，只能包含字母、数字和下划线")
    String targetDuckdbTable;

    /**
     * 响应字段映射（JSON格式）
     */
    String responseMapping;

    /**
     * 是否需要代理
     */
    @NotNull(message = "代理标识不能为空")
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
     * 验证参数逻辑
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
}
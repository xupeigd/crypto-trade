package com.crypto.trade.model;

import com.crypto.trade.entity.DataFetchConfig;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DataFetchConfigModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataFetchConfigModel {

    /**
     * 配置ID
     */
    Long configId;

    /**
     * 任务ID
     */
    Long taskId;

    /**
     * CEX基础URL
     */
    String cexBaseUrl;

    /**
     * API路径
     */
    String apiPath;

    /**
     * HTTP方法：GET/POST/PUT/DELETE
     */
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
     * 从Entity转换为Model
     *
     * @param entity DataFetchConfig实体
     * @return DataFetchConfigModel模型
     */
    public static DataFetchConfigModel fromEntity(DataFetchConfig entity) {
        if (null == entity) {
            return null;
        }

        return DataFetchConfigModel.builder()
                .configId(entity.getConfigId())
                .taskId(entity.getTaskId())
                .cexBaseUrl(entity.getCexBaseUrl())
                .apiPath(entity.getApiPath())
                .httpMethod(entity.getHttpMethod())
                .requestParams(entity.getRequestParams())
                .requiresAuth(entity.getRequiresAuth())
                .authKeyId(entity.getAuthKeyId())
                .signatureClass(entity.getSignatureClass())
                .dataProcessorClass(entity.getDataProcessorClass())
                .targetDuckdbTable(entity.getTargetDuckdbTable())
                .responseMapping(entity.getResponseMapping())
                .requiresProxy(entity.getRequiresProxy())
                .proxyId(entity.getProxyId())
                .build();
    }

    /**
     * 判断HTTP方法是否安全（GET）
     *
     * @return true如果是GET方法
     */
    public boolean isSafeMethod() {
        return "GET".equalsIgnoreCase(httpMethod);
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
     * 获取HTTP方法显示文本
     *
     * @return HTTP方法的大写形式
     */
    public String getHttpMethodText() {
        return null != httpMethod ? httpMethod.toUpperCase() : "";
    }

    /**
     * 判断是否为有效的配置
     *
     * @return true如果必要字段都不为空
     */
    public boolean isValid() {
        return null != taskId &&
                null != cexBaseUrl && !cexBaseUrl.trim().isEmpty() &&
                null != apiPath && !apiPath.trim().isEmpty() &&
                null != httpMethod && !httpMethod.trim().isEmpty() &&
                null != dataProcessorClass && !dataProcessorClass.trim().isEmpty() &&
                null != targetDuckdbTable && !targetDuckdbTable.trim().isEmpty();
    }
}
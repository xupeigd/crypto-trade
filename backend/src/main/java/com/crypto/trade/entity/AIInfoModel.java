package com.crypto.trade.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AIInfoModel
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
public class AIInfoModel {
    private Long configId;            // 配置ID
    private String modelId;           // 模型ID，如 "deepseek-r1:14b"
    private String displayName;        // 显示名称，如 "DeepSeek R1 14B"
    private String provider;           // 提供商，如 "deepseek"
    private Integer parameterSize;     // 参数规模，如 14
    private String description;        // 模型描述
    private Boolean isActive;          // 是否启用
    private Long maxTokens;           // 最大token数
    private Double costPerToken;      // 每token成本
    private Boolean defaultModel;      // 是否为默认模型

    // 新增字段
    private String modelType;          // 模型类型：LOCAL/REMOTE
    private String apiUrl;             // API调用地址（远端模型）
    private String apiKey;             // API密钥（脱敏显示）
    private String apiFormat;          // API格式：ollama/openai/claude/custom
    private Integer timeoutSeconds;    // 请求超时时间（秒）
    private Integer retryCount;        // 重试次数
    private Integer maxConcurrent;     // 最大并发请求数
    private String extraBody;          // 额外的请求体参数(JSON格式)
}
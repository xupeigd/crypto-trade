package com.crypto.trade.entity;

import com.crypto.trade.enums.ApiFormat;
import com.crypto.trade.enums.ModelType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AIModelConfig
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Entity
@Table(name = "t_ai_model_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AIModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long configId;

    @Column(name = "model_id", nullable = false, unique = true, length = 100)
    private String modelId;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "parameter_size")
    private Integer parameterSize;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "max_tokens")
    private Long maxTokens;

    @Column(name = "cost_per_token", precision = 10, scale = 6)
    private BigDecimal costPerToken;

    @Column(name = "default_model", nullable = false)
    private Boolean defaultModel = false;

    @Column(name = "model_type", nullable = false, length = 20)
    private String modelType = "LOCAL";

    @Column(name = "api_url", length = 500)
    private String apiUrl;

    @Column(name = "api_key", length = 500)
    private String apiKey;

    @Column(name = "api_format", length = 50)
    private String apiFormat = "ollama";

    @Column(name = "timeout_seconds", nullable = false)
    private Integer timeoutSeconds = 120;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 3;

    @Column(name = "max_concurrent", nullable = false)
    private Integer maxConcurrent = 5;

    /**
     * 额外的请求体参数(JSON格式)
     * 用于覆盖或补充默认API参数
     * 示例: {"temperature": 0.8, "top_p": 0.9, "thinking": {"budget_tokens": 10000}}
     */
    @Column(name = "extra_body", columnDefinition = "TEXT")
    private String extraBody;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @CreationTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
    }

    // 便利方法

    /**
     * 是否为本地模型
     */
    public boolean isLocalModel() {
        return ModelType.LOCAL.getCode().equals(modelType);
    }

    /**
     * 是否为远端模型
     */
    public boolean isRemoteModel() {
        return ModelType.REMOTE.getCode().equals(modelType);
    }

    /**
     * 获取模型类型枚举
     */
    public ModelType getModelTypeEnum() {
        return ModelType.fromCode(modelType);
    }

    /**
     * 设置模型类型
     */
    public void setModelType(ModelType modelType) {
        this.modelType = modelType.getCode();
    }

    /**
     * 获取API格式枚举
     */
    public ApiFormat getApiFormatEnum() {
        return apiFormat != null ? ApiFormat.fromCode(apiFormat) : null;
    }

    /**
     * 设置API格式
     */
    public void setApiFormat(ApiFormat apiFormat) {
        this.apiFormat = apiFormat != null ? apiFormat.getCode() : "ollama";
    }

    /**
     * 是否需要API密钥
     */
    public boolean requiresApiKey() {
        return isRemoteModel() && !ApiFormat.OLLAMA.getCode().equals(apiFormat);
    }

    /**
     * 是否需要API URL
     */
    public boolean requiresApiUrl() {
        return isRemoteModel();
    }
}
package com.crypto.trade.model;

import com.crypto.trade.entity.ApiKey;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZoneId;

/**
 * ApiKeyDecryptedModel
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
public class ApiKeyDecryptedModel {

    /**
     * 密钥ID
     */
    Long keyId;

    /**
     * CEX名称
     */
    String cexName;

    /**
     * 存储类型
     */
    String storageType;

    /**
     * 状态：active/inactive
     */
    String status;

    /**
     * 实盘交易标识：false=模拟交易，true=实盘交易
     */
    Boolean isLiveTrading;

    /**
     * 描述
     */
    String description;

    /**
     * 访问密钥 - 敏感字段
     */
    String accessKey;

    /**
     * 秘密密钥 - 敏感字段
     */
    String secretKey;

    /**
     * 通行短语 - 敏感字段（可选）
     */
    String passPhrase;

    /**
     * 创建时间
     */
    Long createdTime;

    /**
     * 更新时间
     */
    Long updatedTime;

    /**
     * 从Entity转换为DecryptedModel（包含敏感字段）
     *
     * @param entity CexApiKey实体（已解密）
     * @return CexKeyDecryptedModel模型
     */
    public static ApiKeyDecryptedModel fromEntity(ApiKey entity) {
        if (null == entity) {
            return null;
        }
        // 验证敏感字段是否存在
        if (null == entity.getAccessKey() || entity.getAccessKey().trim().isEmpty()) {
            throw new IllegalArgumentException("AccessKey cannot be null or empty for keyId: " + entity.getKeyId());
        }
        if (null == entity.getSecretKey() || entity.getSecretKey().trim().isEmpty()) {
            throw new IllegalArgumentException("SecretKey cannot be null or empty for keyId: " + entity.getKeyId());
        }

        // Log转换过程用于调试
        System.out.printf("转换DecryptedModel - KeyId: %d, AccessKey长度: %d, SecretKey长度: %d, PassPhrase: %s%n",
                entity.getKeyId(),
                entity.getAccessKey().length(),
                entity.getSecretKey().length(),
                entity.getPassPhrase() != null ? "exists (" + entity.getPassPhrase().length() + " chars)" : "null");
        ApiKeyDecryptedModel model = ApiKeyDecryptedModel.builder()
                .keyId(entity.getKeyId())
                .cexName(entity.getCexName())
                .storageType(entity.getStorageType().name())
                .status(entity.getStatus())
                .isLiveTrading(entity.getIsLiveTrading())
                .description(entity.getDescription())
                .accessKey(entity.getAccessKey())
                .secretKey(entity.getSecretKey())
                .passPhrase(entity.getPassPhrase())
                .createdTime(entity.getCreatedTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .updatedTime(entity.getUpdatedTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .build();
        // 验证Model构建结果
        if (null == model.getAccessKey() || model.getAccessKey().isEmpty()) {
            throw new IllegalStateException("Failed to build DecryptedModel - AccessKey is null for keyId: " + entity.getKeyId());
        }
        if (null == model.getSecretKey() || model.getSecretKey().isEmpty()) {
            throw new IllegalStateException("Failed to build DecryptedModel - SecretKey is null for keyId: " + entity.getKeyId());
        }
        return model;
    }

    /**
     * 判断密钥是否激活
     *
     * @return true如果状态为active
     */
    public boolean isActive() {
        return "active".equals(status);
    }

    /**
     * 判断是否为实盘交易
     *
     * @return true如果isLiveTrading为true
     */
    public boolean isLiveTradingEnabled() {
        return Boolean.TRUE.equals(isLiveTrading);
    }

    /**
     * 获取存储类型显示名称
     *
     * @return 存储类型的中文描述
     */
    public String getStorageTypeDisplayName() {
        if (null == storageType) {
            return "未知";
        }
        switch (storageType) {
            case "DB":
                return "数据库存储";
            case "ENV":
                return "环境变量存储";
            default:
                return "";
        }
    }

    /**
     * 获取状态显示名称
     *
     * @return 状态的中文描述
     */
    public String getStatusDisplayName() {
        if (null == status) {
            return "未知";
        }
        switch (status) {
            case "active":
                return "激活";
            case "inactive":
                return "未激活";
            default:
                return status;
        }
    }

    /**
     * 获取交易模式显示名称
     *
     * @return 交易模式的中文描述
     */
    public String getTradingModeDisplayName() {
        if (Boolean.TRUE.equals(isLiveTrading)) {
            return "实盘交易";
        } else {
            return "模拟交易";
        }
    }

    /**
     * 获取掩码后的访问密钥
     *
     * @return 掩码处理后的访问密钥
     */
    public String getMaskedAccessKey() {
        return maskSensitiveValue(accessKey);
    }

    /**
     * 获取掩码后的秘密密钥
     *
     * @return 掩码处理后的秘密密钥
     */
    public String getMaskedSecretKey() {
        return maskSensitiveValue(secretKey);
    }

    /**
     * 获取掩码后的通行短语
     *
     * @return 掩码处理后的通行短语
     */
    public String getMaskedPassPhrase() {
        return maskSensitiveValue(passPhrase);
    }

    /**
     * 敏感值掩码处理
     *
     * @param value 敏感值
     * @return 掩码处理后的值
     */
    String maskSensitiveValue(String value) {
        if (null == value || value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
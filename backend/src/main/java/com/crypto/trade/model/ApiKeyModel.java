package com.crypto.trade.model;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.StorageType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ApiKeyModel
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
public class ApiKeyModel {

    /**
     * 密钥ID
     */
    Long keyId;

    /**
     * API Key名称标识
     */
    String keyName;

    /**
     * CEX名称
     */
    String cexName;

    /**
     * 存储类型
     */
    StorageType storageType;

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
     * 创建时间
     */
    LocalDateTime createdTime;

    /**
     * 更新时间
     */
    LocalDateTime updatedTime;

    /**
     * 从Entity转换为Model（敏感字段不包含）
     *
     * @param entity CexApiKey实体
     * @return CexKeyModel模型
     */
    public static ApiKeyModel fromEntity(ApiKey entity) {
        if (null == entity) {
            return null;
        }

        return ApiKeyModel.builder()
                .keyId(entity.getKeyId())
                .keyName(entity.getKeyName())
                .cexName(entity.getCexName())
                .storageType(entity.getStorageType())
                .status(entity.getStatus())
                .isLiveTrading(entity.getIsLiveTrading())
                .description(entity.getDescription())
                .createdTime(entity.getCreatedTime())
                .updatedTime(entity.getUpdatedTime())
                .build();
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
            case DB:
                return "数据库存储";
            case ENV:
                return "环境变量存储";
            default:
                return storageType.name();
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
}
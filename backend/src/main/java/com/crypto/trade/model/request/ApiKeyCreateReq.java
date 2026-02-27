package com.crypto.trade.model.request;

import com.crypto.trade.entity.StorageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ApiKeyCreateReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiKeyCreateReq {

    /**
     * CEX名称
     */
    @NotBlank(message = "CEX名称不能为空")
    String cexName;

    /**
     * 访问密钥（敏感字段，仅用于创建）
     */
    @NotBlank(message = "访问密钥不能为空")
    String accessKey;

    /**
     * 秘密密钥（敏感字段，仅用于创建）
     */
    @NotBlank(message = "秘密密钥不能为空")
    String secretKey;

    /**
     * 通行短语（敏感字段，仅用于创建，某些CEX需要）
     */
    String passPhrase;

    /**
     * 存储类型
     */
    @NotNull(message = "存储类型不能为空")
    StorageType storageType;

    /**
     * 状态，默认为active
     */
    String status = "active";

    /**
     * 实盘交易标识，默认为false（模拟交易）
     */
    Boolean isLiveTrading = false;

    /**
     * 描述信息
     */
    String description;

    /**
     * 验证请求参数
     *
     * @return true如果参数有效
     */
    public boolean isValid() {
        return null != cexName && !cexName.trim().isEmpty()
                && null != accessKey && !accessKey.trim().isEmpty()
                && null != secretKey && !secretKey.trim().isEmpty()
                && null != storageType;
    }

    /**
     * 获取掩码后的访问密钥用于日志
     *
     * @return 掩码后的访问密钥
     */
    public String getMaskedAccessKey() {
        if (null == accessKey || accessKey.length() <= 8) {
            return "****";
        }
        return accessKey.substring(0, 4) + "****" + accessKey.substring(accessKey.length() - 4);
    }

    /**
     * 获取掩码后的秘密密钥用于日志
     *
     * @return 掩码后的秘密密钥
     */
    public String getMaskedSecretKey() {
        if (null == secretKey || secretKey.length() <= 8) {
            return "****";
        }
        return "****";
    }
}
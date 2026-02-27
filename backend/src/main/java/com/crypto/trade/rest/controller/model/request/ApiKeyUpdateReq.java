package com.crypto.trade.rest.controller.model.request;

import com.crypto.trade.entity.StorageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ApiKeyUpdateReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiKeyUpdateReq {

    /**
     * CEX名称
     */
    String cexName;

    /**
     * 存储类型
     */
    StorageType storageType;

    /**
     * 状态
     */
    String status;

    /**
     * 实盘交易标识
     */
    Boolean isLiveTrading;

    /**
     * 描述信息
     */
    String description;

    /**
     * 验证状态值是否有效
     *
     * @return true如果状态值有效
     */
    public boolean hasValidStatus() {
        return null == status || "active".equals(status) || "inactive".equals(status);
    }

    /**
     * 检查是否有需要更新的字段
     *
     * @return true如果有非空字段
     */
    public boolean hasUpdateFields() {
        return null != cexName || null != storageType || null != status
                || null != isLiveTrading || null != description;
    }
}
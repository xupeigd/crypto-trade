package com.crypto.trade.rest.controller.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ProxyServiceConfigResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProxyServiceConfigResponse {

    /**
     * 代理ID
     */
    Long proxyId;

    /**
     * 代理名称
     */
    String proxyName;

    /**
     * 代理类型
     */
    String proxyType;

    /**
     * 服务器主机
     */
    String serverHost;

    /**
     * 服务器端口
     */
    Integer serverPort;

    /**
     * 状态
     */
    String status;

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
     * 关联任务数量（额外字段）
     */
    Long associatedTaskCount;
}
package com.crypto.trade.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UpdateProxyServiceConfigReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateProxyServiceConfigReq {

    /**
     * 代理名称
     */
    @Pattern(regexp = "^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$", message = "代理名称只能包含字母、数字、下划线、横线和中文字符")
    String proxyName;

    /**
     * 代理类型
     */
    @Pattern(regexp = "^(HTTP|SOCKS5)$", message = "代理类型必须是HTTP或SOCKS5")
    String proxyType;

    /**
     * 服务器主机
     */
    String serverHost;

    /**
     * 服务器端口
     */
    @Min(value = 1, message = "服务器端口必须大于0")
    @Max(value = 65535, message = "服务器端口不能超过65535")
    Integer serverPort;

    /**
     * 状态
     */
    @Pattern(regexp = "^(active|inactive)$", message = "状态必须是active或inactive")
    String status;

    /**
     * 描述
     */
    String description;

}
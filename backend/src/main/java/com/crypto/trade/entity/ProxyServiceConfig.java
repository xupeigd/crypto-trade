package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ProxyServiceConfig
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Entity
@Table(name = "t_proxy_service_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProxyServiceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proxy_id")
    private Long proxyId;

    @Column(name = "proxy_name", nullable = false, length = 100)
    private String proxyName;

    @Column(name = "proxy_type", nullable = false, length = 20)
    private String proxyType; // HTTP, SOCKS5

    @Column(name = "server_host", nullable = false, length = 200)
    private String serverHost;

    @Column(name = "server_port", nullable = false)
    private Integer serverPort;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "active"; // active, inactive

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime = LocalDateTime.now();

    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime = LocalDateTime.now();
}
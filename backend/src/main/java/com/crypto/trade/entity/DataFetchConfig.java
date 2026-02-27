package com.crypto.trade.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DataFetchConfig
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Entity
@Table(name = "t_data_fetch_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataFetchConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long configId;

    @Column(name = "task_id", nullable = false, unique = true)
    private Long taskId;

    @Column(name = "cex_base_url", nullable = false, length = 200)
    private String cexBaseUrl;

    @Column(name = "api_path", nullable = false, length = 200)
    private String apiPath;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod; // GET, POST, PUT, DELETE

    @Column(name = "request_params", columnDefinition = "LONGTEXT")
    private String requestParams; // JSON格式存储请求参数

    @Column(name = "requires_auth", nullable = false)
    private Boolean requiresAuth = false;

    @Column(name = "auth_key_id")
    private Long authKeyId;

    @Column(name = "signature_class", length = 200)
    private String signatureClass; // 签名类全限定名

    @Column(name = "data_processor_class", nullable = false, length = 200)
    private String dataProcessorClass; // 数据处理类全限定名

    @Column(name = "target_duckdb_table", nullable = false, length = 100)
    private String targetDuckdbTable;

    @Column(name = "response_mapping", columnDefinition = "LONGTEXT")
    private String responseMapping; // JSON格式存储响应字段映射

    @Column(name = "requires_proxy", nullable = false)
    private Boolean requiresProxy = false;

    @Column(name = "proxy_id")
    private Long proxyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proxy_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "none", foreignKeyDefinition = ""))
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private ProxyServiceConfig proxyServiceConfig;
}
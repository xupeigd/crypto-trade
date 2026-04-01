package com.crypto.trade.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * FreqtradeConfig
 * Freqtrade配置实体类
 *
 * @author page
 * @date 2026-03-22
 */

@Entity
@Table(name = "t_freqtrade_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class FreqtradeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 配置名称
     */
    @Column(name = "config_name", nullable = false, length = 100)
    private String configName;

    /**
     * 启动模式: PROCESS/DOCKER
     */
    @Column(name = "startup_mode", nullable = false, length = 20)
    private String startupMode;

    /**
     * 进程启动脚本路径
     */
    @Column(name = "process_path", length = 500)
    private String processPath;

    /**
     * Docker镜像名
     */
    @Column(name = "docker_image", length = 200)
    private String dockerImage;

    /**
     * 用户数据目录（包含配置文件、策略文件、数据库等）
     */
    @Column(name = "user_data_dir", length = 500)
    private String userDataDir;

    /**
     * Freqtrade API地址
     */
    @Column(name = "api_host", length = 100)
    @Builder.Default
    private String apiHost = "127.0.0.1";

    /**
     * 端口范围-最小端口
     */
    @Column(name = "port_range_min")
    @Builder.Default
    private Integer portRangeMin = 8081;

    /**
     * 端口范围-最大端口
     */
    @Column(name = "port_range_max")
    @Builder.Default
    private Integer portRangeMax = 8999;

    /**
     * 是否启用
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
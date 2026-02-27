package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ApiKey
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_cex_api_keys")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "key_id")
    private Long keyId;

    @Column(name = "cex_name", nullable = false, length = 50)
    private String cexName;

    @Column(name = "access_key", nullable = false, length = 200)
    private String accessKey;

    @Column(name = "secret_key", nullable = false, length = 200)
    private String secretKey;

    @Column(name = "pass_phrase", length = 200)
    private String passPhrase;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 10)
    private StorageType storageType = StorageType.DB;

    @Column(name = "status", length = 20)
    private String status = "active"; // active, inactive

    @Column(name = "is_live_trading", nullable = false)
    private Boolean isLiveTrading = false; // 实盘交易标识：false=模拟交易，true=实盘交易

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();

    @Column(name = "updated_time")
    private LocalDateTime updatedTime = LocalDateTime.now();
}
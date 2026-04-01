package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_cex_proxy_bindings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CexProxyBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "binding_id")
    private Long bindingId;

    @Column(name = "cex_name", nullable = false, unique = true, length = 50)
    private String cexName;

    @Column(name = "proxy_id", nullable = false)
    private Long proxyId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "active";

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime = LocalDateTime.now();

    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime = LocalDateTime.now();

}

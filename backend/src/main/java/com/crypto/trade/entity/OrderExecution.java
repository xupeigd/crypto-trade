package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OrderExecution
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_order_executions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "order_id", nullable = false, length = 50)
    private String orderId;

    @Column(name = "execution_id", length = 50)
    private String executionId;

    @Column(name = "trade_id", length = 50)
    private String tradeId;

    @Column(name = "exec_price", precision = 38, scale = 8)
    private BigDecimal execPrice;

    @Column(name = "exec_sz", precision = 38, scale = 8)
    private BigDecimal execSz;

    @Column(name = "exec_value", precision = 38, scale = 8)
    private BigDecimal execValue;

    @Column(name = "exec_fee", precision = 38, scale = 8)
    private BigDecimal execFee;

    @Column(name = "exec_fee_ccy", length = 10)
    private String execFeeCcy;

    @Column(name = "exec_type", length = 20)
    private String execType;

    @Column(name = "exec_ts")
    private LocalDateTime execTs;

    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createdTime = LocalDateTime.now();
    }
}
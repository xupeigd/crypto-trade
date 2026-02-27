package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * RiskModeHistory
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_risk_mode_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskModeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_mode", nullable = false, length = 20)
    private RiskMode oldMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_mode", nullable = false, length = 20)
    private RiskMode newMode;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Column(name = "operator_info", length = 200)
    private String operatorInfo;

    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();

    /**
     * 便利构造方法
     */
    public RiskModeHistory(RiskMode oldMode, RiskMode newMode, String changeReason, String operatorInfo) {
        this.oldMode = oldMode;
        this.newMode = newMode;
        this.changeReason = changeReason;
        this.operatorInfo = operatorInfo;
        this.createdTime = LocalDateTime.now();
    }
}
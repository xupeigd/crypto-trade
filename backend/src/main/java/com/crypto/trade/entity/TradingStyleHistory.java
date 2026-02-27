package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * TradingStyleHistory
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_trading_style_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradingStyleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_style", nullable = false, length = 20)
    private TradingStyle oldStyle;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_style", nullable = false, length = 20)
    private TradingStyle newStyle;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Column(name = "operator_info", length = 200)
    private String operatorInfo;

    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();

    /**
     * 便利构造方法
     */
    public TradingStyleHistory(TradingStyle oldStyle, TradingStyle newStyle, String changeReason, String operatorInfo) {
        this.oldStyle = oldStyle;
        this.newStyle = newStyle;
        this.changeReason = changeReason;
        this.operatorInfo = operatorInfo;
        this.createdTime = LocalDateTime.now();
    }
}
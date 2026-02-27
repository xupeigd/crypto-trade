package com.crypto.trade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * RiskControlConfig
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_risk_control_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskControlConfig {

    /**
     * 配置ID，固定为1
     */
    @Id
    @Column(name = "config_id")
    private Long configId = 1L;

    /**
     * 当前风控模式
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_risk_mode", nullable = false, length = 20)
    private RiskMode currentRiskMode;

    /**
     * 当前交易风格
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_trading_style", nullable = false, length = 20)
    private TradingStyle currentTradingStyle;

    /**
     * 默认风控模式
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "default_risk_mode", nullable = false, length = 20)
    private RiskMode defaultRiskMode;

    /**
     * 默认交易风格
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "default_trading_style", nullable = false, length = 20)
    private TradingStyle defaultTradingStyle;

    /**
     * 更新时间
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime = LocalDateTime.now();

    /**
     * 便利构造方法
     */
    public RiskControlConfig(RiskMode currentRiskMode, TradingStyle currentTradingStyle,
                             RiskMode defaultRiskMode, TradingStyle defaultTradingStyle) {
        this.configId = 1L;
        this.currentRiskMode = currentRiskMode;
        this.currentTradingStyle = currentTradingStyle;
        this.defaultRiskMode = defaultRiskMode;
        this.defaultTradingStyle = defaultTradingStyle;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 更新配置
     */
    public void updateConfig(RiskMode riskMode, TradingStyle tradingStyle) {
        this.currentRiskMode = riskMode;
        this.currentTradingStyle = tradingStyle;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 更新时间
     */
    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}
package com.crypto.trade.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "t_backtest_result")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class BacktestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "task_id", nullable = false, unique = true)
    private Long taskId;

    @Column(name = "total_profit_abs", precision = 18, scale = 8)
    private BigDecimal totalProfitAbs;

    @Column(name = "total_profit_pct", precision = 10, scale = 4)
    private BigDecimal totalProfitPct;

    @Column(name = "max_drawdown_abs", precision = 18, scale = 8)
    private BigDecimal maxDrawdownAbs;

    @Column(name = "max_drawdown_pct", precision = 10, scale = 4)
    private BigDecimal maxDrawdownPct;

    @Column(name = "win_rate", precision = 10, scale = 4)
    private BigDecimal winRate;

    @Column(name = "sharpe_ratio", precision = 10, scale = 4)
    private BigDecimal sharpeRatio;

    @Column(name = "total_trades")
    private Integer totalTrades;

    @Column(name = "result_json_path", length = 500)
    private String resultJsonPath;
}

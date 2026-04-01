package com.crypto.trade.rest.controller.model.response;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class BacktestResultResponse {

    private Long taskId;
    private String strategyName;
    private String freqtradeConfigName;
    private String timeRange;
    private String timeframe;
    private BigDecimal totalProfitAbs;
    private BigDecimal totalProfitPct;
    private BigDecimal maxDrawdownAbs;
    private BigDecimal maxDrawdownPct;
    private BigDecimal winRate;
    private Integer totalTrades;
    private BigDecimal sharpeRatio;

    // Extracted from Freqtrade JSON
    private List<Map<String, Object>> dailyProfit; // for equity curve
    private List<Map<String, Object>> trades; // trade history
    private String logs; // raw logs
}

package com.crypto.trade.rest.controller.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BacktestTaskResponse {
    private Long id;
    private String taskName;
    private Long freqtradeConfigId;
    private String freqtradeConfigName;
    private Long strategyConfigId;
    private String strategyName;
    private String timeRange;
    private String timeframe;
    private String status;
    private String errorMsg;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;
    
    // Result fields
    private BigDecimal totalProfitAbs;
    private BigDecimal totalProfitPct;
    private BigDecimal maxDrawdownAbs;
    private BigDecimal maxDrawdownPct;
    private BigDecimal winRate;
    private BigDecimal sharpeRatio;
    private Integer totalTrades;
}

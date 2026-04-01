package com.crypto.trade.rest.controller.model.request;

import lombok.Data;

@Data
public class BacktestTaskRequest {
    private String taskName;
    private Long freqtradeConfigId;
    private Long strategyConfigId;
    private String timeRange;
    private String timeframe;
}

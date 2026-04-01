package com.crypto.trade.service.conversation;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * KLineParameters
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KLineParameters
        extends ToolParameters {

    /**
     * 合约代码，如 BTC-USDT-SWAP
     */
    private String instId;

    /**
     * 时间周期，如 1m, 5m, 15m, 1h, 4h, 1d
     */
    private String timeframe;

    /**
     * 数据条数限制，默认100
     */
    private Integer limit = 100;

    /**
     * 分析类型：technical, trend, volatility
     */
    private String analysisType;

    @Override
    String getAction() {
        return "k_line";
    }
}
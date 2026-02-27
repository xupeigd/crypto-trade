package com.crypto.trade.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * UnifiedChartDataRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedChartDataRequest {

    /**
     * 合约ID，如 "BTC-USDT-SWAP"
     * 必填
     */
    private String instId;

    /**
     * 时间周期，如 "5m", "1H", "4H", "1D"
     * 必填
     */
    private String timeframe;

    /**
     * K线数据条数限制
     * 默认200，最大240
     */
    @Builder.Default
    private Integer limit = 200;

    /**
     * K线数据开始时间戳（毫秒）
     * 可选，如果不指定则从当前时间往前查询
     */
    private Long startMills;

    /**
     * 是否包含标记价格
     * 默认true
     */
    @Builder.Default
    private Boolean includeMarkPrice = true;

    /**
     * 是否包含资金费率
     * 默认false
     */
    @Builder.Default
    private Boolean includeFundingRate = false;

    /**
     * 技术指标列表
     * 可选值: "EMA", "SMA", "WMA", "RSI", "BOLL", "MACD", "KDJ", "CCI", "ATR", "OBV"
     * 为空则不计算技术指标
     */
    private List<String> indicators;

    /**
     * 各指标的周期参数映射
     * key: 指标名称（如 "EMA", "RSI"）
     * value: 周期列表（如 ["5", "20", "30"]）
     * <p>
     * 特殊说明:
     * - BOLL的periods格式为 ["周期_标准差"]，如 ["20_2.0", "30_1.5"]
     * - MACD的periods固定为 [fast, slow, signal]，如 ["12", "26", "9"]
     */
    private Map<String, List<String>> indicatorPeriods;
}

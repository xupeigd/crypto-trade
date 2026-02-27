package com.crypto.trade.dto.market;

import com.crypto.trade.dto.cex.model.CexFundingRate;
import com.crypto.trade.model.MarketCandleModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * UnifiedChartDataResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedChartDataResponse {

    /**
     * 合约ID
     */
    private String instId;

    /**
     * 时间周期
     */
    private String timeframe;

    /**
     * 请求的数据条数
     */
    private Integer limit;

    /**
     * K线数据列表
     */
    private List<MarketCandleModel> candles;

    /**
     * 技术指标计算结果
     * key: 指标名称（如 "RSI", "EMA", "BOLL"）
     * value: 指标数据（包含metricName和values）
     */
    private Map<String, IndicatorsDataDTO> indicators;

    /**
     * 当前标记价格（如果请求）
     */
    private BigDecimal markPrice;

    /**
     * 资金费率数据（如果请求）
     */
    private CexFundingRate fundingRate;

    /**
     * 响应时间戳
     */
    private Long timestamp;

    /**
     * 数据状态
     * - FULL: 完整数据
     * - PARTIAL: 部分数据（某些指标计算失败）
     * - CANDLES_ONLY: 仅有K线数据
     */
    @Builder.Default
    private String dataStatus = "FULL";

    /**
     * 错误信息（如果有）
     */
    private String errorMessage;
}

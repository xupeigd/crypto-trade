package com.crypto.trade.model;

import com.crypto.trade.dto.market.IndicatorsDataDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * CompleteChartDataModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompleteChartDataModel {

    String instId;

    String timeframe;

    Integer limit;

    List<MarketCandleModel> candles;

    Long timestamp;

    BigDecimal markPrice;

    Map<String, IndicatorsDataDTO> indicators;

}

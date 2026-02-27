package com.crypto.trade.rest.controller.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * RealTimePriceModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RealTimePriceModel {

    String instId;
    BigDecimal changePercent24H;
    BigDecimal changePercent4H;
    BigDecimal changePercent1H;
    Long updateTime;
    BigDecimal lastPrice;

}

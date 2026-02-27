package com.crypto.trade.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * MarkPriceModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkPriceModel {

    BigDecimal markPrice;

    Long timestamp;

}

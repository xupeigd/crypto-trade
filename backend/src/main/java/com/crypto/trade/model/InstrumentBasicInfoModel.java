package com.crypto.trade.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * InstrumentBasicInfoModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InstrumentBasicInfoModel {

    public String instId;           // 合约ID

    public String displayName;       // 显示名称

    public String baseAsset;         // 基础资产

    public String quoteAsset;        // 计价资产

    public String category;          // 合约类别（现货/永续合约）

    public Integer sortOrder;        // 排序

    // 新增字段：价格和涨跌幅信息
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public BigDecimal currentPrice;     // 当前价格

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public BigDecimal change24hPercent;  // 24H涨跌幅

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public BigDecimal change4hPercent;   // 4H涨跌幅

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public BigDecimal volume24hUsdt;     // 24小时USDT计价交易额

}

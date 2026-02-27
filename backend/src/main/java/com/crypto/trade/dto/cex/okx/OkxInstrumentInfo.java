package com.crypto.trade.dto.cex.okx;

import com.crypto.trade.dto.cex.utils.StringToBigDecimalDeserializer;
import com.crypto.trade.dto.cex.utils.StringToLongDeserializer;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * OkxInstrumentInfo
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OkxInstrumentInfo {

    /**
     * 别名（某些合约有）
     */
    String alias;

    /**
     * 合约类型：SPOT, SWAP, FUTURES, OPTION, MARGIN
     */
    @JsonProperty("instType")
    String instType;

    /**
     * 合约ID，如 BTC-USDT
     */
    @JsonProperty("instId")
    String instId;

    /**
     * 自定义合约代码（内部用，部分接口返回）
     */
    @JsonProperty("instIdCode")
    Long instIdCode;

    /**
     * 基础币种（如 BTC）
     */
    @JsonProperty("baseCcy")
    String baseCcy;

    /**
     * 计价币种（如 USDT）
     */
    @JsonProperty("quoteCcy")
    String quoteCcy;

    /**
     * 交易支持的计价币种列表（现货多币种交易用）
     */
    @JsonProperty("tradeQuoteCcyList")
    List<String> tradeQuoteCcyList;

    /**
     * 结算币种（合约用，如 USDT）
     */
    @JsonProperty("settleCcy")
    String settleCcy;

    /**
     * 合约面值（交割/永续合约）
     */
    @JsonProperty("ctVal")
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal ctVal;

    /**
     * 合约面值币种（USD/USDT/BTC 等）
     */
    @JsonProperty("ctValCcy")
    String ctValCcy;

    /**
     * 合约乘数（线性/倒挂）
     */
    @JsonProperty("ctMult")
    String ctMult;

    /**
     * 合约类型：linear（正向） / inverse（反向）
     */
    @JsonProperty("ctType")
    String ctType;

    /**
     * 期权类型：call / put（期权专用）
     */
    @JsonProperty("optType")
    String optType;

    /**
     * 行权价格（期权）
     */
    @JsonProperty("stk")
    String stk;

    /**
     * 上市时间（毫秒）
     */
    @JsonProperty("listTime")
    @JsonDeserialize(using = StringToLongDeserializer.class)
    Long listTime;

    /**
     * 到期时间（毫秒，期货/期权）
     */
    @JsonProperty("expTime")
    @JsonDeserialize(using = StringToLongDeserializer.class)
    Long expTime;

    /**
     * 杠杆倍数（现货保证金交易最大杠杆）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal lever;

    /**
     * 最小下单数量
     */
    @JsonProperty("minSz")
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal minSz;

    /**
     * 下单数量精度（lot size）
     */
    @JsonProperty("lotSz")
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal lotSz;

    /**
     * 价格精度
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    @JsonProperty("tickSz")
    BigDecimal tickSz;

    /**
     * 最大限价单数量
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    @JsonProperty("maxLmtSz")
    BigDecimal maxLmtSz;

    /**
     * 最大限价单金额（USD价值）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    @JsonProperty("maxLmtAmt")
    BigDecimal maxLmtAmt;

    /**
     * 最大市价单金额（USD价值）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    @JsonProperty("maxMktAmt")
    BigDecimal maxMktAmt;

    /**
     * 最大市价单数量
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    @JsonProperty("maxMktSz")
    BigDecimal maxMktSz;

    /**
     * 最大冰山单数量
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    @JsonProperty("maxIcebergSz")
    BigDecimal maxIcebergSz;

    /**
     * 最大TWAP单数量
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    @JsonProperty("maxTwapSz")
    BigDecimal maxTwapSz;

    /**
     * 最大条件单数量
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    @JsonProperty("maxTriggerSz")
    BigDecimal maxTriggerSz;

    /**
     * 最大止损单数量
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    @JsonProperty("maxStopSz")
    BigDecimal maxStopSz;

    /**
     * 合约状态：live / suspend / preopen 等
     */
    String state;

    /**
     * 交易规则类型：normal / amm 等
     */
    String ruleType;

    /**
     * 合约组ID（同一标的物不同到期日的分组）
     */
    String groupId;

    /**
     * 是否为期货结算模式（极少用）
     */
    Boolean futureSettlement;

    /**
     * 合约家族（交割合约用，如 BTC-USD-250627）
     */
    @JsonProperty("instFamily")
    String instFamily;

    /**
     * 标的指数（如 BTC-USD）
     */
    @JsonProperty("uly")
    String uly;

    /**
     * 类别：1=普通交易对
     */
    String category;

    /**
     * 开盘类型：call_auction = 集合竞价
     */
    String openType;

    /**
     * 集合竞价切换时间（毫秒）
     */
    @JsonDeserialize(using = StringToLongDeserializer.class)
    @JsonProperty("contTdSwTime")
    Long contTdSwTime;

    /**
     * 预开盘切换时间（毫秒）
     */
    @JsonDeserialize(using = StringToLongDeserializer.class)
    @JsonProperty("preMktSwTime")
    Long preMktSwTime;

    /**
     * 拍卖结束时间（毫秒）
     */
    @JsonDeserialize(using = StringToLongDeserializer.class)
    @JsonProperty("auctionEndTime")
    Long auctionEndTime;

}

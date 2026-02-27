package com.crypto.trade.dto.cex.okx;

import com.crypto.trade.dto.cex.utils.StringToBigDecimalDeserializer;
import com.crypto.trade.dto.cex.utils.StringToLongDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * OkxPosition
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OkxPosition {

    /**
     * 自动减仓标识：0=无，1=部分减仓，2=全部减仓等
     */
    String adl;

    /**
     * 可用持仓数量
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal availPos;

    /**
     * 平均开仓价格
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal avgPx;

    /**
     * 基础币余额（现货用）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal baseBal;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal baseBorrowed;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal baseInterest;

    /**
     * 强平价格（预估）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal bePx;

    String bizRefId;
    String bizRefType;

    /**
     * 创建时间（毫秒）
     */
    @JsonDeserialize(using = StringToLongDeserializer.class)
    long cTime;

    String ccy; // 保证金币种，如 USDT

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal clSpotInUseAmt;

    /**
     * 算法平仓订单列表（目前为空数组）
     */
    List<OkxAlgoOrder> closeOrderAlgo;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal deltaBS;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal deltaPA;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal fee;                    // 累计手续费（负数表示已支付）

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal fundingFee;             // 累计资金费用

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal gammaBS;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal gammaPA;

    String hedgedPos;

    /**
     * 指数价格
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal idxPx;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal imr;                    // 初始保证金

    String instId;   // 合约代码，如 ETH-USDT-SWAP
    String instType; // SWAP, FUTURES, OPTION 等

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal interest;

    /**
     * 最新成交价格
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal last;

    /**
     * 杠杆倍数
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal lever;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal liab;
    String liabCcy;

    /**
     * 强平罚金
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal liqPenalty;

    /**
     * 预估强平价
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal liqPx;

    /**
     * 保证金（逐仓）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal margin;

    /**
     * 标记价格
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal markPx;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal maxSpotInUseAmt;

    String mgnMode; // isolated / cross

    /**
     * 保证金率
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal mgnRatio;

    /**
     * 维持保证金
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal mmr;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal nonSettleAvgPx;

    /**
     * 持仓名义价值（USD）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal notionalUsd;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal optVal;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal pendingCloseOrdLiabVal;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal pnl;

    /**
     * 已实现盈亏
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal realizedPnl;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal spotInUseAmt;
    String spotInUseCcy;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal thetaBS;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal thetaPA;

    String tradeId;

    /**
     * 持仓更新时间
     */
    @JsonDeserialize(using = StringToLongDeserializer.class)
    long uTime;

    /**
     * 数据摄入时间（系统记录时间，毫秒）
     * 用于标识数据被系统采集的时间
     */
    @JsonDeserialize(using = StringToLongDeserializer.class)
    Long dataIngestionTime;

    /**
     * 开仓时最大持仓数量
     * 历史仓位特有字段
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal openMaxPos;

    /**
     * 平仓时的总持仓数量
     * 历史仓位特有字段
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal closeTotalPos;

    /**
     * 已结算盈亏
     * 历史仓位特有字段
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal settledPnl;

    /**
     * 盈亏比率
     * 历史仓位特有字段
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal pnlRatio;

    /**
     * 未实现盈亏（按标记价格计算）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal upl;

    /**
     * 未实现盈亏（按最新价计算）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal uplLastPx;

    /**
     * 未实现盈亏比率（标记价）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal uplRatio;

    /**
     * 未实现盈亏比率（最新价）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal uplRatioLastPx;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal usdPx;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal vegaBS;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal vegaPA;

    /**
     * 持仓数量（张）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal pos;

    String posCcy;

    String posId;

    String posSide; // long / short / net

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal quoteBal;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal quoteBorrowed;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal quoteInterest;

    String type;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal openAvgPx;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal closeAvgPx;

}

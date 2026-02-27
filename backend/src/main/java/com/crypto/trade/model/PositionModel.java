package com.crypto.trade.model;

import com.crypto.trade.dto.cex.model.CexAlgoOrder;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * PositionModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PositionModel {
    /**
     * 自动减仓标识：0=无，1=部分减仓，2=全部减仓等
     */
    String adl;
    /**
     * 可用持仓数量
     */
    BigDecimal availPos;
    /**
     * 平均开仓价格
     */
    BigDecimal avgPx;
    /**
     * 基础币余额（现货用）
     */
    BigDecimal baseBal;
    BigDecimal baseBorrowed;
    BigDecimal baseInterest;
    /**
     * 强平价格（预估）
     */
    BigDecimal bePx;
    String bizRefId;
    String bizRefType;
    /**
     * 创建时间（毫秒）
     */
    Long cTime;
    String ccy; // 保证金币种，如 USDT
    BigDecimal clSpotInUseAmt;
    /**
     * 算法平仓订单列表
     */
    List<CexAlgoOrder> closeOrderAlgo;
    BigDecimal deltaBS;
    BigDecimal deltaPA;
    /**
     * 累计手续费（负数表示已支付）
     */
    BigDecimal fee;
    /**
     * 累计资金费用
     */
    BigDecimal fundingFee;
    BigDecimal gammaBS;
    BigDecimal gammaPA;
    String hedgedPos;
    /**
     * 指数价格
     */
    BigDecimal idxPx;
    /**
     * 初始保证金
     */
    BigDecimal imr;
    /**
     * 合约代码，如 ETH-USDT-SWAP
     */
    String instId;
    /**
     * SWAP, FUTURES, OPTION 等
     */
    String instType;
    BigDecimal interest;
    /**
     * 最新成交价格
     */
    BigDecimal last;
    /**
     * 杠杆倍数
     */
    BigDecimal lever;
    BigDecimal liab;
    String liabCcy;
    /**
     * 强平罚金
     */
    BigDecimal liqPenalty;
    /**
     * 预估强平价
     */
    BigDecimal liqPx;
    /**
     * 保证金（逐仓）
     */
    BigDecimal margin;
    /**
     * 标记价格
     */
    BigDecimal markPx;
    BigDecimal maxSpotInUseAmt;
    /**
     * isolated / cross
     */
    String mgnMode;
    /**
     * 保证金率
     */
    BigDecimal mgnRatio;
    /**
     * 维持保证金
     */
    BigDecimal mmr;
    BigDecimal nonSettleAvgPx;
    /**
     * 持仓名义价值（USD）
     */
    BigDecimal notionalUsd;
    BigDecimal optVal;
    BigDecimal pendingCloseOrdLiabVal;
    BigDecimal pnl;
    /**
     * 已实现盈亏
     */
    BigDecimal realizedPnl;
    BigDecimal settledPnl;
    BigDecimal spotInUseAmt;
    String spotInUseCcy;
    BigDecimal thetaBS;
    BigDecimal thetaPA;
    String tradeId;
    /**
     * 持仓更新时间
     */
    long uTime;
    /**
     * 未实现盈亏（按标记价格计算）
     */
    BigDecimal upl;
    /**
     * 未实现盈亏（按最新价计算）
     */
    BigDecimal uplLastPx;
    /**
     * 未实现盈亏比率（标记价）
     */
    BigDecimal uplRatio;
    /**
     * 未实现盈亏比率（最新价）
     */
    BigDecimal uplRatioLastPx;
    BigDecimal usdPx;
    BigDecimal vegaBS;
    BigDecimal vegaPA;
    /**
     * 持仓数量（张）
     */
    BigDecimal pos;
    /**
     * 折算后持仓数量
     */
    BigDecimal position;
    String posCcy;
    String posId;
    /**
     * 持仓方向
     * <p>
     * long: 多头
     * short: 空头
     * net: 净仓
     */
    String posSide;
    BigDecimal quoteBal;
    BigDecimal quoteBorrowed;
    BigDecimal quoteInterest;
    Double estimatedLiquidationPx;
    Double marginRatioPercent;
    Long dataIngestionTime;
    List<PositionStopLossStrategyModel> positionStopLossStrategies;
    /**
     * 全仓止盈价格
     */
    String totalTakeProfitPrice;
    /**
     * 全仓止损价格
     */
    String totalStopLossPrice;

    BigDecimal openAvgPx;

    BigDecimal closeAvgPx;
}

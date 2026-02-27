package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * OkxPositionData
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
public class OkxPositionData {

    /**
     * 合约ID
     */
    @JsonProperty("instId")
    private String instId;

    /**
     * 合约类型
     */
    @JsonProperty("instType")
    private String instType;

    /**
     * 保证金模式
     */
    @JsonProperty("mgnMode")
    private String mgnMode;

    /**
     * 持仓ID
     */
    @JsonProperty("posId")
    private String posId;

    /**
     * 持仓方向
     */
    @JsonProperty("posSide")
    private String posSide;

    /**
     * 持仓数量
     */
    @JsonProperty("pos")
    private String pos;

    /**
     * 基础币余额
     */
    @JsonProperty("baseBal")
    private String baseBal;

    /**
     * 计价币余额
     */
    @JsonProperty("quoteBal")
    private String quoteBal;

    /**
     * 仓位币种
     */
    @JsonProperty("positionCcy")
    private String positionCcy;

    /**
     * 可平仓数量
     */
    @JsonProperty("availPos")
    private String availPos;

    /**
     * 开仓平均价
     */
    @JsonProperty("avgPx")
    private String avgPx;

    /**
     * 未实现盈亏
     */
    @JsonProperty("unsettledPnl")
    private String unsettledPnl;

    /**
     * 已实现盈亏
     */
    @JsonProperty("settlementPnl")
    private String settlementPnl;

    /**
     * 杠杆倍数
     */
    @JsonProperty("lever")
    private String lever;

    /**
     * 强平价
     */
    @JsonProperty("liqPx")
    private String liqPx;

    /**
     * 最新标记价格
     */
    @JsonProperty("markPx")
    private String markPx;

    /**
     * 初始保证金
     */
    @JsonProperty("imr")
    private String imr;

    /**
     * 保证金余额
     */
    @JsonProperty("margin")
    private String margin;

    /**
     * 保证金率
     */
    @JsonProperty("mgnRatio")
    private String mgnRatio;

    /**
     * 维持保证金
     */
    @JsonProperty("mmr")
    private String mmr;

    /**
     * 负债额
     */
    @JsonProperty("liab")
    private String liab;

    /**
     * 负债币种
     */
    @JsonProperty("liabCcy")
    private String liabCcy;

    /**
     * 利息
     */
    @JsonProperty("interest")
    private String interest;

    /**
     * 最新成交ID
     */
    @JsonProperty("tradeId")
    private String tradeId;

    /**
     * 以USD计价的持仓数量
     */
    @JsonProperty("notionalUsd")
    private String notionalUsd;

    /**
     * ADL排名
     */
    @JsonProperty("adlRank")
    private String adlRank;

    /**
     * 创建时间
     */
    @JsonProperty("cTime")
    private String cTime;

    /**
     * 更新时间
     */
    @JsonProperty("uTime")
    private String uTime;

    // === 便利方法 ===

    /**
     * 获取持仓数量（BigDecimal）
     */
    public BigDecimal getPositionAsBigDecimal() {
        try {
            return null != pos && !pos.isEmpty() ? new BigDecimal(pos) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取开仓平均价（BigDecimal）
     */
    public BigDecimal getAvgPriceAsBigDecimal() {
        try {
            return null != avgPx && !avgPx.isEmpty() ? new BigDecimal(avgPx) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取未实现盈亏（BigDecimal）
     */
    public BigDecimal getUnsettledPnlAsBigDecimal() {
        try {
            return null != unsettledPnl && !unsettledPnl.isEmpty() ? new BigDecimal(unsettledPnl) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取杠杆倍数（BigDecimal）
     */
    public BigDecimal getLeverageAsBigDecimal() {
        try {
            return null != lever && !lever.isEmpty() ? new BigDecimal(lever) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 判断是否有持仓
     */
    public boolean hasPosition() {
        BigDecimal position = getPositionAsBigDecimal();
        return position.compareTo(BigDecimal.ZERO) != 0;
    }
}
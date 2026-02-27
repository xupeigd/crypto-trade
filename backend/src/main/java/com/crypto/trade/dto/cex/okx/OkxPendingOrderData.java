package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * OkxPendingOrderData
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OkxPendingOrderData extends OkxOrderData {

    /**
     * 委托价格，仅适用于限价单
     */
    @JsonProperty("px")
    private String px;

    /**
     * 委托数量
     */
    @JsonProperty("sz")
    private String sz;

    /**
     * 订单方向
     * buy：买，sell：卖
     */
    @JsonProperty("side")
    private String side;

    /**
     * 订单类型
     */
    @JsonProperty("ordType")
    private String ordType;

    /**
     * 交易模式
     * isolated：逐仓，cross：全仓，cash：现金
     */
    @JsonProperty("tdMode")
    private String tdMode;

    /**
     * 成交价格
     */
    @JsonProperty("fillPx")
    private String fillPx;

    /**
     * 成交数量
     */
    @JsonProperty("fillSz")
    private String fillSz;

    /**
     * 成交金额
     */
    @JsonProperty("fillNotionalUsd")
    private String fillNotionalUsd;

    /**
     * 平仓方向
     * long：买平卖平仓，short：卖平买平仓
     */
    @JsonProperty("posSide")
    private String posSide;

    /**
     * 减仓模式
     */
    @JsonProperty("reduceOnly")
    private String reduceOnly;

    /**
     * 客户端订单ID
     */
    @JsonProperty("clOrdId")
    private String clOrdId;

    /**
     * 标签备注，字母（区分大小写）与数字的组合，可以是空格
     */
    @JsonProperty("tag")
    private String tag;

    /**
     * 快速成交类型
     * maker：只接受maker，taker：只接受taker，fok：全部成交或立即撤销，ioc：立即成交并撤销剩余
     */
    @JsonProperty("tgtCcy")
    private String tgtCcy;

    /**
     * 止盈触发价格
     */
    @JsonProperty("tpTriggerPx")
    private String tpTriggerPx;

    /**
     * 止盈委托价格
     */
    @JsonProperty("tpTriggerPxType")
    private String tpTriggerPxType;

    /**
     * 止盈委托类型
     */
    @JsonProperty("tpOrdPx")
    private String tpOrdPx;

    /**
     * 止损触发价格
     */
    @JsonProperty("slTriggerPx")
    private String slTriggerPx;

    /**
     * 止损委托价格
     */
    @JsonProperty("slTriggerPxType")
    private String slTriggerPxType;

    /**
     * 止损委托类型
     */
    @JsonProperty("slOrdPx")
    private String slOrdPx;

    // === 待成交订单特有便利方法 ===

    /**
     * 获取委托价格（BigDecimal）
     */
    public BigDecimal getPriceAsBigDecimal() {
        try {
            return null != px && !px.isEmpty() ? new BigDecimal(px) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取委托数量（BigDecimal）
     */
    public BigDecimal getSizeAsBigDecimal() {
        try {
            return null != sz && !sz.isEmpty() ? new BigDecimal(sz) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取成交价格（BigDecimal）
     */
    public BigDecimal getFillPriceAsBigDecimal() {
        try {
            return null != fillPx && !fillPx.isEmpty() ? new BigDecimal(fillPx) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取成交数量（BigDecimal）
     */
    public BigDecimal getFillSizeAsBigDecimal() {
        try {
            return null != fillSz && !fillSz.isEmpty() ? new BigDecimal(fillSz) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取成交金额（BigDecimal）
     */
    public BigDecimal getFillNotionalUsdAsBigDecimal() {
        try {
            return null != fillNotionalUsd && !fillNotionalUsd.isEmpty() ? new BigDecimal(fillNotionalUsd) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取止盈触发价格（BigDecimal）
     */
    public BigDecimal getTakeProfitTriggerPriceAsBigDecimal() {
        try {
            return null != tpTriggerPx && !tpTriggerPx.isEmpty() ? new BigDecimal(tpTriggerPx) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取止损触发价格（BigDecimal）
     */
    public BigDecimal getStopLossTriggerPriceAsBigDecimal() {
        try {
            return null != slTriggerPx && !slTriggerPx.isEmpty() ? new BigDecimal(slTriggerPx) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 判断是否为买单
     */
    public boolean isBuyOrder() {
        return "buy".equalsIgnoreCase(side);
    }

    /**
     * 判断是否为卖单
     */
    public boolean isSellOrder() {
        return "sell".equalsIgnoreCase(side);
    }

    /**
     * 判断是否为限价单
     */
    public boolean isLimitOrder() {
        return "limit".equalsIgnoreCase(ordType);
    }

    /**
     * 判断是否为市价单
     */
    public boolean isMarketOrder() {
        return "market".equalsIgnoreCase(ordType);
    }

    /**
     * 判断是否为只减仓
     */
    public boolean isReduceOnly() {
        return "true".equalsIgnoreCase(reduceOnly) || "1".equals(reduceOnly);
    }

    /**
     * 判断是否为逐仓模式
     */
    public boolean isIsolatedMode() {
        return "isolated".equalsIgnoreCase(tdMode);
    }

    /**
     * 判断是否为全仓模式
     */
    public boolean isCrossMode() {
        return "cross".equalsIgnoreCase(tdMode);
    }

    /**
     * 判断是否为现金模式
     */
    public boolean isCashMode() {
        return "cash".equalsIgnoreCase(tdMode);
    }

    /**
     * 判断是否为多头仓位
     */
    public boolean isLongPosition() {
        return "long".equalsIgnoreCase(posSide);
    }

    /**
     * 判断是否为空头仓位
     */
    public boolean isShortPosition() {
        return "short".equalsIgnoreCase(posSide);
    }

    /**
     * 计算成交比例
     */
    public BigDecimal getFillRatio() {
        BigDecimal totalSize = getSizeAsBigDecimal();
        BigDecimal filledSize = getFillSizeAsBigDecimal();

        if (totalSize.compareTo(BigDecimal.ZERO) > 0) {
            return filledSize.divide(totalSize, 4, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * 获取订单状态描述
     */
    public String getOrderStatusDescription() {
        BigDecimal fillRatio = getFillRatio();
        if (fillRatio.compareTo(BigDecimal.ZERO) == 0) {
            return "未成交";
        } else if (fillRatio.compareTo(BigDecimal.ONE) >= 0) {
            return "全部成交";
        } else {
            return "部分成交(" + fillRatio.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "%)";
        }
    }

    /**
     * 判断是否设置了止盈
     */
    public boolean hasTakeProfit() {
        return null != tpTriggerPx && !tpTriggerPx.isEmpty();
    }

    /**
     * 判断是否设置了止损
     */
    public boolean hasStopLoss() {
        return null != slTriggerPx && !slTriggerPx.isEmpty();
    }
}
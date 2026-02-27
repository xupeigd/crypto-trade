package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * OkxOrderData
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
public class OkxOrderData {

    /**
     * 订单ID
     */
    @JsonProperty("ordId")
    private String ordId;

    /**
     * 客户自定义订单ID
     */
    @JsonProperty("clOrdId")
    private String clOrdId;

    /**
     * 产品ID
     */
    @JsonProperty("instId")
    private String instId;

    /**
     * 订单类型
     */
    @JsonProperty("instType")
    private String instType;

    /**
     * 保证金币种
     */
    @JsonProperty("ccy")
    private String ccy;

    /**
     * 订单方向
     * buy：买，sell：卖
     */
    @JsonProperty("side")
    private String side;

    /**
     * 订单类型
     * market：市价单，limit：限价单，post_only：只做maker单，ioc：IOC订单，fok：FOK订单
     */
    @JsonProperty("ordType")
    private String ordType;

    /**
     * 委托数量
     */
    @JsonProperty("sz")
    private String sz;

    /**
     * 委托价格
     */
    @JsonProperty("px")
    private String px;

    /**
     * 交易模式
     * isolated：逐仓，cross：全仓，cash：现金
     */
    @JsonProperty("tdMode")
    private String tdMode;

    /**
     * 持仓方向
     * long：买方，short：卖方，net：净持仓模式
     */
    @JsonProperty("posSide")
    private String posSide;

    /**
     * 实际成交数量
     */
    @JsonProperty("accFillSz")
    private String accFillSz;

    /**
     * 成交均价
     */
    @JsonProperty("fillPx")
    private String fillPx;

    /**
     * 成交均价
     */
    @JsonProperty("avgPx")
    private String avgPx;

    /**
     * 手续费
     */
    @JsonProperty("fee")
    private String fee;

    /**
     * 手续费币种
     */
    @JsonProperty("feeCcy")
    private String feeCcy;

    /**
     * 手续费率
     */
    @JsonProperty("feeRate")
    private String feeRate;

    /**
     * 杠杆倍数
     */
    @JsonProperty("lever")
    private String lever;

    /**
     * 止盈触发价类型
     */
    @JsonProperty("tpTriggerPxType")
    private String tpTriggerPxType;

    /**
     * 止盈触发价
     */
    @JsonProperty("tpTriggerPx")
    private String tpTriggerPx;

    /**
     * 止盈委托价
     */
    @JsonProperty("tpOrdPx")
    private String tpOrdPx;

    /**
     * 止损触发价类型
     */
    @JsonProperty("slTriggerPxType")
    private String slTriggerPxType;

    /**
     * 止损触发价
     */
    @JsonProperty("slTriggerPx")
    private String slTriggerPx;

    /**
     * 止损委托价
     */
    @JsonProperty("slOrdPx")
    private String slOrdPx;

    /**
     * 快速成交类型
     * decrease：减仓，taker：Taker
     */
    @JsonProperty("quickMgnType")
    private String quickMgnType;

    /**
     * 订单状态
     * live：待成交，partially_filled：部分成交，filled：完全成交，canceled：已撤销
     */
    @JsonProperty("state")
    private String state;

    /**
     * 订单来源
     */
    @JsonProperty("source")
    private String source;

    /**
     * 减仓已完成数量
     */
    @JsonProperty("reduceOnlySz")
    private String reduceOnlySz;

    /**
     * 客户端取消的唯一订单ID
     */
    @JsonProperty("cancelSource")
    private String cancelSource;

    /**
     * 止盈止损订单修改次数
     */
    @JsonProperty(" amendOnStopType")
    private String amendOnStopType;

    /**
     * 客户端订单ID
     */
    @JsonProperty("clientId")
    private String clientId;

    /**
     * 下单时间
     */
    @JsonProperty("cTime")
    private String cTime;

    /**
     * 订单最后更新时间
     */
    @JsonProperty("uTime")
    private String uTime;

    /**
     * 请求ID
     */
    @JsonProperty("reqId")
    private String reqId;

    /**
     * 类别
     */
    @JsonProperty("category")
    private String category;

    /**
     * 使用抵扣金的数量
     */
    @JsonProperty("upl")
    private String upl;

    /**
     * 使用抵扣金的币种
     */
    @JsonProperty("uplCcy")
    private String uplCcy;

    // === 便利方法 ===

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
     * 获取成交数量（BigDecimal）
     */
    public BigDecimal getFilledSizeAsBigDecimal() {
        try {
            return null != accFillSz && !accFillSz.isEmpty() ? new BigDecimal(accFillSz) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取平均成交价格（BigDecimal）
     */
    public BigDecimal getAveragePriceAsBigDecimal() {
        try {
            return null != avgPx && !avgPx.isEmpty() ? new BigDecimal(avgPx) : BigDecimal.ZERO;
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
     * 获取手续费（BigDecimal）
     */
    public BigDecimal getFeeAsBigDecimal() {
        try {
            return null != fee && !fee.isEmpty() ? new BigDecimal(fee) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 判断是否为买单
     */
    public boolean isBuy() {
        return "buy".equals(side);
    }

    /**
     * 判断是否为卖单
     */
    public boolean isSell() {
        return "sell".equals(side);
    }

    /**
     * 判断是否为市价单
     */
    public boolean isMarketOrder() {
        return "market".equals(ordType);
    }

    /**
     * 判断是否为限价单
     */
    public boolean isLimitOrder() {
        return "limit".equals(ordType);
    }

    /**
     * 判断是否为完全成交
     */
    public boolean isFullyFilled() {
        return "filled".equals(state);
    }

    /**
     * 判断是否为部分成交
     */
    public boolean isPartiallyFilled() {
        return "partially_filled".equals(state);
    }

    /**
     * 判断是否为待成交
     */
    public boolean isLive() {
        return "live".equals(state);
    }

    /**
     * 判断是否已撤销
     */
    public boolean isCanceled() {
        return "canceled".equals(state);
    }

    /**
     * 获取成交比例
     */
    public BigDecimal getFillRatio() {
        BigDecimal total = getSizeAsBigDecimal();
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            return getFilledSizeAsBigDecimal()
                    .divide(total, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        return BigDecimal.ZERO;
    }

    /**
     * 获取剩余未成交数量
     */
    public BigDecimal getRemainingSize() {
        return getSizeAsBigDecimal().subtract(getFilledSizeAsBigDecimal());
    }

    /**
     * 获取下单时间（Instant）
     */
    public Instant getCreateTime() {
        try {
            return null != cTime && !cTime.isEmpty() ? Instant.ofEpochMilli(Long.parseLong(cTime)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取更新时间（Instant）
     */
    public Instant getUpdateTime() {
        try {
            return null != uTime && !uTime.isEmpty() ? Instant.ofEpochMilli(Long.parseLong(uTime)) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
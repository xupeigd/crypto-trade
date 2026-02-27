package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * OkxAccountConfigData
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OkxAccountConfigData {

    /**
     * 当前杠杆倍数
     */
    @JsonProperty("lev")
    private String lev;

    /**
     * 风控汇率类型
     */
    @JsonProperty("ipVal")
    private String ipVal;

    /**
     * 全仓抵押模式下，币种计价方式
     */
    @JsonProperty("mgnIsoMode")
    private String mgnIsoMode;

    /**
     * 穿透抵押模式下，币种计价方式
     */
    @JsonProperty("ktmIsoMode")
    private String ktmIsoMode;

    /**
     * 现货对冲模式
     */
    @JsonProperty("spotOffsetType")
    private String spotOffsetType;

    /**
     * 盈亏计算方式
     */
    @JsonProperty("pnlIsoMode")
    private String pnlIsoMode;

    /**
     * 一键自动借币
     */
    @JsonProperty("autoLoan")
    private String autoLoan;

    /**
     * 一键自动还币
     */
    @JsonProperty("autoRepay")
    private String autoRepay;

    /**
     * 最大交易倍数
     */
    @JsonProperty("maxLmtSz")
    private String maxLmtSz;

    /**
     * 最大交易币数
     */
    @JsonProperty("maxMktSz")
    private String maxMktSz;

    /**
     * 最大限价单币数
     */
    @JsonProperty("maxLmtSzSingle")
    private String maxLmtSzSingle;

    /**
     * 最大市价单币数
     */
    @JsonProperty("maxMktSzSingle")
    private String maxMktSzSingle;

    /**
     * 最大可开杠杆倍数
     */
    @JsonProperty("maxLevSz")
    private String maxLevSz;

    /**
     * 最大可下单张数
     */
    @JsonProperty("maxSpotSzSingle")
    private String maxSpotSzSingle;

    /**
     * 最大可下单币数
     */
    @JsonProperty("maxTriggerSzSingle")
    private String maxTriggerSzSingle;

    /**
     * 最大止盈止损数量
     */
    @JsonProperty("maxTpslSzSingle")
    private String maxTpslSzSingle;

    // === 便利方法 ===

    /**
     * 获取当前杠杆倍数（BigDecimal）
     */
    public BigDecimal getLeverageAsBigDecimal() {
        try {
            return null != lev && !lev.isEmpty() ? new BigDecimal(lev) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取风险控制汇率（BigDecimal）
     */
    public BigDecimal getIpValueAsBigDecimal() {
        try {
            return null != ipVal && !ipVal.isEmpty() ? new BigDecimal(ipVal) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取最大限价单数量（BigDecimal）
     */
    public BigDecimal getMaxLimitSizeAsBigDecimal() {
        try {
            return null != maxLmtSz && !maxLmtSz.isEmpty() ? new BigDecimal(maxLmtSz) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取最大市价单数量（BigDecimal）
     */
    public BigDecimal getMaxMarketSizeAsBigDecimal() {
        try {
            return null != maxMktSz && !maxMktSz.isEmpty() ? new BigDecimal(maxMktSz) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取最大杠杆倍数（BigDecimal）
     */
    public BigDecimal getMaxLeverageAsBigDecimal() {
        try {
            return null != maxLevSz && !maxLevSz.isEmpty() ? new BigDecimal(maxLevSz) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 判断是否开启自动借币
     */
    public boolean isAutoLoanEnabled() {
        return "1".equals(autoLoan) || "true".equalsIgnoreCase(autoLoan);
    }

    /**
     * 判断是否开启自动还币
     */
    public boolean isAutoRepayEnabled() {
        return "1".equals(autoRepay) || "true".equalsIgnoreCase(autoRepay);
    }

    /**
     * 判断是否为全仓抵押模式
     */
    public boolean isMarginIsolationMode() {
        return "isolated".equals(mgnIsoMode);
    }

    /**
     * 判断是否为穿透抵押模式
     */
    public boolean isCrossMarginMode() {
        return "autonomy".equals(mgnIsoMode) || "cross".equals(mgnIsoMode);
    }

    /**
     * 获取现货对冲模式描述
     */
    public String getSpotOffsetDescription() {
        switch (spotOffsetType) {
            case "1":
                return "仅支持开仓同币种对冲";
            case "2":
                return "仅支持平仓同币种对冲";
            case "3":
                return "支持开仓和平仓同币种对冲";
            case "4":
                return "无对冲";
            default:
                return "未知对冲模式";
        }
    }
}
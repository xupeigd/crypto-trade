package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * OkxInstrumentData
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
public class OkxInstrumentData {

    /**
     * 产品ID
     */
    @JsonProperty("instId")
    private String instId;

    /**
     * 产品类型
     * SPOT：币币，MARGIN：杠杆，SWAP：永续合约，FUTURES：交割合约，OPTION：期权
     */
    @JsonProperty("instType")
    private String instType;

    /**
     * 标的指数
     */
    @JsonProperty("uly")
    private String uly;

    /**
     * 产品分类
     */
    @JsonProperty("category")
    private String category;

    /**
     * 合约面值
     */
    @JsonProperty("ctVal")
    private String ctVal;

    /**
     * 合约面值币种
     */
    @JsonProperty("ctValCcy")
    private String ctValCcy;

    /**
     * 合约乘数
     */
    @JsonProperty("ctMult")
    private String ctMult;

    /**
     * 合约乘数币种
     */
    @JsonProperty("ctMultCcy")
    private String ctMultCcy;

    /**
     * 合约单位
     */
    @JsonProperty("ctVal")
    private String contractValue;

    /**
     * 开仓所需的保证金币种
     */
    @JsonProperty("mgnCcy")
    private String mgnCcy;

    /**
     * 最小下单数量
     */
    @JsonProperty("lotSz")
    private String lotSz;

    /**
     * 下单数量精度
     */
    @JsonProperty("tickSz")
    private String tickSz;

    /**
     * 下单价格精度
     */
    @JsonProperty("minSz")
    private String minSz;

    /**
     * 最大下单数量
     */
    @JsonProperty("maxSz")
    private String maxSz;

    /**
     * 最大持仓数量
     */
    @JsonProperty("maxLmtSz")
    private String maxLmtSz;

    /**
     * 最大市场下单数量
     */
    @JsonProperty("maxMktSz")
    private String maxMktSz;

    /**
     * 最大杠杆倍数
     */
    @JsonProperty("lever")
    private String lever;

    /**
     * 风险准备金费率
     */
    @JsonProperty("riskRate")
    private String riskRate;

    /**
     * 交割/行权日期，毫秒
     */
    @JsonProperty("expTime")
    private String expTime;

    /**
     * 开始交易时间，毫秒
     */
    @JsonProperty("listTime")
    private String listTime;

    /**
     * 交易状态
     * live：正常交易，suspend：暂停交易
     */
    @JsonProperty("state")
    private String state;

    /**
     * 最大杠杆倍数
     */
    @JsonProperty("maxLever")
    private String maxLever;

    /**
     * 配置信息
     */
    @JsonProperty("config")
    private String config;

    /**
     * 是否支持全仓
     */
    @JsonProperty("isIso")
    private String isIso;

    /**
     * 期权类型
     */
    @JsonProperty("optType")
    private String optType;

    /**
     * 期权行权价
     */
    @JsonProperty("stk")
    private String stk;

    // === 便利方法 ===

    /**
     * 获取最小下单数量（BigDecimal）
     */
    public BigDecimal getLotSize() {
        try {
            return null != lotSz && !lotSz.isEmpty() ? new BigDecimal(lotSz) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取价格精度（BigDecimal）
     */
    public BigDecimal getTickSize() {
        try {
            return null != tickSz && !tickSz.isEmpty() ? new BigDecimal(tickSz) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取最小下单量（BigDecimal）
     */
    public BigDecimal getMinSize() {
        try {
            return null != minSz && !minSz.isEmpty() ? new BigDecimal(minSz) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取最大下单量（BigDecimal）
     */
    public BigDecimal getMaxSize() {
        try {
            return null != maxSz && !maxSz.isEmpty() ? new BigDecimal(maxSz) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取合约面值（BigDecimal）
     */
    public BigDecimal getContractValueAsBigDecimal() {
        try {
            return null != contractValue && !contractValue.isEmpty() ? new BigDecimal(contractValue) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取最大杠杆倍数（BigDecimal）
     */
    public BigDecimal getMaxLeverage() {
        try {
            return null != maxLever && !maxLever.isEmpty() ? new BigDecimal(maxLever) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 判断是否为现货产品
     */
    public boolean isSpot() {
        return "SPOT".equals(instType);
    }

    /**
     * 判断是否为永续合约
     */
    public boolean isPerpetual() {
        return "SWAP".equals(instType);
    }

    /**
     * 判断是否为交割合约
     */
    public boolean isFutures() {
        return "FUTURES".equals(instType);
    }

    /**
     * 判断是否为期权
     */
    public boolean isOption() {
        return "OPTION".equals(instType);
    }

    /**
     * 判断是否正在交易
     */
    public boolean isLive() {
        return "live".equals(state);
    }

    /**
     * 判断是否支持全仓
     */
    public boolean isIsolatedSupported() {
        return "1".equals(isIso);
    }

    /**
     * 判断是否为看涨期权
     */
    public boolean isCallOption() {
        return "C".equals(optType);
    }

    /**
     * 判断是否为看跌期权
     */
    public boolean isPutOption() {
        return "P".equals(optType);
    }

    /**
     * 获取期权行权价（BigDecimal）
     */
    public BigDecimal getStrikePrice() {
        try {
            return null != stk && !stk.isEmpty() ? new BigDecimal(stk) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
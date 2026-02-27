package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * OkxBalanceData
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
public class OkxBalanceData {

    /**
     * 币种
     */
    @JsonProperty("ccy")
    private String ccy;

    /**
     * 总权益
     */
    @JsonProperty("bal")
    private String bal;

    /**
     * 可用余额
     */
    @JsonProperty("availBal")
    private String availBal;

    /**
     * 冻结余额
     */
    @JsonProperty("frozenBal")
    private String frozenBal;

    /**
     * 美金层面保证金
     */
    @JsonProperty("usdPrice")
    private String usdPrice;

    // === 便利方法 ===

    /**
     * 获取总权益（BigDecimal）
     */
    public BigDecimal getBalanceAsBigDecimal() {
        try {
            return null != bal && !bal.isEmpty() ? new BigDecimal(bal) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取可用余额（BigDecimal）
     */
    public BigDecimal getAvailableBalanceAsBigDecimal() {
        try {
            return null != availBal && !availBal.isEmpty() ? new BigDecimal(availBal) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取冻结余额（BigDecimal）
     */
    public BigDecimal getFrozenBalanceAsBigDecimal() {
        try {
            return null != frozenBal && !frozenBal.isEmpty() ? new BigDecimal(frozenBal) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取美金价格（BigDecimal）
     */
    public BigDecimal getUsdPriceAsBigDecimal() {
        try {
            return null != usdPrice && !usdPrice.isEmpty() ? new BigDecimal(usdPrice) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取以美元计价的总权益
     */
    public BigDecimal getTotalBalanceInUSD() {
        return getBalanceAsBigDecimal().multiply(getUsdPriceAsBigDecimal());
    }

    /**
     * 获取以美元计价的可用余额
     */
    public BigDecimal getAvailableBalanceInUSD() {
        return getAvailableBalanceAsBigDecimal().multiply(getUsdPriceAsBigDecimal());
    }

    /**
     * 获取以美元计价的冻结余额
     */
    public BigDecimal getFrozenBalanceInUSD() {
        return getFrozenBalanceAsBigDecimal().multiply(getUsdPriceAsBigDecimal());
    }

    /**
     * 判断是否为USDT余额
     */
    public boolean isUSDT() {
        return "USDT".equals(ccy);
    }

    /**
     * 判断是否有余额
     */
    public boolean hasBalance() {
        return getBalanceAsBigDecimal().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 判断是否有可用余额
     */
    public boolean hasAvailableBalance() {
        return getAvailableBalanceAsBigDecimal().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 获取余额利用率
     */
    public BigDecimal getBalanceUtilization() {
        BigDecimal total = getBalanceAsBigDecimal();
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            return getFrozenBalanceAsBigDecimal()
                    .divide(total, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        return BigDecimal.ZERO;
    }
}
package com.crypto.trade.dto.cex.model;

import java.math.BigDecimal;

/**
 * CexInstrument
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public abstract class CexInstrument {

    /**
     * 获取合约ID/交易对
     *
     * @return 合约标识
     */
    public abstract String getSymbol();

    /**
     * 获取合约类型
     * <p>
     * SPOT=现货, SWAP=永续合约, FUTURES=交割合约, OPTION=期权
     * </p>
     *
     * @return 合约类型
     */
    public abstract String getInstrumentType();

    /**
     * 获取基础币种
     *
     * @return 基础币种(如 BTC)
     */
    public abstract String getBaseCurrency();

    /**
     * 获取计价币种
     *
     * @return 计价币种(如 USDT)
     */
    public abstract String getQuoteCurrency();

    /**
     * 获取结算币种
     *
     * @return 结算币种
     */
    public abstract String getSettleCurrency();

    /**
     * 获取合约面值
     *
     * @return 合约面值
     */
    public abstract BigDecimal getContractValue();

    /**
     * 获取下单数量精度
     *
     * @return 数量精度(最小单位)
     */
    public abstract BigDecimal getLotSize();

    /**
     * 获取价格精度
     *
     * @return 价格精度(最小单位)
     */
    public abstract BigDecimal getTickSize();

    /**
     * 获取最小下单量
     *
     * @return 最小下单数量
     */
    public abstract BigDecimal getMinOrderSize();

    /**
     * 获取最大杠杆倍数
     *
     * @return 最大杠杆
     */
    public abstract BigDecimal getMaxLeverage();

    /**
     * 获取合约状态
     *
     * @return 合约状态
     */
    public abstract String getState();

    // ==================== 便捷判断方法 ====================

    /**
     * 判断是否为现货
     *
     * @return true=现货, false=非现货
     */
    public boolean isSpot() {
        return "SPOT".equalsIgnoreCase(getInstrumentType());
    }

    /**
     * 判断是否为永续合约
     *
     * @return true=永续合约, false=非永续合约
     */
    public boolean isPerpetual() {
        return "SWAP".equalsIgnoreCase(getInstrumentType());
    }

    /**
     * 判断是否为交割合约
     *
     * @return true=交割合约, false=非交割合约
     */
    public boolean isFutures() {
        return "FUTURES".equalsIgnoreCase(getInstrumentType());
    }

    /**
     * 判断是否为期权
     *
     * @return true=期权, false=非期权
     */
    public boolean isOption() {
        return "OPTION".equalsIgnoreCase(getInstrumentType());
    }

    /**
     * 判断合约是否可用
     *
     * @return true=可用, false=不可用
     */
    public boolean isAvailable() {
        return !"SUSPEND".equalsIgnoreCase(getState());
    }
}

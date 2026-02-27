package com.crypto.trade.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * CexExchange
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Getter
public enum CexExchange {

    /**
     * OKX交易所
     */
    OKX("okx", "OKX"),

    /**
     * 币安交易所
     */
    BINANCE("binance", "币安"),

    /**
     * Bybit交易所
     */
    BYBIT("bybit", "Bybit");

    private final String code;
    private final String description;

    CexExchange(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     * <p>
     * Jackson反序列化时使用此方法将JSON字符串转换为枚举
     * </p>
     *
     * @param code 枚举code
     * @return 对应的枚举值，如果不存在返回null
     */
    @JsonCreator
    public static CexExchange fromCode(String code) {
        if (null == code) {
            return null;
        }
        for (CexExchange exchange : values()) {
            if (exchange.code.equalsIgnoreCase(code)) {
                return exchange;
            }
        }
        return null;
    }

    /**
     * 获取枚举的code值
     * <p>
     * Jackson序列化时使用此方法将枚举转换为JSON字符串
     * </p>
     *
     * @return code值
     */
    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * 判断是否为OKX交易所
     *
     * @return true表示OKX
     */
    public boolean isOkx() {
        return this == OKX;
    }

    /**
     * 判断是否为币安交易所
     *
     * @return true表示币安
     */
    public boolean isBinance() {
        return this == BINANCE;
    }

    /**
     * 判断是否为Bybit交易所
     *
     * @return true表示Bybit
     */
    public boolean isBybit() {
        return this == BYBIT;
    }
}

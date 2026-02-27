package com.crypto.trade.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * CEX订单类型枚举
 * <p>
 * 用于标识CEX交易所的订单类型。
 * </p>
 *
 * @author Page
 * @since 2025-01-10
 */
@Getter
public enum CexOrderType {

    /**
     * 市价单
     */
    MARKET("market", "市价单"),

    /**
     * 限价单
     */
    LIMIT("limit", "限价单"),

    /**
     * 止盈单
     */
    TAKE_PROFIT("take_profit", "止盈单"),

    /**
     * 止损单
     */
    STOP_LOSS("stop_loss", "止损单");

    private final String code;
    private final String description;

    CexOrderType(String code, String description) {
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
    public static CexOrderType fromCode(String code) {
        if (null == code) {
            return null;
        }
        for (CexOrderType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
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
     * 判断是否为市价单
     *
     * @return true表示市价单
     */
    public boolean isMarket() {
        return this == MARKET;
    }

    /**
     * 判断是否为限价单
     *
     * @return true表示限价单
     */
    public boolean isLimit() {
        return this == LIMIT;
    }

    /**
     * 判断是否为条件单（止盈/止损）
     *
     * @return true表示条件单
     */
    public boolean isConditionalOrder() {
        return this == TAKE_PROFIT || this == STOP_LOSS;
    }
}

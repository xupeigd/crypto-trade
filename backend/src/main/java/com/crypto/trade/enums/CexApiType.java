package com.crypto.trade.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * CexApiType
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Getter
public enum CexApiType {

    /**
     * 下单
     */
    PLACE_ORDER("place_order", "下单"),

    /**
     * 平仓
     */
    CLOSE_POSITION("close_position", "平仓"),

    /**
     * 取消订单
     */
    CANCEL_ORDER("cancel_order", "取消订单"),

    /**
     * 取消订单
     */
    CANCEL("cancel", "取消订单"),

    /**
     * 设置策略订单（止盈止损）
     */
    SET_ALGO_ORDER("set_algo_order", "设置策略订单"),

    /**
     * 修改策略订单
     */
    AMEND_ALGO_ORDER("amend_algo_order", "修改策略订单"),

    /**
     * 取消策略订单
     */
    CANCEL_ALGO_ORDER("cancel_algo_order", "取消策略订单");

    private final String code;
    private final String description;

    CexApiType(String code, String description) {
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
    public static CexApiType fromCode(String code) {
        if (null == code) {
            return null;
        }
        for (CexApiType type : values()) {
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
     * 判断是否为下单操作
     *
     * @return true表示下单操作
     */
    public boolean isPlaceOrder() {
        return this == PLACE_ORDER;
    }

    /**
     * 判断是否为平仓操作
     *
     * @return true表示平仓操作
     */
    public boolean isClosePosition() {
        return this == CLOSE_POSITION;
    }

    /**
     * 判断是否为取消订单操作
     *
     * @return true表示取消订单操作
     */
    public boolean isCancelOrder() {
        return this == CANCEL_ORDER || this == CANCEL;
    }

    /**
     * 判断是否为设置策略订单操作
     *
     * @return true表示设置策略订单操作
     */
    public boolean isSetAlgoOrder() {
        return this == SET_ALGO_ORDER;
    }

    /**
     * 判断是否为修改策略订单操作
     *
     * @return true表示修改策略订单操作
     */
    public boolean isAmendAlgoOrder() {
        return this == AMEND_ALGO_ORDER;
    }

    /**
     * 判断是否为取消策略订单操作
     *
     * @return true表示取消策略订单操作
     */
    public boolean isCancelAlgoOrder() {
        return this == CANCEL_ALGO_ORDER;
    }

    /**
     * 判断是否为策略订单相关操作
     * <p>
     * 包括设置、修改、取消策略订单
     * </p>
     *
     * @return true表示策略订单相关操作
     */
    public boolean isAlgoOrder() {
        return this == SET_ALGO_ORDER || this == AMEND_ALGO_ORDER || this == CANCEL_ALGO_ORDER;
    }
}

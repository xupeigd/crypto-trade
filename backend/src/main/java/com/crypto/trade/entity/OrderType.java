package com.crypto.trade.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * OrderType
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Getter
public enum OrderType {
    /**
     * 限价
     */
    LIMIT("limit", "限价"),

    /**
     * 市价
     */
    MARKET("market", "市价"),

    ;

    private final String code;
    private final String description;

    OrderType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     * <p>
     * Jackson反序列化时使用此方法将JSON字符串("limit"/"market")转换为枚举
     * </p>
     *
     * @param code 枚举code
     * @return 对应的枚举值, 如果不存在返回null
     */
    @JsonCreator
    public static OrderType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OrderType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 获取枚举的code值
     * <p>
     * Jackson序列化时使用此方法将枚举转换为JSON字符串("limit"/"market")
     * </p>
     *
     * @return code值
     */
    @JsonValue
    public String getCode() {
        return code;
    }


}
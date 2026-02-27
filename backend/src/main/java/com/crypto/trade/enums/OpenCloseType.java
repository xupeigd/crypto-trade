package com.crypto.trade.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * OpenCloseType
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Getter
public enum OpenCloseType {

    /**
     * 开仓
     */
    OPEN("open", "开仓"),

    /**
     * 平仓
     */
    CLOSE("close", "平仓");

    private final String code;
    private final String description;

    OpenCloseType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     * <p>
     * Jackson反序列化时使用此方法将JSON字符串("open"/"close")转换为枚举
     * </p>
     *
     * @param code 枚举code
     * @return 对应的枚举值, 如果不存在返回null
     */
    @JsonCreator
    public static OpenCloseType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OpenCloseType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 获取枚举的code值
     * <p>
     * Jackson序列化时使用此方法将枚举转换为JSON字符串("open"/"close")
     * </p>
     *
     * @return code值
     */
    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * 判断是否为开仓
     *
     * @return true表示开仓
     */
    public boolean isOpen() {
        return this == OPEN;
    }

    /**
     * 判断是否为平仓
     *
     * @return true表示平仓
     */
    public boolean isClose() {
        return this == CLOSE;
    }
}

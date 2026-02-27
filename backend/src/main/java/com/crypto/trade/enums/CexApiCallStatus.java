package com.crypto.trade.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * CexApiCallStatus
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Getter
public enum CexApiCallStatus {

    /**
     * 调用中
     */
    PENDING("pending", "调用中"),

    /**
     * 调用成功
     */
    SUCCESS("success", "调用成功"),

    /**
     * 调用失败
     */
    FAILED("failed", "调用失败"),

    /**
     * 超时
     */
    TIMEOUT("timeout", "超时");

    private final String code;
    private final String description;

    CexApiCallStatus(String code, String description) {
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
    public static CexApiCallStatus fromCode(String code) {
        if (null == code) {
            return null;
        }
        for (CexApiCallStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
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
     * 判断是否为调用中状态
     *
     * @return true表示调用中
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * 判断是否为成功状态
     *
     * @return true表示成功
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /**
     * 判断是否为失败状态
     *
     * @return true表示失败
     */
    public boolean isFailed() {
        return this == FAILED;
    }

    /**
     * 判断是否为超时状态
     *
     * @return true表示超时
     */
    public boolean isTimeout() {
        return this == TIMEOUT;
    }

    /**
     * 判断是否为终止状态（成功、失败或超时）
     *
     * @return true表示已终止
     */
    public boolean isTerminated() {
        return this == SUCCESS || this == FAILED || this == TIMEOUT;
    }
}

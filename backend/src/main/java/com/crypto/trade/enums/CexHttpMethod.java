package com.crypto.trade.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * CexHttpMethod
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Getter
public enum CexHttpMethod {

    /**
     * GET请求
     */
    GET("GET", "GET请求"),

    /**
     * POST请求
     */
    POST("POST", "POST请求"),

    /**
     * PUT请求
     */
    PUT("PUT", "PUT请求"),

    /**
     * DELETE请求
     */
    DELETE("DELETE", "DELETE请求");

    private final String code;
    private final String description;

    CexHttpMethod(String code, String description) {
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
    public static CexHttpMethod fromCode(String code) {
        if (null == code) {
            return null;
        }
        for (CexHttpMethod method : values()) {
            if (method.code.equalsIgnoreCase(code)) {
                return method;
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
     * 判断是否为GET请求
     *
     * @return true表示GET请求
     */
    public boolean isGet() {
        return this == GET;
    }

    /**
     * 判断是否为POST请求
     *
     * @return true表示POST请求
     */
    public boolean isPost() {
        return this == POST;
    }

    /**
     * 判断是否为DELETE请求
     *
     * @return true表示DELETE请求
     */
    public boolean isDelete() {
        return this == DELETE;
    }

    /**
     * 判断是否为写操作请求（POST/PUT/DELETE）
     *
     * @return true表示写操作请求
     */
    public boolean isWriteOperation() {
        return this == POST || this == PUT || this == DELETE;
    }
}

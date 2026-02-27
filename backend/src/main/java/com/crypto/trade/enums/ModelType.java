package com.crypto.trade.enums;

import lombok.Getter;

/**
 * ModelType
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Getter
public enum ModelType {

    LOCAL("LOCAL", "本地模型", "使用本地Ollama等模型服务"),
    REMOTE("REMOTE", "远端模型", "使用远端API调用模型服务");

    private final String code;
    private final String description;
    private final String detail;

    ModelType(String code, String description, String detail) {
        this.code = code;
        this.description = description;
        this.detail = detail;
    }

    /**
     * 根据代码获取枚举
     */
    public static ModelType fromCode(String code) {
        for (ModelType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的模型类型: " + code);
    }

    /**
     * 是否为本地模型
     */
    public boolean isLocal() {
        return this == LOCAL;
    }

    /**
     * 是否为远端模型
     */
    public boolean isRemote() {
        return this == REMOTE;
    }
}
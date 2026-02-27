package com.crypto.trade.enums;

import lombok.Getter;

/**
 * ApiFormat
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Getter
public enum ApiFormat {

    OLLAMA("ollama", "Ollama格式", "本地Ollama模型API格式"),
    OPENAI("openai", "OpenAI格式", "OpenAI兼容的API格式"),
    CLAUDE("claude", "Claude格式", "Anthropic Claude API格式"),
    CUSTOM("custom", "自定义格式", "自定义API格式");

    private final String code;
    private final String description;
    private final String detail;

    ApiFormat(String code, String description, String detail) {
        this.code = code;
        this.description = description;
        this.detail = detail;
    }

    /**
     * 根据代码获取枚举
     */
    public static ApiFormat fromCode(String code) {
        for (ApiFormat format : values()) {
            if (format.getCode().equals(code)) {
                return format;
            }
        }
        throw new IllegalArgumentException("未知的API格式: " + code);
    }

    /**
     * 是否为Ollama格式
     */
    public boolean isOllama() {
        return this == OLLAMA;
    }

    /**
     * 是否为OpenAI格式
     */
    public boolean isOpenai() {
        return this == OPENAI;
    }

    /**
     * 是否为Claude格式
     */
    public boolean isClaude() {
        return this == CLAUDE;
    }

    /**
     * 是否为自定义格式
     */
    public boolean isCustom() {
        return this == CUSTOM;
    }
}
package com.crypto.trade.dto.cex.common;

/**
 * MarginMode
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public enum MarginMode {
    /**
     * 逐仓模式(每个合约独立保证金)
     */
    ISOLATED("isolated", "逐仓"),

    /**
     * 全仓模式(所有合约共享保证金)
     */
    CROSS("cross", "全仓"),

    /**
     * 未知模式(用于兜底)
     */
    UNKNOWN("unknown", "未知");

    /**
     * 保证金模式代码
     */
    private final String code;

    /**
     * 保证金模式描述
     */
    private final String description;

    /**
     * 构造函数
     *
     * @param code        保证金模式代码
     * @param description 保证金模式描述
     */
    MarginMode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取保证金模式枚举
     * <p>
     * 支持大小写不敏感匹配,用于适配不同交易所的保证金模式字段。
     * </p>
     *
     * @param code 保证金模式代码
     * @return 保证金模式枚举
     */
    public static MarginMode fromCode(String code) {
        if (null == code || code.trim().isEmpty()) {
            return UNKNOWN;
        }

        String normalizedCode = code.trim().toLowerCase();
        for (MarginMode mode : values()) {
            if (mode.code.equals(normalizedCode)) {
                return mode;
            }
        }

        return UNKNOWN;
    }

    /**
     * 获取保证金模式代码
     *
     * @return 保证金模式代码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取保证金模式描述
     *
     * @return 保证金模式描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 判断是否为逐仓模式
     *
     * @return true=逐仓, false=非逐仓
     */
    public boolean isIsolated() {
        return this == ISOLATED;
    }

    /**
     * 判断是否为全仓模式
     *
     * @return true=全仓, false=非全仓
     */
    public boolean isCross() {
        return this == CROSS;
    }
}

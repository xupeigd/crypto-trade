package com.crypto.trade.dto.cex.common;

/**
 * PositionSide
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public enum PositionSide {
    /**
     * 多头持仓(看涨)
     */
    LONG("long", "多头"),

    /**
     * 空头持仓(看跌)
     */
    SHORT("short", "空头"),

    /**
     * 净持仓(多空对冲后的净值)
     */
    NET("net", "净持仓"),

    /**
     * 未知类型(用于兜底)
     */
    UNKNOWN("unknown", "未知");

    /**
     * 持仓方向代码
     */
    private final String code;

    /**
     * 持仓方向描述
     */
    private final String description;

    /**
     * 构造函数
     *
     * @param code        持仓方向代码
     * @param description 持仓方向描述
     */
    PositionSide(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取持仓方向枚举
     * <p>
     * 支持大小写不敏感匹配,用于适配不同交易所的持仓方向字段。
     * </p>
     *
     * @param code 持仓方向代码
     * @return 持仓方向枚举
     */
    public static PositionSide fromCode(String code) {
        if (null == code || code.trim().isEmpty()) {
            return UNKNOWN;
        }

        String normalizedCode = code.trim().toLowerCase();
        for (PositionSide side : values()) {
            if (side.code.equals(normalizedCode)) {
                return side;
            }
        }

        return UNKNOWN;
    }

    /**
     * 获取持仓方向代码
     *
     * @return 持仓方向代码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取持仓方向描述
     *
     * @return 持仓方向描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 判断是否为多头持仓
     *
     * @return true=多头, false=非多头
     */
    public boolean isLong() {
        return this == LONG;
    }

    /**
     * 判断是否为空头持仓
     *
     * @return true=空头, false=非空头
     */
    public boolean isShort() {
        return this == SHORT;
    }
}

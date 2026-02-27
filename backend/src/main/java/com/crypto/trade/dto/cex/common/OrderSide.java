package com.crypto.trade.dto.cex.common;

/**
 * OrderSide
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public enum OrderSide {
    /**
     * 买入
     */
    BUY("buy", "买入"),

    /**
     * 卖出
     */
    SELL("sell", "卖出"),

    /**
     * 未知类型(用于兜底)
     */
    UNKNOWN("unknown", "未知");

    /**
     * 订单方向代码
     */
    private final String code;

    /**
     * 订单方向描述
     */
    private final String description;

    /**
     * 构造函数
     *
     * @param code        订单方向代码
     * @param description 订单方向描述
     */
    OrderSide(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取订单方向枚举
     * <p>
     * 支持大小写不敏感匹配,用于适配不同交易所的订单方向字段。
     * </p>
     *
     * @param code 订单方向代码
     * @return 订单方向枚举
     */
    public static OrderSide fromCode(String code) {
        if (null == code || code.trim().isEmpty()) {
            return UNKNOWN;
        }

        String normalizedCode = code.trim().toLowerCase();
        for (OrderSide side : values()) {
            if (side.code.equals(normalizedCode)) {
                return side;
            }
        }

        return UNKNOWN;
    }

    /**
     * 获取订单方向代码
     *
     * @return 订单方向代码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取订单方向描述
     *
     * @return 订单方向描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 判断是否为买入方向
     *
     * @return true=买入, false=非买入
     */
    public boolean isBuy() {
        return this == BUY;
    }

    /**
     * 判断是否为卖出方向
     *
     * @return true=卖出, false=非卖出
     */
    public boolean isSell() {
        return this == SELL;
    }
}

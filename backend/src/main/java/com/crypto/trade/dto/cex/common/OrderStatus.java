package com.crypto.trade.dto.cex.common;

/**
 * OrderStatus
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public enum OrderStatus {
    /**
     * 新建订单(等待成交)
     */
    LIVE("live", "新建"),

    /**
     * 部分成交
     */
    PARTIALLY_FILLED("partially_filled", "部分成交"),

    /**
     * 完全成交
     */
    FILLED("filled", "已成交"),

    /**
     * 已取消
     */
    CANCELED("canceled", "已取消"),

    /**
     * 等待取消(中)
     */
    CANCELING("canceling", "取消中"),

    /**
     * 被拒绝(下单失败)
     */
    REJECTED("rejected", "被拒绝"),

    /**
     * 失效(条件单未触发)
     */
    EXPIRED("expired", "已失效"),

    /**
     * 未知状态(用于兜底)
     */
    UNKNOWN("unknown", "未知");

    /**
     * 订单状态代码
     */
    private final String code;

    /**
     * 订单状态描述
     */
    private final String description;

    /**
     * 构造函数
     *
     * @param code        订单状态代码
     * @param description 订单状态描述
     */
    OrderStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取订单状态枚举
     * <p>
     * 支持大小写不敏感匹配,用于适配不同交易所的订单状态字段。
     * </p>
     *
     * @param code 订单状态代码
     * @return 订单状态枚举
     */
    public static OrderStatus fromCode(String code) {
        if (null == code || code.trim().isEmpty()) {
            return UNKNOWN;
        }

        String normalizedCode = code.trim().toLowerCase();
        for (OrderStatus status : values()) {
            if (status.code.equals(normalizedCode)) {
                return status;
            }
        }

        return UNKNOWN;
    }

    /**
     * 获取订单状态代码
     *
     * @return 订单状态代码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取订单状态描述
     *
     * @return 订单状态描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 判断订单是否已完成(最终状态)
     * <p>
     * 已完成包括: 已成交、已取消、被拒绝、已失效
     * </p>
     *
     * @return true=已完成, false=未完成
     */
    public boolean isFinal() {
        return this == FILLED || this == CANCELED || this == REJECTED || this == EXPIRED;
    }

    /**
     * 判断订单是否活跃(仍在交易)
     * <p>
     * 活跃包括: 新建、部分成交、取消中
     * </p>
     *
     * @return true=活跃, false=非活跃
     */
    public boolean isActive() {
        return this == LIVE || this == PARTIALLY_FILLED || this == CANCELING;
    }

    /**
     * 判断订单是否已成交(包括部分成交和完全成交)
     *
     * @return true=已成交, false=未成交
     */
    public boolean isFilled() {
        return this == PARTIALLY_FILLED || this == FILLED;
    }
}

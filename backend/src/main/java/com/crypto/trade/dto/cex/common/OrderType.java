package com.crypto.trade.dto.cex.common;

/**
 * OrderType
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public enum OrderType {
    /**
     * 限价单
     */
    LIMIT("limit", "限价单"),

    /**
     * 市价单
     */
    MARKET("market", "市价单"),

    /**
     * 止盈止损单(条件单)
     */
    CONDITIONAL("conditional", "条件单"),

    /**
     * 止盈限价单
     */
    TAKE_PROFIT("take_profit", "止盈单"),

    /**
     * 止损限价单
     */
    STOP_LOSS("stop_loss", "止损单"),

    /**
     * 只做maker单(不立即成交,提供流动性)
     */
    POST_ONLY("post_only", "只做maker单"),

    /**
     * 全部成交或立即取消(FOK)
     */
    FOK("fok", "全部成交或立即取消"),

    /**
     * 立即成交否则取消(IOC)
     */
    IOC("ioc", "立即成交否则取消"),

    /**
     * OCO订单(止盈止损二选一)
     */
    OCO("oco", "OCO订单"),

    /**
     * 冰山订单(大单隐藏)
     */
    ICEBERG("iceberg", "冰山订单"),

    /**
     * TWAP订单(时间加权平均价格)
     */
    TWAP("twap", "TWAP订单"),

    /**
     * 追踪止损单
     */
    TRAILING_STOP("trailing_stop", "追踪止损单"),

    /**
     * 未知类型(用于兜底)
     */
    UNKNOWN("unknown", "未知");

    /**
     * 订单类型代码
     */
    private final String code;

    /**
     * 订单类型描述
     */
    private final String description;

    /**
     * 构造函数
     *
     * @param code        订单类型代码
     * @param description 订单类型描述
     */
    OrderType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取订单类型枚举
     * <p>
     * 支持大小写不敏感匹配,用于适配不同交易所的订单类型字段。
     * </p>
     *
     * @param code 订单类型代码
     * @return 订单类型枚举
     */
    public static OrderType fromCode(String code) {
        if (null == code || code.trim().isEmpty()) {
            return UNKNOWN;
        }

        String normalizedCode = code.trim().toLowerCase();
        // 特殊处理带下划线的类型
        String normalizedCodeUnderscore = normalizedCode.replace("-", "_");

        for (OrderType type : values()) {
            if (type.code.equals(normalizedCode) || type.code.equals(normalizedCodeUnderscore)) {
                return type;
            }
        }

        return UNKNOWN;
    }

    /**
     * 获取订单类型代码
     *
     * @return 订单类型代码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取订单类型描述
     *
     * @return 订单类型描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 判断是否为市价单
     *
     * @return true=市价单, false=非市价单
     */
    public boolean isMarket() {
        return this == MARKET;
    }

    /**
     * 判断是否为限价单
     *
     * @return true=限价单, false=非限价单
     */
    public boolean isLimit() {
        return this == LIMIT;
    }

    /**
     * 判断是否为条件单(止盈止损等)
     *
     * @return true=条件单, false=非条件单
     */
    public boolean isConditional() {
        return this == CONDITIONAL || this == TAKE_PROFIT || this == STOP_LOSS
                || this == TRAILING_STOP || this == OCO;
    }

    /**
     * 判断是否为算法订单
     *
     * @return true=算法订单, false=非算法订单
     */
    public boolean isAlgo() {
        return this == ICEBERG || this == TWAP || this == OCO || this == TRAILING_STOP;
    }
}

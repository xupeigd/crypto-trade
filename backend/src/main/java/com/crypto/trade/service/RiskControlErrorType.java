package com.crypto.trade.service;

/**
 * RiskControlErrorType
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public enum RiskControlErrorType {

    ORDER_NOT_FOUND(
            "订单不存在",
            "请确认订单ID是否正确，或刷新页面重新获取订单列表"
    ),

    INVALID_STATUS(
            "订单状态无效",
            "请确认订单状态是否为待审核，或刷新页面查看最新状态"
    ),

    INSUFFICIENT_BALANCE(
            "账户余额不足",
            "请检查账户余额是否充足，或调整订单数量/金额"
    ),

    POSITION_LIMIT_EXCEEDED(
            "持仓限制超限",
            "请检查持仓数量是否超过限制，或减少订单数量"
    ),

    PRICE_DEVIATION(
            "价格偏离市场",
            "请检查订单价格是否合理，或调整到市场可接受范围"
    ),

    INVALID_SYMBOL(
            "交易对无效",
            "请确认交易对是否正确，或选择其他可交易的对"
    ),

    NETWORK_ERROR(
            "网络连接异常",
            "网络连接异常，请稍后重试或检查网络连接"
    ),

    TRADING_ERROR(
            "交易执行失败",
            "向交易所下单失败，请检查交易参数或稍后重试"
    ),

    SYSTEM_ERROR(
            "系统错误",
            "系统内部错误，请联系系统管理员或稍后重试"
    );

    private final String description;
    private final String suggestion;

    RiskControlErrorType(String description, String suggestion) {
        this.description = description;
        this.suggestion = suggestion;
    }

    public String getDescription() {
        return description;
    }

    public String getSuggestion() {
        return suggestion;
    }
}
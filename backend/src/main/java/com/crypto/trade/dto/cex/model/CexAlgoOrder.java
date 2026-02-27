package com.crypto.trade.dto.cex.model;

import com.crypto.trade.dto.cex.common.OrderSide;
import com.crypto.trade.dto.cex.common.OrderStatus;
import com.crypto.trade.dto.cex.common.OrderType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CexAlgoOrder
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class CexAlgoOrder {

    /**
     * 获取算法订单ID
     *
     * @return 算法订单唯一标识
     */
    public abstract String getAlgoId();

    /**
     * 获取关联的普通订单ID
     *
     * @return 关联订单ID
     */
    public abstract String getOrderId();

    /**
     * 获取交易对/合约代码
     *
     * @return 交易对标识
     */
    public abstract String getSymbol();

    /**
     * 获取订单方向
     *
     * @return BUY或SELL
     */
    public abstract OrderSide getSide();

    /**
     * 获取订单类型
     *
     * @return 订单类型(条件单 、 止盈 、 止损等)
     */
    public abstract OrderType getOrderType();

    /**
     * 获取订单状态
     *
     * @return 订单状态
     */
    public abstract OrderStatus getStatus();

    /**
     * 获取触发价格
     * <p>
     * 条件单触发条件价格。
     * </p>
     *
     * @return 触发价格
     */
    public abstract BigDecimal getTriggerPrice();

    /**
     * 获取止盈触发价格
     * <p>
     * 止盈策略的触发价格,当市场价格达到此价格时触发止盈。
     * </p>
     *
     * @return 止盈触发价, 如果不可用返回null
     */
    public abstract BigDecimal getTpTriggerPx();

    /**
     * 获取止损触发价格
     * <p>
     * 止损策略的触发价格,当市场价格达到此价格时触发止损。
     * </p>
     *
     * @return 止损触发价, 如果不可用返回null
     */
    public abstract BigDecimal getSlTriggerPx();

    /**
     * 获取委托价格
     * <p>
     * 触发后的挂单价格。
     * </p
     *
     * @return 委托价格
     */
    public abstract BigDecimal getOrderPrice();

    /**
     * 获取订单数量
     *
     * @return 订单数量
     */
    public abstract BigDecimal getQuantity();

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public abstract LocalDateTime getCreateTime();

    /**
     * 判断是否为条件单
     *
     * @return true=条件单, false=非条件单
     */
    public boolean isConditional() {
        return OrderType.CONDITIONAL == getOrderType();
    }

    /**
     * 判断是否为止盈单
     *
     * @return true=止盈单, false=非止盈单
     */
    public boolean isTakeProfit() {
        return OrderType.TAKE_PROFIT == getOrderType();
    }

    /**
     * 判断是否为止损单
     *
     * @return true=止损单, false=非止损单
     */
    public boolean isStopLoss() {
        return OrderType.STOP_LOSS == getOrderType();
    }

    /**
     * 判断是否为OCO订单
     *
     * @return true=OCO订单, false=非OCO订单
     */
    public boolean isOco() {
        return OrderType.OCO == getOrderType();
    }

    /**
     * 判断是否为买单
     *
     * @return true=买单, false=卖单
     */
    public boolean isBuy() {
        return OrderSide.BUY == getSide();
    }

    /**
     * 判断是否为卖单
     *
     * @return true=卖单, false=买单
     */
    public boolean isSell() {
        return OrderSide.SELL == getSide();
    }

    public abstract String getPosSide();

}

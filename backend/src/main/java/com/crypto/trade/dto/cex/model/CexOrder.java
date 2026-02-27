package com.crypto.trade.dto.cex.model;

import com.crypto.trade.dto.cex.common.OrderSide;
import com.crypto.trade.dto.cex.common.OrderStatus;
import com.crypto.trade.dto.cex.common.OrderType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * CexOrder
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class CexOrder {

    /**
     * 获取订单ID
     * <p>
     * 交易所生成的唯一订单标识符。
     * </p>
     *
     * @return 订单唯一标识
     */
    public abstract String getOrderId();

    /**
     * 获取客户自定义订单ID
     * <p>
     * 用户下单时指定的自定义ID,用于关联客户端订单。
     * </p>
     *
     * @return 客户端订单ID
     */
    public String getClientOrderId() {
        return null;
    }

    public String getCode() {
        return null;
    }

    public String getMsg() {
        return null;
    }

    /**
     * 获取交易对/合约代码
     * <p>
     * 统一使用symbol字段名,对应OKX的instId、Binance的symbol等。
     * </p>
     *
     * @return 交易对标识(如 BTC - USDT - SWAP)
     */
    public String getSymbol() {
        return null;
    }

    /**
     * 获取订单方向
     * <p>
     * 买入或卖出。
     * </p>
     *
     * @return BUY或SELL
     */
    public OrderSide getSide() {
        return null;
    }

    /**
     * 获取订单类型
     * <p>
     * 限价单、市价单、条件单等。
     * </p>
     *
     * @return 订单类型枚举
     */
    public OrderType getOrderType() {
        return null;
    }

    /**
     * 获取订单状态
     * <p>
     * 新建、已成交、已取消等。
     * </p>
     *
     * @return 订单状态枚举
     */
    public OrderStatus getStatus() {
        return null;
    }

    /**
     * 获取委托价格
     * <p>
     * 限价单的委托价格,市价单可能为null。
     * </p>
     *
     * @return 委托价格
     */
    public BigDecimal getPrice() {
        return null;
    }

    /**
     * 获取委托数量
     * <p>
     * 统一使用quantity字段名,对应OKX的sz、Binance的origQty等。
     * </p>
     *
     * @return 委托数量
     */
    public BigDecimal getQuantity() {
        return null;
    }

    /**
     * 获取已成交数量
     * <p>
     * 订单已成交的部分数量。
     * </p>
     *
     * @return 已成交数量
     */
    public BigDecimal getFilledQuantity() {
        return null;
    }

    /**
     * 获取成交均价
     * <p>
     * 已成交部分的平均价格。
     * </p>
     *
     * @return 平均成交价格
     */
    public BigDecimal getAvgPrice() {
        return null;
    }

    /**
     * 获取手续费
     * <p>
     * 订单已产生或预估的手续费金额。
     * </p>
     *
     * @return 手续费金额
     */
    public BigDecimal getFee() {
        return null;
    }

    /**
     * 获取手续费币种
     * <p>
     * 手续费的计价币种。
     * </p>
     *
     * @return 手续费币种
     */
    public String getFeeCurrency() {
        return null;
    }

    /**
     * 获取创建时间
     * <p>
     * 订单创建的时间戳。
     * </p>
     *
     * @return 订单创建时间
     */
    public LocalDateTime getCreateTime() {
        return null;
    }

    /**
     * 获取更新时间
     * <p>
     * 订单最后更新的时间戳。
     * </p>
     *
     * @return 最后更新时间
     */
    public Long getUpdateTime() {
        return null;
    }

    /**
     * 获取杠杆倍数
     * <p>
     * 订单使用的杠杆倍数，用于计算保证金。
     * </p>
     *
     * @return 杠杆倍数
     */
    public BigDecimal getLever() {
        return null;
    }

    /**
     * 获取持仓方向
     * <p>
     * long=多头, short=空头, net=净持仓模式
     * </p>
     *
     * @return 持仓方向
     */
    public String getPosSide() {
        return null;
    }

    // ==================== 便捷判断方法 ====================

    /**
     * 判断是否为买单
     *
     * @return true=买单, false=非买单
     */
    public boolean isBuyOrder() {
        return OrderSide.BUY == getSide();
    }

    /**
     * 判断是否为卖单
     *
     * @return true=卖单, false=非卖单
     */
    public boolean isSellOrder() {
        return OrderSide.SELL == getSide();
    }

    /**
     * 判断订单是否已成交
     *
     * @return true=已成交, false=未成交
     */
    public boolean isFilled() {
        return OrderStatus.FILLED == getStatus();
    }

    /**
     * 判断订单是否已取消
     *
     * @return true=已取消, false=未取消
     */
    public boolean isCanceled() {
        return OrderStatus.CANCELED == getStatus();
    }

    /**
     * 判断订单是否活跃(仍在交易中)
     *
     * @return true=活跃, false=已完成
     */
    public boolean isActive() {
        return getStatus().isActive();
    }

    /**
     * 判断是否为市价单
     *
     * @return true=市价单, false=非市价单
     */
    public boolean isMarketOrder() {
        return OrderType.MARKET == getOrderType();
    }

    /**
     * 判断是否为限价单
     *
     * @return true=限价单, false=非限价单
     */
    public boolean isLimitOrder() {
        return OrderType.LIMIT == getOrderType();
    }

    /**
     * 计算成交比例
     * <p>
     * 已成交数量 / 委托数量,返回0.0~1.0之间的小数。
     * </p>
     *
     * @return 成交比例
     */
    public BigDecimal getFillRatio() {
        BigDecimal filled = getFilledQuantity();
        BigDecimal total = getQuantity();
        if (null == total || BigDecimal.ZERO.compareTo(total) == 0) {
            return BigDecimal.ZERO;
        }
        if (null == filled) {
            return BigDecimal.ZERO;
        }
        return filled.divide(total, 4, RoundingMode.HALF_UP);
    }

    /**
     * 计算未成交数量
     *
     * @return 未成交数量
     */
    public BigDecimal getUnfilledQuantity() {
        BigDecimal total = getQuantity();
        BigDecimal filled = getFilledQuantity();
        if (null == total) {
            return BigDecimal.ZERO;
        }
        if (null == filled) {
            return total;
        }
        return total.subtract(filled);
    }

    /**
     * 计算成交金额
     * <p>
     * 已成交数量 * 成交均价
     * </p>
     *
     * @return 成交金额
     */
    public BigDecimal getFilledAmount() {
        BigDecimal filled = getFilledQuantity();
        BigDecimal avgPrice = getAvgPrice();
        if (null == filled || null == avgPrice) {
            return BigDecimal.ZERO;
        }
        return filled.multiply(avgPrice);
    }
}

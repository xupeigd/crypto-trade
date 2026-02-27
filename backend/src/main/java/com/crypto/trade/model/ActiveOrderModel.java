package com.crypto.trade.model;

import com.crypto.trade.entity.CexTradingOrder;
import com.crypto.trade.entity.TradingOrder;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ActiveOrderModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActiveOrderModel {

    /**
     * 订单ID（数据库主键）
     */
    Long id;

    /**
     * 订单号（交易所返回的订单ID）
     */
    String orderId;

    /**
     * API密钥ID
     */
    Long apiKeyId;

    /**
     * 交易所名称
     */
    String vendor;

    /**
     * 合约品种
     */
    String instId;

    /**
     * 订单方向 (buy/sell)
     */
    String side;

    /**
     * 订单类型 (market/limit)
     */
    String orderType;

    /**
     * 交易模式
     */
    String tdMode;

    /**
     * 保证金币种
     */
    String ccy;

    /**
     * 杠杆倍数
     */
    BigDecimal lever;

    /**
     * 委托数量
     */
    BigDecimal sz;

    /**
     * 委托价格
     */
    BigDecimal px;

    /**
     * 委托金额
     */
    BigDecimal amt;

    /**
     * 订单状态
     */
    String orderState;

    /**
     * 成交均价
     */
    BigDecimal avgPx;

    /**
     * 已成交数量
     */
    BigDecimal filledSz;

    /**
     * 已成交金额
     */
    BigDecimal filledAmt;

    /**
     * 手续费
     */
    BigDecimal fee;

    /**
     * 手续费币种
     */
    String feeCcy;

    /**
     * 持仓方向 (long/short/net)
     */
    String posSide;

    /**
     * 止盈价格
     */
    BigDecimal takeProfitPrice;

    /**
     * 止损价格
     */
    BigDecimal stopLossPrice;

    /**
     * 止盈百分比
     */
    BigDecimal takeProfitPct;

    /**
     * 止损百分比
     */
    BigDecimal stopLossPct;

    /**
     * 最后更新时间
     */
    LocalDateTime updatedTime;

    /**
     * 创建时间
     */
    LocalDateTime createdTime;

    /**
     * 从TradingOrder实体转换为ActiveOrderModel
     * <p>
     * 重构说明：
     * - 系统订单字段从TradingOrder获取
     * - CEX订单字段从关联的CexTradingOrder获取
     * </p>
     *
     * @param entity TradingOrder实体
     * @return ActiveOrderModel模型
     */
    public static ActiveOrderModel fromEntity(TradingOrder entity) {
        if (null == entity) {
            return null;
        }

        // 获取关联的CEX订单
        CexTradingOrder cexOrder = entity.getCexOrder();

        return ActiveOrderModel.builder()
                .id(entity.getId())
                .orderId(entity.getOrderUuid()) // 使用系统订单UUID
                .apiKeyId(entity.getApiKeyId())
                .vendor(cexOrder != null ? cexOrder.getExchange() : null) // 从CEX订单获取交易所
                .instId(entity.getInstId())
                .side(entity.getSide())
                .orderType(entity.getOrderType())
                .tdMode(cexOrder != null ? cexOrder.getTdMode() : null) // 从CEX订单获取
                .ccy(cexOrder != null ? cexOrder.getCcy() : null) // 从CEX订单获取
                .lever(entity.getLever())
                .sz(cexOrder != null ? cexOrder.getSz() : null) // 从CEX订单获取
                .px(cexOrder != null ? cexOrder.getPx() : null) // 从CEX订单获取
                .amt(entity.getAmt())
                .orderState(entity.getOrderStatus()) // 使用系统订单状态
                .avgPx(cexOrder != null ? cexOrder.getAvgPx() : null) // 从CEX订单获取
                .filledSz(cexOrder != null ? cexOrder.getFilledSz() : null) // 从CEX订单获取
                .filledAmt(cexOrder != null ? cexOrder.getFilledAmt() : null) // 从CEX订单获取
                .fee(cexOrder != null ? cexOrder.getFee() : null) // 从CEX订单获取
                .feeCcy(cexOrder != null ? cexOrder.getFeeCcy() : null) // 从CEX订单获取
                .posSide(entity.getPosSide())
                .takeProfitPrice(entity.getTakeProfitPrice())
                .stopLossPrice(entity.getStopLossPrice())
                .takeProfitPct(entity.getTakeProfitPct())
                .stopLossPct(entity.getStopLossPct())
                .updatedTime(entity.getUpdatedTime())
                .createdTime(entity.getCreatedTime())
                .build();
    }

    /**
     * 判断订单是否为买入方向
     *
     * @return true如果是买入订单
     */
    public boolean isBuyOrder() {
        return "buy".equals(side);
    }

    /**
     * 判断订单是否为卖出方向
     *
     * @return true如果是卖出订单
     */
    public boolean isSellOrder() {
        return "sell".equals(side);
    }

    /**
     * 判断是否为市价单
     *
     * @return true如果是市价单
     */
    public boolean isMarketOrder() {
        return "market".equals(orderType);
    }

    /**
     * 判断是否为限价单
     *
     * @return true如果是限价单
     */
    public boolean isLimitOrder() {
        return "limit".equals(orderType);
    }

    /**
     * 判断订单是否已完成
     *
     * @return true如果订单已完成
     */
    public boolean isCompleted() {
        return "filled".equals(orderState) || "partially_filled".equals(orderState);
    }

    /**
     * 判断订单是否为活跃状态
     *
     * @return true如果订单为活跃状态
     */
    public boolean isActive() {
        return "live".equals(orderState) || "partially_filled".equals(orderState);
    }

}
package com.crypto.trade.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * TradingOrderModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TradingOrderModel {

    /**
     * 数据库主键ID
     */
    Long id;

    /**
     * 系统订单UUID（唯一标识）
     */
    String orderUuid;

    /**
     * API Key ID
     */
    Long apiKeyId;

    /**
     * 合约品种
     */
    String instId;

    /**
     * 订单方向（buy/sell）
     */
    String side;

    /**
     * 订单类型（market/limit）
     */
    String orderType;

    /**
     * 杠杆倍数
     */
    BigDecimal lever;

    /**
     * 持仓方向（long/short）
     */
    String posSide;

    /**
     * 委托金额（USDT）
     */
    BigDecimal amt;

    /**
     * 订单来源（web/bot/api）
     */
    String source = "web";

    /**
     * 系统订单状态
     * pending - 准备中
     * submitted - 已提交到CEX
     * success - 执行成功
     * failed - 执行失败
     * canceling - 撤销中
     * canceled - 已撤销
     */
    String orderStatus;

    /**
     * 错误信息
     */
    String errorMsg;

    /**
     * 创建时间（时间戳）
     */
    Long createdTime;

    /**
     * 更新时间（时间戳）
     */
    Long updatedTime;

    /**
     * 提交到CEX的时间（时间戳）
     */
    Long submittedTime;

    /**
     * 完成时间（时间戳）
     */
    Long completedTime;

    /**
     * CEX订单执行时间（时间戳）
     */
    Long execTime;

    /**
     * CEX订单详情（嵌套对象）
     * <p>
     * 包含所有CEX特定的订单信息
     * 如果订单还未提交到CEX,此字段可能为null
     * </p>
     */
    CexOrderDetails cexOrder;

    /**
     * 止盈价格
     */
    BigDecimal takeProfitPrice;

    /**
     * 止损价格
     */
    BigDecimal stopLossPrice;

    /**
     * 止盈百分比（%）
     */
    BigDecimal takeProfitPct;

    /**
     * 止损百分比（%）
     */
    BigDecimal stopLossPct;

    /**
     * 数量
     */
    BigDecimal sz;

    // ============================================
    // 内部类：CEX订单详情
    // ============================================

    /**
     * CEX订单详情嵌套对象
     * <p>
     * 存储所有CEX交易所特定的订单信息
     * </p>
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CexOrderDetails {

        /**
         * CEX订单ID（交易所返回的订单ID）
         */
        String orderId;

        /**
         * 交易所（okx/binance/bybit）
         */
        String exchange;

        /**
         * 交易模式（isolated/cross）
         */
        String tdMode;

        /**
         * 保证金币种
         */
        String ccy;

        /**
         * 委托数量
         */
        BigDecimal sz;

        /**
         * 合约面值（合约张数转为基础货币数量的乘数）
         */
        BigDecimal ctVal;

        /**
         * 合约乘数
         */
        String ctMult;

        /**
         * 委托价格
         */
        BigDecimal px;

        /**
         * CEX订单状态
         * live - 待成交
         * partially_filled - 部分成交
         * filled - 完全成交
         * canceled - 已撤销
         * failed - 失败
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
         * 成交比例（%）
         */
        BigDecimal fillRatio;

        /**
         * 手续费
         */
        BigDecimal fee;

        /**
         * 手续费币种
         */
        String feeCcy;

        /**
         * CEX订单创建时间
         */
        Long cTime;

        /**
         * CEX订单最后更新时间
         */
        Long uTime;
    }
}

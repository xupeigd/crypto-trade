package com.crypto.trade.rest.controller.model.response;

import com.crypto.trade.dto.cex.okx.OkxOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * OrderModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderModel {

    /**
     * 订单ID（核心字段）
     */
    String ordId;

    /**
     * 客户自定义订单ID
     */
    String clOrdId;

    /**
     * 算法订单ID（如果是附加的算法单才有值）
     */
    String algoId;

    /**
     * 算法客户订单ID
     */
    String algoClOrdId;

    /**
     * 附加算法订单客户ID（冰山/TWAP等）
     */
    String attachAlgoClOrdId;

    /**
     * 附加算法订单列表（目前一般为空）
     */
    List<Object> attachAlgoOrds;

    /**
     * 关联的算法订单（如止盈止损附加的）
     */
    OkxOrder.LinkedAlgoOrd linkedAlgoOrd;

    /**
     * 合约代码
     */
    String instId;

    /**
     * 合约类型
     */
    String instType;

    /**
     * 目标币种（现货保证金交易用）
     */
    String tgtCcy;

    /**
     * 保证金币种
     */
    String ccy;

    /**
     * 订单类型：limit, market, oco, trigger, conditional 等
     */
    String ordType;

    /**
     * 订单状态：live, partially_filled, filled, canceled
     */
    String state;

    /**
     * 持仓方向：Long / short / net
     */
    String posSide;

    /**
     * 买卖方向：buy / sell
     */
    String side;

    /**
     * 保证金模式：isolated / cross
     */
    String tdMode;

    /**
     * 是否只减仓
     */
    boolean reduceOnly;

    /**
     * 是否为止盈止损限价单
     */
    boolean isTpLimit;

    /**
     * 杠杆倍数
     */
    BigDecimal lever;

    /**
     * 订单标签
     */
    String tag;

    /**
     * 订单类别：normal / twap / iceberg 等
     */
    String category;

    /**
     * 自成交防范模式：cancel_maker / cancel_taker / cancel_both
     */
    String stpMode;

    /**
     * 自成交防范ID
     */
    String stpId;

    /**
     * 快速保证金类型
     */
    String quickMgnType;

    /**
     * 来源
     */
    String source;

    /**
     * 取消来源
     */
    String cancelSource;

    /**
     * 取消原因
     */
    String cancelSourceReason;

    /**
     * 价格类型（目前为空）
     */
    String pxType;

    /**
     * 委托价格
     */
    BigDecimal px;

    /**
     * 委托数量（张或币）
     */
    BigDecimal sz;

    /**
     * 已成交数量
     */
    BigDecimal accFillSz;

    /**
     * 已成交数量（部分成交时）
     */
    BigDecimal fillSz;

    /**
     * 平均成交价
     */
    BigDecimal avgPx;

    /**
     * 最新成交价
     */
    BigDecimal fillPx;

    /**
     * 手续费（负数表示已支付）
     */
    BigDecimal fee;

    /**
     * 手续费币种
     */
    String feeCcy;

    /**
     * 返佣
     */
    BigDecimal rebate;

    /**
     * 返佣币种
     */
    String rebateCcy;

    /**
     * 已实现盈亏（平仓时有值）
     */
    BigDecimal pnl;

    /**
     * 成交时间（毫秒）
     */
    Long fillTime;

    /**
     * 成交ID
     */
    String tradeId;

    /**
     * 创建时间（毫秒）
     */
    Long cTime;

    /**
     * 更新时间（毫秒）
     */
    Long uTime;

    /**
     * 止盈触发价（如果是附加止盈止损）
     */
    BigDecimal tpTriggerPx;

    /**
     * 止盈挂单价
     */
    BigDecimal tpOrdPx;

    /**
     * 止盈触发价类型
     */
    String tpTriggerPxType;

    /**
     * 止损触发价
     */
    BigDecimal slTriggerPx;

    /**
     * 止损挂单价
     */
    BigDecimal slOrdPx;

    /**
     * 止损触发价类型
     */
    String slTriggerPxType;

    /**
     * 交易报价币种（某些模式下）
     */
    String tradeQuoteCcy;

    @Data
    public static class LinkedAlgoOrd {
        String algoId;
    }

}

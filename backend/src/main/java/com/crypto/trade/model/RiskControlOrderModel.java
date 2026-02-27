package com.crypto.trade.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * RiskControlOrderModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskControlOrderModel {

    /**
     * 订单ID
     */
    Long orderId;

    /**
     * 原始订单ID（关联到交易订单表）
     */
    String originalOrderId;

    /**
     * API密钥ID
     */
    Long apiKeyId;

    /**
     * 交易对（如BTC-USDT-SWAP）
     */
    String symbol;

    /**
     * 订单类型（limit/market）
     */
    String orderType;

    /**
     * 订单方向（buy/sell）
     */
    String side;

    /**
     * 订单数量
     */
    BigDecimal quantity;

    /**
     * 订单价格（限价单必填）
     */
    BigDecimal price;

    /**
     * 止盈价格
     */
    BigDecimal takeProfitPrice;

    /**
     * 止损价格
     */
    BigDecimal stopLossPrice;

    /**
     * 风控审核状态
     */
    String auditStatus;

    /**
     * 审核人（暂时为空，后续可扩展）
     */
    String auditor;

    /**
     * 审核时间
     */
    Long auditTime;

    /**
     * 驳回原因
     */
    String rejectionReason;

    /**
     * 创建时间
     */
    Long createTime;

    /**
     * 更新时间
     */
    Long updateTime;

    /**
     * 订单来源（okx_trading）
     */
    String orderSource;

    /**
     * 持仓方向（long/short）
     */
    String posSide;

    /**
     * 杠杆倍数
     */
    BigDecimal lever;

    /**
     * 原始成本金额（USDT）
     */
    BigDecimal originalAmount;

    /**
     * 预估总占用资金(保证金+开仓手续费+平仓手续费)
     */
    BigDecimal estimatedTotalCapital;

    /**
     * 风控等级（由交易风格决定）
     */
    String riskLevel;

}

package com.crypto.trade.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * OrderRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
public class OrderRequest {
    /**
     * API密钥ID
     */
    private Long apiKeyId;

    /**
     * 来源TradeAction ID
     * 记录该订单是由哪个TradeAction产生的
     */
    private Long actionId;

    /**
     * 来源调用记录ID (冗余字段)
     * 记录该订单是由哪个LlmCallRecord产生的
     */
    private Long recordId;

    /**
     * 合约品种
     */
    private String instId;

    /**
     * 订单方向 (buy/sell)
     */
    private String side;

    /**
     * 订单类型 (market/limit)
     */
    private String orderType;

    /**
     * 委托数量
     */
    private BigDecimal sz;

    /**
     * 委托价格（限价单使用）
     */
    private BigDecimal px;

    /**
     * 成本金额（USDT）
     */
    private BigDecimal amount;

    /**
     * 杠杆倍数
     */
    private BigDecimal lever;

    /**
     * 止盈价格
     */
    private BigDecimal takeProfitPrice;

    /**
     * 止损价格
     */
    private BigDecimal stopLossPrice;

    /**
     * 持仓方向 (long/short)
     */
    private String posSide;

    /**
     * 订单来源 (web/ai)
     * web: 用户手动下单
     * ai: AI自动下单
     */
    private String source = "web";

    /**
     * 是否绕过风控
     */
    private boolean bypassRiskControl;

    /**
     * 智能体ID（用于执行模式优先级判断）
     */
    private Long agentId;

    /**
     * 风控配置ID（AI交易配置ID，用于执行模式优先级判断）
     */
    private Long riskControlId;
}
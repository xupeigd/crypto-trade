package com.crypto.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TradingResult
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TradingResult {

    /**
     * 订单ID
     */
    String orderId;

    /**
     * 客户端订单ID
     */
    String clOrdId;

    /**
     * 算法订单ID
     */
    String algoId;

    /**
     * 状态码
     */
    String sCode;

    /**
     * 状态消息
     */
    String sMsg;

    /**
     * 操作是否成功
     */
    Boolean success;

    /**
     * 是否为手动风控模式
     */
    Boolean isManualRiskMode;

    /**
     * 响应数据
     */
    Object data;

    /**
     * 创建成功的交易结果
     *
     * @param orderId 订单ID
     * @param message 成功消息
     * @return TradingResult实例
     */
    public static TradingResult success(String orderId, String message) {
        return TradingResult.builder()
                .orderId(orderId)
                .sCode("0")
                .sMsg(message)
                .success(true)
                .build();
    }

    /**
     * 创建成功的交易结果（带额外数据）
     *
     * @param orderId 订单ID
     * @param message 成功消息
     * @param data    额外数据
     * @return TradingResult实例
     */
    public static TradingResult success(String orderId, String message, Object data) {
        return TradingResult.builder()
                .orderId(orderId)
                .sCode("0")
                .sMsg(message)
                .success(true)
                .data(data)
                .build();
    }

    /**
     * 创建失败的交易结果
     *
     * @param message 失败消息
     * @return TradingResult实例
     */
    public static TradingResult failure(String message) {
        return TradingResult.builder()
                .sCode("1")
                .sMsg(message)
                .success(false)
                .build();
    }

    /**
     * 创建失败的交易结果（带状态码）
     *
     * @param sCode   状态码
     * @param message 失败消息
     * @return TradingResult实例
     */
    public static TradingResult failure(String sCode, String message) {
        return TradingResult.builder()
                .sCode(sCode)
                .sMsg(message)
                .success(false)
                .build();
    }

    /**
     * 设置手动风控模式标识
     *
     * @param isManualRiskMode 是否为手动风控模式
     * @return 当前实例
     */
    public TradingResult withManualRiskMode(Boolean isManualRiskMode) {
        this.isManualRiskMode = isManualRiskMode;
        return this;
    }

    /**
     * 设置客户端订单ID
     *
     * @param clOrdId 客户端订单ID
     * @return 当前实例
     */
    public TradingResult withClOrdId(String clOrdId) {
        this.clOrdId = clOrdId;
        return this;
    }

    /**
     * 判断操作是否成功
     *
     * @return true如果成功
     */
    public boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }

    /**
     * 判断是否为手动风控模式
     *
     * @return true如果是手动风控模式
     */
    public boolean isManualRiskMode() {
        return Boolean.TRUE.equals(isManualRiskMode);
    }

    /**
     * 获取成功状态显示文本
     *
     * @return 成功状态描述
     */
    public String getSuccessDisplay() {
        if (isSuccess()) {
            return "操作成功";
        } else {
            return "操作失败: " + (null != sMsg ? sMsg : "未知错误");
        }
    }
}
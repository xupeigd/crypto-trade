package com.crypto.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * StopLossResult
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StopLossResult {

    /**
     * 算法订单ID
     */
    String algoId;

    /**
     * 订单ID（如果有）
     */
    String orderId;

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
     * 止盈触发价格
     */
    String tpTriggerPx;

    /**
     * 止损触发价格
     */
    String slTriggerPx;

    /**
     * 响应数据
     */
    Object data;

    /**
     * 创建成功的止盈止损结果
     *
     * @param algoId  算法订单ID
     * @param message 成功消息
     * @return StopLossResult实例
     */
    public static StopLossResult success(String algoId, String message) {
        return StopLossResult.builder()
                .algoId(algoId)
                .sCode("0")
                .sMsg(message)
                .success(true)
                .build();
    }

    /**
     * 创建成功的止盈止损结果（带价格信息）
     *
     * @param algoId      算法订单ID
     * @param message     成功消息
     * @param tpTriggerPx 止盈触发价格
     * @param slTriggerPx 止损触发价格
     * @return StopLossResult实例
     */
    public static StopLossResult success(String algoId, String message, String tpTriggerPx, String slTriggerPx) {
        return StopLossResult.builder()
                .algoId(algoId)
                .sCode("0")
                .sMsg(message)
                .success(true)
                .tpTriggerPx(tpTriggerPx)
                .slTriggerPx(slTriggerPx)
                .build();
    }

    /**
     * 创建失败的止盈止损结果
     *
     * @param message 失败消息
     * @return StopLossResult实例
     */
    public static StopLossResult failure(String message) {
        return StopLossResult.builder()
                .sCode("1")
                .sMsg(message)
                .success(false)
                .build();
    }

    /**
     * 创建失败的止盈止损结果（带状态码）
     *
     * @param sCode   状态码
     * @param message 失败消息
     * @return StopLossResult实例
     */
    public static StopLossResult failure(String sCode, String message) {
        return StopLossResult.builder()
                .sCode(sCode)
                .sMsg(message)
                .success(false)
                .build();
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
     * 判断是否设置了止盈
     *
     * @return true如果设置了止盈
     */
    public boolean hasTakeProfit() {
        return null != tpTriggerPx && !tpTriggerPx.trim().isEmpty();
    }

    /**
     * 判断是否设置了止损
     *
     * @return true如果设置了止损
     */
    public boolean hasStopLoss() {
        return null != slTriggerPx && !slTriggerPx.trim().isEmpty();
    }

    /**
     * 获取操作类型描述
     *
     * @return 操作类型描述
     */
    public String getOperationType() {
        if (hasTakeProfit() && hasStopLoss()) {
            return "止盈止损";
        } else if (hasTakeProfit()) {
            return "止盈";
        } else if (hasStopLoss()) {
            return "止损";
        } else {
            return "操作";
        }
    }

    /**
     * 获取成功状态显示文本
     *
     * @return 成功状态描述
     */
    public String getSuccessDisplay() {
        if (isSuccess()) {
            String operation = getOperationType();
            String info = null != algoId ? " (算法订单: " + algoId + ")" : "";
            return operation + "设置成功" + info;
        } else {
            return getOperationType() + "设置失败: " + (null != sMsg ? sMsg : "未知错误");
        }
    }

    /**
     * 获取触发条件描述
     *
     * @return 触发条件描述
     */
    public String getTriggerCondition() {
        StringBuilder condition = new StringBuilder();

        if (hasTakeProfit()) {
            condition.append("止盈: ").append(tpTriggerPx);
        }

        if (hasStopLoss()) {
            if (condition.length() > 0) {
                condition.append(", ");
            }
            condition.append("止损: ").append(slTriggerPx);
        }

        return condition.length() > 0 ? condition.toString() : "无触发条件";
    }
}
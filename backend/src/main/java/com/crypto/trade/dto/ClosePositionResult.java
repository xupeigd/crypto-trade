package com.crypto.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * ClosePositionResult
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClosePositionResult {

    /**
     * 订单ID
     */
    String orderId;

    /**
     * 客户端订单ID
     */
    String clOrdId;

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
     * 合约品种
     */
    String instId;

    /**
     * 持仓方向 (long/short)
     */
    String posSide;

    /**
     * 平仓数量
     */
    BigDecimal sz;

    /**
     * 平仓类型 (long/short)
     */
    String closeType;

    /**
     * 平均成交价格
     */
    String avgPx;

    /**
     * 成交数量
     */
    String filledSz;

    /**
     * 成交金额
     */
    String filledAmt;

    /**
     * 手续费
     */
    String fee;

    /**
     * 手续费币种
     */
    String feeCcy;

    /**
     * 响应数据
     */
    Object data;

    /**
     * 创建成功的平仓结果
     *
     * @param orderId 订单ID
     * @param message 成功消息
     * @return ClosePositionResult实例
     */
    public static ClosePositionResult success(String orderId, String message) {
        return ClosePositionResult.builder()
                .orderId(orderId)
                .sCode("0")
                .sMsg(message)
                .success(true)
                .build();
    }

    /**
     * 创建成功的平仓结果（带详细信息）
     *
     * @param orderId 订单ID
     * @param message 成功消息
     * @param instId  合约品种
     * @param posSide 持仓方向
     * @param sz      平仓数量
     * @return ClosePositionResult实例
     */
    public static ClosePositionResult success(String orderId, String message, String instId, String posSide, BigDecimal sz) {
        return ClosePositionResult.builder()
                .orderId(orderId)
                .sCode("0")
                .sMsg(message)
                .success(true)
                .instId(instId)
                .posSide(posSide)
                .sz(sz)
                .closeType("market")
                .build();
    }

    /**
     * 创建失败的平仓结果
     *
     * @param message 失败消息
     * @return ClosePositionResult实例
     */
    public static ClosePositionResult failure(String message) {
        return ClosePositionResult.builder()
                .sCode("1")
                .sMsg(message)
                .success(false)
                .build();
    }

    /**
     * 创建失败的平仓结果（带状态码）
     *
     * @param sCode   状态码
     * @param message 失败消息
     * @return ClosePositionResult实例
     */
    public static ClosePositionResult failure(String sCode, String message) {
        return ClosePositionResult.builder()
                .sCode(sCode)
                .sMsg(message)
                .success(false)
                .build();
    }

    /**
     * 设置平仓类型
     *
     * @param closeType 平仓类型
     * @return 当前实例
     */
    public ClosePositionResult withCloseType(String closeType) {
        this.closeType = closeType;
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
     * 判断是否为完全成交
     *
     * @return true如果完全成交
     */
    public boolean isFullyFilled() {
        return null != filledSz && null != sz &&
                new BigDecimal(filledSz).compareTo(sz) >= 0;
    }

    /**
     * 获取平仓方向显示
     *
     * @return 平仓方向描述
     */
    public String getCloseDirectionDisplay() {
        if (null == posSide) {
            return "未知方向";
        }
        switch (posSide) {
            case "long":
                return "平多";
            case "short":
                return "平空";
            default:
                return posSide;
        }
    }

    /**
     * 获取平仓类型显示
     *
     * @return 平仓类型描述
     */
    public String getCloseTypeDisplay() {
        if ("market".equals(closeType)) {
            return "市价平仓";
        } else if ("limit".equals(closeType)) {
            return "限价平仓";
        } else {
            return null != closeType ? closeType : "平仓";
        }
    }

    /**
     * 获取成功状态显示文本
     *
     * @return 成功状态描述
     */
    public String getSuccessDisplay() {
        if (isSuccess()) {
            String direction = getCloseDirectionDisplay();
            String type = getCloseTypeDisplay();
            String info = null != instId ? " (" + instId + ")" : "";
            return direction + type + "成功" + info;
        } else {
            return "平仓失败: " + (null != sMsg ? sMsg : "未知错误");
        }
    }

    /**
     * 获取成交信息显示
     *
     * @return 成交信息描述
     */
    public String getFillInfo() {
        if (!isSuccess() || null == filledSz) {
            return "未成交";
        }

        StringBuilder info = new StringBuilder();
        info.append("成交数量: ").append(filledSz);

        if (null != avgPx) {
            info.append(", 成交均价: ").append(avgPx);
        }

        if (null != filledAmt) {
            info.append(", 成交金额: ").append(filledAmt);
        }

        return info.toString();
    }
}
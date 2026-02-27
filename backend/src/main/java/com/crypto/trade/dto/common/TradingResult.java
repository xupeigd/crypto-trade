package com.crypto.trade.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
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
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradingResult {

    /**
     * 交易是否成功
     */
    public Boolean success;

    /**
     * 订单ID
     */
    public String orderId;

    /**
     * 交易消息
     */
    public String message;

    /**
     * 是否为手动风控模式
     */
    public Boolean isManualRiskMode = false;

    // OKX API 完整响应字段
    /**
     * 响应代码
     */
    public String code;

    /**
     * 订单状态代码
     */
    public String sCode;

    /**
     * 订单状态消息
     */
    public String sMsg;

    /**
     * 请求开始时间戳
     */
    public String inTime;

    /**
     * 响应结束时间戳
     */
    public String outTime;

    /**
     * 客户端订单ID
     */
    public String clOrdId;

    /**
     * 数据时间戳
     */
    public String ts;

    /**
     * 标签
     */
    public String tag;

    // 构造函数
    public TradingResult(Boolean success, String orderId, String message) {
        this.success = success;
        this.orderId = orderId;
        this.message = message;
        this.isManualRiskMode = false;
    }

    public TradingResult(Boolean success, String orderId, String message, Boolean isManualRiskMode) {
        this.success = success;
        this.orderId = orderId;
        this.message = message;
        this.isManualRiskMode = isManualRiskMode;
    }

    // 静态工厂方法

    /**
     * 创建成功的交易结果（TradingOrderService兼容）
     */
    public static TradingResult success(String orderId, String message) {
        return new TradingResult(true, orderId, message, false);
    }

    /**
     * 创建成功的交易结果（支持手动风控模式）
     */
    public static TradingResult success(String orderId, String message, boolean isManualRiskMode) {
        return new TradingResult(true, orderId, message, isManualRiskMode);
    }

    /**
     * 创建失败的交易结果（TradingOrderService兼容）
     */
    public static TradingResult failure(String message) {
        return new TradingResult(false, null, message, false);
    }

    /**
     * 创建失败的交易结果（支持手动风控模式）
     */
    public static TradingResult failure(String message, boolean isManualRiskMode) {
        return new TradingResult(false, null, message, isManualRiskMode);
    }

    /**
     * 创建OKX API风格的交易结果
     */
    public static TradingResult okxSuccess(String orderId, String message) {
        TradingResult result = new TradingResult(true, orderId, message);
        return result;
    }

    public static TradingResult okxFailure(String message) {
        TradingResult result = new TradingResult(false, null, message);
        return result;
    }

    // 便利方法

    /**
     * 检查交易是否成功
     */
    public boolean isSuccess() {
        return Boolean.TRUE.equals(this.success);
    }

    /**
     * 检查是否为手动风控模式
     */
    public boolean isManualRiskModeEnabled() {
        return Boolean.TRUE.equals(this.isManualRiskMode);
    }

    /**
     * 设置手动风控模式
     */
    public void setManualRiskMode(boolean isManualRiskMode) {
        this.isManualRiskMode = isManualRiskMode;
    }

    /**
     * 链式设置手动风控模式
     */
    public TradingResult withManualRiskMode(Boolean isManualRiskMode) {
        this.isManualRiskMode = isManualRiskMode;
        return this;
    }

    /**
     * 链式设置客户端订单ID
     */
    public TradingResult withClOrdId(String clOrdId) {
        this.clOrdId = clOrdId;
        return this;
    }
}
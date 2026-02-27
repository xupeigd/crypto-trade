package com.crypto.trade.service.cex;

import com.crypto.trade.dto.cex.response.CexOperationResponse;
import com.crypto.trade.dto.cex.response.CexOrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CEX通用错误处理器
 * <p>
 * 提供交易所无关的统一错误处理机制。
 * 各交易所的具体错误码处理应在各自的实现类中完成(如OkxApiService)。
 * </p>
 * <p>
 * <b>架构说明:</b>
 * <ul>
 * <li>提供通用的错误消息解析</li>
 * <li>处理常见的CEX错误场景</li>
 * <li>支持未来扩展到多个交易所</li>
 * <li>不包含任何特定交易所的错误码</li>
 * </ul>
 * </p>
 *
 * @author Page
 * @since 2025-01-22
 */
@Slf4j
@Component
public class CexErrorHandler {

    // ==================== 通用错误处理 ====================

    /**
     * 判断操作是否成功
     *
     * @param response 操作响应
     * @return true-成功, false-失败
     */
    public boolean isSuccess(CexOperationResponse response) {
        if (null == response) {
            return false;
        }
        return Boolean.TRUE.equals(response.getSuccess());
    }

    /**
     * 判断下单是否成功
     *
     * @param response 下单响应
     * @return true-成功, false-失败
     */
    public boolean isSuccess(CexOrderResponse response) {
        if (null == response) {
            return false;
        }
        return Boolean.TRUE.equals(response.getSuccess());
    }

    /**
     * 提取错误消息
     *
     * @param response 操作响应
     * @return 用户友好的错误消息
     */
    public String getErrorMessage(CexOperationResponse response) {
        if (null == response) {
            return "操作响应为空";
        }

        if (Boolean.TRUE.equals(response.getSuccess())) {
            return "操作成功";
        }

        // 优先返回具体错误消息
        if (response.getErrorMessage() != null && !response.getErrorMessage().isEmpty()) {
            return response.getErrorMessage();
        }

        // 返回错误码
        if (response.getErrorCode() != null && !response.getErrorCode().isEmpty()) {
            return String.format("操作失败(错误码: %s)", response.getErrorCode());
        }

        return "操作失败,原因未知";
    }

    /**
     * 提取下单错误消息
     *
     * @param response 下单响应
     * @return 用户友好的错误消息
     */
    public String getErrorMessage(CexOrderResponse response) {
        if (null == response) {
            return "订单响应为空";
        }

        if (Boolean.TRUE.equals(response.getSuccess())) {
            return "下单成功";
        }

        // 优先返回具体错误消息
        if (response.getErrorMessage() != null && !response.getErrorMessage().isEmpty()) {
            return response.getErrorMessage();
        }

        // 返回错误码
        if (response.getErrorCode() != null && !response.getErrorCode().isEmpty()) {
            return String.format("下单失败(错误码: %s)", response.getErrorCode());
        }

        return "下单失败,原因未知";
    }

    // ==================== 常见错误场景处理 ====================

    /**
     * 判断是否为余额不足错误
     *
     * @param errorCode 错误码
     * @return true-余额不足, false-其他错误
     */
    public boolean isInsufficientBalance(String errorCode) {
        if (null == errorCode) {
            return false;
        }

        // 常见的余额不足错误码(不同交易所可能不同)
        // OKX: 51001, 51002
        // Binance: -2010
        // 这里只处理通用情况,特定交易所的错误码在各自实现中处理
        return errorCode.equals("51001") || errorCode.equals("51002")
                || errorCode.equals("-2010")
                || errorCode.toLowerCase().contains("insufficient");
    }

    /**
     * 判断是否为仓位不存在错误
     *
     * @param errorCode 错误码
     * @return true-仓位不存在, false-其他错误
     */
    public boolean isPositionNotFound(String errorCode) {
        if (null == errorCode) {
            return false;
        }

        return errorCode.equals("51004") || errorCode.equals("-4051")
                || errorCode.toLowerCase().contains("position") && errorCode.toLowerCase().contains("not");
    }

    /**
     * 判断是否为订单不存在错误
     *
     * @param errorCode 错误码
     * @return true-订单不存在, false-其他错误
     */
    public boolean isOrderNotFound(String errorCode) {
        if (null == errorCode) {
            return false;
        }

        return errorCode.equals("51008") || errorCode.equals("-2013")
                || errorCode.toLowerCase().contains("order") && errorCode.toLowerCase().contains("not");
    }

    /**
     * 判断是否为参数错误
     *
     * @param errorCode 错误码
     * @return true-参数错误, false-其他错误
     */
    public boolean isInvalidParameter(String errorCode) {
        if (null == errorCode) {
            return false;
        }

        return errorCode.equals("50001") || errorCode.equals("-1100")
                || errorCode.toLowerCase().contains("invalid") && errorCode.toLowerCase().contains("parameter");
    }

    /**
     * 判断是否为网络错误
     *
     * @param errorMessage 错误消息
     * @return true-网络错误, false-其他错误
     */
    public boolean isNetworkError(String errorMessage) {
        if (null == errorMessage) {
            return false;
        }

        return errorMessage.toLowerCase().contains("timeout")
                || errorMessage.toLowerCase().contains("connection")
                || errorMessage.toLowerCase().contains("network")
                || errorMessage.toLowerCase().contains("socket");
    }

    /**
     * 判断是否为速率限制错误
     *
     * @param errorCode    错误码
     * @param errorMessage 错误消息
     * @return true-速率限制, false-其他错误
     */
    public boolean isRateLimitError(String errorCode, String errorMessage) {
        if (errorCode != null) {
            return errorCode.equals("50011") || errorCode.equals("-1003")
                    || errorCode.equals("50029") || errorCode.equals("-1021");
        }

        if (errorMessage != null) {
            return errorMessage.toLowerCase().contains("rate limit")
                    || errorMessage.toLowerCase().contains("too many requests");
        }

        return false;
    }

    // ==================== 用户友好消息转换 ====================

    /**
     * 将技术错误转换为用户友好消息
     *
     * @param errorCode    错误码
     * @param errorMessage 原始错误消息
     * @return 用户友好的错误消息
     */
    public String toUserFriendlyMessage(String errorCode, String errorMessage) {
        // 优先使用原始错误消息(如果已有友好描述)
        if (errorMessage != null && !errorMessage.isEmpty()) {
            return errorMessage;
        }

        if (errorCode == null) {
            return "操作失败,原因未知";
        }

        // 根据错误码提供友好消息
        if (isInsufficientBalance(errorCode)) {
            return "账户余额不足,请检查账户余额";
        }

        if (isPositionNotFound(errorCode)) {
            return "持仓不存在,请先查询持仓信息";
        }

        if (isOrderNotFound(errorCode)) {
            return "订单不存在,可能已被成交或取消";
        }

        if (isInvalidParameter(errorCode)) {
            return "订单参数无效,请检查订单参数";
        }

        if (isRateLimitError(errorCode, null)) {
            return "请求过于频繁,请稍后重试";
        }

        // 默认返回错误码
        return String.format("操作失败(错误码: %s)", errorCode);
    }

    /**
     * 记录错误日志
     *
     * @param operation    操作名称
     * @param errorCode    错误码
     * @param errorMessage 错误消息
     */
    public void logError(String operation, String errorCode, String errorMessage) {
        log.error("CEX操作失败 - 操作: {}, 错误码: {}, 错误消息: {}",
                operation, errorCode, errorMessage);

        // 根据错误类型提供额外的诊断信息
        if (isNetworkError(errorMessage)) {
            log.warn("检测到网络问题,请检查网络连接");
        }

        if (isRateLimitError(errorCode, errorMessage)) {
            log.warn("检测到速率限制,请降低请求频率");
        }

        if (isInsufficientBalance(errorCode)) {
            log.warn("检测到余额不足,请检查账户余额");
        }
    }
}

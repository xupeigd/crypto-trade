package com.crypto.trade.exception;

/**
 * TradeException
 * 异常类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class TradeException extends RuntimeException {

    private final String errorCode;

    public TradeException(String message) {
        super(message);
        this.errorCode = "TRADE_ERROR";
    }

    public TradeException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TradeException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

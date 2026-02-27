package com.crypto.trade.exception;

/**
 * TradeExecutionException
 * 异常类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class TradeExecutionException extends TradeException {

    public TradeExecutionException(String message) {
        super("TRADE_EXECUTION_ERROR", message);
    }

    public TradeExecutionException(String message, Throwable cause) {
        super("TRADE_EXECUTION_ERROR", message, cause);
    }
}

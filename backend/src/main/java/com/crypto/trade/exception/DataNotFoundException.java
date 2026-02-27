package com.crypto.trade.exception;

/**
 * DataNotFoundException
 * 异常类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class DataNotFoundException extends TradeException {

    public DataNotFoundException(String message) {
        super("DATA_NOT_FOUND", message);
    }

    public DataNotFoundException(String resourceName, Long id) {
        super("DATA_NOT_FOUND", String.format("%s not found with id: %d", resourceName, id));
    }
}

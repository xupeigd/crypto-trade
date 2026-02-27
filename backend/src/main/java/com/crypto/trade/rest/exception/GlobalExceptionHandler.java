package com.crypto.trade.rest.exception;

import com.crypto.trade.exception.DataNotFoundException;
import com.crypto.trade.exception.TradeException;
import com.crypto.trade.exception.TradeExecutionException;
import com.crypto.trade.model.ctm.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * GlobalExceptionHandler
 * REST控制器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理交易业务异常
     */
    @ExceptionHandler(TradeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleTradeException(TradeException e) {
        log.error("交易业务异常 - errorCode: {}, message: {}", e.getErrorCode(), e.getMessage(), e);
        return ApiResponse.fail(e.getMessage());
    }

    /**
     * 处理交易执行异常
     */
    @ExceptionHandler(TradeExecutionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleTradeExecutionException(TradeExecutionException e) {
        log.error("交易执行异常 - message: {}", e.getMessage(), e);
        return ApiResponse.fail("交易执行失败: " + e.getMessage());
    }

    /**
     * 处理数据未找到异常
     */
    @ExceptionHandler(DataNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleDataNotFoundException(DataNotFoundException e) {
        log.warn("数据未找到 - message: {}", e.getMessage());
        return ApiResponse.fail(e.getMessage());
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("非法参数异常 - message: {}", e.getMessage(), e);
        return ApiResponse.fail("参数错误: " + e.getMessage());
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常 - message: {}", e.getMessage(), e);
        return ApiResponse.fail("系统错误: " + e.getMessage());
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("未知异常 - message: {}", e.getMessage(), e);
        return ApiResponse.fail("系统内部错误，请稍后重试");
    }
}

package com.crypto.trade.service.prompt;

/**
 * PromptProcessException
 * 异常类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class PromptProcessException extends Exception {

    private final String processorName;
    private final String errorCode;

    public PromptProcessException(String message, String processorName) {
        super(message);
        this.processorName = processorName;
        this.errorCode = "PROCESS_ERROR";
    }

    public PromptProcessException(String message, String processorName, String errorCode) {
        super(message);
        this.processorName = processorName;
        this.errorCode = errorCode;
    }

    public PromptProcessException(String message, Throwable cause, String processorName) {
        super(message, cause);
        this.processorName = processorName;
        this.errorCode = "PROCESS_ERROR";
    }

    public PromptProcessException(String message, Throwable cause, String processorName, String errorCode) {
        super(message, cause);
        this.processorName = processorName;
        this.errorCode = errorCode;
    }

    public String getProcessorName() {
        return processorName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 常用错误代码
     */
    public static class ErrorCodes {
        public static final String DATA_NOT_AVAILABLE = "DATA_NOT_AVAILABLE";
        public static final String PROCESSING_FAILED = "PROCESSING_FAILED";
        public static final String CONFIGURATION_ERROR = "CONFIGURATION_ERROR";
        public static final String TIMEOUT_ERROR = "TIMEOUT_ERROR";
        public static final String INVALID_INPUT = "INVALID_INPUT";
        public static final String DEPENDENCY_FAILED = "DEPENDENCY_FAILED";
        public static final String TEMPLATE_ERROR = "TEMPLATE_ERROR";
    }
}
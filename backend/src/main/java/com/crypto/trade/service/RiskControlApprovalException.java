package com.crypto.trade.service;

/**
 * RiskControlApprovalException
 * 异常类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class RiskControlApprovalException extends RuntimeException {

    private final RiskControlErrorType errorType;

    public RiskControlApprovalException(String message, RiskControlErrorType errorType) {
        super(message);
        this.errorType = errorType;
    }

    public RiskControlApprovalException(String message, RiskControlErrorType errorType, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public RiskControlErrorType getErrorType() {
        return errorType;
    }

    /**
     * 获取用户友好的错误类型描述
     */
    public String getErrorTypeDescription() {
        return errorType.getDescription();
    }

    /**
     * 获取错误建议
     */
    public String getSuggestion() {
        return errorType.getSuggestion();
    }
}
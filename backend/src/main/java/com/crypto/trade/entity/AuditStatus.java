package com.crypto.trade.entity;

/**
 * AuditStatus
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public enum AuditStatus {
    /**
     * 待审核
     */
    PENDING("待审核"),

    /**
     * 已通过
     */
    APPROVED("已通过"),

    /**
     * 已驳回
     */
    REJECTED("已驳回");

    private final String description;

    AuditStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
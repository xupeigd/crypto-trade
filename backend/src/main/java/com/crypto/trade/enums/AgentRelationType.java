package com.crypto.trade.enums;

import lombok.Getter;

/**
 * AgentRelationType
 * 智能体关系类型枚举
 *
 * @author page
 * @date 2026-03-22
 */
@Getter
public enum AgentRelationType {

    MASTER_SLAVE("MASTER_SLAVE", "主从模式", "主Agent协调多个子Agent"),
    COLLABORATIVE("COLLABORATIVE", "协作模式", "多个Agent平等协作"),
    HIERARCHICAL("HIERARCHICAL", "层级模式", "多级调用链");

    private final String code;
    private final String description;
    private final String detail;

    AgentRelationType(String code, String description, String detail) {
        this.code = code;
        this.description = description;
        this.detail = detail;
    }

    /**
     * 根据代码获取枚举
     */
    public static AgentRelationType fromCode(String code) {
        for (AgentRelationType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的Agent关系类型: " + code);
    }
}

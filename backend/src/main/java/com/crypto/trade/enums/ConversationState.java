package com.crypto.trade.enums;

/**
 * ConversationState
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public enum ConversationState {

    /**
     * 处理中
     * 对话正在进行,可能需要继续多轮交互
     */
    PROCESSING("PROCESSING", "处理中"),

    /**
     * 已完成
     * 对话已完成,AI已给出最终决策
     */
    COMPLETED("COMPLETED", "已完成"),

    /**
     * 已终止
     * 对话被强制终止,通常因为达到最大轮次限制
     */
    TERMINATED("TERMINATED", "已终止"),

    /**
     * 需要更多信息
     * AI表示需要更多数据或信息才能给出决策
     */
    NEED_MORE_INFO("NEED_MORE_INFO", "需要更多信息"),

    /**
     * 错误状态
     * 对话过程中发生错误
     */
    ERROR_STATE("ERROR_STATE", "错误状态");

    private final String code;
    private final String description;

    ConversationState(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举值
     *
     * @param code 状态代码
     * @return 对应的枚举值, 如果未找到返回null
     */
    public static ConversationState fromCode(String code) {
        if (null == code) {
            return null;
        }
        for (ConversationState state : values()) {
            if (state.code.equals(code)) {
                return state;
            }
        }
        return null;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否为终态(不会再继续对话)
     *
     * @return true表示终态, false表示可能继续
     */
    public boolean isTerminalState() {
        return this == COMPLETED || this == TERMINATED || this == ERROR_STATE;
    }

    /**
     * 判断是否为成功状态
     *
     * @return true表示成功, false表示失败或进行中
     */
    public boolean isSuccessState() {
        return this == COMPLETED;
    }
}

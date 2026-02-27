package com.crypto.trade.service.conversation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ToolExecutionResult
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionResult {

    /**
     * 执行是否成功
     */
    private Boolean success;

    /**
     * 执行结果数据
     */
    private Object data;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 处理时间（毫秒）
     */
    private Long processingTimeMs;

    /**
     * 工具名称（如 "k_line"）
     */
    private String toolName;

    /**
     * 创建成功结果
     *
     * @param data 结果数据
     * @return 成功结果
     */
    public static ToolExecutionResult success(Object data) {
        return new ToolExecutionResult(true, data, null, null, null);
    }

    /**
     * 创建成功结果（带工具名称）
     *
     * @param data     结果数据
     * @param toolName 工具名称
     * @return 成功结果
     */
    public static ToolExecutionResult success(Object data, String toolName) {
        return new ToolExecutionResult(true, data, null, null, toolName);
    }

    /**
     * 创建成功结果
     *
     * @param data             结果数据
     * @param processingTimeMs 处理时间
     * @return 成功结果
     */
    public static ToolExecutionResult success(Object data, Long processingTimeMs) {
        return new ToolExecutionResult(true, data, null, processingTimeMs, null);
    }

    /**
     * 创建成功结果（带工具名称和处理时间）
     *
     * @param data             结果数据
     * @param processingTimeMs 处理时间
     * @param toolName         工具名称
     * @return 成功结果
     */
    public static ToolExecutionResult success(Object data, Long processingTimeMs, String toolName) {
        return new ToolExecutionResult(true, data, null, processingTimeMs, toolName);
    }

    /**
     * 创建失败结果
     *
     * @param errorMessage 错误消息
     * @return 失败结果
     */
    public static ToolExecutionResult failure(String errorMessage) {
        return new ToolExecutionResult(false, null, errorMessage, null, null);
    }

    /**
     * 创建失败结果（带工具名称）
     *
     * @param errorMessage 错误消息
     * @param toolName     工具名称
     * @return 失败结果
     */
    public static ToolExecutionResult failure(String errorMessage, String toolName) {
        return new ToolExecutionResult(false, null, errorMessage, null, toolName);
    }

    /**
     * 创建失败结果
     *
     * @param errorMessage     错误消息
     * @param processingTimeMs 处理时间
     * @return 失败结果
     */
    public static ToolExecutionResult failure(String errorMessage, Long processingTimeMs) {
        return new ToolExecutionResult(false, null, errorMessage, processingTimeMs, null);
    }

    /**
     * 创建失败结果（带工具名称和处理时间）
     *
     * @param errorMessage     错误消息
     * @param processingTimeMs 处理时间
     * @param toolName         工具名称
     * @return 失败结果
     */
    public static ToolExecutionResult failure(String errorMessage, Long processingTimeMs, String toolName) {
        return new ToolExecutionResult(false, null, errorMessage, processingTimeMs, toolName);
    }

    /**
     * 设置工具名称（链式调用）
     *
     * @param toolName 工具名称
     * @return this
     */
    public ToolExecutionResult withToolName(String toolName) {
        this.toolName = toolName;
        return this;
    }
}
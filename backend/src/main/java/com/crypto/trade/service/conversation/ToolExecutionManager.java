package com.crypto.trade.service.conversation;

import java.util.List;

/**
 * ToolExecutionManager
 * 管理类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public interface ToolExecutionManager {

    /**
     * 注册工具执行器
     *
     * @param executor 工具执行器
     */
    void registerExecutor(ToolExecutor executor);

    /**
     * 注销工具执行器
     *
     * @param toolName 工具名称
     */
    void unregisterExecutor(String toolName);

    /**
     * 执行工具调用
     *
     * @param action    解析后的动作
     * @param sessionId 会话ID
     * @param apiKeyId  API密钥ID
     * @return 执行结果
     */
    ToolExecutionResult executeTool(ActionParser.ParsedAction action,
                                    Long sessionId,
                                    Long apiKeyId);

    /**
     * 批量执行工具调用
     *
     * @param actions   动作列表
     * @param sessionId 会话ID
     * @param apiKeyId  API密钥ID
     * @return 执行结果列表
     */
    List<ToolExecutionResult> executeTools(List<ActionParser.ParsedAction> actions,
                                           Long sessionId,
                                           Long apiKeyId);

    /**
     * 获取已注册的工具列表
     *
     * @return 工具名称列表
     */
    List<String> getRegisteredTools();

    /**
     * 检查工具是否已注册
     *
     * @param toolName 工具名称
     * @return 是否已注册
     */
    boolean isToolRegistered(String toolName);
}
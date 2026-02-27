package com.crypto.trade.service.conversation;

/**
 * ToolExecutor
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public interface ToolExecutor {

    /**
     * 执行工具
     *
     * @param parameters 工具参数
     * @param apiKeyId   API密钥ID
     * @return 执行结果
     */
    ToolExecutionResult execute(ToolParameters parameters, Long apiKeyId);

    /**
     * 获取工具名称
     *
     * @return 工具名称
     */
    String getToolName();

    /**
     * 验证参数
     *
     * @param parameters 工具参数
     * @return 验证结果
     */
    boolean validateParameters(ToolParameters parameters);

    /**
     * 获取参数类型
     *
     * @return 参数类型Class
     */
    Class<? extends ToolParameters> getParameterType();
}
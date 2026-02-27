package com.crypto.trade.service.conversation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ToolExecutionManagerImpl
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class ToolExecutionManagerImpl implements ToolExecutionManager {

    private final Map<String, ToolExecutor> executors = new ConcurrentHashMap<>();

    /**
     * 通过构造函数注入已注册的工具执行器
     */
    public ToolExecutionManagerImpl(List<ToolExecutor> executors) {
        if (executors != null) {
            for (ToolExecutor executor : executors) {
                registerExecutor(executor);
            }
        }
    }

    @Override
    public void registerExecutor(ToolExecutor executor) {
        if (executor == null || executor.getToolName() == null) {
            log.warn("无效的工具执行器，跳过注册");
            return;
        }

        String toolName = executor.getToolName();
        executors.put(toolName, executor);
        log.info("工具执行器已注册 - toolName: {}, class: {}",
                toolName, executor.getClass().getSimpleName());
    }

    @Override
    public void unregisterExecutor(String toolName) {
        if (toolName == null) {
            return;
        }

        ToolExecutor removed = executors.remove(toolName);
        if (removed != null) {
            log.info("工具执行器已注销 - toolName: {}", toolName);
        }
    }

    @Override
    public ToolExecutionResult executeTool(ActionParser.ParsedAction action,
                                           Long sessionId,
                                           Long apiKeyId) {
        if (action == null) {
            return ToolExecutionResult.failure("动作参数为空");
        }

        String toolName = getToolNameFromAction(action);
        if (toolName == null) {
            return ToolExecutionResult.failure("无法识别工具类型");
        }

        ToolExecutor executor = executors.get(toolName);
        if (executor == null) {
            return ToolExecutionResult.failure("工具未注册: " + toolName, toolName);
        }

        try {
            // 构建工具参数
            ToolParameters parameters = buildParameters(action, executor);

            // 验证参数
            if (!executor.validateParameters(parameters)) {
                return ToolExecutionResult.failure("工具参数验证失败", toolName);
            }

            // 执行工具
            long startTime = System.currentTimeMillis();
            ToolExecutionResult result = executor.execute(parameters, apiKeyId);

            // 记录工具名称和处理时间
            if (result != null) {
                result.setToolName(toolName);
                if (result.getProcessingTimeMs() == null) {
                    result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                }
            }

            log.info("工具执行完成 - toolName: {}, success: {}, sessionId: {}, apiKeyId: {}",
                    toolName, result != null && result.getSuccess(), sessionId, apiKeyId);

            return result;

        } catch (Exception e) {
            log.error("工具执行异常 - toolName: {}, sessionId: {}, apiKeyId: {}",
                    toolName, sessionId, apiKeyId, e);
            return ToolExecutionResult.failure("工具执行异常: " + e.getMessage(), toolName);
        }
    }

    @Override
    public List<ToolExecutionResult> executeTools(List<ActionParser.ParsedAction> actions,
                                                  Long sessionId,
                                                  Long apiKeyId) {
        if (actions == null || actions.isEmpty()) {
            return Collections.emptyList();
        }

        List<ToolExecutionResult> results = new ArrayList<>();
        for (ActionParser.ParsedAction action : actions) {
            ToolExecutionResult result = executeTool(action, sessionId, apiKeyId);
            results.add(result);
        }

        return results;
    }

    @Override
    public List<String> getRegisteredTools() {
        return new ArrayList<>(executors.keySet());
    }

    @Override
    public boolean isToolRegistered(String toolName) {
        return toolName != null && executors.containsKey(toolName);
    }

    /**
     * 从动作中提取工具名称
     */
    private String getToolNameFromAction(ActionParser.ParsedAction action) {
        // 根据动作类型确定工具名称
        if (action.getAction() == ActionParser.ActionType.QUERY) {
            // K线查询工具
            return "k_line";
        }

        // 未来可扩展其他工具类型
        return null;
    }

    /**
     * 构建工具参数
     */
    private ToolParameters buildParameters(ActionParser.ParsedAction action,
                                           ToolExecutor executor) {
        // 根据执行器类型构建对应的参数对象
        Class<? extends ToolParameters> paramType = executor.getParameterType();

        try {
            if (paramType == KLineParameters.class) {
                KLineParameters params = new KLineParameters();
                params.setInstId(action.getInstId());
                params.setTimeframe(action.getTimeframe());
                params.setLimit(action.getLimit());
                return params;
            }

            // 未来可扩展其他参数类型
            log.warn("未知的参数类型 - {}", paramType);
            return null;

        } catch (Exception e) {
            log.error("构建工具参数失败", e);
            return null;
        }
    }
}
package com.crypto.trade.service.prompt;

import com.crypto.trade.model.SegmentModel;

import java.util.Map;

/**
 * PromptProcessor
 * 处理器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public interface PromptProcessor {

    /**
     * 获取处理器名称
     *
     * @return 处理器名称
     */
    String getName();

    /**
     * 获取处理器优先级（数值越小优先级越高）
     *
     * @return 优先级
     */
    int getPriority();

    /**
     * 判断该处理器是否应该执行
     *
     * @param context 处理上下文
     * @return 是否应该执行
     */
    boolean shouldExecute(PromptContext context);

    /**
     * 处理Prompt内容
     *
     * @param context 处理上下文
     * @return 处理后的段落模型
     * @throws PromptProcessException 处理异常
     */
    SegmentModel process(PromptContext context) throws PromptProcessException;

    /**
     * 获取处理器支持的自定义参数
     *
     * @return 参数Map
     */
    default Map<String, Object> getParameters() {
        return Map.of();
    }

    /**
     * 设置处理器的自定义参数
     *
     * @param parameters 参数Map
     */
    default void setParameters(Map<String, Object> parameters) {
        // 默认空实现
    }
}
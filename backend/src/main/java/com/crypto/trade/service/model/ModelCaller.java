package com.crypto.trade.service.model;

import com.crypto.trade.entity.AIModelConfig;
import com.crypto.trade.service.UnifiedModelFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * ModelCaller
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public interface ModelCaller {

    /**
     * 调用模型
     *
     * @param prompt 用户提示词
     * @param config 模型配置
     * @return 模型响应结果
     * @throws com.crypto.trade.service.UnifiedModelFactory.ModelCallException 调用异常
     */
    String call(String prompt, AIModelConfig config) throws UnifiedModelFactory.ModelCallException;

    /**
     * 使用messages数组格式调用模型
     *
     * @param messages 消息数组
     * @param config   模型配置
     * @return 模型响应结果
     * @throws UnifiedModelFactory.ModelCallException 调用异常
     */
    String callWithMessages(List<UnifiedModelFactory.Message> messages, AIModelConfig config) throws UnifiedModelFactory.ModelCallException;

    /**
     * 异步调用模型
     *
     * @param prompt 用户提示词
     * @param config 模型配置
     * @return 异步调用结果
     */
    CompletableFuture<String> callAsync(String prompt, AIModelConfig config);

    /**
     * 检查模型可用性
     *
     * @param config 模型配置
     * @return 是否可用
     */
    boolean isAvailable(AIModelConfig config);

    /**
     * 获取支持的模型类型
     *
     * @return 支持的模型类型
     */
    String getSupportedModelType();

    /**
     * 获取调用器名称
     *
     * @return 调用器名称
     */
    String getName();
}
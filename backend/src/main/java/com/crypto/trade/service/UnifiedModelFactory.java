package com.crypto.trade.service;

import com.crypto.trade.dto.ModelCallResult;
import com.crypto.trade.entity.AIModelConfig;
import com.crypto.trade.repository.AIModelConfigRepository;
import com.crypto.trade.service.model.LocalModelCaller;
import com.crypto.trade.service.model.ModelCaller;
import com.crypto.trade.service.model.RemoteModelCaller;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * UnifiedModelFactory
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class UnifiedModelFactory {

    /**
     * 模型配置缓存名称
     */
    private static final String MODEL_CONFIG_CACHE = "modelConfigs";

    /**
     * 模型配置缓存过期时间（分钟）
     */
    private static final int CACHE_EXPIRE_MINUTES = 5;

    /**
     * 模型配置缓存最大容量
     */
    private static final int CACHE_MAX_SIZE = 100;
    /**
     * 模型配置缓存（Caffeine）
     * <p>
     * 缓存配置：
     * - 最大容量：100个模型配置
     * - 过期时间：5分钟
     * - 过期策略：写入后5分钟自动过期
     * </p>
     */
    private final Cache<String, AIModelConfig> modelConfigCache = Caffeine.newBuilder()
            .maximumSize(CACHE_MAX_SIZE)
            .expireAfterWrite(Duration.ofMinutes(CACHE_EXPIRE_MINUTES))
            .build();
    /**
     * 并发限制器映射
     * <p>
     * Key: 模型ID
     * Value: 该模型的并发限制信号量
     * </p>
     */
    private final Map<String, Semaphore> concurrencyLimiters = new ConcurrentHashMap<>();
    /**
     * AI模型配置Repository
     */
    @Autowired
    private AIModelConfigRepository aiModelConfigRepository;
    /**
     * 本地模型调用器
     */
    @Autowired
    private LocalModelCaller localModelCaller;
    /**
     * 远端模型调用器
     */
    @Autowired
    private RemoteModelCaller remoteModelCaller;
    /**
     * 默认ChatModel实例（Spring AI提供）
     */
    @Autowired(required = false)
    private ChatModel defaultChatModel;
    /**
     * 默认模型ID（从配置文件读取）
     */
    @Value("${ai.model.default.id:deepseek-r1:14b}")
    private String defaultModelId;
    /**
     * 默认模型URL（从配置文件读取）
     */
    @Value("${ai.model.default.url:http://localhost:11434}")
    private String defaultModelUrl;

    /**
     * 获取默认的模型调用器
     * <p>
     * 当无法确定模型类型时，默认使用本地调用器。
     * </p>
     *
     * @return 默认模型调用器
     */
    private ModelCaller getDefaultModelCaller() {
        // 可以根据配置设置默认的调用器类型
        return localModelCaller;
    }

    /**
     * 根据模型名称调用模型
     * <p>
     * 这是主要的调用方法，供ChatService使用。
     * 调用流程：
     * 1. 参数校验 - 检查prompt和modelName是否为空
     * 2. 获取配置 - 从缓存或数据库获取模型配置
     * 3. 执行调用 - 委托给callWithConfig方法
     * 4. 异常处理 - 捕获异常并包装为ModelCallException
     * </p>
     *
     * @param prompt    用户提示词，不能为空
     * @param modelName 模型名称，不能为空
     * @return 模型响应结果
     * @throws UnifiedModelFactory.ModelCallException 调用异常
     * @throws IllegalArgumentException               参数非法异常
     */
    public String callWithModel(String prompt, String modelName) throws UnifiedModelFactory.ModelCallException {
        // 参数校验
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("提示词不能为空");
        }
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }

        try {
            // 从缓存获取模型配置
            AIModelConfig config = getModelConfig(modelName);
            // 委托给callWithConfig执行调用
            return callWithConfig(prompt, config);
        } catch (Exception e) {
            log.error("模型调用失败: {}, 错误: {}", modelName, e.getMessage(), e);
            throw new ModelCallException("模型调用失败: " + e.getMessage(),
                    e, ModelCallException.ErrorCodes.UNKNOWN_ERROR, modelName);
        }
    }

    /**
     * 根据模型配置调用模型
     * <p>
     * 这是模型调用的核心方法，负责：
     * <ul>
     *   <li>1. 参数校验 - 检查配置有效性、模型是否激活</li>
     *   <li>2. 并发控制 - 根据maxConcurrent限制并发调用数</li>
     *   <li>3. 调用执行 - 通过ModelCaller接口执行实际调用</li>
     *   <li>4. 错误处理 - 统一处理异常，必要时触发重试</li>
     * </ul>
     * </p>
     *
     * @param prompt 用户提示词，不能为空或空字符串
     * @param config 模型配置，必须为激活状态
     * @return 模型响应结果
     * @throws UnifiedModelFactory.ModelCallException 当模型调用失败时抛出
     * @throws IllegalArgumentException               当参数非法时抛出
     */
    public String callWithConfig(String prompt, AIModelConfig config) throws UnifiedModelFactory.ModelCallException {
        // 参数校验
        if (config == null) {
            throw new ModelCallException("模型配置不能为空",
                    ModelCallException.ErrorCodes.INVALID_REQUEST, "null");
        }

        if (!config.getIsActive()) {
            throw new ModelCallException("模型已禁用: " + config.getModelId(),
                    ModelCallException.ErrorCodes.MODEL_NOT_FOUND, config.getModelId());
        }

        log.debug("调用模型: {} (类型: {})", config.getModelId(), config.getModelType());

        // 并发控制
        Semaphore limiter = concurrencyLimiters.computeIfAbsent(config.getModelId(),
                k -> new Semaphore(config.getMaxConcurrent()));

        try {
            // 尝试获取调用许可（带超时）
            boolean acquired = limiter.tryAcquire(config.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("获取模型调用许可超时: {}, 并发请求数过多", config.getModelId());
                throw new ModelCallException("获取调用许可超时，并发请求数过多",
                        ModelCallException.ErrorCodes.RATE_LIMIT_ERROR, config.getModelId());
            }

            // 执行模型调用
            try {
                return callDirectly(prompt, config);
            } finally {
                // 释放许可
                limiter.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelCallException("调用被中断",
                    e, ModelCallException.ErrorCodes.UNKNOWN_ERROR, config.getModelId());
        }
    }

    /**
     * 使用messages数组格式调用模型（支持多轮对话）
     * <p>
     * 通过role角色的方式传递完整的对话历史，支持多轮对话场景。
     * messages数组格式遵循OpenAI Chat Completion API标准：
     * <pre>
     * [
     *   {"role": "system", "content": "系统提示"},
     *   {"role": "user", "content": "用户问题1"},
     *   {"role": "assistant", "content": "AI回答1"},
     *   {"role": "user", "content": "用户问题2"},
     *   {"role": "assistant", "content": "AI回答2"}
     * ]
     * </pre>
     * </p>
     *
     * @param messages  消息数组，不能为空或空数组
     * @param modelName 模型名称
     * @return 模型响应结果
     * @throws UnifiedModelFactory.ModelCallException 当模型调用失败时抛出
     * @throws IllegalArgumentException               当参数非法时抛出
     */
    public String callWithMessages(List<Message> messages, String modelName) throws UnifiedModelFactory.ModelCallException {
        // 参数校验
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("消息数组不能为空");
        }
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }

        try {
            // 从缓存获取模型配置
            AIModelConfig config = getModelConfig(modelName);
            // 委托给callWithConfigAndMessages执行调用
            return callWithConfigAndMessages(messages, config);
        } catch (Exception e) {
            log.error("模型调用失败: {}, 错误: {}", modelName, e.getMessage(), e);
            throw new ModelCallException("模型调用失败: " + e.getMessage(),
                    e, ModelCallException.ErrorCodes.UNKNOWN_ERROR, modelName);
        }
    }

    /**
     * 使用messages数组格式调用模型（基于模型配置）
     *
     * @param messages 消息数组，不能为空或空数组
     * @param config   模型配置，必须为激活状态
     * @return 模型响应结果
     * @throws UnifiedModelFactory.ModelCallException 当模型调用失败时抛出
     */
    public String callWithConfigAndMessages(List<Message> messages, AIModelConfig config) throws UnifiedModelFactory.ModelCallException {
        // 参数校验
        if (config == null) {
            throw new ModelCallException("模型配置不能为空",
                    ModelCallException.ErrorCodes.INVALID_REQUEST, "null");
        }

        if (!config.getIsActive()) {
            throw new ModelCallException("模型已禁用: " + config.getModelId(),
                    ModelCallException.ErrorCodes.MODEL_NOT_FOUND, config.getModelId());
        }

        log.debug("使用messages数组调用模型: {} (类型: {}), 消息数: {}",
                config.getModelId(), config.getModelType(), messages.size());

        // 并发控制
        Semaphore limiter = concurrencyLimiters.computeIfAbsent(config.getModelId(),
                k -> new Semaphore(config.getMaxConcurrent()));

        try {
            // 尝试获取调用许可（带超时）
            boolean acquired = limiter.tryAcquire(config.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("获取模型调用许可超时: {}, 并发请求数过多", config.getModelId());
                throw new ModelCallException("获取调用许可超时，并发请求数过多",
                        ModelCallException.ErrorCodes.RATE_LIMIT_ERROR, config.getModelId());
            }

            // 执行模型调用
            try {
                return callDirectlyWithMessages(messages, config);
            } finally {
                // 释放许可
                limiter.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelCallException("调用被中断",
                    e, ModelCallException.ErrorCodes.UNKNOWN_ERROR, config.getModelId());
        }
    }

    /**
     * 直接调用模型（使用messages数组）
     *
     * @param messages 消息数组
     * @param config   模型配置
     * @return 模型响应
     */
    private String callDirectlyWithMessages(List<Message> messages, AIModelConfig config) throws ModelCallException {
        // 获取对应的调用器
        ModelCaller caller = getModelCaller(config);

        try {
            // 执行调用
            return caller.callWithMessages(messages, config);

        } catch (ModelCallException e) {
            // 如果是远端模型且应该重试，执行重试逻辑
            if (caller instanceof RemoteModelCaller && shouldRetry(e)) {
                log.warn("远端模型调用失败(使用messages)，尝试重试: {}, 错误: {}", config.getModelId(), e.getMessage());
                return ((RemoteModelCaller) caller).callWithMessagesWithRetry(messages, config);
            }
            throw e;
        }
    }

    /**
     * 异步调用模型
     * <p>
     * 使用CompletableFuture在独立线程池中执行调用，不阻塞主线程。
     * </p>
     *
     * @param prompt 用户提示词
     * @param config 模型配置
     * @return 异步调用结果
     */
    public CompletableFuture<String> callAsync(String prompt, AIModelConfig config) {
        try {
            ModelCaller caller = getModelCaller(config);
            return caller.callAsync(prompt, config);
        } catch (Exception e) {
            log.error("异步模型调用失败: {}, 错误: {}",
                    config != null ? config.getModelId() : "unknown", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 检查模型可用性
     * <p>
     * 快速检查模型是否可用，不执行实际调用。
     * 检查项：
     * 1. 配置是否存在
     * 2. 模型是否激活
     * 3. 模型服务是否可访问
     * </p>
     *
     * @param modelName 模型名称
     * @return 是否可用
     */
    public boolean isModelAvailable(String modelName) {
        try {
            AIModelConfig config = getModelConfig(modelName);
            return isModelAvailable(config);
        } catch (Exception e) {
            String detailedError = getDetailedAvailabilityError(e, modelName);
            log.debug("检查模型可用性失败: {} - {}", modelName, detailedError);
            return false;
        }
    }

    /**
     * 检查模型配置可用性
     * <p>
     * 根据模型类型执行不同的可用性检查：
     * - 本地模型：检查Ollama服务和模型是否已下载
     * - 远端模型：检查配置有效性和API可达性
     * </p>
     *
     * @param config 模型配置
     * @return 是否可用
     */
    public boolean isModelAvailable(AIModelConfig config) {
        if (config == null || !config.getIsActive()) {
            return false;
        }

        try {
            if (config.isLocalModel()) {
                // 本地模型直接检查Ollama服务
                return localModelCaller.isAvailable(config);
            } else if (config.isRemoteModel()) {
                // 远端模型使用适配器检查
                ModelCaller caller = getModelCaller(config);
                return caller.isAvailable(config);
            } else {
                return false;
            }
        } catch (Exception e) {
            String detailedError = getDetailedAvailabilityError(e, config.getModelId());
            log.debug("检查模型可用性失败: {} - {}", config.getModelId(), detailedError);
            return false;
        }
    }

    /**
     * 获取默认模型配置
     * <p>
     * 优先从数据库获取标记为defaultModel的配置，
     * 如果不存在则返回硬编码的默认配置。
     * </p>
     *
     * @return 默认模型配置
     */
    public AIModelConfig getDefaultModelConfig() {
        // 使用配置文件中的值构建默认模型配置
        AIModelConfig fallbackConfig = AIModelConfig.builder()
                .modelId(defaultModelId)
                .displayName("Default Model")
                .apiUrl(defaultModelUrl)
                .build();

        try {
            return aiModelConfigRepository.findByDefaultModelTrue().orElse(fallbackConfig);
        } catch (Exception e) {
            log.warn("获取默认模型配置失败: {}, 使用本地默认配置", e.getMessage(), e);
            return fallbackConfig;
        }
    }

    /**
     * 获取活跃模型列表
     * <p>
     * 返回所有激活状态的模型配置。
     * </p>
     *
     * @return 活跃模型列表
     */
    public List<AIModelConfig> getActiveModels() {
        try {
            return aiModelConfigRepository.findByIsActiveTrue();
        } catch (Exception e) {
            log.error("获取活跃模型列表失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 根据模型名称获取模型配置
     * <p>
     * 优先从缓存获取，缓存未命中时查询数据库并更新缓存。
     * </p>
     *
     * @param modelName 模型名称
     * @return 模型配置
     * @throws UnifiedModelFactory.ModelCallException 模型不存在异常
     */
    private AIModelConfig getModelConfig(String modelName) throws UnifiedModelFactory.ModelCallException {
        // 先从缓存获取
        AIModelConfig cachedConfig = modelConfigCache.getIfPresent(modelName);
        if (cachedConfig != null) {
            log.debug("从缓存获取模型配置: {}", modelName);
            return cachedConfig;
        }

        // 缓存未命中，查询数据库
        log.debug("从数据库加载模型配置: {}", modelName);
        return aiModelConfigRepository.findByModelId(modelName)
                .map(config -> {
                    // 放入缓存
                    modelConfigCache.put(modelName, config);
                    log.debug("模型配置已缓存: {}", modelName);
                    return config;
                })
                .orElseThrow(() -> new ModelCallException("模型配置不存在: " + modelName,
                        ModelCallException.ErrorCodes.MODEL_NOT_FOUND, modelName));
    }

    /**
     * 根据模型类型获取对应的调用器
     * <p>
     * 调用器选择策略：
     * - 本地模型 -> LocalModelCaller
     * - 远端模型 -> RemoteModelCaller
     * - 未知类型 -> 默认调用器
     * </p>
     *
     * @param config 模型配置
     * @return 模型调用器
     */
    private ModelCaller getModelCaller(AIModelConfig config) {
        if (config.isLocalModel()) {
            return localModelCaller;
        } else if (config.isRemoteModel()) {
            return remoteModelCaller;
        } else {
            // 默认使用本地调用器
            return getDefaultModelCaller();
        }
    }

    /**
     * 直接调用模型（本地或远端）
     * <p>
     * 统一调用路径，通过ModelCaller接口执行调用。
     * 对于远端模型，如果调用失败且支持重试，则自动触发重试机制。
     * </p>
     *
     * @param prompt 用户提示词
     * @param config 模型配置
     * @return 模型响应结果
     * @throws ModelCallException 调用异常
     */
    private String callDirectly(String prompt, AIModelConfig config) throws ModelCallException {
        // 获取对应的调用器
        ModelCaller caller = getModelCaller(config);

        try {
            // 执行调用
            return caller.call(prompt, config);

        } catch (ModelCallException e) {
            // 如果是远端模型且应该重试，执行重试逻辑
            if (caller instanceof RemoteModelCaller && shouldRetry(e)) {
                log.warn("远端模型调用失败，尝试重试: {}, 错误: {}", config.getModelId(), e.getMessage());
                return ((RemoteModelCaller) caller).callWithRetry(prompt, config);
            }
            throw e;
        }
    }

    /**
     * 判断是否应该重试
     * <p>
     * 重试策略：
     * - 不重试：认证错误、模型不存在、请求无效
     * - 重试：连接错误、超时、服务器错误、限流
     * </p>
     *
     * @param exception 异常
     * @return 是否应该重试
     */
    private boolean shouldRetry(ModelCallException exception) {
        String errorCode = exception.getErrorCode();

        // 以下错误类型不重试
        return !ModelCallException.ErrorCodes.AUTHENTICATION_ERROR.equals(errorCode) &&
                !ModelCallException.ErrorCodes.MODEL_NOT_FOUND.equals(errorCode) &&
                !ModelCallException.ErrorCodes.INVALID_REQUEST.equals(errorCode);
    }

    /**
     * 获取详细的可用性检查错误信息
     * <p>
     * 根据异常类型返回用户友好的错误描述和解决建议。
     * </p>
     *
     * @param e         异常
     * @param modelName 模型名称
     * @return 详细错误信息
     */
    private String getDetailedAvailabilityError(Exception e, String modelName) {
        if (e instanceof ModelCallException mce) {
            return String.format("[%s] %s", mce.getErrorCode(), mce.getMessage());
        } else {
            String message = e.getMessage().toLowerCase();
            if (message.contains("not found") || message.contains("不存在")) {
                return String.format("模型配置 '%s' 不存在。请先创建该模型的配置", modelName);
            } else if (message.contains("connection")) {
                return "连接模型服务失败，请检查服务状态和网络连接";
            } else {
                return String.format("检查失败: %s", e.getMessage());
            }
        }
    }

    /**
     * 根据模型名称获取ChatModel实例（兼容DynamicModelFactory接口）
     * <p>
     * 此方法保留是为了向后兼容，建议直接使用callWithModel方法。
     * </p>
     *
     * @param modelName 模型名称
     * @return ChatModel实例
     */
    public ChatModel getModel(String modelName) {
        try {
            AIModelConfig config = getModelConfig(modelName);

            // 如果是本地模型且有默认ChatModel，优先使用
            if (config.isLocalModel() && defaultChatModel != null) {
                log.debug("使用默认ChatModel实例进行本地模型调用: {}", modelName);
                return defaultChatModel;
            }

            log.debug("模型 {} 将通过UnifiedModelFactory直接调用", modelName);
            return defaultChatModel;

        } catch (ModelCallException e) {
            log.warn("获取模型 {} 失败，使用默认ChatModel: {}", modelName, e.getMessage());
            return defaultChatModel;
        }
    }

    /**
     * 获取默认模型实例（兼容DynamicModelFactory接口）
     *
     * @return 默认ChatModel实例
     */
    public ChatModel getDefaultModel() {
        return defaultChatModel;
    }

    // ==================== 从DynamicModelFactory整合的功能 ====================

    /**
     * 清空模型缓存（兼容DynamicModelFactory接口）
     */
    public void clearCache() {
        log.debug("清空模型配置缓存");
        modelConfigCache.invalidateAll();
    }

    /**
     * 获取当前缓存的模型数量（兼容DynamicModelFactory接口）
     *
     * @return 缓存的模型数量
     */
    public long getCachedModelCount() {
        return modelConfigCache.estimatedSize();
    }

    /**
     * 清理资源
     * <p>
     * 在Spring容器关闭时自动执行，清理以下资源：
     * 1. 清空模型配置缓存
     * 2. 清空并发限制器
     * 3. 关闭线程池（LocalModelCaller和RemoteModelCaller）
     * </p>
     */
    @PreDestroy
    public void destroy() {
        log.info("UnifiedModelFactory 开始清理资源");

        try {
            // 清理模型配置缓存
            long cacheSize = modelConfigCache.estimatedSize();
            modelConfigCache.invalidateAll();
            log.debug("已清理模型配置缓存，清理数量: {}", cacheSize);

            // 清理并发限制器
            int limiterCount = concurrencyLimiters.size();
            concurrencyLimiters.clear();
            log.debug("已清理并发限制器，清理数量: {}", limiterCount);

            // 关闭线程池
            if (localModelCaller != null) {
                localModelCaller.shutdown();
                log.debug("LocalModelCaller 线程池已关闭");
            }
            if (remoteModelCaller != null) {
                remoteModelCaller.shutdown();
                log.debug("RemoteModelCaller 线程池已关闭");
            }

            log.info("UnifiedModelFactory 资源清理完成");

        } catch (Exception e) {
            log.error("清理资源时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 调用模型并记录耗时信息
     * <p>
     * 在原有callWithModel基础上,增加耗时记录功能,返回包含耗时信息的结果。
     * 该方法不会影响现有的callWithModel调用,提供向后兼容性。
     * </p>
     *
     * @param prompt    用户提示词
     * @param modelName 模型名称
     * @return 包含响应内容和LLM调用耗时的结果对象
     * @throws UnifiedModelFactory.ModelCallException 调用异常
     * @throws IllegalArgumentException               参数非法异常
     */
    public ModelCallResult callWithModelAndTiming(String prompt, String modelName)
            throws UnifiedModelFactory.ModelCallException {
        // 参数校验
        if (null == prompt || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("提示词不能为空");
        }
        if (null == modelName || modelName.trim().isEmpty()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }

        // 记录LLM调用开始时间
        long llmCallStartTime = System.currentTimeMillis();

        try {
            // 调用原方法获取响应
            String response = callWithModel(prompt, modelName);

            // 计算LLM调用耗时
            long llmCallTimeMs = System.currentTimeMillis() - llmCallStartTime;

            log.debug("LLM调用耗时: {}ms, 模型: {}", llmCallTimeMs, modelName);

            // 返回包含耗时信息的结果
            return ModelCallResult.of(response, llmCallTimeMs);

        } catch (Exception e) {
            log.error("模型调用失败: {}, 错误: {}", modelName, e.getMessage(), e);
            throw new ModelCallException("模型调用失败: " + e.getMessage(),
                    e, ModelCallException.ErrorCodes.UNKNOWN_ERROR, modelName);
        }
    }

    /**
     * Message类（用于构建messages数组）
     */
    @Getter
    public static class Message {

        private final String role;
        private final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        /**
         * 创建系统消息
         */
        public static Message system(String content) {
            return new Message("system", content);
        }

        /**
         * 创建用户消息
         */
        public static Message user(String content) {
            return new Message("user", content);
        }

        /**
         * 创建助手消息
         */
        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }

    /**
     * 模型调用异常类
     * <p>
     * 统一的模型调用异常，包含错误码和模型ID信息。
     * </p>
     */
    public static class ModelCallException extends Exception {
        /**
         * 序列化版本号
         */
        private static final long serialVersionUID = 1L;

        /**
         * 错误码
         */
        private final String errorCode;

        /**
         * 模型ID
         */
        private final String modelId;

        /**
         * 基础构造函数
         *
         * @param message 异常消息
         * @param modelId 模型ID
         */
        public ModelCallException(String message, String modelId) {
            super(message);
            this.errorCode = "MODEL_CALL_ERROR";
            this.modelId = modelId;
        }

        /**
         * 带错误码的构造函数
         *
         * @param message   异常消息
         * @param errorCode 错误码
         * @param modelId   模型ID
         */
        public ModelCallException(String message, String errorCode, String modelId) {
            super(message);
            this.errorCode = errorCode;
            this.modelId = modelId;
        }

        /**
         * 带原因的完整构造函数
         *
         * @param message   异常消息
         * @param cause     原因异常
         * @param errorCode 错误码
         * @param modelId   模型ID
         */
        public ModelCallException(String message, Throwable cause, String errorCode, String modelId) {
            super(message, cause);
            this.errorCode = errorCode;
            this.modelId = modelId;
        }

        /**
         * 获取错误码
         *
         * @return 错误码
         */
        public String getErrorCode() {
            return errorCode;
        }

        /**
         * 获取模型ID
         *
         * @return 模型ID
         */
        public String getModelId() {
            return modelId;
        }

        /**
         * 常用错误代码定义
         * <p>
         * 错误码分类：
         * - 客户端错误：INVALID_REQUEST, AUTHENTICATION_ERROR, MODEL_NOT_FOUND
         * - 网络错误：CONNECTION_ERROR, TIMEOUT_ERROR
         * - 服务端错误：RATE_LIMIT_ERROR, QUOTA_EXCEEDED, SERVER_ERROR
         * - 未知错误：UNKNOWN_ERROR
         * </p>
         */
        public static class ErrorCodes {
            /**
             * 连接错误 - 无法连接到模型服务
             */
            public static final String CONNECTION_ERROR = "CONNECTION_ERROR";

            /**
             * 超时错误 - 请求超时
             */
            public static final String TIMEOUT_ERROR = "TIMEOUT_ERROR";

            /**
             * 认证错误 - API密钥无效或缺失
             */
            public static final String AUTHENTICATION_ERROR = "AUTHENTICATION_ERROR";

            /**
             * 限流错误 - 超过速率限制
             */
            public static final String RATE_LIMIT_ERROR = "RATE_LIMIT_ERROR";

            /**
             * 请求无效 - 请求参数错误
             */
            public static final String INVALID_REQUEST = "INVALID_REQUEST";

            /**
             * 模型不存在 - 请求的模型不存在
             */
            public static final String MODEL_NOT_FOUND = "MODEL_NOT_FOUND";

            /**
             * 配额超限 - 超过配额限制
             */
            public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";

            /**
             * 服务器错误 - 服务端内部错误
             */
            public static final String SERVER_ERROR = "SERVER_ERROR";

            /**
             * 未知错误 - 未分类的错误
             */
            public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
        }
    }
}

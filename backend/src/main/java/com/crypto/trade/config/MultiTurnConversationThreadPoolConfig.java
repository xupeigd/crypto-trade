package com.crypto.trade.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * MultiTurnConversationThreadPoolConfig
 * 配置类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Configuration
public class MultiTurnConversationThreadPoolConfig {

    /**
     * 多轮会话专用线程池
     * 核心配置:
     * - 核心线程数: 3 (同时处理3个多轮对话会话)
     * - 最大线程数: 6 (峰值时最多6个)
     * - 队列容量: 50 (等待处理的会话队列)
     * - 拒绝策略: CallerRunsPolicy (调用者运行,保证不丢失任务)
     */
    @Bean("multiTurnConversationExecutor")
    public Executor multiTurnConversationExecutor() {
        log.info("初始化多轮会话线程池...");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心配置
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);

        // 线程命名
        executor.setThreadNamePrefix("multi-turn-");
        executor.setThreadPriority(Thread.NORM_PRIORITY - 1); // 稍高优先级

        // 拒绝策略: 队列满时由调用线程执行,保证任务不丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 优雅关闭配置
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);

        // 线程存活时间
        executor.setKeepAliveSeconds(300); // 5分钟

        // 允许核心线程超时
        executor.setAllowCoreThreadTimeOut(true);

        executor.initialize();

        log.info("多轮会话线程池初始化完成 - 核心线程:{}, 最大线程:{}, 队列容量:{}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }
}

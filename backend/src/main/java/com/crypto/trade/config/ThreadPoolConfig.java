package com.crypto.trade.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ThreadPoolConfig
 * 配置类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Configuration
public class ThreadPoolConfig {

    @Value("${spring.task.thread-pool.core-size:10}")
    private int corePoolSize;

    @Value("${spring.task.thread-pool.max-size:20}")
    private int maxPoolSize;

    @Value("${spring.task.thread-pool.queue-capacity:100}")
    private int queueCapacity;

    @Value("${spring.task.thread-pool.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("task-executor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Bot处理专用线程池
     * 用于异步执行BOT触发等耗时任务
     */
    @Bean(name = "botTaskExecutor")
    public Executor botTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // BOT任务核心线程数较少，避免过度消耗资源
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(5);
        // 较大的队列容量，避免拒绝任务
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("bot-task-");
        // 拒绝策略：调用线程运行，保证任务不丢失
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }
}
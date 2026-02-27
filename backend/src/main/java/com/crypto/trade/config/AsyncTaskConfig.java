package com.crypto.trade.config;

import com.crypto.trade.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AsyncTaskConfig
 * 配置类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncTaskConfig {

    @Autowired
    private AlertService alertService;

    /**
     * 交易任务执行器
     * 用于处理AI模型调用的异步任务
     */
    @Bean("tradingTaskExecutor")
    public Executor tradingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：2 → 10（增加并发处理能力）
        executor.setCorePoolSize(10);
        // 最大线程数：5 → 20（防止线程池耗尽）
        executor.setMaxPoolSize(20);
        // 队列容量
        executor.setQueueCapacity(100);
        // 线程名前缀
        executor.setThreadNamePrefix("TradingTask-");
        // 【修复】拒绝策略：CallerRunsPolicy → AbortPolicy
        // 原因：CallerRunsPolicy会导致定时任务线程被阻塞，防止新的定时任务触发
        // AbortPolicy会抛出异常，由上层捕获并记录失败状态
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                // 详细的拒绝日志
                log.error("[线程池拒绝] 活跃线程: {}/{}, 队列大小: {}/{}, 已完成任务: {}", e.getActiveCount(), e.getMaximumPoolSize(),
                        e.getQueue().size(), 100, e.getCompletedTaskCount());

                // 【新增】发送告警
                try {
                    alertService.alertThreadPoolRejection(e.getActiveCount(), e.getMaximumPoolSize(), e.getQueue().size(),
                            100, e.getCompletedTaskCount());
                } catch (Exception alertEx) {
                    log.error("发送线程池拒绝告警失败", alertEx);
                }

                // 调用父类方法抛出异常
                super.rejectedExecution(r, e);
            }
        });
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 等待时间
        executor.setAwaitTerminationSeconds(60);
        // 初始化
        executor.initialize();
        return executor;
    }

    /**
     * CEX API调用记录执行器
     * 专用于处理API调用记录的异步保存
     * 使用独立线程池避免与交易任务争抢资源
     */
    @Bean("cexApiCallRecordTaskExecutor")
    public Executor cexApiCallRecordTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数（记录任务不需要太多线程）
        executor.setCorePoolSize(2);
        // 最大线程数
        executor.setMaxPoolSize(5);
        // 队列容量
        executor.setQueueCapacity(100);
        // 线程名前缀
        executor.setThreadNamePrefix("CexApiCallRecord-");
        // 拒绝策略：由调用线程执行（确保不丢失记录）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 等待时间
        executor.setAwaitTerminationSeconds(60);
        // 初始化
        executor.initialize();
        return executor;
    }

    /**
     * 通用异步任务执行器
     * 用于处理通用的异步任务，如余额缓存刷新等
     */
    @Bean("cacheTaskExecutor")
    public TaskExecutor cacheTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(4);
        // 最大线程数
        executor.setMaxPoolSize(8);
        // 队列容量
        executor.setQueueCapacity(100);
        // 线程名前缀
        executor.setThreadNamePrefix("AsyncTask-");
        // 拒绝策略：由调用线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 等待时间
        executor.setAwaitTerminationSeconds(60);
        // 初始化
        executor.initialize();
        return executor;
    }
}
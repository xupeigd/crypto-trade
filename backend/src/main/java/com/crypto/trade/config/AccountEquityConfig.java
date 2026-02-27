package com.crypto.trade.config;

import com.crypto.trade.entity.AccountEquityData;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * AccountEquityConfig
 * 配置类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Configuration
@EnableScheduling
public class AccountEquityConfig implements SchedulingConfigurer {

    /**
     * 账户权益缓存配置
     * - TTL: 3分钟
     * - 最大容量: 100个活跃API密钥
     */
    @Bean("accountEquityCache")
    public Cache<Long, AccountEquityData> accountEquityCache() {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(3, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * 账户权益监控专用线程池
     * - 核心线程数: 2个（监控 + 持久化）
     * - 最大线程数: 4个
     * - 线程名前缀: account-equity-
     */
    @Bean("accountEquityTaskExecutor")
    public Executor accountEquityTaskExecutor() {
        return Executors.newFixedThreadPool(4, r -> {
            Thread thread = new Thread(r);
            thread.setName("account-equity-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 账户权益监控专用任务调度器
     * 用于执行@Scheduled注解的定时任务
     */
    @Bean("accountEquityTaskScheduler")
    public TaskScheduler accountEquityTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("account-equity-scheduler-");
        scheduler.setDaemon(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }

    /**
     * 配置定时任务调度器使用专用线程池
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(accountEquityTaskScheduler());
    }
}
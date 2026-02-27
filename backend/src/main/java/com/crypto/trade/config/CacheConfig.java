package com.crypto.trade.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * CacheConfig
 * 配置类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 配置缓存管理器
     * - 标记价格缓存：10秒过期
     * - 最大缓存1000个合约
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // 配置标记价格缓存
        cacheManager.registerCustomCache("markPrice", markPriceCache());

        // 配置BOT Prompts缓存
        cacheManager.registerCustomCache("botPrompts", botPromptsCache());

        // 配置Market Top30缓存
        cacheManager.registerCustomCache("marketTop30", marketTop30Cache());

        return cacheManager;
    }

    /**
     * 标记价格缓存配置
     * TTL: 10秒
     * 最大容量: 1000个合约
     */
    private Cache<Object, Object> markPriceCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .recordStats()
                .build();
    }

    /**
     * BOT Prompts缓存配置
     * TTL: 30秒
     * 最大容量: 100个API Key的最新数据
     */
    private Cache<Object, Object> botPromptsCache() {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .recordStats()
                .build();
    }

    /**
     * Market Top30缓存配置
     * TTL: 5分钟
     * 最大容量: 100个供应商小时的Top30数据
     */
    private Cache<Object, Object> marketTop30Cache() {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * 配置异步任务执行器
     * 用于Top30数据的异步预计算
     */
    @Bean(name = "top30AsyncExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(5);
        // 最大线程数
        executor.setMaxPoolSize(20);
        // 队列容量
        executor.setQueueCapacity(200);
        // 线程名前缀
        executor.setThreadNamePrefix("top30-async-");
        // 拒绝策略：调用者运行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 线程空闲时间
        executor.setKeepAliveSeconds(60);
        // 等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 等待时间
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
package com.crypto.trade.service;

import com.crypto.trade.config.Top30Properties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Top30PerformanceMonitor
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class Top30PerformanceMonitor {

    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    // 性能指标存储
    private final ConcurrentHashMap<String, ApiMetrics> metricsMap = new ConcurrentHashMap<>();
    @Autowired
    private Top30Properties top30Properties;

    /**
     * 记录API调用
     */
    public void recordApiCall(String operation, long executionTime, boolean success, int resultCount) {
        try {
            String key = getCurrentMinuteKey() + ":" + operation;

            ApiMetrics metrics = metricsMap.computeIfAbsent(key, k -> new ApiMetrics());

            metrics.incrementTotalCalls();
            metrics.incrementExecutionTime(executionTime);

            if (success) {
                metrics.incrementSuccessCalls();
                metrics.setResultCount(resultCount);
            } else {
                metrics.incrementFailureCalls();
            }

            // 检查是否需要告警
            checkAlerts(operation, executionTime, success);

        } catch (Exception e) {
            log.error("记录API调用指标失败", e);
        }
    }

    /**
     * 记录缓存命中
     */
    public void recordCacheHit(String operation) {
        try {
            String key = getCurrentMinuteKey() + ":" + operation;
            ApiMetrics metrics = metricsMap.get(key);
            if (metrics != null) {
                metrics.incrementCacheHits();
            }
        } catch (Exception e) {
            log.error("记录缓存命中失败", e);
        }
    }

    /**
     * 记录缓存未命中
     */
    public void recordCacheMiss(String operation) {
        try {
            String key = getCurrentMinuteKey() + ":" + operation;
            ApiMetrics metrics = metricsMap.get(key);
            if (metrics != null) {
                metrics.incrementCacheMisses();
            }
        } catch (Exception e) {
            log.error("记录缓存未命中失败", e);
        }
    }

    /**
     * 获取性能指标报告
     */
    public PerformanceReport getPerformanceReport() {
        try {
            PerformanceReport report = new PerformanceReport();

            long totalCalls = 0;
            long totalSuccessCalls = 0;
            long totalFailures = 0;
            long totalExecutionTime = 0;
            long totalCacheHits = 0;
            long totalCacheMisses = 0;

            for (ApiMetrics metrics : metricsMap.values()) {
                totalCalls += metrics.getTotalCalls().get();
                totalSuccessCalls += metrics.getSuccessCalls().get();
                totalFailures += metrics.getFailureCalls().get();
                totalExecutionTime += metrics.getTotalExecutionTime().get();
                totalCacheHits += metrics.getCacheHits().get();
                totalCacheMisses += metrics.getCacheMisses().get();
            }

            report.setTotalCalls(totalCalls);
            report.setSuccessCalls(totalSuccessCalls);
            report.setFailureCalls(totalFailures);
            report.setSuccessRate(calculateSuccessRate(totalSuccessCalls, totalCalls));
            report.setAverageExecutionTime(calculateAverage(totalExecutionTime, totalCalls));
            report.setCacheHitRate(calculateCacheHitRate(totalCacheHits, totalCacheMisses));
            report.setGeneratedAt(LocalDateTime.now());

            return report;

        } catch (Exception e) {
            log.error("生成性能报告失败", e);
            return new PerformanceReport(); // 返回空报告
        }
    }

    /**
     * 检查告警条件
     */
    private void checkAlerts(String operation, long executionTime, boolean success) {
        // 执行时间告警
        if (executionTime > 5000) { // 5秒告警
            log.warn("API执行时间过长告警 - operation: {}, executionTime: {}ms", operation, executionTime);
        }

        // 失败率告警
        String key = getCurrentMinuteKey() + ":" + operation;
        ApiMetrics metrics = metricsMap.get(key);
        if (metrics != null && metrics.getTotalCalls().get() >= 10) {
            double failureRate = calculateFailureRate(metrics.getFailureCalls().get(), metrics.getTotalCalls().get());
            if (failureRate > 0.1) { // 失败率超过10%告警
                log.warn("API失败率过高告警 - operation: {}, failureRate: {}%", operation, failureRate * 100);
            }
        }

        // 零失败告警
        if (!success) {
            log.error("API调用失败 - operation: {}", operation);
        }
    }

    /**
     * 清理过期的指标数据
     */
    public void cleanupExpiredMetrics() {
        try {
            String currentMinute = getCurrentMinuteKey();

            metricsMap.entrySet().removeIf(entry -> {
                String key = entry.getKey();
                String minuteKey = key.split(":")[0];

                // 保留最近60分钟的数据
                return currentMinute.compareTo(minuteKey) > 60;
            });

        } catch (Exception e) {
            log.error("清理过期指标失败", e);
        }
    }

    /**
     * 获取当前分钟键
     */
    private String getCurrentMinuteKey() {
        return LocalDateTime.now().format(MINUTE_FORMATTER);
    }

    private double calculateSuccessRate(long successCalls, long totalCalls) {
        return totalCalls > 0 ? (double) successCalls / totalCalls : 0.0;
    }

    private double calculateFailureRate(long failureCalls, long totalCalls) {
        return totalCalls > 0 ? (double) failureCalls / totalCalls : 0.0;
    }

    private long calculateAverage(long total, long count) {
        return count > 0 ? total / count : 0;
    }

    private double calculateCacheHitRate(long hits, long misses) {
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }

    /**
     * API指标数据
     */
    @Data
    private static class ApiMetrics {
        private AtomicLong totalCalls = new AtomicLong(0);
        private AtomicLong successCalls = new AtomicLong(0);
        private AtomicLong failureCalls = new AtomicLong(0);
        private AtomicLong totalExecutionTime = new AtomicLong(0);
        private AtomicLong cacheHits = new AtomicLong(0);
        private AtomicLong cacheMisses = new AtomicLong(0);
        private Integer resultCount = 0;

        public void incrementTotalCalls() {
            totalCalls.incrementAndGet();
        }

        public void incrementSuccessCalls() {
            successCalls.incrementAndGet();
        }

        public void incrementFailureCalls() {
            failureCalls.incrementAndGet();
        }

        public void incrementExecutionTime(long time) {
            totalExecutionTime.addAndGet(time);
        }

        public void incrementCacheHits() {
            cacheHits.incrementAndGet();
        }

        public void incrementCacheMisses() {
            cacheMisses.incrementAndGet();
        }
    }

    /**
     * 性能报告
     */
    @Data
    public static class PerformanceReport {
        private long totalCalls;
        private long successCalls;
        private long failureCalls;
        private double successRate;
        private long averageExecutionTime;
        private double cacheHitRate;
        private LocalDateTime generatedAt;

        public boolean isHealthy() {
            return successRate >= 0.95 && averageExecutionTime <= 1000;
        }

        public String getHealthStatus() {
            if (isHealthy()) {
                return "HEALTHY";
            } else if (successRate >= 0.9) {
                return "WARNING";
            } else {
                return "CRITICAL";
            }
        }
    }
}
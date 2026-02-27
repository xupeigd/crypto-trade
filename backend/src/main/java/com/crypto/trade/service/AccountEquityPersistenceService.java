package com.crypto.trade.service;

import com.crypto.trade.entity.AccountEquityData;
import com.crypto.trade.entity.AccountEquitySnapshot;
import com.crypto.trade.repository.AccountEquitySnapshotRepository;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AccountEquityPersistenceService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class AccountEquityPersistenceService {

    @Autowired
    private AccountEquitySnapshotRepository accountEquitySnapshotRepository;

    @Autowired
    @Qualifier("accountEquityCache")
    private Cache<Long, AccountEquityData> accountEquityCache;

    /**
     * 每1分钟执行一次账户权益数据持久化
     * 将缓存中的数据覆盖写入数据库快照表
     */
    @Scheduled(cron = "0 */1 * * * ?")
    @Transactional
    public void persistEquityData() {
        log.debug("开始执行账户权益数据持久化任务");

        try {
            // 获取所有缓存的账户权益数据
            Map<Long, AccountEquityData> cachedEquities = accountEquityCache.asMap();

            if (cachedEquities.isEmpty()) {
                log.debug("缓存中没有账户权益数据，跳过本次持久化");
                return;
            }

            log.debug("开始持久化{}个API密钥的账户权益数据", cachedEquities.size());

            // 并发持久化所有缓存数据
            @SuppressWarnings("rawtypes") CompletableFuture[] futures = cachedEquities.values().stream()
                    .map(this::persistEquityDataItem)
                    .toArray(CompletableFuture[]::new);

            // 等待所有持久化操作完成
            CompletableFuture.allOf(futures)
                    .whenComplete((result, throwable) -> {
                        if (null != throwable) {
                            log.error("账户权益数据持久化任务执行过程中发生错误", throwable);
                        } else {
                            log.debug("账户权益数据持久化任务执行完成，处理了{}个API密钥", cachedEquities.size());
                        }
                    });

        } catch (Exception e) {
            log.error("账户权益数据持久化任务执行失败", e);
        }
    }

    /**
     * 持久化单个账户权益数据项
     * 使用Repository的upsert方法实现覆盖写入
     *
     * @param equityData 账户权益数据
     * @return CompletableFuture
     */
    private CompletableFuture<Void> persistEquityDataItem(AccountEquityData equityData) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 使用saveOrUpdate方法进行覆盖写入
                AccountEquitySnapshot saved = accountEquitySnapshotRepository.saveOrUpdateByApiKeyId(
                        equityData.getApiKeyId(),
                        equityData.getTotalEquityUsdt(),
                        equityData.getAvailableEquityUsdt(),
                        equityData.getFrozenEquityUsdt(),
                        equityData.getMarginEquityUsdt(),
                        equityData.getUpdateTime()
                );

                if (null != saved && saved.getEquityId() != null) {
                    log.debug("成功持久化API密钥 {} 的账户权益数据", equityData.getApiKeyId());
                } else {
                    log.warn("API密钥 {} 的账户权益数据持久化失败", equityData.getApiKeyId());
                }

            } catch (Exception e) {
                log.error("持久化API密钥 {} 的账户权益数据失败", equityData.getApiKeyId(), e);
            }
        });
    }

    /**
     * 手动持久化指定API密钥的账户权益数据
     *
     * @param apiKeyId API密钥ID
     * @return 是否持久化成功
     */
    @Transactional
    public boolean persistEquityDataByKeyId(Long apiKeyId) {
        try {
            AccountEquityData equityData = accountEquityCache.getIfPresent(apiKeyId);
            if (null == equityData) {
                log.warn("API密钥 {} 的账户权益数据在缓存中不存在", apiKeyId);
                return false;
            }

            AccountEquitySnapshot saved = accountEquitySnapshotRepository.saveOrUpdateByApiKeyId(
                    apiKeyId,
                    equityData.getTotalEquityUsdt(),
                    equityData.getAvailableEquityUsdt(),
                    equityData.getFrozenEquityUsdt(),
                    equityData.getMarginEquityUsdt(),
                    equityData.getUpdateTime()
            );

            boolean success = null != saved && saved.getEquityId() != null;
            log.info("手动持久化API密钥 {} 的账户权益数据，结果: {}", apiKeyId, success ? "成功" : "失败");
            return success;

        } catch (Exception e) {
            log.error("手动持久化API密钥 {} 的账户权益数据失败", apiKeyId, e);
            return false;
        }
    }

    /**
     * 获取最新的账户权益快照数据
     *
     * @param apiKeyId API密钥ID
     * @return 账户权益快照数据
     */
    public AccountEquitySnapshot getLatestSnapshot(Long apiKeyId) {
        return accountEquitySnapshotRepository.findByApiKeyId(apiKeyId).orElse(null);
    }

    /**
     * 清理指定时间之前的快照数据
     *
     * @param before 指定时间
     * @return 清理的记录数
     */
    @Transactional
    public int cleanupOldSnapshots(LocalDateTime before) {
        try {
            int deletedRows = accountEquitySnapshotRepository.deleteOldSnapshots(before);
            log.info("清理{}之前的旧快照数据，删除{}条记录", before, deletedRows);
            return deletedRows;
        } catch (Exception e) {
            log.error("清理旧快照数据失败", e);
            return 0;
        }
    }

    /**
     * 获取快照数据统计信息
     *
     * @return 统计信息字符串
     */
    public String getSnapshotStats() {
        try {
            long totalSnapshots = accountEquitySnapshotRepository.count();
            var cachedEquities = accountEquityCache.asMap();

            return String.format("账户权益快照统计 - 数据库记录数: %d, 缓存记录数: %d",
                    totalSnapshots, cachedEquities.size());
        } catch (Exception e) {
            log.error("获取快照统计信息失败", e);
            return "获取快照统计信息失败: " + e.getMessage();
        }
    }
}
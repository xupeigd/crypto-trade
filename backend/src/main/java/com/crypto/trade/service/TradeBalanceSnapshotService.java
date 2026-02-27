package com.crypto.trade.service;

import com.crypto.trade.entity.TradeBalanceSnapshot;
import com.crypto.trade.enums.TradeBalanceSnapshotSource;
import com.crypto.trade.model.AccountDetailModel;
import com.crypto.trade.repository.TradeBalanceSnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * TradeBalanceSnapshotService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class TradeBalanceSnapshotService {

    @Autowired
    TradeBalanceSnapshotRepository repository;

    /**
     * 创建账户余额快照的构建器
     * 方便快速创建快照对象
     *
     * @return TradeBalanceSnapshotBuilder
     */
    public static TradeBalanceSnapshot.TradeBalanceSnapshotBuilder createSnapshotBuilder() {
        return TradeBalanceSnapshot.builder();
    }

    /**
     * 创建应用AI资金限制后的账户余额快照
     * 统一处理所有source类型（INITIAL、REPLAY等）的快照创建
     * 确保所有快照使用一致的AI资金限制逻辑
     *
     * @param apiKeyId           API密钥ID
     * @param cexName            交易所名称
     * @param accountDetail      原始账户数据
     * @param source             来源类型（INITIAL、REPLAY等）
     * @param maxAvailableAmount AI最大可用金额限制
     * @param totalPnl           总盈亏（可选，如果为null则不使用新逻辑）
     * @return 应用AI限制后的TradeBalanceSnapshot对象
     */
    public static TradeBalanceSnapshot createSnapshotWithAiLimits(Long apiKeyId, String cexName, AccountDetailModel accountDetail,
                                                                  String source, BigDecimal maxAvailableAmount, BigDecimal totalPnl) {
        if (null == accountDetail) {
            log.warn("创建快照失败：账户数据为空，apiKeyId: {}, source: {}", apiKeyId, source);
            return null;
        }

        // 应用AI交易资金限制
        AccountDetailModel adjustedDetail = applyAiFundingLimitsStatic(accountDetail, maxAvailableAmount, totalPnl);
        // 构建快照对象
        BigDecimal displayTotalEquity = adjustedDetail.getDisplayTotalEquity() != null
                ? adjustedDetail.getDisplayTotalEquity()
                : adjustedDetail.getTotalEquity();
        return TradeBalanceSnapshot.builder()
                .apiKeyId(apiKeyId)
                .cexName(cexName)
                .totalEquityUsdt(adjustedDetail.getTotalEquity())
                .displayTotalEquityUsdt(displayTotalEquity)
                .availableEquityUsdt(adjustedDetail.getAvailableBalance())
                .usedMarginUsdt(adjustedDetail.getUsedMargin())
                .unrealizedPnlUsdt(adjustedDetail.getUnrealizedPnl())
                .marginRatio(adjustedDetail.getMarginRatio())
                .maxAvailableAmount(maxAvailableAmount)
                .source(source)
                .snapshotTime(LocalDateTime.now())
                .build();
    }

    /**
     * 应用AI交易资金限制（静态方法）
     * 复用AccountInfoProcessor中的逻辑
     *
     * @param accountDetail      原始账户数据
     * @param maxAvailableAmount AI最大可用金额限制
     * @param totalPnl           总盈亏（可选，如果为null则不使用新逻辑）
     * @return 应用AI限制后的账户数据
     */
    private static AccountDetailModel applyAiFundingLimitsStatic(
            AccountDetailModel accountDetail,
            BigDecimal maxAvailableAmount,
            BigDecimal totalPnl) {

        // 如果未配置AI账户最大可用金额限制，直接返回原始数据
        if (maxAvailableAmount == null || maxAvailableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return accountDetail;
        }

        BigDecimal originalTotalEquity = accountDetail.getTotalEquity();
        if (originalTotalEquity == null) {
            return accountDetail;
        }

        // 如果原始总权益未超过AI限制，不需要调整
        if (originalTotalEquity.compareTo(maxAvailableAmount) <= 0) {
            return accountDetail;
        }

        // 计算显示权益
        BigDecimal displayTotalEquity;
        if (totalPnl != null) {
            // 使用新逻辑：min(AI账户最大可用金额限制 + 总盈亏, 真实总权益)
            BigDecimal aiAdjustedTotalEquity = maxAvailableAmount.add(totalPnl);
            aiAdjustedTotalEquity = aiAdjustedTotalEquity.max(BigDecimal.ZERO);
            displayTotalEquity = aiAdjustedTotalEquity.min(originalTotalEquity);
            log.debug("使用新逻辑计算显示权益: AI限制调整权益={}, 真实权益={}, 最终显示权益={}, 总盈亏={}",
                    aiAdjustedTotalEquity, originalTotalEquity, displayTotalEquity, totalPnl);
        } else {
            // 使用旧逻辑：不超过AI账户最大可用金额限制
            displayTotalEquity = maxAvailableAmount;
        }

        // 获取其他字段
        BigDecimal usedMargin = accountDetail.getUsedMargin();
        BigDecimal unrealizedPnl = accountDetail.getUnrealizedPnl();

        // 计算真实的可用余额（真实总权益 - 已用保证金）
        BigDecimal realAvailableBalance = originalTotalEquity.subtract(usedMargin != null ? usedMargin : BigDecimal.ZERO);
        realAvailableBalance = realAvailableBalance.max(BigDecimal.ZERO);

        // 计算最终的可用余额
        BigDecimal calculatedAvailableBalance;
        if (totalPnl != null) {
            // 使用新逻辑：min((AI账户最大可用金额限制 + 总盈亏), 真实可用余额)
            BigDecimal aiAdjustedAvailableBalance = maxAvailableAmount.add(totalPnl);
            aiAdjustedAvailableBalance = aiAdjustedAvailableBalance.max(BigDecimal.ZERO);
            calculatedAvailableBalance = aiAdjustedAvailableBalance.min(realAvailableBalance);
            log.debug("使用新逻辑计算可用余额: AI限制调整可用={}, 真实可用={}, 最终可用={}, 总盈亏={}",
                    aiAdjustedAvailableBalance, realAvailableBalance, calculatedAvailableBalance, totalPnl);
        } else {
            // 使用旧逻辑：min(maxAvailableAmount - 已用保证金, 真实可用余额)
            BigDecimal aiLimitedAvailableBalance = maxAvailableAmount.subtract(usedMargin != null ? usedMargin : BigDecimal.ZERO);
            aiLimitedAvailableBalance = aiLimitedAvailableBalance.max(BigDecimal.ZERO);
            calculatedAvailableBalance = realAvailableBalance.min(aiLimitedAvailableBalance);
            log.debug("使用旧逻辑计算可用余额: AI限制可用={}, 真实可用={}, 最终可用={}",
                    aiLimitedAvailableBalance, realAvailableBalance, calculatedAvailableBalance);
        }

        // 基于显示权益计算保证金使用率
        BigDecimal calculatedMarginRatio = BigDecimal.ZERO;
        if (displayTotalEquity.compareTo(BigDecimal.ZERO) > 0 && usedMargin != null) {
            calculatedMarginRatio = usedMargin
                    .divide(displayTotalEquity, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        // 创建新的账户数据对象（不修改原始对象）
        AccountDetailModel adjustedAccountDetail = new AccountDetailModel();
        adjustedAccountDetail.setTotalEquity(originalTotalEquity);  // 保持真实权益
        adjustedAccountDetail.setDisplayTotalEquity(displayTotalEquity);  // 设置显示权益
        adjustedAccountDetail.setAvailableBalance(calculatedAvailableBalance);  // 使用min()计算的结果
        adjustedAccountDetail.setUsedMargin(usedMargin);
        adjustedAccountDetail.setUnrealizedPnl(unrealizedPnl);
        adjustedAccountDetail.setMarginRatio(calculatedMarginRatio);
        adjustedAccountDetail.setLastUpdateTime(accountDetail.getLastUpdateTime());

        log.debug("应用AI资金限制后: 真实总权益={}, 显示总权益={}, 最终可用={}, 保证金使用率={}%(分母={})",
                originalTotalEquity, displayTotalEquity, calculatedAvailableBalance, calculatedMarginRatio, displayTotalEquity);

        return adjustedAccountDetail;
    }

    /**
     * 保存账户余额快照（同步）
     *
     * @param snapshot 快照对象
     * @return 保存后的快照对象
     */
    @Transactional
    public TradeBalanceSnapshot saveSnapshot(TradeBalanceSnapshot snapshot) {
        try {
            TradeBalanceSnapshot saved = repository.save(snapshot);
            log.debug("成功保存账户余额快照，apiKeyId: {}, source: {}, time: {}",
                    snapshot.getApiKeyId(), snapshot.getSource(), snapshot.getSnapshotTime());
            return saved;
        } catch (Exception e) {
            log.error("保存账户余额快照失败，apiKeyId: {}, source: {}",
                    snapshot.getApiKeyId(), snapshot.getSource(), e);
            throw e;
        }
    }

    /**
     * 异步保存账户余额快照
     * 用于提高响应速度，不阻塞主流程
     *
     * @param snapshot 快照对象
     */
    @Async
    @Transactional
    public void saveSnapshotAsync(TradeBalanceSnapshot snapshot) {
        try {
            repository.save(snapshot);
            log.debug("异步保存账户余额快照成功，apiKeyId: {}, source: {}, time: {}",
                    snapshot.getApiKeyId(), snapshot.getSource(), snapshot.getSnapshotTime());
        } catch (Exception e) {
            log.error("异步保存账户余额快照失败，apiKeyId: {}, source: {}",
                    snapshot.getApiKeyId(), snapshot.getSource(), e);
        }
    }

    /**
     * 根据ID查询快照
     *
     * @param snapshotId 快照ID
     * @return 快照对象
     */
    public TradeBalanceSnapshot getSnapshotById(Long snapshotId) {
        return repository.findById(snapshotId).orElse(null);
    }

    /**
     * 查询指定API密钥的所有快照
     *
     * @param apiKeyId API密钥ID
     * @return 快照列表，按时间降序排列
     */
    public List<TradeBalanceSnapshot> getSnapshotsByApiKeyId(Long apiKeyId) {
        return repository.findByApiKeyIdOrderBySnapshotTimeDesc(apiKeyId);
    }

    /**
     * 查询指定API密钥和来源的快照
     *
     * @param apiKeyId API密钥ID
     * @param source   来源（INITIAL/REPLAY）
     * @return 快照列表，按时间降序排列
     */
    public List<TradeBalanceSnapshot> getSnapshotsByApiKeyIdAndSource(Long apiKeyId, String source) {
        return repository.findByApiKeyIdAndSourceOrderBySnapshotTimeDesc(apiKeyId, source);
    }

    /**
     * 查询指定API密钥的最新快照
     *
     * @param apiKeyId API密钥ID
     * @return 最新的快照，如果不存在则返回null
     */
    public TradeBalanceSnapshot getLatestSnapshotByApiKeyId(Long apiKeyId) {
        return repository.findLatestByApiKeyId(apiKeyId);
    }

    /**
     * 查询指定API密钥和来源的最新快照
     *
     * @param apiKeyId API密钥ID
     * @param source   来源（INITIAL/REPLAY）
     * @return 最新的快照，如果不存在则返回null
     */
    public TradeBalanceSnapshot getLatestSnapshotByApiKeyIdAndSource(Long apiKeyId, String source) {
        return repository.findLatestByApiKeyIdAndSource(apiKeyId, source);
    }

    /**
     * 查询指定API密钥的最早快照
     *
     * @param apiKeyId API密钥ID
     * @return 最早的快照，如果不存在则返回null
     */
    public TradeBalanceSnapshot getEarliestSnapshotByApiKeyId(Long apiKeyId) {
        return repository.findEarliestByApiKeyId(apiKeyId);
    }

    /**
     * 查询指定API密钥和来源的最早快照
     *
     * @param apiKeyId API密钥ID
     * @param source   来源（INITIAL/REPLAY）
     * @return 最早的快照，如果不存在则返回null
     */
    public TradeBalanceSnapshot getEarliestSnapshotByApiKeyIdAndSource(Long apiKeyId, String source) {
        return repository.findEarliestByApiKeyIdAndSource(apiKeyId, source);
    }

    /**
     * 计算账户总盈亏
     * 计算逻辑：当前账户权益 - 最早的TradeBalanceSnapshot的权益
     *
     * @param currentEquity 当前账户权益
     * @param apiKeyId      API密钥ID
     * @return 总盈亏（正数表示盈利，负数表示亏损），如果没有找到最早快照则返回null
     */
    public BigDecimal calculateTotalPnl(BigDecimal currentEquity, Long apiKeyId) {
        if (currentEquity == null) {
            log.warn("计算总盈亏失败：当前权益为空，apiKeyId: {}", apiKeyId);
            return null;
        }

        TradeBalanceSnapshot earliestSnapshot = getEarliestSnapshotByApiKeyId(apiKeyId);
        if (earliestSnapshot == null) {
            log.debug("计算总盈亏：未找到最早的快照，apiKeyId: {}", apiKeyId);
            return null;
        }

        BigDecimal earliestEquity = earliestSnapshot.getTotalEquityUsdt();
        if (earliestEquity == null) {
            log.warn("计算总盈亏失败：最早快照的权益为空，apiKeyId: {}", apiKeyId);
            return null;
        }

        BigDecimal totalPnl = currentEquity.subtract(earliestEquity);
        log.debug("计算总盈亏成功：当前权益={}, 最早权益={}, 总盈亏={}, apiKeyId: {}",
                currentEquity, earliestEquity, totalPnl, apiKeyId);

        return totalPnl;
    }

    /**
     * 查询最近的N条快照
     *
     * @param apiKeyId API密钥ID
     * @param limit    限制数量
     * @return 快照列表，按时间降序排列
     */
    public List<TradeBalanceSnapshot> getRecentSnapshots(Long apiKeyId, int limit) {
        return repository.findRecentSnapshotsByApiKeyId(apiKeyId, limit);
    }

    /**
     * 查询指定时间范围内的快照
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 快照列表，按时间降序排列
     */
    public List<TradeBalanceSnapshot> getSnapshotsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return repository.findBySnapshotTimeBetweenOrderBySnapshotTimeDesc(startTime, endTime);
    }

    /**
     * 删除指定时间之前的旧快照
     * 用于数据清理和历史数据管理
     *
     * @param before 时间阈值
     * @return 删除的记录数
     */
    @Transactional
    public int deleteOldSnapshots(LocalDateTime before) {
        try {
            int deletedCount = repository.deleteOldSnapshots(before);
            log.info("删除{}条时间早于{}的账户余额快照", deletedCount, before);
            return deletedCount;
        } catch (Exception e) {
            log.error("删除旧快照失败，before: {}", before, e);
            throw e;
        }
    }

    /**
     * 统计指定API密钥的快照数量
     *
     * @param apiKeyId API密钥ID
     * @return 快照数量
     */
    public long countSnapshotsByApiKeyId(Long apiKeyId) {
        return repository.countByApiKeyId(apiKeyId);
    }

    /**
     * 统计指定来源的快照数量
     *
     * @param source 来源（INITIAL/REPLAY）
     * @return 快照数量
     */
    public long countSnapshotsBySource(String source) {
        return repository.countBySource(source);
    }

    /**
     * 验证快照数据的完整性
     *
     * @param snapshot 快照对象
     * @return true if valid, false otherwise
     */
    public boolean validateSnapshot(TradeBalanceSnapshot snapshot) {
        if (snapshot == null) {
            log.warn("快照对象为null");
            return false;
        }

        if (snapshot.getApiKeyId() == null) {
            log.warn("快照缺少apiKeyId");
            return false;
        }

        if (snapshot.getCexName() == null || snapshot.getCexName().isEmpty()) {
            log.warn("快照缺少cexName，apiKeyId: {}", snapshot.getApiKeyId());
            return false;
        }

        if (snapshot.getTotalEquityUsdt() == null) {
            log.warn("快照缺少totalEquityUsdt，apiKeyId: {}", snapshot.getApiKeyId());
            return false;
        }

        if (snapshot.getSource() == null || snapshot.getSource().isEmpty()) {
            log.warn("快照缺少source，apiKeyId: {}", snapshot.getApiKeyId());
            return false;
        }

        // 验证source值是否合法
        try {
            TradeBalanceSnapshotSource.valueOf(snapshot.getSource());
        } catch (Exception e) {
            log.warn("快照source值不合法: {}，apiKeyId: {}", snapshot.getSource(), snapshot.getApiKeyId());
            return false;
        }

        return true;
    }

    /**
     * 计算两个快照之间的权益变化
     *
     * @param before 之前的快照
     * @param after  之后的快照
     * @return 权益变化值（正数表示增加，负数表示减少）
     */
    public BigDecimal calculateEquityChange(TradeBalanceSnapshot before, TradeBalanceSnapshot after) {
        if (before == null || after == null) {
            return BigDecimal.ZERO;
        }
        if (before.getTotalEquityUsdt() == null || after.getTotalEquityUsdt() == null) {
            return BigDecimal.ZERO;
        }
        return after.getTotalEquityUsdt().subtract(before.getTotalEquityUsdt());
    }

    /**
     * 更新快照的recordId
     * 在AI调用完成后，将生成的recordId关联到快照
     *
     * @param snapshotId 快照ID
     * @param recordId   LlmCallRecord ID
     * @return 更新是否成功
     */
    @Transactional
    public boolean updateRecordId(Long snapshotId, Long recordId) {
        try {
            if (snapshotId == null || recordId == null) {
                log.warn("更新recordId失败：snapshotId或recordId为空，snapshotId: {}, recordId: {}", snapshotId, recordId);
                return false;
            }

            int updatedCount = repository.updateRecordId(snapshotId, recordId);
            if (updatedCount > 0) {
                log.debug("成功更新快照recordId，snapshotId: {}, recordId: {}", snapshotId, recordId);
                return true;
            } else {
                // 区分不同的失败原因
                TradeBalanceSnapshot snapshot = repository.findById(snapshotId).orElse(null);
                if (snapshot == null) {
                    log.warn("更新recordId失败：快照不存在，snapshotId: {}, recordId: {}", snapshotId, recordId);
                } else if (snapshot.getRecordId() != null) {
                    log.info("更新recordId跳过：快照已关联其他recordId，snapshotId: {}, 当前recordId: {}, 尝试设置recordId: {}",
                            snapshotId, snapshot.getRecordId(), recordId);
                } else {
                    log.warn("更新recordId失败：未知原因，snapshotId: {}, recordId: {}, updatedCount: {}",
                            snapshotId, recordId, updatedCount);
                }
                return false;
            }
        } catch (Exception e) {
            // 分类处理不同类型的异常
            String errorMessage = e.getMessage();
            if (errorMessage != null) {
                if (errorMessage.contains("could not execute statement")) {
                    log.error("更新recordId失败：数据库执行错误，snapshotId: {}, recordId: {}", snapshotId, recordId, e);
                } else if (errorMessage.contains("constraint")) {
                    log.error("更新recordId失败：违反约束条件，snapshotId: {}, recordId: {}", snapshotId, recordId, e);
                } else if (errorMessage.contains("deadlock") || errorMessage.contains("lock")) {
                    log.warn("更新recordId失败：数据库锁冲突或死锁，snapshotId: {}, recordId: {}", snapshotId, recordId, e);
                } else {
                    log.error("更新快照recordId失败：未知异常，snapshotId: {}, recordId: {}", snapshotId, recordId, e);
                }
            } else {
                log.error("更新快照recordId失败：snapshotId: {}, recordId: {}", snapshotId, recordId, e);
            }
            return false;
        }
    }

    /**
     * 根据recordId查询快照
     *
     * @param recordId LlmCallRecord ID
     * @return 快照列表
     */
    public List<TradeBalanceSnapshot> getSnapshotsByRecordId(Long recordId) {
        return repository.findByRecordId(recordId);
    }

    /**
     * 查找没有关联recordId的快照
     * 用于数据清理和验证
     *
     * @return 快照列表
     */
    public List<TradeBalanceSnapshot> getSnapshotsWithoutRecordId() {
        return repository.findByRecordIdIsNull();
    }

    /**
     * 统计没有关联recordId的快照数量
     *
     * @return 快照数量
     */
    public long countSnapshotsWithoutRecordId() {
        return repository.countByRecordIdIsNull();
    }

    /**
     * 查找指定API密钥的最新且未关联recordId的快照
     * 用于并发场景下避免重复更新
     *
     * @param apiKeyId API密钥ID
     * @return 最新的未关联recordId的快照，如果不存在则返回null
     */
    public TradeBalanceSnapshot getLatestSnapshotByApiKeyIdAndRecordIdIsNull(Long apiKeyId) {
        return repository.findLatestByApiKeyIdAndRecordIdIsNull(apiKeyId);
    }

    /**
     * 查找指定API密钥和来源的最新且未关联recordId的快照
     * 用于并发场景下避免重复更新，同时考虑来源类型
     *
     * @param apiKeyId API密钥ID
     * @param source   来源（INITIAL/REPLAY）
     * @return 最新的未关联recordId的快照，如果不存在则返回null
     */
    public TradeBalanceSnapshot getLatestSnapshotByApiKeyIdAndSourceAndRecordIdIsNull(Long apiKeyId, String source) {
        return repository.findLatestByApiKeyIdAndSourceAndRecordIdIsNull(apiKeyId, source);
    }
}

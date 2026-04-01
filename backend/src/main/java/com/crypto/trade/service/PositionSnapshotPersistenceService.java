package com.crypto.trade.service;

import com.crypto.trade.dto.cex.model.CexPosition;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.PositionSnapshot;
import com.crypto.trade.repository.PositionSnapshotRepository;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PositionSnapshotPersistenceService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class PositionSnapshotPersistenceService {

    /**
     * 数据保留天数
     */
    private static final int RETENTION_DAYS = 90;
    /**
     * 历史数据查询天数
     */
    private static final int HISTORY_DAYS = 30;

    @Autowired
    PositionSnapshotRepository positionSnapshotRepository;
    /**
     * 注入unifiedCexApiService以调用历史持仓API
     */
    @Autowired
    UnifiedCexApiService unifiedCexApiService;
    /**
     * 注入ApiKeyRepository以获取活跃的API Key
     */
    @Autowired
    ApiKeyService apiKeyService;

    /**
     * 每5分钟执行一次仓位数据持久化
     * 从OKX API获取历史持仓数据并批量保存到数据库快照表
     * 使用覆盖写入模式(api_key_id + inst_id + pos_side唯一索引)
     */
    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional
    public void persistPositionData() {
        log.debug("开始执行仓位快照持久化任务(最近{}天)", HISTORY_DAYS);

        try {
            // 获取所有活跃的OKX API Key
            List<ApiKey> activeKeys = apiKeyService.getActiveKeys();
            if (activeKeys.isEmpty()) {
                log.debug("没有活跃的OKX API Key，跳过本次持久化");
                return;
            }

            log.info("找到 {} 个活跃的OKX API Key", activeKeys.size());

            // 计算时间范围（7天前的时间戳，毫秒）
            long sevenDaysAgo = System.currentTimeMillis() - (HISTORY_DAYS * 24L * 60L * 60L * 1000L);
            String afterTimestamp = String.valueOf(sevenDaysAgo);

            // 存储所有API Key的历史持仓数据
            Map<Long, List<CexPosition>> historyPositionsMap = new ConcurrentHashMap<>();

            // 使用数组来支持并发修改
            final int[] totalPositions = {0};

            // 遍历每个API Key获取历史持仓数据
            for (ApiKey apiKey : activeKeys) {
                try {
                    Long apiKeyId = apiKey.getKeyId();
                    log.debug("开始获取API Key {} 的历史持仓数据", apiKeyId);
                    long curafterTimestamp = sevenDaysAgo;
                    List<CexPosition> allPositions = new ArrayList<>();
                    // 获取多种合约类型的历史持仓
                    List<String> instTypes = List.of("SWAP");
                    ApiKey decryptedKey = apiKeyService.getDecryptedKey(apiKeyId);
                    for (String instType : instTypes) {
                        try {
                            Page<PositionSnapshot> positionSnapshots = positionSnapshotRepository.findOneByApiKeyIdAndInstType(apiKeyId,
                                    instType, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "utime")));
                            if (!positionSnapshots.isEmpty()) {
                                PositionSnapshot positionSnapshot = positionSnapshots.getContent().get(0);
                                curafterTimestamp = Math.max(sevenDaysAgo, positionSnapshot.getUtime());
                            }
                            // 直接使用通用CexPosition，无需适配
                            List<CexPosition> positions = unifiedCexApiService.getPositionsHistory(decryptedKey, instType,
                                    null, null, String.valueOf(curafterTimestamp), 100);
                            if (null != positions && !positions.isEmpty()) {
                                allPositions.addAll(positions);
                                log.debug("API Key {} 获取到 {} 个 {} 类型历史持仓", apiKeyId, positions.size(), instType);
                            }
                        } catch (Exception e) {
                            log.warn("获取API Key {} 的 {} 类型历史持仓失败", apiKeyId, instType, e);
                            // 继续处理其他类型
                        }
                    }

                    if (!allPositions.isEmpty()) {
                        historyPositionsMap.put(apiKeyId, allPositions);
                        totalPositions[0] += allPositions.size();
                        log.info("API Key {} 共获取到 {} 个历史持仓", apiKeyId, allPositions.size());
                    }

                } catch (Exception e) {
                    log.error("获取API Key {} 的历史持仓数据失败", apiKey.getKeyId(), e);
                }
            }

            if (historyPositionsMap.isEmpty()) {
                log.debug("没有获取到历史持仓数据，跳过本次持久化");
                return;
            }

            log.info("共需持久化 {} 个历史持仓快照", totalPositions[0]);

            // 同步批量持久化所有API密钥的仓位数据
            // 使用同步处理确保事务一致性,避免异步操作导致的事务失效问题
            for (Map.Entry<Long, List<CexPosition>> entry : historyPositionsMap.entrySet()) {
                try {
                    persistPositionDataByKeyIdSync(entry);
                } catch (Exception e) {
                    log.error("持久化API密钥 {} 的仓位数据失败", entry.getKey(), e);
                }
            }

            log.info("仓位快照持久化任务执行完成，处理了{}个API密钥，{}个持仓",
                    historyPositionsMap.size(), totalPositions[0]);

        } catch (Exception e) {
            log.error("仓位快照持久化任务执行失败", e);
        }
    }

    /**
     * 同步持久化指定API密钥的仓位数据
     * 遍历所有仓位,使用唯一索引实现覆盖写入
     * 使用同步方法确保事务一致性
     *
     * @param entry API密钥ID与仓位列表的映射
     */
    private void persistPositionDataByKeyIdSync(Map.Entry<Long, List<CexPosition>> entry) {
        Long apiKeyId = entry.getKey();
        List<CexPosition> positions = entry.getValue();

        try {
            if (positions == null || positions.isEmpty()) {
                log.debug("API密钥 {} 没有仓位数据，跳过持久化", apiKeyId);
                return;
            }

            LocalDateTime updateTime = LocalDateTime.now();

            // 遍历每个仓位进行持久化
            for (CexPosition position : positions) {
                try {
                    persistSinglePosition(apiKeyId, position, updateTime);
                } catch (Exception e) {
                    log.error("持久化API密钥 {} 的仓位 {} 失败",
                            apiKeyId, position.getSymbol(), e);
                }
            }

            log.debug("成功持久化API密钥 {} 的{}个仓位", apiKeyId, positions.size());

        } catch (Exception e) {
            log.error("持久化API密钥 {} 的仓位数据失败", apiKeyId, e);
        }
    }

    /**
     * 持久化单个仓位快照
     * 使用唯一索引(api_key_id + inst_id + pos_side + pos_id)实现覆盖写入
     *
     * @param apiKeyId   API密钥ID
     * @param position   仓位对象
     * @param updateTime 更新时间
     */
    private void persistSinglePosition(Long apiKeyId, CexPosition position, LocalDateTime updateTime) {
        try {
            // 验证posId不为空,避免重复数据插入
            if (!org.springframework.util.StringUtils.hasText(position.getPosId())) {
                log.warn("持仓缺少posId,跳过持久化: apiKeyId={}, instId={}, posSide={}",
                        apiKeyId, position.getSymbol(), position.getSide().getCode());
                return;
            }

            // 先查找是否存在(按新的4字段唯一索引)
            PositionSnapshot existing = positionSnapshotRepository
                    .findByApiKeyIdAndInstIdAndPosSideAndPosIdAndTypeAndCtimeAndUtime(apiKeyId, position.getSymbol(),
                            position.getSide().getCode(), position.getPosId(), position.getType(), position.getCreateTime(),
                            position.getUTime())
                    .orElse(null);

            PositionSnapshot snapshot;

            if (null != existing) {
                // 更新现有记录
                snapshot = existing;
                updateSnapshotFromPosition(snapshot, position, updateTime);
                log.debug("更新仓位快照: API密钥={}, 合约={}, 方向={}, posId={}",
                        apiKeyId, position.getSymbol(), position.getSide().getCode(), position.getPosId());
            } else {
                // 创建新记录
                snapshot = new PositionSnapshot();
                snapshot.setApiKeyId(apiKeyId);
                updateSnapshotFromPosition(snapshot, position, updateTime);
                log.debug("创建仓位快照: API密钥={}, 合约={}, 方向={}, posId={}",
                        apiKeyId, position.getSymbol(), position.getSide().getCode(), position.getPosId());
            }

            positionSnapshotRepository.save(snapshot);

        } catch (Exception e) {
            log.error("保存仓位快照失败: API密钥={}, symbol={}",
                    apiKeyId, position.getSymbol(), e);
            throw e;
        }
    }

    /**
     * 从CexPosition对象更新PositionSnapshot的字段
     *
     * @param snapshot   快照对象
     * @param position   仓位对象
     * @param updateTime 更新时间
     */
    private void updateSnapshotFromPosition(PositionSnapshot snapshot,
                                            CexPosition position,
                                            LocalDateTime updateTime) {
        // 基础字段
        snapshot.setInstId(position.getSymbol());
        snapshot.setInstType(position.getInstrumentType());
        snapshot.setPosSide(position.getSide().getCode());

        // 持仓ID (唯一标识)
        snapshot.setPosId(position.getPosId());

        // 数量和价格字段
        snapshot.setPos(nullToZero(position.getQuantity()));
        snapshot.setAvailPos(nullToZero(position.getAvailableQuantity()));
        snapshot.setAvgPx(position.getAvgPrice());
        snapshot.setMarkPx(position.getMarkPrice());
        snapshot.setLastPx(position.getLast());

        // 杠杆和保证金字段
        snapshot.setLever(position.getLeverage());
        snapshot.setMargin(position.getMargin());
        snapshot.setImr(position.getImr());
        snapshot.setMmr(position.getMmr());
        snapshot.setMgnRatio(position.getMarginRatio());
        snapshot.setMgnMode(position.getMgnMode());

        // 盈亏字段
        snapshot.setUpl(position.getUnrealizedPnl());
        snapshot.setUplLastPx(position.getUplLastPx());
        snapshot.setRealizedPnl(position.getRealizedPnl());

        // 历史仓位特有字段
        snapshot.setOpenMaxPos(position.getOpenMaxPos());
        snapshot.setCloseTotalPos(position.getCloseTotalPos());
        snapshot.setSettledPnl(position.getSettledPnl());
        snapshot.setPnlRatio(position.getPnlRatio());

        // 价值字段
        snapshot.setNotionalUsd(position.getNotionalValue());

        // 其他字段
        snapshot.setLiqPx(position.getLiquidationPrice());
        snapshot.setFee(position.getFee());
        snapshot.setFundingFee(position.getFundingFee());
        snapshot.setCcy(position.getCurrency());
        snapshot.setType(position.getType());

        // 时间字段
        if (null != position.getCreateTime() && position.getCreateTime() > 0) {
            snapshot.setCtime(position.getCreateTime());
        }
        if (null != position.getUTime() && position.getUTime() > 0) {
            snapshot.setUtime(position.getUTime());
        }
        if (null != position.getDataIngestionTime()) {
            snapshot.setDataIngestionTime(position.getDataIngestionTime());
        }

        snapshot.setOpenAvgPx(position.getOpenAvgPx());
        snapshot.setCloseAvgPx(position.getCloseAvgPx());

        // 快照更新时间
        snapshot.setUpdateTime(updateTime);
    }

    /**
     * 将null值转换为0
     */
    private BigDecimal nullToZero(BigDecimal value) {
        return null == value ? BigDecimal.ZERO : value;
    }

    /**
     * 每天凌晨2点清理90天前的历史快照数据
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldSnapshots() {
        log.info("开始清理{}天前的旧仓位快照数据", RETENTION_DAYS);

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(RETENTION_DAYS);
            int deletedRows = positionSnapshotRepository.deleteOldSnapshots(cutoffTime);

            log.info("清理完成，删除了{}条旧快照数据", deletedRows);

        } catch (Exception e) {
            log.error("清理旧快照数据失败", e);
        }
    }

    /**
     * 获取指定API密钥的仓位历史快照
     * 支持按合约类型、时间范围过滤和数量限制
     *
     * @param apiKeyId API密钥ID
     * @param instType 产品类型(可选)
     * @param before   结束时间戳-毫秒(可选)
     * @param limit    返回数量限制(可选)
     * @return 仓位快照列表, 按utime降序排列
     */
    public List<PositionSnapshot> getPositionHistory(Long apiKeyId, String instType, String before, Integer limit) {
        try {
            log.debug("查询仓位历史快照 - apiKeyId: {}, instType: {}, before: {}, limit: {}",
                    apiKeyId, instType, before, limit);

            // 解析时间参数
            final Long beforeTime = StringUtils.hasText(before) ? Long.parseLong(before) : null;

            // 基础查询 - 在数据库层过滤时间范围,提升查询性能
            List<PositionSnapshot> snapshots;

            if (instType != null && !instType.isEmpty()) {
                // 按合约类型 + 时间范围过滤
                if (beforeTime != null) {
                    snapshots = positionSnapshotRepository.findByApiKeyIdAndInstTypeAndUtimeBeforeOrderByUtimeDesc(apiKeyId,
                            instType, beforeTime);
                } else {
                    snapshots = positionSnapshotRepository
                            .findByApiKeyIdAndInstTypeOrderByUtimeDesc(apiKeyId, instType);
                }
            } else {
                // 只按时间范围过滤
                if (beforeTime != null) {
                    snapshots = positionSnapshotRepository.findByApiKeyIdAndUtimeBeforeOrderByUtimeDesc(apiKeyId, beforeTime);
                } else {
                    snapshots = positionSnapshotRepository.findByApiKeyIdOrderByUtimeDesc(apiKeyId);
                }
            }

            // 应用limit限制
            if (limit != null && limit > 0 && snapshots.size() > limit) {
                snapshots = snapshots.subList(0, limit);
            }

            log.debug("查询到 {} 条仓位历史快照", snapshots.size());
            return snapshots;

        } catch (Exception e) {
            log.error("查询仓位历史快照失败 - apiKeyId: {}", apiKeyId, e);
            return Collections.emptyList();
        }
    }

}

package com.crypto.trade.service;

import com.crypto.trade.entity.TradeBalanceSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SnapshotMonitoringService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class SnapshotMonitoringService {

    /**
     * 告警阈值：未关联recordId的快照数量
     * 超过此数量将触发告警
     */
    private static final long ALERT_THRESHOLD = 10;
    /**
     * 未关联recordId的快照数量缓存
     */
    private final AtomicLong unlinkedSnapshotCount = new AtomicLong(0);
    @Autowired
    TradeBalanceSnapshotService tradeBalanceSnapshotService;
    /**
     * 最后一次检查时间
     */
    private volatile LocalDateTime lastCheckTime;

    /**
     * 定时检查快照一致性
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void scheduledCheckSnapshotConsistency() {
        try {
            log.debug("开始定时检查快照一致性");

            long unlinkedCount = tradeBalanceSnapshotService.countSnapshotsWithoutRecordId();
            unlinkedSnapshotCount.set(unlinkedCount);
            lastCheckTime = LocalDateTime.now();

            // 如果超过阈值，记录告警日志
            if (unlinkedCount > ALERT_THRESHOLD) {
                log.warn("快照一致性告警：发现 {} 个未关联recordId的快照，超过阈值 {}",
                        unlinkedCount, ALERT_THRESHOLD);

                // 获取详细的快照列表用于分析
                List<TradeBalanceSnapshot> unlinkedSnapshots =
                        tradeBalanceSnapshotService.getSnapshotsWithoutRecordId();

                log.debug("未关联recordId的快照详情：");
                for (TradeBalanceSnapshot snapshot : unlinkedSnapshots) {
                    log.debug("  - snapshotId: {}, apiKeyId: {}, source: {}, snapshotTime: {}",
                            snapshot.getSnapshotId(),
                            snapshot.getApiKeyId(),
                            snapshot.getSource(),
                            snapshot.getSnapshotTime());
                }
            } else {
                log.debug("快照一致性检查通过：未关联recordId的快照数量为 {}", unlinkedCount);
            }

        } catch (Exception e) {
            log.error("定时检查快照一致性失败", e);
        }
    }

}

package com.crypto.trade.service;

import com.crypto.trade.entity.CexTradingOrder;
import com.crypto.trade.repository.CexTradingOrderRepository;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CexOrderSyncService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class CexOrderSyncService {

    @Autowired
    CexTradingOrderRepository cexTradingOrderRepository;
    @Autowired
    UnifiedCexApiService unifiedCexApiService;

    /**
     * 定时同步CEX订单状态
     * <p>
     * 每30秒执行一次，查询活跃订单的最新状态
     * 使用synchronized锁防止并发重复执行
     * </p>
     */
    @Scheduled(fixedRate = 30000) // 每30秒
    @Transactional
    public synchronized void syncCexOrders() {
        // 防止并发重复执行
        try {
            // 查询需要同步的订单（pending或failed状态）
            List<CexTradingOrder> ordersToSync = cexTradingOrderRepository.findOrdersNeedSync();
            if (ordersToSync.isEmpty()) {
                log.debug("没有需要同步的CEX订单");
                return;
            }

            log.info("开始同步CEX订单，数量: {}", ordersToSync.size());

            int successCount = 0;
            int failCount = 0;

            for (CexTradingOrder order : ordersToSync) {
                try {
                    syncSingleOrder(order);
                    successCount++;
                } catch (Exception e) {
                    log.error("同步订单失败: orderId={}, error={}", order.getOrderId(), e.getMessage(), e);
                    failCount++;
                    handleSyncFailure(order, e);
                }
            }

            log.info("CEX订单同步完成 - 成功: {}, 失败: {}", successCount, failCount);

        } catch (Exception e) {
            log.error("CEX订单同步任务执行失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 同步单个订单
     *
     * @param order CEX订单
     */
    private void syncSingleOrder(CexTradingOrder order) {
        try {
            // 标记为同步中
            order.setSyncStatus("syncing");
            cexTradingOrderRepository.save(order);

            // 根据交易所调用不同的API
            if ("okx".equalsIgnoreCase(order.getExchange())) {
                syncOkxOrder(order);
            } else {
                log.warn("暂不支持该交易所的订单同步: {}", order.getExchange());
                order.setSyncStatus("failed");
                order.setSyncErrorMsg("不支持的交易所");
            }

        } catch (Exception e) {
            throw new RuntimeException("同步订单失败: " + e.getMessage(), e);
        }
    }

    /**
     * 同步OKX订单状态
     *
     * @param order CEX订单
     */
    private void syncOkxOrder(CexTradingOrder order) {
        // TODO: 调用OKX API查询订单详情
        // 这里需要实现实际的API调用逻辑

        // 示例：查询订单状态
        // OkxOrderDetail detail = unifiedCexApiService.getOrderDetail(order.getOrderId());

        // 更新订单信息
        // updateOrderFromOkxResponse(order, detail);

        // 模拟更新（实际实现时需要删除）
        log.debug("同步OKX订单: orderId={}", order.getOrderId());

        // 更新同步时间和状态
        order.setLastSyncTime(LocalDateTime.now());
        // todo
        order.setSyncStatus("completed"); // 修改：使用completed替代synced
        order.setSyncRetryCount(0); // 重置重试次数
        order.setSyncErrorMsg(null);

        cexTradingOrderRepository.save(order);
    }

    /**
     * 从OKX响应更新订单信息
     *
     * @param order  CEX订单实体
     * @param detail OKX订单详情
     */
    private void updateOrderFromOkxResponse(CexTradingOrder order, Object detail) {
        // TODO: 实现从OKX响应更新订单字段
        // order.setOrderState(detail.getState());
        // order.setFilledSz(detail.getFilledSz());
        // order.setAvgPx(detail.getAvgPx());
        // order.setFillRatio(order.calculateFillRatio());
    }

    /**
     * 处理同步失败
     *
     * @param order CEX订单
     * @param error 异常信息
     */
    private void handleSyncFailure(CexTradingOrder order, Exception error) {
        order.setSyncStatus("failed");
        order.setSyncErrorMsg(error.getMessage());
        order.setSyncRetryCount(order.getSyncRetryCount() + 1);

        // 如果重试次数超过阈值，标记为陈旧数据
        if (order.getSyncRetryCount() > 10) {
            order.setSyncStatus("stale");
        }

        cexTradingOrderRepository.save(order);
    }

    /**
     * 手动触发同步指定订单
     *
     * @param orderId CEX订单ID
     */
    @Transactional
    public void syncOrderById(String orderId) {
        CexTradingOrder order = cexTradingOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在: " + orderId));

        syncSingleOrder(order);
        log.info("手动同步订单完成: orderId={}", orderId);
    }

    /**
     * 清理历史订单数据
     * <p>
     * 删除3个月前已完成（filled/canceled/failed）的订单
     * </p>
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
    @Transactional
    public void cleanupOldOrders() {
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);

        // 执行删除操作
        cexTradingOrderRepository.deleteCompletedOrdersBefore(threeMonthsAgo);

        log.info("清理历史CEX订单完成，删除3个月前的已完成订单");
    }

    /**
     * 获取同步统计信息
     *
     * @return 统计信息
     */
    public SyncStats getSyncStats() {
        SyncStats stats = new SyncStats();

        stats.setTotalOrders(cexTradingOrderRepository.count());
        stats.setActiveOrders(cexTradingOrderRepository.countByOrderState("live") +
                cexTradingOrderRepository.countByOrderState("partially_filled"));
        stats.setSyncedOrders(cexTradingOrderRepository.findBySyncStatus("completed").size()); // 修改：使用completed
        stats.setSyncingOrders(cexTradingOrderRepository.findBySyncStatus("syncing").size());
        stats.setFailedOrders(cexTradingOrderRepository.findBySyncStatus("failed").size());
        stats.setStaleOrders(cexTradingOrderRepository.findBySyncStatus("stale").size());

        return stats;
    }

    /**
     * 同步统计信息
     */
    public static class SyncStats {
        private long totalOrders;
        private long activeOrders;
        private long syncedOrders;
        private long syncingOrders;
        private long failedOrders;
        private long staleOrders;

        // Getters and Setters
        public long getTotalOrders() {
            return totalOrders;
        }

        public void setTotalOrders(long totalOrders) {
            this.totalOrders = totalOrders;
        }

        public long getActiveOrders() {
            return activeOrders;
        }

        public void setActiveOrders(long activeOrders) {
            this.activeOrders = activeOrders;
        }

        public long getSyncedOrders() {
            return syncedOrders;
        }

        public void setSyncedOrders(long syncedOrders) {
            this.syncedOrders = syncedOrders;
        }

        public long getSyncingOrders() {
            return syncingOrders;
        }

        public void setSyncingOrders(long syncingOrders) {
            this.syncingOrders = syncingOrders;
        }

        public long getFailedOrders() {
            return failedOrders;
        }

        public void setFailedOrders(long failedOrders) {
            this.failedOrders = failedOrders;
        }

        public long getStaleOrders() {
            return staleOrders;
        }

        public void setStaleOrders(long staleOrders) {
            this.staleOrders = staleOrders;
        }
    }
}

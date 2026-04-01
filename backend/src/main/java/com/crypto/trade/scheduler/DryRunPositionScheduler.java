package com.crypto.trade.scheduler;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.DryRunPosition;
import com.crypto.trade.service.DryRunPositionService;
import com.crypto.trade.service.ExecutionModeResolver;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.market.UnifiedPriceDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DryRunPositionScheduler
 * 模拟仓位止盈止损定时检查器
 * 每30秒检查所有未平仓的模拟仓位的止盈止损触发状态
 *
 * @author page
 * @date 2026-03-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DryRunPositionScheduler {

    private final DryRunPositionService dryRunPositionService;
    private final ApiKeyService apiKeyService;
    private final UnifiedPriceDataService unifiedPriceDataService;
    private final ExecutionModeResolver executionModeResolver;

    // 缓存API Key，避免重复查询
    private final Map<Long, ApiKey> apiKeyCache = new ConcurrentHashMap<>();

    /**
     * 每30秒检查所有未平仓的模拟仓位的止盈止损触发状态，以及限价单委托是否成交
     */
    @Scheduled(fixedRate = 30000)
    public void checkTpSlAndLimitOrders() {
        // 首先检查系统是否启用了模拟模式
        if (!isAnyDryRunModeEnabled()) {
            log.trace("【模拟仓位定时任务】未开启模拟模式，跳过检查");
            return;
        }

        // 1. 检查限价单委托是否成交
        checkLimitOrdersForFill();

        // 2. 检查止盈止损触发
        checkTpSlForAllPositions();
    }

    /**
     * 检查是否有任何配置启用了模拟模式
     * 只要全局、AI交易或任何智能体启用了模拟模式，就需要执行此定时任务
     */
    private boolean isAnyDryRunModeEnabled() {
        try {
            return executionModeResolver.isAnyDryRunEnabled();
        } catch (Exception e) {
            log.error("检查是否启用模拟模式失败", e);
            // 发生异常时默认执行，以防漏掉处理
            return true;
        }
    }

    /**
     * 检查限价单委托是否成交
     * 成交条件：价格穿过委托价
     * - 买入限价开多：markPx < pendingPx（价格跌破委托价）
     * - 卖出限价开空：markPx > pendingPx（价格涨破委托价）
     */
    private void checkLimitOrdersForFill() {
        try {
            log.debug("【模拟仓位定时任务】开始检查限价单委托成交");

            // 获取所有委托中的模拟订单
            List<DryRunPosition> pendingPositions = dryRunPositionService.getAllPendingPositions();

            if (pendingPositions.isEmpty()) {
                log.debug("【模拟仓位定时任务】无委托中的限价单");
                return;
            }

            log.debug("【模拟仓位定时任务】委托中限价单数量: {}", pendingPositions.size());

            int filledCount = 0;
            for (DryRunPosition pos : pendingPositions) {
                try {
                    // 获取API Key
                    ApiKey apiKey = apiKeyCache.computeIfAbsent(pos.getApiKeyId(),
                            id -> apiKeyService.getDecryptedKey(id));

                    if (apiKey == null) {
                        log.warn("【模拟仓位定时任务】API Key不存在 - apiKeyId: {}", pos.getApiKeyId());
                        continue;
                    }

                    // 获取当前标记价格
                    BigDecimal markPx = unifiedPriceDataService.getMarkPrice(apiKey, pos.getInstId());
                    if (markPx == null || markPx.compareTo(BigDecimal.ZERO) <= 0) {
                        log.warn("【模拟仓位定时任务】无法获取标记价格 - instId: {}", pos.getInstId());
                        continue;
                    }

                    // 检查限价单是否成交
                    boolean filled = dryRunPositionService.checkAndFillLimitOrder(pos, markPx);
                    if (filled) {
                        filledCount++;
                        log.info("【模拟仓位定时任务】限价单成交 - instId: {}, posSide: {}, fillPx: {}",
                                pos.getInstId(), pos.getPosSide(), markPx);
                    }
                } catch (Exception e) {
                    log.error("【模拟仓位定时任务】检查限价单失败 - instId: {}, posSide: {}",
                            pos.getInstId(), pos.getPosSide(), e);
                }
            }

            if (filledCount > 0) {
                log.info("【模拟仓位定时任务】本轮限价单成交数量: {}", filledCount);
            }
        } catch (Exception e) {
            log.error("【模拟仓位定时任务】检查限价单异常", e);
        }
    }

    /**
     * 检查所有未平仓的模拟仓位的止盈止损触发状态
     */
    private void checkTpSlForAllPositions() {
        try {
            log.debug("【模拟仓位定时任务】开始检查止盈止损触发状态");

            // 获取所有未平仓的模拟仓位
            List<DryRunPosition> openPositions = dryRunPositionService.getAllOpenPositions();

            if (openPositions.isEmpty()) {
                log.debug("【模拟仓位定时任务】无未平仓的模拟仓位");
                return;
            }

            log.debug("【模拟仓位定时任务】未平仓仓位数量: {}", openPositions.size());

            int triggeredCount = 0;
            for (DryRunPosition pos : openPositions) {
                try {
                    // 跳过没有设置止盈止损的仓位
                    if (pos.getTakeProfitPrice() == null && pos.getStopLossPrice() == null) {
                        continue;
                    }

                    // 获取API Key
                    ApiKey apiKey = apiKeyCache.computeIfAbsent(pos.getApiKeyId(), 
                            id -> apiKeyService.getDecryptedKey(id));

                    if (apiKey == null) {
                        log.warn("【模拟仓位定时任务】API Key不存在 - apiKeyId: {}", pos.getApiKeyId());
                        continue;
                    }

                    // 获取当前标记价格
                    BigDecimal markPx = unifiedPriceDataService.getMarkPrice(apiKey, pos.getInstId());
                    if (markPx == null || markPx.compareTo(BigDecimal.ZERO) <= 0) {
                        log.warn("【模拟仓位定时任务】无法获取标记价格 - instId: {}", pos.getInstId());
                        continue;
                    }

                    // 检查是否触发止盈止损
                    boolean triggered = dryRunPositionService.checkAndCloseOnTpSl(pos, markPx);
                    if (triggered) {
                        triggeredCount++;
                        log.info("【模拟仓位定时任务】止盈止损触发 - instId: {}, posSide: {}, closeReason: {}",
                                pos.getInstId(), pos.getPosSide(), pos.getCloseReason());
                    }
                } catch (Exception e) {
                    log.error("【模拟仓位定时任务】检查仓位失败 - instId: {}, posSide: {}",
                            pos.getInstId(), pos.getPosSide(), e);
                }
            }

            if (triggeredCount > 0) {
                log.info("【模拟仓位定时任务】本轮触发平仓数量: {}", triggeredCount);
            }
        } catch (Exception e) {
            log.error("【模拟仓位定时任务】执行异常", e);
        }
    }

    /**
     * 清理API Key缓存（在API Key更新时调用）
     */
    public void clearApiKeyCache(Long apiKeyId) {
        apiKeyCache.remove(apiKeyId);
    }

    /**
     * 清理所有缓存
     */
    public void clearAllCache() {
        apiKeyCache.clear();
    }
}

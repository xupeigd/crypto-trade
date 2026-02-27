package com.crypto.trade.service.trading;

import com.crypto.trade.dto.cex.adapter.CexOrderAdapter;
import com.crypto.trade.dto.cex.model.CexOrder;
import com.crypto.trade.dto.cex.okx.OkxOrder;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.CexTradingOrder;
import com.crypto.trade.entity.TradingOrder;
import com.crypto.trade.repository.CexTradingOrderRepository;
import com.crypto.trade.repository.TradingOrderRepository;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * OrderSyncService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSyncService {

    private final TradingOrderRepository tradingOrderRepository;
    private final CexTradingOrderRepository cexTradingOrderRepository;
    private final UnifiedCexApiService unifiedCexApiService;
    private final ApiKeyService apiKeyService;

    /**
     * 同步所有活跃API Key的订单状态
     * 1. 动态获取所有活跃的API Key
     * 2. 按交易所分组并发同步
     * 3. 从交易所API获取最近7天的SWAP永续合约订单
     * 4. 更新本地CexTradingOrder表(只更新状态字段)
     * 5. 更新本地TradingOrder表状态
     * <p>
     * 定时执行: 每60秒执行一次
     *
     * @return 更新数量
     */
    @Scheduled(fixedDelay = 60 * 1000L)
    public int syncOrders() {
        Long apiKeyId = 1L;
        log.debug("开始同步订单 - apiKeyId: {}", apiKeyId);
        int updatedCount = 0;

        try {
            List<ApiKey> activeKeys = apiKeyService.getActiveKeys();
            if (!CollectionUtils.isEmpty(activeKeys)) {
                for (ApiKey activeKey : activeKeys) {
                    ApiKey apiKey = apiKeyService.getDecryptedKey(activeKey.getKeyId());
                    syncOrdersForApiKey(apiKey);
                }
            }
        } catch (Exception e) {
            log.error("订单同步过程发生异常 - apiKeyId: {}", apiKeyId, e);
            throw new RuntimeException("订单同步失败", e);
        }

        log.debug("订单同步完成 - apiKeyId: {}, 更新数量: {}", apiKeyId, updatedCount);
        return updatedCount;
    }

    /**
     * 同步指定API Key的订单
     *
     * @param apiKey API Key
     */
    private void syncOrdersForApiKey(ApiKey apiKey) {
        String cexName = apiKey.getCexName();
        Long apiKeyId = apiKey.getKeyId();
        log.debug("开始同步 {} 交易所的订单 - apiKeyId: {}", cexName, apiKey.getKeyId());
        int updatedCount = 0;
        try {
            // 2. 根据交易所类型调用对应的API获取历史订单 (SWAP永续合约, 最近7天)
            List<OkxOrder> okxOrders;
            if ("OKX".equalsIgnoreCase(cexName)) {
                // 调用OKX API获取SWAP永续合约订单 - 现在返回通用CexOrder,需反向适配
                List<CexOrder> cexOrders = unifiedCexApiService.getHistoryOrders(apiKey, "SWAP", null, null);
                okxOrders = CexOrderAdapter.adaptToOkx(cexOrders);
            } else {
                // 其他交易所暂未实现
                log.debug("暂不支持 {} 交易所的订单同步", cexName);
                return;
            }
            if (null == okxOrders || okxOrders.isEmpty()) {
                log.debug("{} 交易所没有历史订单 - apiKeyId: {}", cexName, apiKeyId);
                return;
            }
            log.debug("从 {} 获取到 {} 条历史订单 - apiKeyId: {}", cexName, okxOrders.size(), apiKeyId);
            // 3. 遍历订单进行同步
            for (OkxOrder okxOrder : okxOrders) {
                try {
                    boolean changed = syncSingleOrder(okxOrder, apiKeyId);
                    if (changed) {
                        updatedCount++;
                    }
                } catch (Exception e) {
                    log.error("同步单个订单失败 - orderId: {}, apiKeyId: {}", okxOrder.getOrdId(), apiKeyId, e);
                }
            }

        } catch (Exception e) {
            log.error("同步API Key订单失败 - apiKeyId: {}, cexName: {}", apiKeyId, cexName, e);
        }
    }

    /**
     * 同步单个订单数据
     *
     * @return 是否有更新
     */
    private boolean syncSingleOrder(OkxOrder okxOrder, Long apiKeyId) {
        boolean updated = false;

        // 1. 同步到 CexTradingOrder 表
        CexTradingOrder cexOrder = cexTradingOrderRepository.findByOrderId(okxOrder.getOrdId())
                .orElse(new CexTradingOrder());

        // 更新CexTradingOrder字段
        updateCexOrderFromOkx(cexOrder, okxOrder, apiKeyId);
        cexTradingOrderRepository.save(cexOrder);

        // 2. 同步到 TradingOrder 表
        // 尝试通过 clOrdId (orderUuid) 查找
        Optional<TradingOrder> tradingOrderOpt = Optional.empty();
        if (StringUtils.hasText(okxOrder.getClOrdId())) {
            tradingOrderOpt = tradingOrderRepository.findByOrderUuid(okxOrder.getClOrdId());
        }

        // 如果没找到，尝试通过 cexOrderId 查找
        if (tradingOrderOpt.isEmpty()) {
            tradingOrderOpt = tradingOrderRepository.findByCexOrderId(okxOrder.getOrdId());
        }

        if (tradingOrderOpt.isPresent()) {
            TradingOrder tradingOrder = tradingOrderOpt.get();

            // 只有当状态不一致，或者本地未完成时才更新
            if (!tradingOrder.isCompleted() || !isStatusConsistent(tradingOrder, okxOrder)) {
                updateTradingOrderFromOkx(tradingOrder, okxOrder);
                tradingOrderRepository.save(tradingOrder);
                updated = true;
            }
        } else {
            // 如果没找到对应的TradingOrder记录,创建新记录
            // 额外去重检查:防止重复创建
            if (!isOrderAlreadyExists(okxOrder, apiKeyId)) {
                TradingOrder newOrder = createNewTradingOrderFromOkx(okxOrder, apiKeyId);
                tradingOrderRepository.save(newOrder);

                // 为外部订单建立双向关联
                // 外部订单没有clOrdId，需要关联到新生成的orderUuid
                if (StringUtils.hasText(newOrder.getOrderUuid()) &&
                        !StringUtils.hasText(cexOrder.getClOrdId())) {
                    cexOrder.setClOrdId(newOrder.getOrderUuid());
                    cexTradingOrderRepository.save(cexOrder);
                    log.info("建立外部订单双向关联 - orderId: {}, orderUuid: {}",
                            okxOrder.getOrdId(), newOrder.getOrderUuid());
                }

                updated = true;
                log.info("创建新的TradingOrder记录(同步) - orderId: {}, clOrdId: {}, instId: {}",
                        okxOrder.getOrdId(), okxOrder.getClOrdId(), okxOrder.getInstId());
            } else {
                log.debug("订单已存在,跳过创建 - orderId: {}", okxOrder.getOrdId());
            }
        }

        return updated;
    }

    /**
     * 从OkxOrder更新CexTradingOrder
     * ✅ 优化：新增订单设置所有字段，已存在订单只更新状态相关字段
     *
     * @param cexOrder 数据库订单对象
     * @param okxOrder OKX API订单对象
     * @param apiKeyId API Key ID
     */
    private void updateCexOrderFromOkx(CexTradingOrder cexOrder, OkxOrder okxOrder, Long apiKeyId) {
        // 判断是否为新订单（通过orderId字段判断）
        boolean isNewOrder = (cexOrder.getOrderId() == null);

        if (isNewOrder) {
            // ========== 新增订单：设置所有字段 ==========
            cexOrder.setOrderId(okxOrder.getOrdId());
            cexOrder.setApiKeyId(apiKeyId);
            cexOrder.setClOrdId(okxOrder.getClOrdId());
            cexOrder.setInstId(okxOrder.getInstId());
            cexOrder.setInstType(okxOrder.getInstType());
            cexOrder.setSide(okxOrder.getSide());
            cexOrder.setPosSide(okxOrder.getPosSide());
            cexOrder.setOrderType(okxOrder.getOrdType());
            cexOrder.setSz(okxOrder.getSz());
            cexOrder.setPx(okxOrder.getPx());

            // 创建时间（只设置一次，不更新）
            if (okxOrder.getCTime() != null) {
                cexOrder.setCTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(okxOrder.getCTime()), ZoneId.systemDefault()));
            }

            // 交易所名称
            cexOrder.setExchange("OKX");

            log.debug("创建新订单: orderId={}, instId={}, side={}, posSide={}",
                    okxOrder.getOrdId(), okxOrder.getInstId(), okxOrder.getSide(), okxOrder.getPosSide());
        } else {
            log.debug("更新已存在订单状态: orderId={}", okxOrder.getOrdId());
        }

        // ========== 状态相关字段：新增和已存在订单都更新 ==========

        // 订单状态（核心状态字段）
        cexOrder.setOrderState(okxOrder.getState());

        // 成交均价（状态字段，会变化）
        cexOrder.setAvgPx(okxOrder.getAvgPx());

        // 成交数量（使用累计成交数量）
        if (okxOrder.getAccFillSz() != null) {
            cexOrder.setFilledSz(okxOrder.getAccFillSz());
        } else {
            cexOrder.setFilledSz(okxOrder.getFillSz());
        }

        // 成交金额（API未直接返回，设为null）
        cexOrder.setFilledAmt(null);

        // 手续费和返佣（状态字段）
        cexOrder.setFee(okxOrder.getFee());
        cexOrder.setFeeCcy(okxOrder.getFeeCcy());
        cexOrder.setRebate(okxOrder.getRebate());
        cexOrder.setRebateCcy(okxOrder.getRebateCcy());

        // 订单更新时间（CEX侧）
        if (okxOrder.getUTime() != null) {
            cexOrder.setUTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(okxOrder.getUTime()), ZoneId.systemDefault()));
        }

        // 最后同步时间（本地时间）
        cexOrder.setLastSyncTime(LocalDateTime.now());
    }

    private void updateTradingOrderFromOkx(TradingOrder order, OkxOrder okxOrder) {
        order.setInstId(okxOrder.getInstId());
        order.setSide(okxOrder.getSide());
        order.setPosSide(okxOrder.getPosSide());
        order.setOrderType(okxOrder.getOrdType());
        order.setLever(okxOrder.getLever() != null ? okxOrder.getLever() : BigDecimal.ONE); // 默认1

        // 状态映射
        // OKX: live, partially_filled, filled, canceled
        String state = okxOrder.getState();
        if ("filled".equals(state)) {
            order.markAsSuccess();
        } else if ("canceled".equals(state)) {
            order.markAsCanceled();
        } else if ("live".equals(state) || "partially_filled".equals(state)) {
            // 如果原来是 pending，更新为 submitted
            if ("pending".equals(order.getOrderStatus())) {
                order.markAsSubmitted(okxOrder.getOrdId());
            }
        }

        // 如果是新建的外部订单，可能没有amt
        if (order.getAmt() == null) {
            order.setAmt(BigDecimal.ZERO); // 避免非空约束报错
        }

        // 确保 cexOrderId 关联
        if (order.getCexOrderId() == null) {
            order.setCexOrderId(okxOrder.getOrdId());
        }

        // 更新完成时间
        if (order.isCompleted() && order.getCompletedTime() == null) {
            if (okxOrder.getUTime() != null) {
                order.setCompletedTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(okxOrder.getUTime()), ZoneId.systemDefault()));
            } else {
                order.setCompletedTime(LocalDateTime.now());
            }
        }
    }

    private boolean isStatusConsistent(TradingOrder systemOrder, OkxOrder okxOrder) {
        String sysStatus = systemOrder.getOrderStatus();
        String okxState = okxOrder.getState();
        if ("filled".equals(okxState)) {
            return "success".equals(sysStatus);
        }
        if ("canceled".equals(okxState)) {
            return "canceled".equals(sysStatus);
        }
        if ("live".equals(okxState) || "partially_filled".equals(okxState)) {
            return "submitted".equals(sysStatus) || "pending".equals(sysStatus); // pending allowed here
        }
        return true;
    }

    /**
     * 从OkxOrder创建新的TradingOrder记录
     * 用于同步外部订单到t_trading_orders表
     *
     * @param okxOrder OKX订单对象
     * @param apiKeyId API密钥ID
     * @return 新创建的TradingOrder对象
     */
    private TradingOrder createNewTradingOrderFromOkx(OkxOrder okxOrder, Long apiKeyId) {
        TradingOrder newOrder = new TradingOrder();

        // 基础字段
        newOrder.setApiKeyId(apiKeyId);
        newOrder.setSource("sync");  // 标识为同步来源
        newOrder.setOrderType("market");  // 默认市价单

        // 特殊标记:recordId和actionId设为-1(外部订单)
        newOrder.setRecordId(-1L);
        newOrder.setActionId(-1L);

        // 委托金额:从CexTradingOrder计算或默认0
        BigDecimal amt = calculateAmt(okxOrder);
        newOrder.setAmt(amt);

        // 填充其他字段(复用updateTradingOrderFromOkx方法)
        updateTradingOrderFromOkx(newOrder, okxOrder);

        return newOrder;
    }

    /**
     * 计算委托金额
     * amt = sz * avgPx
     *
     * @param okxOrder OKX订单对象
     * @return 委托金额
     */
    private BigDecimal calculateAmt(OkxOrder okxOrder) {
        if (okxOrder.getSz() != null && okxOrder.getAvgPx() != null) {
            return okxOrder.getSz().multiply(okxOrder.getAvgPx());
        }
        return BigDecimal.ZERO;
    }

    /**
     * 检查订单是否已存在(增强去重)
     * 通过apiKeyId、cexOrderId和instId三重检查
     *
     * @param okxOrder OKX订单对象
     * @param apiKeyId API密钥ID
     * @return 是否已存在
     */
    private boolean isOrderAlreadyExists(OkxOrder okxOrder, Long apiKeyId) {
        return tradingOrderRepository.existsByApiKeyIdAndCexOrderIdAndInstId(
                apiKeyId,
                okxOrder.getOrdId(),
                okxOrder.getInstId()
        );
    }
}

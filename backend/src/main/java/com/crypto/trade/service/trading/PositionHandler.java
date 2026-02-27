package com.crypto.trade.service.trading;

import com.crypto.trade.dto.cex.model.CexContractInfo;
import com.crypto.trade.dto.cex.model.CexOrder;
import com.crypto.trade.dto.common.InstrumentPriceInfo;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.enums.CexApiType;
import com.crypto.trade.event.CexApiCallEvent;
import com.crypto.trade.event.PositionChangedEvent;
import com.crypto.trade.model.OrderModel;
import com.crypto.trade.service.UnifiedInstrumentService;
import com.crypto.trade.service.UnifiedTradingService;
import com.crypto.trade.service.cex.ApiKeyService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * PositionHandler
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class PositionHandler {

    /**
     * 当前委托订单缓存
     * Key: apiKeyId
     * Value: List<OrderModel>
     * TTL: 5分钟
     * 最大容量: 100个API Key
     */
    private final Cache<Long, List<OrderModel>> pendingOrdersCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build();

    @Autowired
    UnifiedTradingService unifiedTradingService;
    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    UnifiedInstrumentService unifiedInstrumentService;

    /**
     * 获取当前委托订单列表（支持缓存控制）
     *
     * @param apiKeyId API Key ID
     * @param useCache 是否使用缓存
     * @return 当前委托订单列表
     */
    public List<OrderModel> getPendingOrders(Long apiKeyId, boolean useCache) {
        try {
            // 如果启用缓存，先尝试从缓存获取
            if (useCache) {
                List<OrderModel> cachedOrders = pendingOrdersCache.getIfPresent(apiKeyId);
                if (cachedOrders != null) {
                    log.debug("从缓存获取当前委托订单 - apiKeyId: {}, 数量: {}", apiKeyId, cachedOrders.size());
                    return cachedOrders;
                }
            }

            // 获取API Key信息
            ApiKey apiKey = apiKeyService.getDecryptedKey(apiKeyId);
            if (apiKey == null) {
                throw new RuntimeException("API Key不存在");
            }

            // 调用统一CEX API查询当前委托订单
            List<CexOrder> pendingOrders = unifiedTradingService.queryPendingOrders(apiKey, "SWAP");

            // 手动映射CexOrder到OrderModel,避免JSON序列化抽象类导致null
            List<OrderModel> result = pendingOrders.stream()
                    .map(cexOrder -> {
                        try {
                            OrderModel model = new OrderModel();
                            // 基础字段映射
                            model.setOrdId(cexOrder.getOrderId());
                            model.setClOrdId(cexOrder.getClientOrderId());
                            model.setInstId(cexOrder.getSymbol());

                            // 订单类型和状态
                            if (cexOrder.getOrderType() != null) {
                                model.setOrdType(cexOrder.getOrderType().name().toLowerCase());
                            }
                            if (cexOrder.getStatus() != null) {
                                model.setState(cexOrder.getStatus().name().toLowerCase());
                            }

                            // 买卖方向
                            if (cexOrder.getSide() != null) {
                                model.setSide(cexOrder.getSide().name().toLowerCase());
                            }

                            // 数量和价格 - BigDecimal处理
                            if (cexOrder.getQuantity() != null) {
                                model.setSz(cexOrder.getQuantity());
                            }
                            if (cexOrder.getPrice() != null) {
                                model.setPx(cexOrder.getPrice());
                            }

                            // 成交相关
                            if (cexOrder.getFilledQuantity() != null) {
                                model.setAccFillSz(cexOrder.getFilledQuantity());
                            }
                            if (cexOrder.getAvgPrice() != null) {
                                model.setAvgPx(cexOrder.getAvgPrice());
                            }

                            // 手续费
                            if (cexOrder.getFee() != null) {
                                model.setFee(cexOrder.getFee());
                            }
                            if (cexOrder.getFeeCurrency() != null) {
                                model.setFeeCcy(cexOrder.getFeeCurrency());
                            }

                            // 时间字段转换
                            if (cexOrder.getCreateTime() != null) {
                                model.setCTime(cexOrder.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                            }
                            if (cexOrder.getUpdateTime() != null) {
                                model.setUTime(cexOrder.getUpdateTime());
                            }

                            // 杠杆倍数
                            if (cexOrder.getLever() != null) {
                                model.setLever(cexOrder.getLever());
                            }

                            // 持仓方向
                            if (cexOrder.getPosSide() != null) {
                                model.setPosSide(cexOrder.getPosSide());
                            }

                            // CexOrder没有提供以下字段，设置为null
                            // instType, algoId, algoClOrdId等需要从具体实现获取

                            // 调试日志：记录订单状态映射结果
                            if (log.isDebugEnabled()) {
                                log.debug("订单状态映射 - orderId: {}, state: {}, originalStatus: {}, lever: {}, posSide: {}",
                                        model.getOrdId(), model.getState(), cexOrder.getStatus(),
                                        model.getLever(), model.getPosSide());
                            }

                            return model;
                        } catch (Exception e) {
                            log.error("映射订单数据失败 - orderId: {}", cexOrder.getOrderId(), e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 不管本次是否启用缓存，将结果放入缓存
            pendingOrdersCache.put(apiKeyId, result);
            log.debug("缓存当前委托订单 - apiKeyId: {}, 数量: {}", apiKeyId, result.size());

            return result;
        } catch (Exception e) {
            log.error("获取当前委托订单失败 - API Key: {}", apiKeyId, e);
            throw new RuntimeException("获取当前委托订单失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取指定合约的实时价格数据
     * 优化：使用OKX API + 10秒缓存，DuckDB作为回退
     *
     * @param apiKey apiKey
     * @param instId 合约ID
     * @return 实时价格数据
     */
    public InstrumentPriceInfo getRealtimePrice(ApiKey apiKey, String instId) {
        try {
            log.debug("获取合约 {} 的实时价格（使用缓存优化）", instId);

            // 使用UnifiedTradingService的缓存方法获取价格数据
            InstrumentPriceInfo priceInfo = unifiedTradingService.getCachedMarkPrice(apiKey, instId);

            if (priceInfo != null) {
                // 使用便利方法确保兼容性
                priceInfo.ensurePriceCompatibility();

                log.debug("获取合约实时价格成功 - instId: {}, markPrice: {}, lastPrice: {}",
                        instId, priceInfo.getMarkPrice(), priceInfo.getLastPrice());
                return priceInfo;
            }

            log.warn("从缓存获取合约 {} 价格失败，返回null", instId);
            return null;

        } catch (Exception e) {
            log.error("获取合约实时价格失败, instId: {}", instId, e);
            return null;
        }
    }

    /**
     * 计算下单数量（考虑合约面值和最小单位限制）
     * 永续合约张数 = (成本金额 * 杠杆) / (当前价格 * 合约面值)，然后调整为最小单位的倍数
     */
    public BigDecimal calculateOrderSize(String instId, BigDecimal amount, BigDecimal lever, BigDecimal currentPrice,
                                         boolean isClose, BigDecimal quantity) {
        try {
            // 1. 获取合约信息
            CexContractInfo contractInfo = unifiedInstrumentService.getCexContractInfo(instId)
                    .orElseThrow(() -> new RuntimeException("未找到合约信息: " + instId));

            // 平仓逻辑: quantity是币数量,需要除以ctVal得到张数
            if (isClose) {
                BigDecimal sz = quantity.divide(contractInfo.getCtVal(), 8, RoundingMode.HALF_UP);

                // 平仓时也需要进行最小单位调整
                BigDecimal lotSz = contractInfo.getLotSz();
                BigDecimal minSz = contractInfo.getMinSz();

                // 确保数量满足最小要求
                if (sz.compareTo(minSz) < 0) {
                    log.warn("平仓张数 {} 小于最小张数 {},调整为最小张数", sz, minSz);
                    return minSz;
                }

                // 调整为最小单位的倍数
                BigDecimal adjustedSize = sz.divide(lotSz, 0, RoundingMode.DOWN).multiply(lotSz);

                // 确保调整后的数量不小于最小数量
                if (adjustedSize.compareTo(minSz) < 0) {
                    adjustedSize = minSz;
                }

                log.debug("平仓张数调整: 计算张数={}, 最小单位={}, 最小张数={}, 调整后张数={}",
                        sz, lotSz, minSz, adjustedSize);

                return adjustedSize.stripTrailingZeros();
            }

            // 2. 计算合约张数（考虑合约面值）
            BigDecimal nominalValue = amount.multiply(lever); // 名义价值 = 成本金额 * 杠杆
            assert null != currentPrice;
            BigDecimal contractValue = currentPrice.multiply(contractInfo.getCtVal()); // 每张合约的面值价值 = 价格 * 合约面值
            BigDecimal contractSize = nominalValue.divide(contractValue, 8, RoundingMode.HALF_UP);

            log.debug("永续合约计算: 名义价值={}, 合约面值={}, 每张合约价值={}, 计算张数={}", nominalValue, contractInfo.getCtVal(),
                    contractValue, contractSize);

            // 3. 调整为最小单位的倍数
            BigDecimal lotSz = contractInfo.getLotSz();
            BigDecimal minSz = contractInfo.getMinSz();

            // 确保数量满足最小要求
            if (contractSize.compareTo(minSz) < 0) {
                log.warn("下单张数 {} 小于最小张数 {}，调整为最小张数", contractSize, minSz);
                return minSz;
            }

            // 调整为最小单位的倍数
            BigDecimal adjustedSize = contractSize.divide(lotSz, 0, RoundingMode.DOWN).multiply(lotSz);

            // 确保调整后的数量不小于最小数量
            if (adjustedSize.compareTo(minSz) < 0) {
                adjustedSize = minSz;
            }

            log.debug("订单张数调整: 计算张数={}, 最小单位={}, 最小张数={}, 调整后张数={}",
                    contractSize, lotSz, minSz, adjustedSize);

            return adjustedSize;

        } catch (Exception e) {
            log.error("获取合约信息失败，使用原始计算结果: {}", e.getMessage());
            // 如果获取合约信息失败，使用原始计算方法（不推荐）
            return amount.multiply(lever).divide(currentPrice, 8, RoundingMode.HALF_UP);
        }
    }

    /**
     * 监听CEX API调用事件,自动失效相关缓存
     * 当下单、撤单操作时,清除对应apiKeyId的委托订单缓存
     *
     * @param event CEX API调用事件
     */
    @EventListener
    public void onCexApiCallEvent(CexApiCallEvent event) {
        // 检查apiKeyId
        if (event.getApiKeyId() == null) {
            return;
        }

        // 监听PLACE_ORDER、CANCEL_ORDER、CANCEL、CLOSE_POSITION事件
        CexApiType apiType = event.getApiType();
        if (apiType == CexApiType.PLACE_ORDER ||
                apiType == CexApiType.CANCEL_ORDER ||
                apiType == CexApiType.CANCEL ||
                apiType == CexApiType.CLOSE_POSITION) {
            evictPendingOrdersCache(event.getApiKeyId());
            log.debug("委托订单缓存已失效 - apiKeyId: {}, apiType: {}",
                    event.getApiKeyId(), apiType);
        }
    }

    /**
     * 监听持仓变更事件
     * 当持仓发生变化时，清理委托订单缓存
     *
     * @param event 持仓变更事件
     */
    @EventListener
    public void onPositionChanged(PositionChangedEvent event) {
        if (event == null || event.getApiKeyId() == null) {
            return;
        }

        // 只有持仓发生变化时才清理缓存
        if (event.isChanged()) {
            evictPendingOrdersCache(event.getApiKeyId());
            log.debug("持仓已变更，委托订单缓存已失效 - apiKeyId: {}", event.getApiKeyId());
        }
    }

    /**
     * 失效指定API Key的委托订单缓存
     *
     * @param apiKeyId API Key ID
     */
    public void evictPendingOrdersCache(Long apiKeyId) {
        if (apiKeyId != null) {
            pendingOrdersCache.invalidate(apiKeyId);
            log.debug("清除委托订单缓存 - apiKeyId: {}", apiKeyId);
        }
    }

}
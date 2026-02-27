package com.crypto.trade.service;

import com.crypto.trade.dto.cex.model.CexMarketCandle;
import com.crypto.trade.dto.common.InstrumentPriceInfo;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.repository.FuturesTickerDataRepository;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * RealTimePriceService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service("realTimePriceService")
public class RealTimePriceService {

    private final UnifiedCexApiService unifiedCexApiService;

    public RealTimePriceService(UnifiedCexApiService unifiedCexApiService,
                                FuturesTickerDataRepository futuresTickerDataRepository,
                                ApiKeyService apiKeyService) {
        this.unifiedCexApiService = unifiedCexApiService;
    }

    /**
     * 获取合约的真实价格信息
     * 直接通过K线数据计算所有价格指标，确保数据一致性和准确性
     */
    public InstrumentPriceInfo getRealPriceData(ApiKey apiKey, String instId) {
        if (instId == null || instId.trim().isEmpty()) {
            log.warn("合约ID为空，返回null");
            return null;
        }

        try {
            log.debug("开始获取合约 {} 的实时价格数据", instId);
            // 直接基于K线数据计算价格信息
            return calculatePriceFromKlines(instId, apiKey);

        } catch (Exception e) {
            log.error("获取合约 {} 的价格数据失败", instId, e);
            return null;
        }
    }

    /**
     * 基于K线数据计算完整的价格信息
     * 获取25根1H K线数据，计算所有价格指标
     */
    private InstrumentPriceInfo calculatePriceFromKlines(String instId, ApiKey apiKey) {
        try {
            log.debug("基于K线数据计算价格信息: {}", instId);

            // 使用通用CEX方法获取K线数据
            List<CexMarketCandle> candles1H = unifiedCexApiService.getMarketCandles(apiKey, instId, "1H", 25);
            if (CollectionUtils.isEmpty(candles1H)) {
                log.warn("未获取到K线数据: {}", instId);
                return null;
            }

            // 按时间戳排序，确保数据顺序正确
            candles1H.sort(Comparator.comparingLong(CexMarketCandle::getTimestamp));

            // 获取当前价格（最新K线的收盘价）
            BigDecimal currentPrice = candles1H.get(candles1H.size() - 1).getClose();
            if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("当前价格无效: {}", currentPrice);
                return null;
            }

            // 创建价格信息对象
            InstrumentPriceInfo priceInfo = new InstrumentPriceInfo();
            priceInfo.setInstId(instId);
            priceInfo.setLastPrice(currentPrice);
            priceInfo.setMarkPrice(currentPrice); // 使用收盘价作为标记价格
            priceInfo.setUpdateTime(System.currentTimeMillis());

            // 计算24小时数据
            calculate24hData(priceInfo, candles1H, currentPrice);

            // 计算1小时和4小时涨跌幅
            calculate1hChangePercent(priceInfo, candles1H, currentPrice);
            calculate4hChangePercent(priceInfo, candles1H, currentPrice);

            // 计算24小时交易量（从K线数据汇总）
            calculate24hVolume(priceInfo, candles1H);

            log.debug("价格数据计算完成: {}, 当前价: {}, 24h涨跌幅: {}%",
                    instId, currentPrice, priceInfo.getChangePercent());

            return priceInfo;

        } catch (Exception e) {
            log.error("基于K线数据计算价格信息失败: {}", instId, e);
            return null;
        }
    }

    /**
     * 计算24小时相关数据
     */
    private void calculate24hData(InstrumentPriceInfo priceInfo, List<CexMarketCandle> candles1H, BigDecimal currentPrice) {
        try {
            // 24小时开盘价（最早K线的开盘价）
            BigDecimal open24h = candles1H.get(0).getOpen();
            if (open24h != null && open24h.compareTo(BigDecimal.ZERO) > 0) {
                priceInfo.setOpen24h(open24h);

                // 计算24小时涨跌幅
                BigDecimal change24h = calculateChangePercent(currentPrice, open24h);
                priceInfo.setChangePercent(change24h);
            }

            // 24小时最高价
            BigDecimal high24h = candles1H.stream()
                    .map(CexMarketCandle::getHigh)
                    .filter(high -> high != null && high.compareTo(BigDecimal.ZERO) > 0)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            if (high24h.compareTo(BigDecimal.ZERO) > 0) {
                priceInfo.setHigh24h(high24h);
            }

            // 24小时最低价
            BigDecimal low24h = candles1H.stream()
                    .map(CexMarketCandle::getLow)
                    .filter(low -> low != null && low.compareTo(BigDecimal.ZERO) > 0)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            if (low24h.compareTo(BigDecimal.ZERO) > 0) {
                priceInfo.setLow24h(low24h);
            }

        } catch (Exception e) {
            log.warn("计算24小时数据失败: {}", priceInfo.getInstId(), e);
        }
    }

    /**
     * 计算1小时涨跌幅
     */
    private void calculate1hChangePercent(InstrumentPriceInfo priceInfo, List<CexMarketCandle> candles1H, BigDecimal currentPrice) {
        try {
            // 1小时前的开盘价（倒数第二根K线的开盘价，或者最新K线的开盘价）
            BigDecimal open1h = candles1H.size() >= 2 ?
                    candles1H.get(candles1H.size() - 2).getOpen() :
                    candles1H.get(candles1H.size() - 1).getOpen();

            if (open1h != null && open1h.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal change1h = calculateChangePercent(currentPrice, open1h);
                priceInfo.setChangePercent1H(change1h);
            }

        } catch (Exception e) {
            log.warn("计算1小时涨跌幅失败: {}", priceInfo.getInstId(), e);
        }
    }

    /**
     * 计算4小时涨跌幅
     */
    private void calculate4hChangePercent(InstrumentPriceInfo priceInfo, List<CexMarketCandle> candles1H, BigDecimal currentPrice) {
        try {
            // 4小时前的开盘价（取4小时前对应K线的开盘价）
            BigDecimal open4h = null;
            if (candles1H.size() >= 5) {
                // 4小时前是第5根K线（当前是第25根，4小时前是第21根）
                open4h = candles1H.get(candles1H.size() - 5).getOpen();
            } else if (candles1H.size() >= 4) {
                // 如果数据不足5根，使用第4根
                open4h = candles1H.get(0).getOpen();
            }

            if (open4h != null && open4h.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal change4h = calculateChangePercent(currentPrice, open4h);
                priceInfo.setChangePercent4H(change4h);
            }

        } catch (Exception e) {
            log.warn("计算4小时涨跌幅失败: {}", priceInfo.getInstId(), e);
        }
    }

    /**
     * 计算24小时交易量
     */
    private void calculate24hVolume(InstrumentPriceInfo priceInfo, List<CexMarketCandle> candles1H) {
        try {
            // 汇总24小时交易量
            BigDecimal volume24h = candles1H.stream()
                    .map(CexMarketCandle::getVolume)
                    .filter(volume -> volume != null && volume.compareTo(BigDecimal.ZERO) > 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (volume24h.compareTo(BigDecimal.ZERO) > 0) {
                priceInfo.setVolume24h(volume24h);
            }

            // 汇总24小时交易额
            BigDecimal volumeCcy24h = candles1H.stream()
                    .map(CexMarketCandle::getVolumeCcy)
                    .filter(volumeCcy -> volumeCcy != null && volumeCcy.compareTo(BigDecimal.ZERO) > 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (volumeCcy24h.compareTo(BigDecimal.ZERO) > 0) {
                priceInfo.setVolumeCcy24h(volumeCcy24h);
            }

        } catch (Exception e) {
            log.warn("计算24小时交易量失败: {}", priceInfo.getInstId(), e);
        }
    }

    /**
     * 计算涨跌幅百分比
     */
    private BigDecimal calculateChangePercent(BigDecimal currentPrice, BigDecimal basePrice) {
        if (currentPrice == null || basePrice == null ||
                currentPrice.compareTo(BigDecimal.ZERO) <= 0 || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return currentPrice.subtract(basePrice)
                .divide(basePrice, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }


    /**
     * 检查价格数据的有效性
     */
    public boolean isValidPriceData(InstrumentPriceInfo priceInfo) {
        if (priceInfo == null) {
            return false;
        }

        // 检查价格是否有效
        BigDecimal primaryPrice = priceInfo.getPrimaryPrice();
        if (primaryPrice == null || primaryPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        // 检查时间戳（确保数据不是太久之前的）
        if (priceInfo.getUpdateTime() != null) {
            long ageMillis = System.currentTimeMillis() - priceInfo.getUpdateTime();
            // 如果数据超过10分钟，认为无效
            return ageMillis <= 10 * 60 * 1000;
        }

        return true;
    }

}
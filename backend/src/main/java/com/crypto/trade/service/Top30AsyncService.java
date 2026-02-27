package com.crypto.trade.service;

import com.crypto.trade.dto.Top30MarketTickerDto;
import com.crypto.trade.entity.FuturesTickerData;
import com.crypto.trade.repository.FuturesTickerDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Top30AsyncService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class Top30AsyncService {

    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH");
    @Autowired
    private FuturesTickerDataRepository futuresTickerDataRepository;
    @Autowired
    private UnifiedMarketTickerService unifiedMarketTickerService;

    /**
     * 异步预计算并缓存Top30数据
     *
     * @param vendor   供应商
     * @param instType 合约类型
     */
    @Async("top30AsyncExecutor")
    @CacheEvict(value = "marketTop30",
            key = "'top30:' + #vendor + ':' + #instType + ':' + T(java.time.LocalDateTime).now().format(T(java.time.format.DateTimeFormatter).ofPattern('yyyy-MM-dd HH'))",
            beforeInvocation = false)
    public void precomputeTop30Async(String vendor, String instType) {
        try {
            log.debug("异步预计算Top30数据 - vendor: {}, instType: {}", vendor, instType);

            String currentHourStr = LocalDateTime.now().format(HOUR_FORMATTER);

            // 查询并计算Top30数据
            List<FuturesTickerData> top30Data = futuresTickerDataRepository
                    .findTop30ByVendorAndInstTypeAndHourStr(vendor, instType, currentHourStr);

            if (!top30Data.isEmpty()) {
                log.debug("异步预计算Top30数据完成 - vendor: {}, instType: {}, count: {}",
                        vendor, instType, top30Data.size());

                // 触发缓存更新
                unifiedMarketTickerService.getTop30MarketTickers(vendor, instType, currentHourStr);
            } else {
                log.warn("未找到可用于预计算的Top30数据 - vendor: {}, instType: {}", vendor, instType);
            }

        } catch (Exception e) {
            log.error("异步预计算Top30数据失败 - vendor: {}, instType: {}", vendor, instType, e);
        }
    }

    /**
     * 定时任务：每分钟更新一次Top30数据缓存
     */
    @Scheduled(cron = "0 */1 * * * ?")
    public void scheduledTop30Update() {
        try {
            log.debug("开始定时更新Top30数据缓存");

            String currentHourStr = LocalDateTime.now().format(HOUR_FORMATTER);

            // 更新OKX SWAP的Top30数据
            precomputeTop30Async("OKX", "SWAP");

            log.debug("定时更新Top30数据缓存任务完成");

        } catch (Exception e) {
            log.error("定时更新Top30数据缓存失败", e);
        }
    }

    /**
     * 获取预计算的Top30数据（带缓存）
     * 这个方法提供快速的访问入口
     */
//    @Cacheable(value = "marketTop30",
//            key = "'top30:' + #vendor + ':' + #instType + ':' + #hourStr",
//            unless = "#result == null || #result.top30Tickers.isEmpty()")
    public Top30MarketTickerDto getPrecomputedTop30(String vendor, String instType, String hourStr) {
        try {
            log.debug("获取预计算的Top30数据 - vendor: {}, instType: {}, hour: {}", vendor, instType, hourStr);

            List<FuturesTickerData> top30Data = futuresTickerDataRepository
                    .findTop30ByVendorAndInstTypeAndHourStr(vendor, instType, hourStr);

            if (top30Data.isEmpty()) {
                log.warn("未找到预计算的Top30数据 - vendor: {}, instType: {}, hour: {}", vendor, instType, hourStr);

                // 如果缓存中没有数据，触发异步预计算
                precomputeTop30Async(vendor, instType);
                return null;
            }

            // 转换为Top30MarketTickerDto
            return convertToTop30MarketTickerDto(vendor, instType, hourStr, top30Data);

        } catch (Exception e) {
            log.error("获取预计算的Top30数据失败 - vendor: {}, instType: {}, hour: {}", vendor, instType, hourStr, e);
            return null;
        }
    }

    /**
     * 批量预计算多个时间段的Top30数据
     */
    @Async("top30AsyncExecutor")
    public void batchPrecomputeTop30(String vendor, String instType, int hours) {
        try {
            log.debug("批量预计算Top30数据 - vendor: {}, instType: {}, hours: {}", vendor, instType, hours);

            for (int i = 0; i < hours; i++) {
                String hourStr = LocalDateTime.now().minusHours(i).format(HOUR_FORMATTER);

                List<FuturesTickerData> data = futuresTickerDataRepository
                        .findTop30ByVendorAndInstTypeAndHourStr(vendor, instType, hourStr);

                if (!data.isEmpty()) {
                    log.debug("预计算历史数据完成 - vendor: {}, instType: {}, hour: {}, count: {}",
                            vendor, instType, hourStr, data.size());
                }
            }

            log.debug("批量预计算Top30数据完成 - vendor: {}, instType: {}, hours: {}", vendor, instType, hours);

        } catch (Exception e) {
            log.error("批量预计算Top30数据失败 - vendor: {}, instType: {}, hours: {}", vendor, instType, hours, e);
        }
    }

    /**
     * 转换FuturesTickerData列表为Top30MarketTickerDto
     */
    private Top30MarketTickerDto convertToTop30MarketTickerDto(String vendor, String instType,
                                                               String hourStr, List<FuturesTickerData> data) {
        try {
            // 使用UnifiedMarketTickerService的转换逻辑
            return unifiedMarketTickerService.getTop30MarketTickers(vendor, instType, hourStr);
        } catch (Exception e) {
            log.error("转换Top30数据失败", e);
            return null;
        }
    }

    /**
     * 清除所有Top30相关缓存
     */
    @CacheEvict(value = "marketTop30", allEntries = true)
    public void clearAllTop30Cache() {
        log.debug("清除所有Top30缓存数据");
    }
}
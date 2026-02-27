package com.crypto.trade.service;

import com.crypto.trade.dto.cex.model.CexAccountPortfolio;
import com.crypto.trade.entity.AccountEquityData;
import com.crypto.trade.service.unified.UnifiedBalanceService;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * AccountEquityMonitorService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class AccountEquityMonitorService {

    @Autowired
    UnifiedBalanceService unifiedBalanceService;
    @Autowired
    BalanceDataConverter balanceDataConverter;
    @Autowired
    @Qualifier("accountEquityCache")
    Cache<Long, AccountEquityData> accountEquityCache;

    /**
     * 监听余额数据更新事件
     * 当统一余额服务获取到新的余额数据时，自动更新账户权益缓存
     *
     * @param event 余额更新事件
     */
    @EventListener
    public void handleBalanceUpdateEvent(BalanceUpdateEvent event) {
        try {
            Long apiKeyId = event.getApiKeyId();
            CexAccountPortfolio accountData = event.getAccountPortfolio();
            log.debug("处理API密钥 {} 的余额更新事件", apiKeyId);
            // 转换为账户权益数据
            AccountEquityData equityData = balanceDataConverter.toAccountEquityData(apiKeyId, accountData);
            if (null != equityData) {
                // 更新缓存
                accountEquityCache.put(apiKeyId, equityData);
                log.debug("成功更新API密钥 {} 的USDT权益 - 总权益: {}, 可用权益: {}, 冻结权益: {}",
                        apiKeyId, equityData.getTotalEquityUsdt(), equityData.getAvailableEquityUsdt(), equityData.getFrozenEquityUsdt());
            } else {
                log.warn("API密钥 {} 的权益数据转换失败", apiKeyId);
            }

        } catch (Exception e) {
            log.error("处理余额更新事件失败", e);
        }
    }

    /**
     * 获取指定API密钥的账户权益缓存数据
     *
     * @param apiKeyId API密钥ID
     * @return 账户权益数据，如果缓存中不存在则返回null
     */
    public AccountEquityData getCachedEquity(Long apiKeyId) {
        // 先从本地缓存获取
        AccountEquityData cachedData = accountEquityCache.getIfPresent(apiKeyId);
        if (null != cachedData) {
            return cachedData;
        }
        // 从统一余额服务获取最新数据
        CexAccountPortfolio accountPortfolio = unifiedBalanceService.getLatestBalanceData(apiKeyId);
        if (null != accountPortfolio) {
            return balanceDataConverter.toAccountEquityData(apiKeyId, accountPortfolio);
        }
        return null;
    }

}
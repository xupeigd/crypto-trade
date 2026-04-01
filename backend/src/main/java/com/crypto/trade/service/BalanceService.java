package com.crypto.trade.service;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.CexBalance;
import com.crypto.trade.model.BalanceSummaryModel;
import com.crypto.trade.model.CexBalanceModel;
import com.crypto.trade.repository.ApiKeyRepository;
import com.crypto.trade.repository.CexBalanceRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BalanceService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Service
public class BalanceService {

    @Autowired
    CexBalanceRepository cexBalanceRepository;
    @Autowired
    ApiKeyRepository apiKeyRepository;

    /**
     * 获取指定CEX的最新余额数据
     */
    public List<CexBalance> getLatestBalancesByCex(String cexName) {
        // 获取所有活跃的API Key ID列表
        List<Long> activeApiKeyIds = apiKeyRepository.findByStatus("active")
                .stream()
                .map(ApiKey::getKeyId)
                .collect(Collectors.toList());
        if ("ALL".equalsIgnoreCase(cexName)) {
            if (activeApiKeyIds.isEmpty()) {
                return new ArrayList<>();
            }
            return cexBalanceRepository.findLatestBalancesByCexWithActiveKeys(activeApiKeyIds);
        } else {
            if (activeApiKeyIds.isEmpty()) {
                return new ArrayList<>();
            }
            return cexBalanceRepository.findLatestByCexNameWithActiveKeys(cexName, activeApiKeyIds);
        }
    }

    /**
     * 获取余额汇总信息
     */
    public BalanceSummary getBalanceSummary(String cexName) {
        List<CexBalance> balances = getLatestBalancesByCex(cexName);
        if (balances.isEmpty()) {
            BalanceSummary emptySummary = new BalanceSummary();
            emptySummary.setCexName(cexName);
            return emptySummary;
        }
        // 计算汇总数据
        BigDecimal totalUsdValue = BigDecimal.ZERO;
        BigDecimal totalAvailableBalance = BigDecimal.ZERO;
        BigDecimal totalLockedBalance = BigDecimal.ZERO;
        Map<String, BigDecimal> currencyDistribution = new HashMap<>();
        for (CexBalance balance : balances) {
            totalUsdValue = totalUsdValue.add(balance.getUsdValue() != null ? balance.getUsdValue() : BigDecimal.ZERO);
            totalAvailableBalance = totalAvailableBalance.add(balance.getAvailableBalance() != null ? balance.getAvailableBalance() : BigDecimal.ZERO);
            totalLockedBalance = totalLockedBalance.add(balance.getLockedBalance() != null ? balance.getLockedBalance() : BigDecimal.ZERO);
            if (balance.getUsdValue() != null && balance.getUsdValue().compareTo(BigDecimal.ZERO) > 0) {
                currencyDistribution.merge(balance.getCurrency(), balance.getUsdValue(), BigDecimal::add);
            }
        }
        BalanceSummary summary = new BalanceSummary();
        summary.setCexName(cexName);
        summary.setTotalUsdValue(totalUsdValue);
        summary.setTotalAvailableBalance(totalAvailableBalance);
        summary.setTotalLockedBalance(totalLockedBalance);
        summary.setCurrencyCount(currencyDistribution.size());
        summary.setCurrencyDistribution(currencyDistribution);
        summary.setLatestUpdateTime(balances.stream()
                .map(CexBalance::getDataIngestionTime)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now()));
        return summary;
    }

    /**
     * 获取所有活跃的CEX名称
     */
    public List<String> getActiveCexNames() {
        List<String> cexNames = cexBalanceRepository.findDistinctCexNames();
        if (cexNames == null) {
            cexNames = new ArrayList<>();
        }
        // 在列表开头添加"全部"选项
        cexNames.add(0, "ALL");
        return cexNames;
    }

    /**
     * 获取指定CEX的最新余额数据（Model格式）
     * 用于Controller层返回，避免直接暴露Entity
     */
    public List<CexBalanceModel> getLatestBalancesByCexModel(String cexName) {
        List<CexBalance> entities = getLatestBalancesByCex(cexName);
        return entities.stream()
                .map(CexBalanceModel::fromEntity)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取余额汇总信息（Model格式）
     * 用于Controller层返回，避免直接暴露内嵌类
     */
    public BalanceSummaryModel getBalanceSummaryModel(String cexName) {
        BalanceSummary summary = getBalanceSummary(cexName);
        return BalanceSummaryModel.fromServiceClass(summary);
    }

    /**
     * 余额汇总信息类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceSummary {
        String cexName;
        BigDecimal totalUsdValue = BigDecimal.ZERO;
        BigDecimal totalAvailableBalance = BigDecimal.ZERO;
        BigDecimal totalLockedBalance = BigDecimal.ZERO;
        int currencyCount = 0;
        Map<String, BigDecimal> currencyDistribution = new HashMap<>();
        LocalDateTime latestUpdateTime;
    }
}
package com.crypto.trade.service;

import com.crypto.trade.dto.cex.model.CexAccountBalance;
import com.crypto.trade.dto.cex.model.CexAccountPortfolio;
import com.crypto.trade.entity.AccountEquityData;
import com.crypto.trade.entity.AccountEquitySnapshot;
import com.crypto.trade.entity.CexBalance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * BalanceDataConverter
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class BalanceDataConverter {

    /**
     * 将CexAccountPortfolio转换为AccountEquityData
     *
     * @param apiKeyId    API密钥ID
     * @param accountData 账户余额数据
     * @return 账户权益数据
     */
    public AccountEquityData toAccountEquityData(Long apiKeyId, CexAccountPortfolio accountData) {
        if (null == accountData) {
            return null;
        }

        AccountEquityData equityData = new AccountEquityData();
        equityData.setApiKeyId(apiKeyId);

        // 计算USDT权益
        BigDecimal[] usdtEquity = extractUSDTEquity(accountData);
        equityData.setTotalEquityUsdt(usdtEquity[0]);
        equityData.setAvailableEquityUsdt(usdtEquity[1]);
        equityData.setFrozenEquityUsdt(usdtEquity[2]);

        // 计算保证金权益
        equityData.setMarginEquityUsdt(calculateMarginEquity(accountData));

        // 设置更新时间
        equityData.setUpdateTime(convertTimestamp(accountData.getUpdateTime()));

        return equityData;
    }

    /**
     * 将CexAccountPortfolio转换为TAccountEquitySnapshot
     *
     * @param apiKeyId    API密钥ID
     * @param accountData 账户余额数据
     * @return 账户权益快照实体
     */
    public AccountEquitySnapshot toAccountEquitySnapshot(Long apiKeyId, CexAccountPortfolio accountData) {
        if (null == accountData) {
            return null;
        }

        AccountEquitySnapshot snapshot = new AccountEquitySnapshot();
        snapshot.setApiKeyId(apiKeyId);

        // 计算USDT权益
        BigDecimal[] usdtEquity = extractUSDTEquity(accountData);
        snapshot.setTotalEquityUsdt(usdtEquity[0]);
        snapshot.setAvailableEquityUsdt(usdtEquity[1]);
        snapshot.setFrozenEquityUsdt(usdtEquity[2]);

        // 计算保证金权益
        snapshot.setMarginEquityUsdt(calculateMarginEquity(accountData));

        // 设置更新时间
        snapshot.setUpdateTime(convertTimestamp(accountData.getUpdateTime()));

        return snapshot;
    }

    /**
     * 将CexAccountPortfolio转换为TCexBalance列表
     *
     * @param apiKeyId    API密钥ID
     * @param accountData 账户余额数据
     * @return CEX余额实体列表
     */
    public List<CexBalance> toCexBalances(Long apiKeyId, CexAccountPortfolio accountData) {
        if (null == accountData || accountData.getBalances() == null) {
            return new ArrayList<>();
        }

        List<CexBalance> balances = new ArrayList<>();
        LocalDateTime updateTime = convertTimestamp(accountData.getUpdateTime());

        for (CexAccountBalance detail : accountData.getBalances()) {
            try {
                CexBalance balance = new CexBalance();
                balance.setCexName("OKX");
                balance.setCurrency(detail.getCurrency());

                // 设置余额数据
                BigDecimal cashBal = detail.getAvailableBalance();
                BigDecimal frozenBal = detail.getFrozenBalance();  // 使用getFrozenBalance()替代getLockedBalance()
                BigDecimal totalBal = cashBal.add(frozenBal);

                balance.setTotalBalance(totalBal);
                balance.setAvailableBalance(cashBal);
                balance.setLockedBalance(frozenBal);

                // 设置USD估值
                BigDecimal eqUsd = detail.getEquityInUsd();  // 使用getEquityInUsd()替代getUsdValue()
                balance.setUsdValue(eqUsd);

                // 设置其他字段
                balance.setApiKeyId(apiKeyId);
                balance.setDataIngestionTime(updateTime);

                balances.add(balance);

            } catch (Exception e) {
                log.error("转换币种 {} 的余额数据失败", detail.getCurrency(), e);
            }
        }

        return balances;
    }

    /**
     * 从账户数据中提取USDT权益信息
     *
     * @param accountData 账户余额数据
     * @return USDT权益信息数组 [totalEquity, availableEquity, frozenEquity]
     */
    private BigDecimal[] extractUSDTEquity(CexAccountPortfolio accountData) {
        if (accountData.getBalances() == null) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        }

        for (CexAccountBalance detail : accountData.getBalances()) {
            if ("USDT".equals(detail.getCurrency())) {
                BigDecimal cashBal = detail.getAvailableBalance();
                BigDecimal frozenBal = detail.getFrozenBalance();  // 使用getFrozenBalance()替代getLockedBalance()

                BigDecimal availableEquity = cashBal;
                BigDecimal frozenEquity = frozenBal;
                BigDecimal totalEquity = availableEquity.add(frozenEquity);

                return new BigDecimal[]{totalEquity, availableEquity, frozenEquity};
            }
        }

        return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }

    /**
     * 计算保证金权益
     *
     * @param accountData 账户余额数据
     * @return 保证金权益
     */
    private BigDecimal calculateMarginEquity(CexAccountPortfolio accountData) {
        // 使用 totalEquity 作为保证金权益
        BigDecimal totalEquity = accountData.getTotalEquity();
        if (null != totalEquity) {
            try {
                return totalEquity;
            } catch (NumberFormatException e) {
                log.warn("解析totalEquity失败: {}", totalEquity, e);
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * 转换时间戳为LocalDateTime
     *
     * @param timestamp 秒级时间戳
     * @return LocalDateTime
     */
    private LocalDateTime convertTimestamp(Long timestamp) {
        if (null == timestamp) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        } catch (Exception e) {
            log.warn("转换时间戳失败: {}", timestamp, e);
            return LocalDateTime.now();
        }
    }

    /**
     * 转换LocalDateTime(兼容方法,直接返回)
     *
     * @param dateTime LocalDateTime对象
     * @return LocalDateTime
     */
    private LocalDateTime convertTimestamp(LocalDateTime dateTime) {
        return null != dateTime ? dateTime : LocalDateTime.now();
    }

    /**
     * 验证账户余额数据的完整性
     *
     * @param accountData 账户余额数据
     * @return 是否有效
     */
    public boolean isValidAccountData(CexAccountPortfolio accountData) {
        if (null == accountData) {
            return false;
        }

        // 检查必要字段
        if (accountData.getBalances() == null || accountData.getBalances().isEmpty()) {
            return false;
        }
        // 检查是否包含USDT
        return accountData.getBalances().stream()
                .anyMatch(detail -> "USDT".equals(detail.getCurrency()));
    }

    /**
     * 获取账户余额数据摘要信息
     *
     * @param accountData 账户余额数据
     * @return 摘要信息字符串
     */
    public String getAccountDataSummary(CexAccountPortfolio accountData) {
        if (null == accountData) {
            return "AccountData: null";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("AccountData: ");
        summary.append("totalEquity=").append(accountData.getTotalEquity());
        summary.append(", availableEquity=").append(accountData.getAvailableEquity());
        summary.append(", balancesCount=").append(accountData.getBalances() != null ? accountData.getBalances().size() : 0);

        if (accountData.getBalances() != null) {
            long usdtCount = accountData.getBalances().stream()
                    .filter(detail -> "USDT".equals(detail.getCurrency()))
                    .count();
            summary.append(", usdtCount=").append(usdtCount);
        }

        return summary.toString();
    }
}
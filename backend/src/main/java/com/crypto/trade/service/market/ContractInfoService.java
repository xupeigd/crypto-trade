package com.crypto.trade.service.market;

import com.crypto.trade.dto.cex.model.CexInstrument;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * ContractInfoService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class ContractInfoService {

    @Autowired
    UnifiedCexApiService unifiedCexApiService;

    /**
     * 获取合约详细信息
     *
     * @param apiKey   API密钥
     * @param instId   合约ID
     * @param instType 合约类型，如 "SWAP" 永续合约
     * @return 合约信息
     */
    public CexInstrument getContractInfo(ApiKey apiKey, String instId, String instType) {
        // 使用通用CEX方法获取合约信息
        List<CexInstrument> instruments = unifiedCexApiService.getInstruments(apiKey, instType, instId);
        return CollectionUtils.isEmpty(instruments) ? null : instruments.get(0);
    }

    /**
     * 获取指定类型的所有合约列表
     *
     * @param apiKey   API密钥
     * @param instType 合约类型
     * @return 合约列表
     */
    public List<CexInstrument> getContractList(ApiKey apiKey, String instType) {
        // 使用通用CEX方法获取合约信息
        return unifiedCexApiService.getInstruments(apiKey, instType, null);
    }

    /**
     * 检查合约是否可交易
     *
     * @param contractInfo 合约信息
     * @return 是否可交易
     */
    public boolean isTradable(CexInstrument contractInfo) {
        if (null == contractInfo) {
            return false;
        }
        String state = contractInfo.getState();
        BigDecimal minSz = contractInfo.getMinOrderSize();
        BigDecimal lever = contractInfo.getMaxLeverage();
        return "live".equals(state) &&
                null != minSz && minSz.compareTo(BigDecimal.ZERO) > 0 &&
                null != lever && lever.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 获取合约的最小交易数量
     *
     * @param contractInfo 合约信息
     * @return 最小交易数量
     */
    public BigDecimal getMinTradingSize(CexInstrument contractInfo) {
        if (null == contractInfo) {
            return BigDecimal.ZERO;
        }
        return contractInfo.getMinOrderSize();
    }

    /**
     * 获取合约的价格精度
     *
     * @param contractInfo 合约信息
     * @return 价格精度（小数位数）
     */
    public int getPricePrecision(CexInstrument contractInfo) {
        if (null == contractInfo) {
            return 0;
        }

        BigDecimal tickSize = contractInfo.getTickSize();
        if (null == tickSize || 0 == tickSize.compareTo(BigDecimal.ZERO)) {
            return 0;
        }
        // 计算小数位数
        String tickSzStr = tickSize.stripTrailingZeros().toPlainString();
        int decimalIndex = tickSzStr.indexOf('.');
        return decimalIndex >= 0 ? tickSzStr.length() - decimalIndex - 1 : 0;
    }

    /**
     * 获取合约的数量精度
     *
     * @param contractInfo 合约信息
     * @return 数量精度（小数位数）
     */
    public int getSizePrecision(CexInstrument contractInfo) {
        if (null == contractInfo) {
            return 0;
        }
        BigDecimal lotSize = contractInfo.getLotSize();
        if (null == lotSize || lotSize.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }

        // 计算小数位数
        String lotSzStr = lotSize.stripTrailingZeros().toPlainString();
        int decimalIndex = lotSzStr.indexOf('.');
        return decimalIndex >= 0 ? lotSzStr.length() - decimalIndex - 1 : 0;
    }

    /**
     * 根据基础货币和计价货币筛选合约
     *
     * @param contracts 合约列表
     * @param baseCcy   基础货币，如 "BTC"
     * @param quoteCcy  计价货币，如 "USDT"
     * @return 筛选后的合约列表
     */
    public List<CexInstrument> filterContractsByCurrency(List<CexInstrument> contracts, String baseCcy, String quoteCcy) {
        List<CexInstrument> filtered = new ArrayList<>();
        for (CexInstrument contract : contracts) {
            String contractBaseCcy = contract.getBaseCurrency();
            String contractQuoteCcy = contract.getQuoteCurrency();
            if (baseCcy.equals(contractBaseCcy) && quoteCcy.equals(contractQuoteCcy)) {
                filtered.add(contract);
            }
        }
        return filtered;
    }

    /**
     * 搜索合约
     *
     * @param contracts 合约列表
     * @param keyword   关键词
     * @return 匹配的合约列表
     */
    public List<CexInstrument> searchContracts(List<CexInstrument> contracts, String keyword) {
        if (null == keyword || keyword.trim().isEmpty()) {
            return contracts;
        }
        List<CexInstrument> matched = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase().trim();
        for (CexInstrument contract : contracts) {
            String instId = contract.getSymbol().toLowerCase();
            String baseCcy = contract.getBaseCurrency().toLowerCase();
            String quoteCcy = contract.getQuoteCurrency().toLowerCase();
            if (instId.contains(lowerKeyword) ||
                    baseCcy.contains(lowerKeyword) ||
                    quoteCcy.contains(lowerKeyword)) {
                matched.add(contract);
            }
        }
        return matched;
    }
}
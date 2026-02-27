package com.crypto.trade.service.market;

import com.crypto.trade.dto.cex.model.CexInstrument;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.model.PositionModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * MarketDataService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class MarketDataService {

    @Autowired
    @Qualifier("marketPriceService")
    UnifiedPriceDataService priceDataService;
    @Autowired
    ContractInfoService contractInfoService;
    @Autowired
    PositionQueryService positionQueryService;

    /**
     * 获取标记价格
     */
    public BigDecimal getMarkPrice(ApiKey apiKey, String instId) {
        return priceDataService.getMarkPrice(apiKey, instId);
    }

    /**
     * 获取合约详细信息
     */
    public CexInstrument getContractInfo(ApiKey apiKey, String instId, String instType) {
        return contractInfoService.getContractInfo(apiKey, instId, instType);
    }

    /**
     * 检查合约是否可交易
     */
    public boolean isTradable(CexInstrument contractInfo) {
        return contractInfoService.isTradable(contractInfo);
    }

    /**
     * 查询指定合约仓位
     */
    public PositionModel getPosition(ApiKey apiKey, String instId) {
        return positionQueryService.getPosition(apiKey, instId);
    }

}
package com.crypto.trade.service;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.CexInstrument;
import com.crypto.trade.repository.ApiKeyRepository;
import com.crypto.trade.repository.CexInstrumentRepository;
import com.crypto.trade.service.market.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * CapitalCalculatorService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CapitalCalculatorService {

    private final CexInstrumentRepository instrumentRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final MarketDataService marketDataService;

    /**
     * 计算预估总占用资金
     *
     * @param apiKeyId API密钥ID（用于获取交易模式和CEX名称）
     * @param symbol   交易对ID (BTC-USDT-SWAP)
     * @param quantity 数量
     * @param lever    杠杆倍数
     * @param price    价格
     * @return 预估总占用资金 (保证金 + 开仓手续费 + 平仓手续费)
     */
    public BigDecimal calculateEstimatedTotalCapital(Long apiKeyId, String symbol, BigDecimal quantity, BigDecimal lever,
                                                     BigDecimal price) {
        log.info("开始计算预估总占用资金 - apiKeyId: {}, symbol: {}, quantity: {}, lever: {}, price: {}",
                apiKeyId, symbol, quantity, lever, price);

        // 查询ApiKey获取交易模式和CEX名称
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new IllegalArgumentException("API密钥不存在: " + apiKeyId));

        Boolean isLiveTrading = apiKey.getIsLiveTrading();
        String provider = apiKey.getCexName(); // 例如 "OKX", "Binance"

        log.info("从ApiKey获取参数 - provider: {}, isLiveTrading: {}", provider, isLiveTrading);

        // 处理市价单：如果价格为null，获取当前标记价格
        BigDecimal actualPrice = price;
        if (null == actualPrice) {
            log.info("检测到市价单，price为null，获取当前标记价格 - symbol: {}", symbol);
            try {
                actualPrice = marketDataService.getMarkPrice(apiKey, symbol);
                log.info("成功获取标记价格 - symbol: {}, markPrice: {}", symbol, actualPrice);
            } catch (Exception e) {
                log.error("获取标记价格失败 - symbol: {}", symbol, e);
                throw new RuntimeException("无法获取市价单的当前价格，无法计算预估资金: " + e.getMessage());
            }
        }

        // 验证价格不能为null或小于等于0
        if (null == actualPrice || actualPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("价格无效: " + actualPrice + " (symbol: " + symbol + ")");
        }

        // 获取合约信息 (使用ApiKey中的provider和isLiveTrading参数)
        CexInstrument instrument = instrumentRepository
                .findByProviderAndInstIdAndIsLiveTrading(provider, symbol, isLiveTrading)
                .orElseThrow(() -> new RuntimeException("合约信息不存在: " + symbol +
                        " (provider=" + provider + ", isLiveTrading=" + isLiveTrading + ")"));

        // 获取合约面值和手续费率
        BigDecimal ctVal = new BigDecimal(instrument.getCtVal());
        BigDecimal feeRate = null == instrument.getFeeRate()
                ? new BigDecimal(0) : new BigDecimal(instrument.getFeeRate());

        log.info("获取合约信息 - ctVal: {}, feeRate: {}", ctVal, feeRate);

        // 计算保证金: (数量 × 面值 × 价格) / 杠杆倍数
        BigDecimal estimatedMargin = quantity
                .multiply(ctVal)
                .multiply(actualPrice)
                .divide(lever, 8, RoundingMode.UP);

        // 计算开仓手续费
        BigDecimal openFee = quantity
                .multiply(ctVal)
                .multiply(actualPrice)
                .multiply(feeRate);

        // 计算平仓手续费(预估)
        BigDecimal closeFee = quantity
                .multiply(ctVal)
                .multiply(actualPrice)
                .multiply(feeRate);

        // 总占用资金 = 保证金 + 开仓手续费 + 平仓手续费
        BigDecimal totalCapital = estimatedMargin
                .add(openFee)
                .add(closeFee);

        log.info("计算预估总占用资金完成 - 保证金: {}, 开仓手续费: {}, 平仓手续费: {}, 总占用: {}",
                estimatedMargin, openFee, closeFee, totalCapital);

        return totalCapital;
    }

}

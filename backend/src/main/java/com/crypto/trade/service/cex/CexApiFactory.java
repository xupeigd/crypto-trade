package com.crypto.trade.service.cex;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.enums.CexExchange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * CexApiFactory
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class CexApiFactory {

    /**
     * OKX API服务实现
     */
    @Autowired
    @Qualifier("okxApiService")
    CexApiService okxApiService;

    /**
     * 根据ApiKey获取对应的CEX API服务
     * <p>
     * 通过ApiKey.cexName字段判断交易所类型,返回对应的实现。
     * 当前仅支持OKX,其他交易所暂未实现。
     * </p>
     *
     * @param apiKey API密钥,必须包含cexName字段
     * @return CEX API服务实现
     * @throws IllegalArgumentException      如果ApiKey为null或cexName为空
     * @throws UnsupportedOperationException 如果不支持该交易所
     */
    public CexApiService getCexApiService(ApiKey apiKey) {
        // 参数校验
        if (null == apiKey) {
            throw new IllegalArgumentException("ApiKey不能为null");
        }

        String cexName = apiKey.getCexName();
        if (null == cexName || cexName.trim().isEmpty()) {
            throw new IllegalArgumentException("ApiKey的cexName字段不能为空");
        }

        // 路由到对应的交易所实现
        CexExchange exchange = CexExchange.fromCode(cexName);

        return switch (exchange) {
            case OKX -> {
                log.debug("路由到OKX API服务 - apiKeyId: {}", apiKey.getKeyId());
                yield okxApiService;
            }
            case BINANCE -> {
                log.error("暂不支持Binance交易所 - apiKeyId: {}", apiKey.getKeyId());
                throw new UnsupportedOperationException("暂不支持Binance交易所,即将推出");
            }
            case BYBIT -> {
                log.error("暂不支持Bybit交易所 - apiKeyId: {}", apiKey.getKeyId());
                throw new UnsupportedOperationException("暂不支持Bybit交易所,即将推出");
            }
        };
    }

    /**
     * 检查是否支持该交易所
     *
     * @param cexName 交易所名称
     * @return true=支持, false=不支持
     */
    public boolean isSupported(String cexName) {
        try {
            CexExchange exchange = CexExchange.fromCode(cexName);
            // 当前仅OKX已实现
            return null != exchange && CexExchange.OKX == exchange;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取支持的交易所列表
     *
     * @return 已实现的交易所代码数组
     */
    public String[] getSupportedExchanges() {
        return new String[]{"okx"};
    }
}

package com.crypto.trade.dto.cex.adapter;

import com.crypto.trade.dto.cex.model.CexTicker;
import com.crypto.trade.dto.cex.okx.OkxMarketTicker;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CexTickerAdapter
 * 适配器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class CexTickerAdapter {
    public static CexTicker adapt(OkxMarketTicker okxTicker) {
        if (null == okxTicker) return null;
        return new CexTicker() {
            @Override
            public String getSymbol() {
                return okxTicker.getInstId();
            }

            @Override
            public BigDecimal getLastPrice() {
                return okxTicker.getLast();
            }

            @Override
            public BigDecimal getBidPrice() {
                return okxTicker.getBidPx();
            }

            @Override
            public BigDecimal getAskPrice() {
                return okxTicker.getAskPx();
            }

            @Override
            public BigDecimal getBidSize() {
                // OKX的bidSz是Long类型,需要转换为BigDecimal
                Long bidSz = okxTicker.getBidSz();
                return null != bidSz ? BigDecimal.valueOf(bidSz) : null;
            }

            @Override
            public BigDecimal getAskSize() {
                // OKX的askSz是Long类型,需要转换为BigDecimal
                Long askSz = okxTicker.getAskSz();
                return null != askSz ? BigDecimal.valueOf(askSz) : null;
            }

            @Override
            public BigDecimal getOpen24h() {
                return okxTicker.getOpen24h();
            }

            @Override
            public BigDecimal getHigh24h() {
                return okxTicker.getHigh24h();
            }

            @Override
            public BigDecimal getLow24h() {
                return okxTicker.getLow24h();
            }

            @Override
            public BigDecimal getVolume24h() {
                return okxTicker.getVol24h();
            }

            @Override
            public BigDecimal getVolumeCcy24h() {
                return okxTicker.getVolCcy24h();
            }

            @Override
            public Long getTimestamp() {
                return okxTicker.getTs();
            }
        };
    }

    public static List<CexTicker> adapt(List<OkxMarketTicker> okxTickers) {
        if (CollectionUtils.isEmpty(okxTickers)) return Collections.emptyList();
        return okxTickers.stream().filter(Objects::nonNull).map(CexTickerAdapter::adapt).collect(Collectors.toList());
    }
}

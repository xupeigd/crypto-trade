package com.crypto.trade.dto.cex.adapter;

import com.crypto.trade.dto.cex.model.CexMarketTicker;
import com.crypto.trade.dto.cex.okx.OkxMarketTicker;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CexMarketTickerAdapter
 * 适配器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class CexMarketTickerAdapter {

    /**
     * 正向适配:将OKX市场行情转换为通用CEX市场行情
     *
     * @param okxTicker OKX市场行情对象
     * @return 通用CEX市场行情对象
     */
    public static CexMarketTicker adapt(OkxMarketTicker okxTicker) {
        if (null == okxTicker) {
            return null;
        }

        return new CexMarketTicker() {
            @Override
            public String getSymbol() {
                return okxTicker.getInstId();
            }

            @Override
            public String getInstrumentType() {
                return okxTicker.getInstType();
            }

            @Override
            public java.math.BigDecimal getLastPrice() {
                return okxTicker.getLast();
            }

            @Override
            public java.math.BigDecimal getBidPrice() {
                return okxTicker.getBidPx();
            }

            @Override
            public java.math.BigDecimal getAskPrice() {
                return okxTicker.getAskPx();
            }

            @Override
            public java.math.BigDecimal getBidSize() {
                Long bidSz = okxTicker.getBidSz();
                if (null == bidSz) {
                    return null;
                }
                return java.math.BigDecimal.valueOf(bidSz);
            }

            @Override
            public java.math.BigDecimal getAskSize() {
                Long askSz = okxTicker.getAskSz();
                if (null == askSz) {
                    return null;
                }
                return java.math.BigDecimal.valueOf(askSz);
            }

            @Override
            public java.math.BigDecimal getLastSize() {
                Long lastSz = okxTicker.getLastSz();
                if (null == lastSz) {
                    return null;
                }
                return java.math.BigDecimal.valueOf(lastSz);
            }

            @Override
            public java.math.BigDecimal getOpen24h() {
                return okxTicker.getOpen24h();
            }

            @Override
            public java.math.BigDecimal getHigh24h() {
                return okxTicker.getHigh24h();
            }

            @Override
            public java.math.BigDecimal getLow24h() {
                return okxTicker.getLow24h();
            }

            @Override
            public java.math.BigDecimal getVolume24h() {
                return okxTicker.getVol24h();
            }

            @Override
            public java.math.BigDecimal getVolumeCcy24h() {
                return okxTicker.getVolCcy24h();
            }

            @Override
            public Long getTimestamp() {
                return okxTicker.getTs();
            }

            @Override
            public LocalDateTime getUpdateTime() {
                Long ts = okxTicker.getTs();
                if (null == ts) {
                    return null;
                }
                return LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(ts),
                        ZoneId.systemDefault()
                );
            }
        };
    }

    /**
     * 批量正向适配:将OKX市场行情列表转换为通用CEX市场行情列表
     *
     * @param okxTickers OKX市场行情列表
     * @return 通用CEX市场行情列表
     */
    public static List<CexMarketTicker> adapt(List<OkxMarketTicker> okxTickers) {
        if (CollectionUtils.isEmpty(okxTickers)) {
            return Collections.emptyList();
        }
        return okxTickers.stream()
                .filter(Objects::nonNull)
                .map(CexMarketTickerAdapter::adapt)
                .collect(Collectors.toList());
    }

    /**
     * 反向适配:将通用CEX市场行情转换为OKX市场行情
     *
     * @param cexTicker 通用CEX市场行情对象
     * @return OKX市场行情对象
     */
    public static OkxMarketTicker adaptToOkx(CexMarketTicker cexTicker) {
        if (null == cexTicker) {
            return null;
        }

        OkxMarketTicker okxTicker = new OkxMarketTicker();
        okxTicker.setInstId(cexTicker.getSymbol());
        okxTicker.setInstType(cexTicker.getInstrumentType());
        okxTicker.setLast(cexTicker.getLastPrice());
        okxTicker.setBidPx(cexTicker.getBidPrice());
        okxTicker.setAskPx(cexTicker.getAskPrice());

        // 转换BigDecimal到Long (OKX API使用Long表示数量)
        if (null != cexTicker.getBidSize()) {
            okxTicker.setBidSz(cexTicker.getBidSize().longValue());
        }
        if (null != cexTicker.getAskSize()) {
            okxTicker.setAskSz(cexTicker.getAskSize().longValue());
        }
        if (null != cexTicker.getLastSize()) {
            okxTicker.setLastSz(cexTicker.getLastSize().longValue());
        }

        okxTicker.setOpen24h(cexTicker.getOpen24h());
        okxTicker.setHigh24h(cexTicker.getHigh24h());
        okxTicker.setLow24h(cexTicker.getLow24h());
        okxTicker.setVol24h(cexTicker.getVolume24h());
        okxTicker.setVolCcy24h(cexTicker.getVolumeCcy24h());
        okxTicker.setTs(cexTicker.getTimestamp());

        return okxTicker;
    }

    /**
     * 批量反向适配:将通用CEX市场行情列表转换为OKX市场行情列表
     *
     * @param cexTickers 通用CEX市场行情列表
     * @return OKX市场行情列表
     */
    public static List<OkxMarketTicker> adaptToOkx(List<CexMarketTicker> cexTickers) {
        if (CollectionUtils.isEmpty(cexTickers)) {
            return Collections.emptyList();
        }
        return cexTickers.stream()
                .filter(Objects::nonNull)
                .map(CexMarketTickerAdapter::adaptToOkx)
                .collect(Collectors.toList());
    }
}

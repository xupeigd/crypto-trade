package com.crypto.trade.dto.cex.adapter;

import com.crypto.trade.dto.cex.model.CexCandle;
import com.crypto.trade.dto.cex.okx.OkxMarketCandle;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CexCandleAdapter
 * 适配器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class CexCandleAdapter {
    public static CexCandle adapt(OkxMarketCandle okxCandle) {
        if (null == okxCandle) return null;
        return new CexCandle() {
            @Override
            public LocalDateTime getTimestamp() {
                Long ts = okxCandle.getTimestamp();
                return null != ts ? LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()) : null;
            }

            @Override
            public BigDecimal getOpen() {
                return okxCandle.getOpen();
            }

            @Override
            public BigDecimal getHigh() {
                return okxCandle.getHigh();
            }

            @Override
            public BigDecimal getLow() {
                return okxCandle.getLow();
            }

            @Override
            public BigDecimal getClose() {
                return okxCandle.getClose();
            }

            @Override
            public BigDecimal getVolume() {
                return okxCandle.getVolume();
            }

            @Override
            public BigDecimal getVolumeCcy() {
                return okxCandle.getVolumeCcy();
            }

            @Override
            public BigDecimal getVolumeCcyQuote() {
                return okxCandle.getVolCcyQuote();
            }

            @Override
            public boolean isConfirmed() {
                return Integer.valueOf(1).equals(okxCandle.getConfirm());
            }

            @Override
            public BigDecimal getBodySize() {
                BigDecimal open = getOpen();
                BigDecimal close = getClose();
                if (null == open || null == close) {
                    return BigDecimal.ZERO;
                }
                return close.subtract(open).abs();
            }
        };
    }

    public static List<CexCandle> adapt(List<OkxMarketCandle> okxCandles) {
        if (CollectionUtils.isEmpty(okxCandles)) return Collections.emptyList();
        return okxCandles.stream().filter(Objects::nonNull).map(CexCandleAdapter::adapt).collect(Collectors.toList());
    }
}

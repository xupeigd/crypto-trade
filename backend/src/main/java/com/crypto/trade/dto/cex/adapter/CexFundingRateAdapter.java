package com.crypto.trade.dto.cex.adapter;

import com.crypto.trade.dto.cex.model.CexFundingRate;
import com.crypto.trade.dto.cex.okx.OkxFundingRateData;
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
 * CexFundingRateAdapter
 * 适配器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class CexFundingRateAdapter {
    public static CexFundingRate adapt(OkxFundingRateData okxFundingRate) {
        if (null == okxFundingRate) return null;
        return new CexFundingRate() {
            @Override
            public String getSymbol() {
                return okxFundingRate.getInstId();
            }

            @Override
            public BigDecimal getFundingRate() {
                return okxFundingRate.getFundingRate();
            }

            @Override
            public BigDecimal getNextFundingRate() {
                return okxFundingRate.getNextFundingRate();
            }

            @Override
            public LocalDateTime getFundingTime() {
                Long ts = okxFundingRate.getFundingTime();
                return null != ts ? LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()) : null;
            }

            @Override
            public LocalDateTime getNextFundingTime() {
                Long ts = okxFundingRate.getNextFundingTime();
                return null != ts ? LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()) : null;
            }

            @Override
            public BigDecimal getInterestRate() {
                return okxFundingRate.getInterestRate();
            }

            @Override
            public BigDecimal getPremium() {
                return okxFundingRate.getPremium();
            }

            @Override
            public BigDecimal getSettledFundingRate() {
                return okxFundingRate.getSettFundingRate();
            }
        };
    }

    public static List<CexFundingRate> adapt(List<OkxFundingRateData> okxFundingRates) {
        if (CollectionUtils.isEmpty(okxFundingRates)) return Collections.emptyList();
        return okxFundingRates.stream().filter(Objects::nonNull).map(CexFundingRateAdapter::adapt).collect(Collectors.toList());
    }
}

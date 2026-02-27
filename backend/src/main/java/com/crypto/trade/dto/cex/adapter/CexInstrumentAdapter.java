package com.crypto.trade.dto.cex.adapter;

import com.crypto.trade.dto.cex.model.CexInstrument;
import com.crypto.trade.dto.cex.okx.OkxInstrumentInfo;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CexInstrumentAdapter
 * 适配器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class CexInstrumentAdapter {
    public static CexInstrument adapt(OkxInstrumentInfo okxInstrument) {
        if (null == okxInstrument) return null;
        return new CexInstrument() {
            @Override
            public String getSymbol() {
                return okxInstrument.getInstId();
            }

            @Override
            public String getInstrumentType() {
                return okxInstrument.getInstType();
            }

            @Override
            public String getBaseCurrency() {
                return okxInstrument.getBaseCcy();
            }

            @Override
            public String getQuoteCurrency() {
                return okxInstrument.getQuoteCcy();
            }

            @Override
            public String getSettleCurrency() {
                return okxInstrument.getSettleCcy();
            }

            @Override
            public BigDecimal getContractValue() {
                return okxInstrument.getCtVal();
            }

            @Override
            public BigDecimal getLotSize() {
                return okxInstrument.getLotSz();
            }

            @Override
            public BigDecimal getTickSize() {
                return okxInstrument.getTickSz();
            }

            @Override
            public BigDecimal getMinOrderSize() {
                return okxInstrument.getMinSz();
            }

            @Override
            public BigDecimal getMaxLeverage() {
                return okxInstrument.getLever();
            }

            @Override
            public String getState() {
                return okxInstrument.getState();
            }
        };
    }

    public static List<CexInstrument> adapt(List<OkxInstrumentInfo> okxInstruments) {
        if (CollectionUtils.isEmpty(okxInstruments)) return Collections.emptyList();
        return okxInstruments.stream().filter(Objects::nonNull).map(CexInstrumentAdapter::adapt).collect(Collectors.toList());
    }
}

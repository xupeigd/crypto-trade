package com.crypto.trade.dto.cex.adapter;

import com.crypto.trade.dto.cex.model.CexMarkPrice;
import com.crypto.trade.dto.cex.okx.OkxMarkPrice;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CexMarkPriceAdapter
 * 适配器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class CexMarkPriceAdapter {
    public static CexMarkPrice adapt(OkxMarkPrice okxMarkPrice) {
        if (null == okxMarkPrice) return null;
        return new CexMarkPrice() {
            @Override
            public String getSymbol() {
                return okxMarkPrice.getInstId();
            }

            @Override
            public BigDecimal getMarkPrice() {
                return okxMarkPrice.getMarkPx();
            }

            @Override
            public Long getTimestamp() {
                return okxMarkPrice.getTs();
            }
        };
    }

    public static List<CexMarkPrice> adapt(List<OkxMarkPrice> okxMarkPrices) {
        if (CollectionUtils.isEmpty(okxMarkPrices)) return Collections.emptyList();
        return okxMarkPrices.stream().filter(Objects::nonNull).map(CexMarkPriceAdapter::adapt).collect(Collectors.toList());
    }
}

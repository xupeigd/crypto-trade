package com.crypto.trade.dto.cex.adapter;

import com.crypto.trade.dto.cex.model.CexMarketCandle;
import com.crypto.trade.dto.cex.okx.OkxMarketCandle;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CexMarketCandleAdapter
 * 适配器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class CexMarketCandleAdapter {

    /**
     * 正向适配:将OKX K线数据转换为通用CEX K线数据
     *
     * @param okxCandle OKX K线数据对象
     * @return 通用CEX K线数据对象
     */
    public static CexMarketCandle adapt(OkxMarketCandle okxCandle) {
        if (null == okxCandle) {
            return null;
        }

        return new CexMarketCandle() {
            @Override
            public Long getTimestamp() {
                return okxCandle.getTimestamp();
            }

            @Override
            public java.math.BigDecimal getOpen() {
                return okxCandle.getOpen();
            }

            @Override
            public java.math.BigDecimal getHigh() {
                return okxCandle.getHigh();
            }

            @Override
            public java.math.BigDecimal getLow() {
                return okxCandle.getLow();
            }

            @Override
            public java.math.BigDecimal getClose() {
                return okxCandle.getClose();
            }

            @Override
            public java.math.BigDecimal getVolume() {
                return okxCandle.getVolume();
            }

            @Override
            public java.math.BigDecimal getVolumeCcy() {
                return okxCandle.getVolumeCcy();
            }

            @Override
            public Boolean isConfirmed() {
                Integer confirm = okxCandle.getConfirm();
                if (null == confirm) {
                    return false;
                }
                // confirm: 0=未完结, 1=已完结
                return confirm == 1;
            }
        };
    }

    /**
     * 批量正向适配:将OKX K线数据列表转换为通用CEX K线数据列表
     *
     * @param okxCandles OKX K线数据列表
     * @return 通用CEX K线数据列表
     */
    public static List<CexMarketCandle> adapt(List<OkxMarketCandle> okxCandles) {
        if (CollectionUtils.isEmpty(okxCandles)) {
            return Collections.emptyList();
        }
        return okxCandles.stream()
                .filter(Objects::nonNull)
                .map(CexMarketCandleAdapter::adapt)
                .collect(Collectors.toList());
    }

    /**
     * 反向适配:将通用CEX K线数据转换为OKX K线数据
     *
     * @param cexCandle 通用CEX K线数据对象
     * @return OKX K线数据对象
     */
    public static OkxMarketCandle adaptToOkx(CexMarketCandle cexCandle) {
        if (null == cexCandle) {
            return null;
        }

        OkxMarketCandle okxCandle = new OkxMarketCandle();
        okxCandle.setTimestamp(cexCandle.getTimestamp());
        okxCandle.setOpen(cexCandle.getOpen());
        okxCandle.setHigh(cexCandle.getHigh());
        okxCandle.setLow(cexCandle.getLow());
        okxCandle.setClose(cexCandle.getClose());
        okxCandle.setVolume(cexCandle.getVolume());
        okxCandle.setVolumeCcy(cexCandle.getVolumeCcy());

        // 转换确认状态: true→1, false→0
        if (null != cexCandle.isConfirmed()) {
            okxCandle.setConfirm(cexCandle.isConfirmed() ? 1 : 0);
        }

        return okxCandle;
    }

    /**
     * 批量反向适配:将通用CEX K线数据列表转换为OKX K线数据列表
     *
     * @param cexCandles 通用CEX K线数据列表
     * @return OKX K线数据列表
     */
    public static List<OkxMarketCandle> adaptToOkx(List<CexMarketCandle> cexCandles) {
        if (CollectionUtils.isEmpty(cexCandles)) {
            return Collections.emptyList();
        }
        return cexCandles.stream()
                .filter(Objects::nonNull)
                .map(CexMarketCandleAdapter::adaptToOkx)
                .collect(Collectors.toList());
    }
}

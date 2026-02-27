package com.crypto.trade.dto.cex.adapter;

import com.crypto.trade.dto.cex.model.CexAccountBalance;
import com.crypto.trade.dto.cex.okx.OkxAccountBalance;
import com.crypto.trade.dto.cex.okx.OkxAccountBalanceDetail;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CexAccountBalanceAdapter
 * 适配器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class CexAccountBalanceAdapter {

    /**
     * 适配OKX账户余额到通用CEX账户余额
     * 修复:处理details列表,为每个币种创建一个CexAccountBalance记录
     *
     * @param okxAccountBalance OKX账户余额
     * @return 多币种账户余额列表
     */
    public static List<CexAccountBalance> adapt(OkxAccountBalance okxAccountBalance) {
        if (null == okxAccountBalance) {
            return Collections.emptyList();
        }

        // 如果details为空,返回空列表
        if (CollectionUtils.isEmpty(okxAccountBalance.getDetails())) {
            return Collections.emptyList();
        }

        // 处理details列表,为每个币种创建一个CexAccountBalance
        return okxAccountBalance.getDetails().stream()
                .filter(Objects::nonNull)
                .map(CexAccountBalanceAdapter::adaptDetail)
                .collect(Collectors.toList());
    }

    /**
     * 适配单个币种的余额详情
     *
     * @param detail OKX单币种余额详情
     * @return CEX单币种余额
     */
    private static CexAccountBalance adaptDetail(OkxAccountBalanceDetail detail) {
        if (null == detail) {
            return null;
        }

        return new CexAccountBalance() {

            final OkxAccountBalanceDetail orgDetail = detail;

            @Override
            public String getCurrency() {
                // 返回实际币种代码,如USDT、BTC等
                return orgDetail.getCcy();
            }

            @Override
            public BigDecimal getTotalBalance() {
                return orgDetail.getEq();
            }

            @Override
            public BigDecimal getAvailableBalance() {
                return orgDetail.getAvailBal();
            }

            @Override
            public BigDecimal getFrozenBalance() {
                return orgDetail.getFrozenBal();
            }

            @Override
            public BigDecimal getEquityInUsd() {
                return orgDetail.getEqUsd();
            }

            @Override
            public BigDecimal getUsedMargin() {
                // 初始保证金要求
                return orgDetail.getDisEq();
            }

            @Override
            public BigDecimal getUnrealizedPnl() {
                // 未实现盈亏
                return orgDetail.getUpl();
            }

            @Override
            public Long getUpdateTime() {
                return orgDetail.getUTime();
            }


        };
    }

    public static List<CexAccountBalance> adapt(List<OkxAccountBalance> okxAccountBalances) {
        if (CollectionUtils.isEmpty(okxAccountBalances)) {
            return Collections.emptyList();
        }

        // 扁平化处理:每个OkxAccountBalance包含多个币种details
        return okxAccountBalances.stream()
                .filter(Objects::nonNull)
                .flatMap(okxBalance -> adapt(okxBalance).stream())
                .collect(Collectors.toList());
    }

    /**
     * 反向适配:将通用CEX账户余额转换为OKX账户余额
     */
    public static OkxAccountBalance adaptToOkx(CexAccountBalance cexAccountBalance) {
        if (null == cexAccountBalance) {
            return null;
        }

        OkxAccountBalance okxBalance = new OkxAccountBalance();
        okxBalance.setTotalEq(cexAccountBalance.getTotalBalance());
        okxBalance.setAvailEq(cexAccountBalance.getAvailableBalance());
        return okxBalance;
    }

    /**
     * 批量反向适配
     */
    public static List<OkxAccountBalance> adaptToOkx(List<CexAccountBalance> cexAccountBalances) {
        if (CollectionUtils.isEmpty(cexAccountBalances)) {
            return Collections.emptyList();
        }

        return cexAccountBalances.stream()
                .filter(Objects::nonNull)
                .map(CexAccountBalanceAdapter::adaptToOkx)
                .collect(Collectors.toList());
    }
}

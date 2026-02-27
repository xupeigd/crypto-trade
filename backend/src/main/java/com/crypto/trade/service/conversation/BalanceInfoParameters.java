package com.crypto.trade.service.conversation;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * BalanceInfoParameters
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BalanceInfoParameters extends ToolParameters {

    /**
     * 特定货币，可选
     */
    private String currency;

    /**
     * 是否包含保证金信息
     */
    private Boolean includeMargin = true;

    @Override
    public String getAction() {
        return "balance_info";
    }
}
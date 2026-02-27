package com.crypto.trade.dto.cex.okx;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AccountBalanceResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalanceResponse {

    /**
     * 响应状态码，"0"表示成功
     */
    private String code;

    /**
     * 响应消息
     */
    private String msg;

    /**
     * 账户余额数据数组
     */
    private List<OkxAccountBalance> data;
}

package com.crypto.trade.rest.controller.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AccountDetailModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountDetailModel {

    BigDecimal totalEquity;        // 账户权益（账户估值）
    BigDecimal usedMargin;         // 已用保证金
    BigDecimal availableBalance;   // 可用余额
    BigDecimal unrealizedPnl;      // 未实现盈亏
    BigDecimal marginRatio;        // 保证金使用率

    /**
     * 账户余额最后更新时间
     * 使用统一的日期格式序列化，避免前端解析为Invalid Date
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime lastUpdateTime;  // 最后更新时间

    public static AccountDetailModel empty() {
        return new AccountDetailModel(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, LocalDateTime.now());
    }

}

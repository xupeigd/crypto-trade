package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * OkxClosePositionRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OkxClosePositionRequest {

    public String instId;        // 合约品种

    public String tdMode;        // 交易模式

    public String mgnMode;       // 保证金模式

    public String ccy;           // 保证金币种

    public String side;          // 订单方向

    public String posSide;       // 持仓方向：long/short

    public BigDecimal sz;        // 平仓数量

}
package com.crypto.trade.dto.cex.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CexClosePositionRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CexClosePositionRequest {

    public String tdMode;        // 交易模式
    public String mgnMode;       // 保证金模式
    public String ccy;           // 保证金币种
    public String side;          // 订单方向
    private String symbol; // 交易对/合约
    private String positionSide; // long/short/net
    private String quantity; // 平仓数量

}

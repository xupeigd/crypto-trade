package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OkxAlgoRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OkxAlgoRequest {

    /**
     * {
     * "side": "buy",
     * "cxlOnClosePos": true,
     * "tdMode": "isolated",
     * "tpTriggerPx": "2429.7850",
     * "tpOrdPx": -1,
     * "instId": "ETH-USDT-SWAP",
     * "posSide": "short",
     * "reduceOnly": true,
     * "closeFraction": 1,
     * "slOrdPx": -1,
     * "ccy": "USDT",
     * "slTriggerPx": "3188.2150",
     * "ordType": "oco"
     * }
     */

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String instId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String tdMode;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String ccy;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String side;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String posSide;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String ordType;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String sz;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String tag;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String tgtCcy;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String algoClOrdId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String closeFraction;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String tradeQuoteCcy;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Boolean cxlOnClosePos;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Boolean reduceOnly;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String tpTriggerPx;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String tpOrdPx;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String tpTriggerPxType;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String slTriggerPx;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String slOrdPx;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String slTriggerPxType;


}

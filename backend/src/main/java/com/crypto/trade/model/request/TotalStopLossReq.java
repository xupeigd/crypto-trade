package com.crypto.trade.model.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TotalStopLossReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */


@Data
@NoArgsConstructor
@AllArgsConstructor
public class TotalStopLossReq {

    Integer apiKeyId;

    String algoId;

    String instId;

    String posSide;

    String side;

    String sz;

    String tpTriggerPx;

    String slTriggerPx;

    String tpTriggerPxType;

    String slTriggerPxType;

    /**
     * 保证金模式: cross(全仓) / isolated(逐仓)
     */
    String mgnMode;

}


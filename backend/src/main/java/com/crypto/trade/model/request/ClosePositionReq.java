package com.crypto.trade.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * ClosePositionReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosePositionReq {

    public Long apiKeyId;

    public String instId;

    public String posSide;

    public BigDecimal sz;

    /**
     * 保证金模式: cross(全仓) / isolated(逐仓)
     */
    public String mgnMode;

}

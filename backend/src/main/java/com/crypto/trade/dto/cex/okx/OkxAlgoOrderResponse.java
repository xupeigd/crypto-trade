package com.crypto.trade.dto.cex.okx;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OkxAlgoOrderResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class OkxAlgoOrderResponse
        extends OkxApiResponse<OkxAlgoOrder> {

}

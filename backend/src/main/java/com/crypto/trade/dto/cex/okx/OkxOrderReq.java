package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OkxOrderReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OkxOrderReq {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String instId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String ordId;

}

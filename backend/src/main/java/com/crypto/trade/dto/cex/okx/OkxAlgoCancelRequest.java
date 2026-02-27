package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OkxAlgoCancelRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OkxAlgoCancelRequest {

    /**
     * 策略id
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String algoId;

    /**
     * 产品id
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String instId;

}

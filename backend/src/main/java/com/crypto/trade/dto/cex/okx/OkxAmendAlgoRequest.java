package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OkxAmendAlgoRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OkxAmendAlgoRequest {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String instId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String algoId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String algoClOrdId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Boolean cxlOnFail;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String reqId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String newSz;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String newTpTriggerPx;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String newTpOrdPx;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String newSlTriggerPx;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String newSlOrdPx;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String newTpTriggerPxType;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String newSlTriggerPxType;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String newOrdType;

}

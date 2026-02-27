package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OkxAlgoState
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OkxAlgoState {

    String algoClOrdId;

    String algoId;

    String clOrdId;

    String sCode;

    String sMsg;

    String tag;

    String ts;

    String ordId;

    @Data
    public static class OkxAlgoStateResponse
            extends OkxApiResponse<OkxAlgoState> {

        public static OkxAlgoStateResponse fail(String msg) {
            OkxAlgoStateResponse response = new OkxAlgoStateResponse();
            response.setCode("1");
            response.setMsg(msg);
            return response;
        }

    }

}

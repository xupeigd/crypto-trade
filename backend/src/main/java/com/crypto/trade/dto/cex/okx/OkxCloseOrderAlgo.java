package com.crypto.trade.dto.cex.okx;

import com.crypto.trade.dto.cex.utils.StringToBigDecimalDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * OkxCloseOrderAlgo
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OkxCloseOrderAlgo {

    String algoId;

    String closeFraction;

    String ordType;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal slTriggerPx;

    String slTriggerPxType;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal tpTriggerPx;

    String tpTriggerPxType;

}

package com.crypto.trade.dto.cex.okx;

import com.crypto.trade.dto.cex.utils.StringToBigDecimalDeserializer;
import com.crypto.trade.dto.cex.utils.StringToLongDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * OkxMarkPrice
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OkxMarkPrice {

    String instId;

    String instType;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal markPx;

    @JsonDeserialize(using = StringToLongDeserializer.class)
    Long ts;

}

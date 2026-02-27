package com.crypto.trade.dto.cex.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * StringToBooleanDeserializer
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

public class StringToBooleanDeserializer
        extends JsonDeserializer<Boolean> {


    @Override
    public Boolean getNullValue(DeserializationContext ctxt) {
        return false;
    }

    @Override
    public Boolean deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        return "true".equalsIgnoreCase(value);
    }

}

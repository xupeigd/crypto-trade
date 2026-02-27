package com.crypto.trade.dto.cex.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * StringToLongDeserializer
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

public class StringToLongDeserializer
        extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        String value = p.getText();
        if (null == value || value.isEmpty()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}

package com.crypto.trade.service.conversation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 * ToolParameters
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "action"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = KLineParameters.class, name = "k_line"),
        @JsonSubTypes.Type(value = PositionInfoParameters.class, name = "position_info"),
        @JsonSubTypes.Type(value = BalanceInfoParameters.class, name = "balance_info")
})
public abstract class ToolParameters {

    abstract String getAction();

}
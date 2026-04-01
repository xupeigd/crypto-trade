package com.crypto.trade.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowNodeStatusResponse {

    @JsonProperty("nodeCode")
    private String nodeCode;

    @JsonProperty("nodeName")
    private String nodeName;

    @JsonProperty("orderNo")
    private Integer orderNo;

    @JsonProperty("status")
    private String status;

    @JsonProperty("startTime")
    private Long startTime;

    @JsonProperty("endTime")
    private Long endTime;

    @JsonProperty("durationMs")
    private Long durationMs;

    @JsonProperty("message")
    private String message;

}

package com.crypto.trade.dto.proxy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CexProxyBindingResponse {

    Long bindingId;

    String cexName;

    Long proxyId;

    String proxyName;

    String proxyType;

    String serverHost;

    Integer serverPort;

    String status;

    String description;

    LocalDateTime createdTime;

    LocalDateTime updatedTime;
}

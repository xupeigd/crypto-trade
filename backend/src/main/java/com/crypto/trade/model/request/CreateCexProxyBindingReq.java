package com.crypto.trade.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateCexProxyBindingReq {

    @NotBlank(message = "交易所不能为空")
    String cexName;

    @NotNull(message = "代理ID不能为空")
    Long proxyId;

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "^(active|inactive)$", message = "状态必须是active或inactive")
    String status;

    String description;
}

package com.crypto.trade.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ActiveApiKeyDTO
 * 数据传输对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActiveApiKeyDTO {

    /**
     * API Key ID (主键)
     */
    private Long keyId;

    /**
     * Key显示名称
     */
    private String keyName;

    /**
     * 访问密钥 (已掩码处理)
     */
    private String accessKey;

    /**
     * 交易所名称
     */
    private String vendor;

    /**
     * 状态 (active/inactive)
     */
    private String status;

    /**
     * 实盘交易标识
     */
    private Boolean isLiveTrading;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
}

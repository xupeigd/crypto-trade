package com.crypto.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ActionHistoryResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionHistoryResponse {

    /**
     * 动作ID
     */
    private Long actionId;

    /**
     * 执行来源（INITIAL-初始，REPLAY-重放）
     */
    private String executionSource;

    /**
     * 创建时间（时间戳）
     */
    private Long createdTime;

    /**
     * 执行状态（PENDING-待执行，SUCCESS-成功，FAILED-失败）
     */
    private String status;

    /**
     * 重放次数（仅对INITIAL记录有效）
     */
    private Integer replayCount;

    /**
     * 执行时间
     */
    private LocalDateTime executedTime;
}

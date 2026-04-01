package com.crypto.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AttentionInfo
 * ATTENTION信息数据传输对象
 *
 * @author page
 * @date 2026-03-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttentionInfo {

    /**
     * 队列ID
     */
    private Long queueId;

    /**
     * 合约代码（如BTC-USDT-SWAP）
     */
    private String instId;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 时间周期 (1m, 5m, 1H, etc.)
     */
    private String timeframe;

    /**
     * 查询条数限制
     */
    private Integer limit;

    /**
     * 预期触发时间
     */
    private LocalDateTime expectedTriggerTime;

}

package com.crypto.trade.event;

import com.crypto.trade.dto.cex.model.CexPosition;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PositionChangedEvent
 * 持仓变更事件
 *
 * 当持仓数据发生变化时发布此事件，用于触发相关缓存清理等操作
 *
 * @author page
 * @date 2026-02-26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PositionChangedEvent {

    /**
     * API密钥ID
     */
    private Long apiKeyId;

    /**
     * 旧持仓列表
     */
    private List<CexPosition> oldPositions;

    /**
     * 新持仓列表
     */
    private List<CexPosition> newPositions;

    /**
     * 持仓是否发生变化
     */
    private boolean changed;

    /**
     * 创建持仓变更事件
     *
     * @param apiKeyId     API密钥ID
     * @param oldPositions 旧持仓列表
     * @param newPositions 新持仓列表
     * @param changed      是否发生变化
     * @return 持仓变更事件
     */
    public static PositionChangedEvent of(Long apiKeyId, List<CexPosition> oldPositions,
                                           List<CexPosition> newPositions, boolean changed) {
        return new PositionChangedEvent(apiKeyId, oldPositions, newPositions, changed);
    }
}

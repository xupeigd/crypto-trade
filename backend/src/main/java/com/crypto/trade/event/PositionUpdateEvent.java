package com.crypto.trade.event;

import com.crypto.trade.dto.cex.model.CexPosition;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * PositionUpdateEvent
 * 事件类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PositionUpdateEvent {

    /**
     * API密钥ID
     */
    private Long apiKeyId;

    /**
     * 更新的仓位数据列表
     */
    private List<CexPosition> positions;

    /**
     * 仓位数组数据(按API密钥ID分组)
     */
    private Map<Long, List<CexPosition>> allPositions;

    /**
     * 更新时间戳
     */
    private long timestamp;

    /**
     * 更新类型(FULL_UPDATE/PARTIAL_UPDATE/ERROR)
     */
    private UpdateType updateType;

    /**
     * 错误信息(仅在更新失败时有效)
     */
    private String errorMessage;

    /**
     * 成功更新的API密钥数量
     */
    private Integer successCount;

    /**
     * 失败更新的API密钥数量
     */
    private Integer failureCount;

    /**
     * 创建成功更新事件
     */
    public static PositionUpdateEvent success(Long apiKeyId, List<CexPosition> positions,
                                              Map<Long, List<CexPosition>> allPositions) {
        return new PositionUpdateEvent(
                apiKeyId,
                positions,
                allPositions,
                System.currentTimeMillis(),
                UpdateType.FULL_UPDATE,
                null,
                1,
                0
        );
    }

    /**
     * 创建失败更新事件
     */
    public static PositionUpdateEvent error(Long apiKeyId, String errorMessage) {
        return new PositionUpdateEvent(
                apiKeyId,
                null,
                null,
                System.currentTimeMillis(),
                UpdateType.ERROR,
                errorMessage,
                0,
                1
        );
    }

    /**
     * 更新类型枚举
     */
    public enum UpdateType {
        FULL_UPDATE("全部更新"),
        PARTIAL_UPDATE("部分更新"),
        ERROR("更新失败");

        private final String description;

        UpdateType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
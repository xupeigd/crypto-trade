package com.crypto.trade.dto;

import com.crypto.trade.entity.RiskMode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CurrentModeResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CurrentModeResponse {

    /**
     * 当前风控模式 (AUTO/MANUAL)
     */
    String currentMode;

    /**
     * 模式描述
     */
    String description;

    /**
     * 是否为自动模式
     */
    Boolean isAutoMode;

    /**
     * 是否为手动模式
     */
    Boolean isManualMode;

    /**
     * 从RiskMode枚举创建响应
     *
     * @param riskMode 风控模式枚举
     * @return CurrentModeResponse响应对象
     */
    public static CurrentModeResponse fromRiskMode(RiskMode riskMode) {
        if (null == riskMode) {
            return CurrentModeResponse.builder()
                    .currentMode("UNKNOWN")
                    .description("未知模式")
                    .isAutoMode(false)
                    .isManualMode(false)
                    .build();
        }

        String mode = riskMode.name();
        String description = riskMode.getDescription();
        boolean isAuto = RiskMode.AUTO.equals(riskMode);
        boolean isManual = RiskMode.MANUAL.equals(riskMode);

        return CurrentModeResponse.builder()
                .currentMode(mode)
                .description(description)
                .isAutoMode(isAuto)
                .isManualMode(isManual)
                .build();
    }

    /**
     * 获取模式显示名称
     *
     * @return 模式的中文描述
     */
    public String getDisplayName() {
        if (null == currentMode) {
            return "未知";
        }
        switch (currentMode) {
            case "AUTO":
                return "自动模式";
            case "MANUAL":
                return "手动模式";
            default:
                return currentMode;
        }
    }
}
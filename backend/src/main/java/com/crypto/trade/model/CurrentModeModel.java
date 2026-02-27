package com.crypto.trade.model;

import com.crypto.trade.entity.RiskMode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CurrentModeModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CurrentModeModel {

    /**
     * 当前风控模式
     */
    RiskMode currentMode;

    /**
     * 模式描述
     */
    String description;

    /**
     * 是否为自动模式
     */
    boolean isAutoMode;

    /**
     * 是否为手动模式
     */
    boolean isManualMode;

    /**
     * 从RiskMode枚举创建CurrentModeModel
     *
     * @param riskMode 风控模式枚举
     * @return CurrentModeModel实例
     */
    public static CurrentModeModel fromRiskMode(RiskMode riskMode) {
        return CurrentModeModel.builder()
                .currentMode(riskMode)
                .description(riskMode.getDescription())
                .isAutoMode(riskMode == RiskMode.AUTO)
                .isManualMode(riskMode == RiskMode.MANUAL)
                .build();
    }
}
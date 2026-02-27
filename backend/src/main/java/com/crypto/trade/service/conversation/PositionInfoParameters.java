package com.crypto.trade.service.conversation;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PositionInfoParameters
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PositionInfoParameters extends ToolParameters {

    /**
     * 特定合约代码，可选
     */
    private String instId;

    /**
     * 详细级别：summary, detailed, risk_analysis
     */
    private String detailLevel = "summary";

    @Override
    public String getAction() {
        return "position_info";
    }
}
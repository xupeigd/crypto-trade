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
public class PositionInfoParameters
        extends ToolParameters {

    /**
     * 类型：ALIVE-当前持仓, PENDING-委托中, HISTORY-历史仓位
     */
    private String type = "ALIVE";

    /**
     * 返回数量，-1表示全部，最大30
     */
    private Integer limit = -1;

    @Override
    String getAction() {
        return "position_info";
    }
}
package com.crypto.trade.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ReplaySingleActionRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplaySingleActionRequest {

    /**
     * 历史记录ID
     * 用于校验action归属,对应 t_llm_call_record 表的主键
     */
    @NotNull(message = "recordId不能为空")
    private Long recordId;

    /**
     * 动作ID
     * 对应 t_trade_action 表的主键
     */
    @NotNull(message = "actionId不能为空")
    private Long actionId;
}

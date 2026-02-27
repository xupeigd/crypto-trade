package com.crypto.trade.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ReplayDecisionRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayDecisionRequest {

    /**
     * 历史记录ID
     * 对应 t_llm_call_record 表的主键
     */
    @NotNull(message = "recordId不能为空")
    private Long recordId;
}

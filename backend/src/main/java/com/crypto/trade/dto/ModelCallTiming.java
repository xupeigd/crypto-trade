package com.crypto.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModelCallTiming
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCallTiming {

    /**
     * Prompt生成耗时(毫秒)
     * 记录生成完整Prompt所需的时间,包括模板渲染、数据组装等
     */
    private Long promptGenerationTimeMs;

    /**
     * 大模型调用耗时(毫秒)
     * 记录AI模型API调用的实际耗时,从请求发送到响应返回的时间
     */
    private Long llmCallTimeMs;

    /**
     * 后置动作耗时(毫秒)
     * 记录响应解析、决策提取、结果保存等后置处理时间
     */
    private Long postActionTimeMs;

    /**
     * 计算总耗时
     *
     * @return 三个阶段的总耗时(毫秒), 如果任一字段为null则返回null
     */
    public Long getTotalTimeMs() {
        if (null == promptGenerationTimeMs || null == llmCallTimeMs || null == postActionTimeMs) {
            return null;
        }
        return promptGenerationTimeMs + llmCallTimeMs + postActionTimeMs;
    }
}

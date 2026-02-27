package com.crypto.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModelCallResult
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCallResult {

    /**
     * 大模型响应内容
     */
    private String response;

    /**
     * 调用耗时数据
     */
    private ModelCallTiming timing;

    /**
     * 快速构建仅包含响应的结果(耗时信息为空)
     *
     * @param response 响应内容
     * @return ModelCallResult实例
     */
    public static ModelCallResult of(String response) {
        return ModelCallResult.builder()
                .response(response)
                .timing(null)
                .build();
    }

    /**
     * 快速构建包含响应和耗时的结果
     *
     * @param response      响应内容
     * @param llmCallTimeMs 大模型调用耗时(毫秒)
     * @return ModelCallResult实例
     */
    public static ModelCallResult of(String response, Long llmCallTimeMs) {
        ModelCallTiming timing = ModelCallTiming.builder()
                .llmCallTimeMs(llmCallTimeMs)
                .build();
        return ModelCallResult.builder()
                .response(response)
                .timing(timing)
                .build();
    }
}

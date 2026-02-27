package com.crypto.trade.service.conversation;

import com.crypto.trade.service.prompt.PromptContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * ConversationRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRequest {

    /**
     * API密钥ID
     */
    private Long apiKeyId;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 初始Prompt
     */
    private String initialPrompt;

    /**
     * 调用来源
     */
    private String callSource;

    /**
     * 最大轮数
     */
    @Builder.Default
    private Integer maxRounds = 10;

    /**
     * 会话名称
     */
    private String sessionName;

    /**
     * 用户ID
     */
    @Builder.Default
    private String userId = "default";

    /**
     * 自定义参数
     */
    private Map<String, Object> customParams;

    /**
     * Prompt上下文（用于业务数据获取）
     */
    private PromptContext promptContext;
}
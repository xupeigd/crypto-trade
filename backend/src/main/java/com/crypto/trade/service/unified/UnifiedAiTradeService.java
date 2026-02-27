package com.crypto.trade.service.unified;

import com.crypto.trade.dto.response.BotPromptGenerateResponse;
import com.crypto.trade.service.AiDecisionService;
import com.crypto.trade.service.conversation.ActionParser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * UnifiedAiTradeService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Slf4j
@Service
public class UnifiedAiTradeService {

    @Autowired
    AiDecisionService aiDecisionService;

    /**
     * AI交易
     *
     * @param aiTradeRequest AI交易请求
     * @return AI交易响应
     */
    public AiTradeResponse aiTrade(AiTradeRequest aiTradeRequest) {
        if (Objects.equals(true, aiTradeRequest.isFirstRequest)) {
            // 首次请求的prompt是空的，需要调用处理器生成prompt
            BotPromptGenerateResponse promptGenerateResponse = aiDecisionService.generatePromptOnly(aiTradeRequest.getApiKeyId());
            aiTradeRequest.setPrompt(promptGenerateResponse.getPromptContent());
        }
        // 检查actions


        return null;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiTradeRequest {

        /**
         * 请求Id
         * 用于追踪请求
         */
        String requestId;

        /**
         * 是否首次请求
         * <p>
         * 用于指定是否是首次请求
         */
        Boolean isFirstRequest;

        /**
         * API Key ID
         */
        Long apiKeyId;

        /**
         * 模型ID
         * （选填）
         * 用于指定模型(使用系统指定的时，不传)
         */
        String modelId;

        /**
         * 提示词
         * 用于指定模型的输入内容
         */
        String prompt;

        /**
         * 上下文
         * （选填）
         * 用于指定模型的上下文信息
         */
        List<String> prompts;

        /**
         * 当前的actionPack
         * （选填）
         */
        ActionParser.ActionPack currentActionPack;

        /**
         * 动作包列表
         * （选填）
         * 包含多个动作包，每个动作包包含多个动作
         */
        List<ActionParser.ActionPack> actionPacks;

        /**
         * 动作包索引
         * （选填）
         * 用于指定要执行的动作包索引
         */
        Integer actionPackIndex;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiTradeResponse {

        /**
         * 请求Id
         * 用于追踪请求
         */
        String requestId;

        /**
         * 响应内容
         * 包含模型的输出内容
         */
        String responseContent;

    }

}

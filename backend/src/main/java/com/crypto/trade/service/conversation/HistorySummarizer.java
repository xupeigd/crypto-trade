package com.crypto.trade.service.conversation;

import com.crypto.trade.entity.ChatMessage;
import com.crypto.trade.entity.LlmCallRecord;
import com.crypto.trade.repository.ChatMessageRepository;
import com.crypto.trade.util.AiResponseParserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * HistorySummarizer
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistorySummarizer {

    private static final int MAX_PROMPT_LENGTH = 100; // prompt摘要最大长度
    private static final int MAX_THINKING_LENGTH = 150; // thinking摘要最大长度

    private final ChatMessageRepository chatMessageRepository;

    /**
     * 将历史对话记录摘要为文本
     * <p>
     * 摘要策略:
     * 1. 每轮保留: prompt类型、AI响应类型、工具调用结果
     * 2. 压缩冗长的thinking内容
     * 3. 聚合连续的工具调用
     * </p>
     *
     * @param history 历史对话记录列表(按时间升序)
     * @return 摘要文本
     */
    public String summarize(List<LlmCallRecord> history) {
        if (null == history || history.isEmpty()) {
            return "无历史对话记录";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("=== 对话历史摘要 ===\n\n");

        for (LlmCallRecord record : history) {
            summary.append(String.format("第%d轮:\n", record.getRoundNumber()));

            // 摘要prompt内容(通过userMessageId关联ChatMessage获取)
            if (record.getUserMessageId() != null) {
                Optional<ChatMessage> userMsg = chatMessageRepository.findById(record.getUserMessageId());
                String promptContent = userMsg.map(ChatMessage::getContent).orElse(null);
                String promptSummary = truncate(promptContent, MAX_PROMPT_LENGTH);
                summary.append("  请求: ").append(promptSummary);
                if (promptContent != null && promptContent.length() > MAX_PROMPT_LENGTH) {
                    summary.append("...");
                }
                summary.append("\n");
            }

            // 摘要AI响应(通过assistantMessageId关联ChatMessage获取)
            if (record.getAssistantMessageId() != null) {
                Optional<ChatMessage> assistantMsg = chatMessageRepository.findById(record.getAssistantMessageId());
                String responseContent = assistantMsg.map(ChatMessage::getContent).orElse(null);
                if (responseContent != null) {
                    String responseSummary = extractKeyInfo(responseContent);
                    summary.append("  响应: ").append(responseSummary).append("\n");
                }
            }

            summary.append("\n");
        }

        log.debug("生成历史摘要完成 - 历史记录数: {}, 摘要长度: {}", history.size(), summary.length());
        return summary.toString();
    }

    /**
     * 从AI响应中提取关键信息
     * <p>
     * 提取策略:
     * 1. 优先提取action、instId、confidence等决策字段
     * 2. 压缩thinking内容
     * 3. 识别工具调用
     * </p>
     *
     * @param response AI响应内容
     * @return 关键信息摘要
     */
    private String extractKeyInfo(String response) {
        if (null == response || response.trim().isEmpty()) {
            return "[无响应内容]";
        }

        StringBuilder keyInfo = new StringBuilder();

        // 1. 提取thinking内容(压缩)
        String thinking = AiResponseParserUtil.extractThinkingProcess(response);
        if (null != thinking && !thinking.trim().isEmpty()) {
            String thinkingSummary = truncate(thinking, MAX_THINKING_LENGTH);
            keyInfo.append("思考: ").append(thinkingSummary);
            if (thinking.length() > MAX_THINKING_LENGTH) {
                keyInfo.append("...");
            }
            keyInfo.append("; ");
        }

        // 2. 检测工具调用
        if (hasToolCall(response)) {
            keyInfo.append("请求工具调用; ");
        }

        // 3. 检测决策动作
        String action = extractAction(response);
        if (null != action) {
            keyInfo.append("决策: ").append(action).append("; ");
        }

        // 4. 如果没有提取到任何信息,返回截断的原始响应
        if (keyInfo.length() == 0) {
            return truncate(response, MAX_PROMPT_LENGTH);
        }

        return keyInfo.toString();
    }

    /**
     * 检测响应是否包含工具调用
     *
     * @param response AI响应
     * @return true表示包含工具调用
     */
    private boolean hasToolCall(String response) {
        if (null == response) {
            return false;
        }
        // 简单检测: 查找"action":"QUERY"等工具调用标记
        return response.contains("\"action\"")
                && (response.contains("QUERY") || response.contains("query"));
    }

    /**
     * 从响应中提取决策动作
     *
     * @param response AI响应
     * @return 动作字符串(BUY / SELL / HOLD等), 如果未找到返回null
     */
    private String extractAction(String response) {
        if (null == response) {
            return null;
        }

        // 查找"action":"BUY/SELL/HOLD"模式
        String[] actions = {"BUY", "SELL", "HOLD"};
        for (String action : actions) {
            if (response.contains("\"action\":\"" + action + "\"")
                    || response.contains("\"action\": \"" + action + "\"")) {
                return action;
            }
        }

        return null;
    }

    /**
     * 截断文本到指定长度
     *
     * @param text      原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLength) {
        if (null == text) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}

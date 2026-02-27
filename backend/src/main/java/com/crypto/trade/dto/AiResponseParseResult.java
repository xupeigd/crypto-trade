package com.crypto.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AiResponseParseResult
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResponseParseResult {

    /**
     * 完整的AI响应内容
     * <p>
     * 包含原始的AI模型输出，未经任何处理。
     * 可能包含thinking标签、JSON数据、markdown标记等。
     * </p>
     */
    private String fullResponse;

    /**
     * 提取的思考过程内容
     * <p>
     * 从{@code <thinking>}标签中提取的纯文本内容，
     * 不包含标签本身，已清理多余的空白行。
     * </p>
     * <p>
     * 如果响应中没有thinking标签，此字段为null或空字符串。
     * </p>
     */
    private String thinkingProcess;

    /**
     * 提取的JSON内容
     * <p>
     * 已清理的纯JSON字符串，不包含：
     * <ul>
     *   <li>thinking标签及其内容</li>
     *   <li>markdown代码块标记（```json、```）</li>
     *   <li>多余的空白字符</li>
     * </ul>
     * </p>
     * <p>
     * 如果响应中没有JSON数据，此字段为null或空字符串。
     * </p>
     */
    private String jsonContent;

    /**
     * 是否包含思考过程
     * <p>
     * true表示响应中存在{@code <thinking>}标签且内容非空；
     * false表示响应中没有thinking标签或thinking内容为空。
     * </p>
     */
    private boolean hasThinking = false;

    /**
     * 是否包含JSON数据
     * <p>
     * true表示响应中存在JSON格式的数据；
     * false表示响应中没有JSON数据。
     * </p>
     */
    private boolean hasJson = false;

    /**
     * JSON是否有效
     * <p>
     * true表示提取的jsonContent是有效的JSON格式；
     * false表示jsonContent不是有效的JSON或不存在。
     * </p>
     */
    private boolean isValidJson = false;

    /**
     * 获取思考过程内容，如果不存在则返回空字符串
     *
     * @return 思考过程内容，不为null
     */
    public String getThinkingProcessOrEmpty() {
        if (null == thinkingProcess) {
            return "";
        }
        return thinkingProcess;
    }

    /**
     * 获取JSON内容，如果不存在则返回空字符串
     *
     * @return JSON内容，不为null
     */
    public String getJsonContentOrEmpty() {
        if (null == jsonContent) {
            return "";
        }
        return jsonContent;
    }
}

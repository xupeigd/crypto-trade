/**
 * AI响应解析工具函数
 * <p>
 * 提供从前端解析AI模型响应内容的功能，支持从fullResponse中提取thinking和JSON。
 * 与后端的AiResponseParserUtil功能一致，但实现为TypeScript工具函数。
 * </p>
 *
 * @author Claude
 * @since 2025-12-27
 */

/**
 * thinking标签的正则表达式
 * <p>
 * 非贪婪匹配，支持多行内容，大小写不敏感。
 * 匹配格式：{@code <thinking>内容</thinking>}
 * </p>
 */
const THINKING_PATTERN = /<thinking>([\s\S]*?)<\/thinking>/i;

/**
 * 从AI响应中提取思考过程
 * <p>
 * 提取{@code <thinking>}标签中的内容，不包含标签本身。
 * 支持多行内容和大小写不敏感。
 * </p>
 *
 * <p>支持的格式：</p>
 * <ul>
 *   <li>{@code <thinking>单行内容</thinking>}</li>
 *   <li>{@code <thinking>多行\n内容</thinking>}</li>
 *   <li>{@code <THINKING>大小写不敏感</THINKING>}</li>
 * </ul>
 *
 * @param fullResponse AI模型的完整响应，包含thinking标签
 * @return 提取的思考过程内容（不包含标签），如果未找到thinking标签则返回null
 */
export function extractThinkingProcess(fullResponse: string): string | null {
    // 参数校验
    if (null == fullResponse) {
        return null;
    }

    // 使用正则表达式匹配thinking标签
    const match = fullResponse.match(THINKING_PATTERN);

    if (match && match[1]) {
        // 提取thinking标签内的内容
        let thinkingContent = match[1];

        // 清理多余的空白行：将连续的换行符替换为单个换行符
        thinkingContent = thinkingContent.replace(/\n\s*\n/g, '\n');

        // trim首尾空白
        return thinkingContent.trim();
    }

    // 未找到thinking标签
    return null;
}

/**
 * 清理markdown代码块标记
 * <p>
 * 移除常见的markdown代码块标记，返回纯文本内容。
 * </p>
 *
 * <p>清理规则：</p>
 * <ul>
 *   <li>移除开头的{@code ```json}（7个字符）</li>
 *   <li>移除开头的{@code ```}（3个字符）</li>
 *   <li>移除结尾的{@code ```}（3个字符）</li>
 *   <li>trim首尾空白</li>
 * </ul>
 *
 * @param content 可能包含markdown代码块标记的内容
 * @return 清理后的内容，不包含markdown标记
 */
export function cleanMarkdownCodeBlock(content: string): string {
    // 参数校验
    if (null == content || content.trim().length === 0) {
        return content;
    }

    let cleaned = content.trim();

    // 移除开头的markdown代码块标记
    if (cleaned.startsWith('```json')) {
        // 移除前7个字符（```json）
        cleaned = cleaned.substring(7);
    } else if (cleaned.startsWith('```')) {
        // 移除前3个字符（```）
        cleaned = cleaned.substring(3);
    }

    // 移除结尾的markdown代码块标记
    if (cleaned.endsWith('```')) {
        // 移除最后3个字符
        cleaned = cleaned.substring(0, cleaned.length - 3);
    }

    // trim首尾空白
    return cleaned.trim();
}

/**
 * 从AI响应中提取JSON决策数据
 * <p>
 * 移除thinking标签及其内容，清理markdown代码块标记，返回纯JSON字符串。
 * </p>
 *
 * <p>处理流程：</p>
 * <ol>
 *   <li>移除{@code <thinking>}标签及其内容（如果存在）</li>
 *   <li>清理markdown代码块标记（{@code ```json}、{@code ```}）</li>
 *   <li>trim首尾空白</li>
 * </ol>
 *
 * @param fullResponse AI模型的完整响应，可能包含thinking标签和markdown标记
 * @return 清理后的纯JSON字符串，如果响应为空则返回原响应
 */
export function extractDecisionJson(fullResponse: string): string {
    // 参数校验
    if (null == fullResponse) {
        return '';
    }

    if (fullResponse.trim().length === 0) {
        return fullResponse;
    }

    // 步骤1：移除thinking标签及其内容
    // 使用正则表达式替换，非贪婪匹配thinking标签及其内容
    const withoutThinking = fullResponse.replace(/<thinking>[\s\S]*?<\/thinking>/gi, '');

    // 步骤2：清理markdown代码块标记
    const cleaned = cleanMarkdownCodeBlock(withoutThinking);

    return cleaned;
}

/**
 * 解析AI响应
 * <p>
 * 综合解析方法，从AI响应中提取思考过程和JSON决策数据。
 * 该方法会自动识别响应格式，并返回详细的解析结果。
 * </p>
 *
 * <p>支持的格式：</p>
 * <ul>
 *   <li>格式1：{@code <thinking>}思考内容{@code </thinking>} + JSON</li>
 *   <li>格式2：只有{@code <thinking>}思考内容{@code </thinking>}</li>
 *   <li>格式3：只有JSON（直接的或markdown包裹的）</li>
 * </ul>
 *
 * @param fullResponse AI模型的完整响应，可能包含thinking标签和JSON、markdown标记等
 * @return 解析结果对象，包含thinking和json两部分
 */
export function parseAiResponse(fullResponse: string): {
    thinking: string | null;
    json: string | null;
} {
    // 参数校验：响应内容不能为空
    if (null == fullResponse || fullResponse.trim().length === 0) {
        return { thinking: null, json: null };
    }

    // 提取思考过程
    const thinking = extractThinkingProcess(fullResponse);

    // 提取JSON内容
    const json = extractDecisionJson(fullResponse);

    // 判断JSON是否为空
    const hasJson = json && json.trim().length > 0;

    return {
        thinking: thinking || null,
        json: hasJson ? json : null
    };
}

/**
 * 判断字符串是否为JSON格式
 * <p>
 * 通过检查字符串的开头和结尾字符来判断是否为JSON格式。
 * </p>
 *
 * @param str 待检测的字符串
 * @return true表示可能是JSON格式，false表示不是JSON格式
 */
export function isJsonFormat(str: string): boolean {
    if (null == str || str.trim().length === 0) {
        return false;
    }

    const trimmed = str.trim();

    // 检查是否为JSON对象（以{开始，以}结束）
    if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
        return true;
    }

    // 检查是否为JSON数组（以[开始，以]结束）
    if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
        return true;
    }

    return false;
}

/**
 * 验证JSON是否有效
 * <p>
 * 尝试解析JSON字符串，检查其语法是否正确。
 * </p>
 *
 * @param jsonString 待验证的JSON字符串
 * @return true表示JSON格式有效，false表示无效
 */
export function isValidJson(jsonString: string): boolean {
    if (null == jsonString || jsonString.trim().length === 0) {
        return false;
    }

    try {
        JSON.parse(jsonString);
        return true;
    } catch {
        return false;
    }
}

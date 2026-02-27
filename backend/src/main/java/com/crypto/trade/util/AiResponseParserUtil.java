package com.crypto.trade.util;

import com.crypto.trade.dto.AiResponseParseResult;
import com.crypto.trade.model.SegmentModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AiResponseParserUtil
 * 工具类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
public class AiResponseParserUtil {

    /**
     * thinking标签的正则表达式模式
     * <p>
     * 使用非贪婪匹配，支持多行内容，大小写不敏感。
     * 匹配格式：{@code <thinking>内容</thinking>}
     * </p>
     */
    private static final Pattern THINKING_PATTERN = Pattern.compile(
            "<thinking>([\\s\\S]*?)</thinking>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    /**
     * JSON对象检测模式
     * <p>
     * 检测响应是否为JSON对象格式，即以{@code {}}开始和结束。
     * </p>
     */
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("^\\s*\\{.*\\}\\s*$", Pattern.DOTALL);

    /**
     * JSON数组检测模式
     * <p>
     * 检测响应是否为JSON数组格式，即以{@code []}开始和结束。
     * </p>
     */
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("^\\s*\\[.*\\]\\s*$", Pattern.DOTALL);

    /**
     * JSON解析器
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 解析AI响应
     * <p>
     * 综合解析方法，从AI响应中提取思考过程和JSON决策数据。
     * 该方法会自动识别响应格式，并返回详细的解析结果。
     * </p>
     *
     * <p>支持的格式：</p>
     * <ul>
     *   <li>格式1：{@code <thinking>思考内容</thinking>} + JSON</li>
     *   <li>格式2：只有{@code <thinking>思考内容</thinking>}</li>
     *   <li>格式3：只有JSON（直接的或markdown包裹的）</li>
     * </ul>
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>提取thinking标签内容（如果存在）</li>
     *   <li>移除thinking标签，获取剩余内容</li>
     *   <li>清理markdown代码块标记</li>
     *   <li>检测是否为有效JSON</li>
     *   <li>封装结果到AiResponseParseResult对象</li>
     * </ol>
     *
     * @param aiResponse      AI模型的完整响应，可能包含thinking标签、JSON、markdown标记等
     * @param jsonOnlyDisplay json是否只用于显示
     * @return 解析结果对象，包含思考过程、JSON内容以及各种标志位
     * @throws IllegalArgumentException 如果aiResponse为null或空字符串
     * @see AiResponseParseResult
     */
    public static AiResponseParseResult parseAiResponse(String aiResponse, boolean jsonOnlyDisplay) {
        // 参数校验：响应内容不能为空
        if (null == aiResponse || aiResponse.trim().isEmpty()) {
            throw new IllegalArgumentException("AI响应内容不能为空");
        }

        // 构建结果对象
        AiResponseParseResult.AiResponseParseResultBuilder builder = AiResponseParseResult.builder()
                .fullResponse(aiResponse);

        // 步骤1：提取思考过程
        String thinkingProcess = extractThinkingProcess(aiResponse);
        if (null != thinkingProcess && !thinkingProcess.trim().isEmpty()) {
            builder.thinkingProcess(thinkingProcess)
                    .hasThinking(true);
            log.debug("成功提取思考过程，长度：{} 字符", thinkingProcess.length());
        } else {
            builder.hasThinking(false);
            log.debug("未发现thinking标签或思考内容为空");
        }

        // 步骤2：提取JSON内容
        String jsonContent = extractDecisionJson(aiResponse, jsonOnlyDisplay);
        if (!jsonContent.trim().isEmpty()) {
            builder.jsonContent(jsonContent);
            // 步骤3：检测JSON是否有效
            if (isJsonFormat(jsonContent)) {
                builder.hasJson(true)
                        .isValidJson(true);
                log.debug("成功提取有效的JSON内容，长度：{} 字符", jsonContent.length());
            } else {
                builder.hasJson(false)
                        .isValidJson(false);
                log.debug("提取的JSON内容格式无效");
            }
        } else {
            builder.hasJson(false)
                    .isValidJson(false);
            log.debug("未发现JSON内容");
        }
        return builder.build();
    }

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
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>使用正则表达式匹配thinking标签</li>
     *   <li>提取标签内的内容</li>
     *   <li>清理多余的空白行（连续的换行符）</li>
     *   <li>trim首尾空白</li>
     * </ol>
     *
     * @param aiResponse AI模型的完整响应，包含thinking标签
     * @return 提取的思考过程内容（不包含标签），如果未找到thinking标签则返回null
     * @throws IllegalArgumentException 如果aiResponse为null
     */
    public static String extractThinkingProcess(String aiResponse) {
        // 参数校验
        if (null == aiResponse) {
            throw new IllegalArgumentException("AI响应内容不能为null");
        }

        // 使用正则表达式提取thinking标签内容
        Matcher matcher = THINKING_PATTERN.matcher(aiResponse);

        if (matcher.find()) {
            // 提取thinking标签内的内容
            String thinkingContent = matcher.group(1);

            if (null != thinkingContent && !thinkingContent.trim().isEmpty()) {
                // 清理多余的空白行：将连续的换行符替换为单个换行符
                thinkingContent = thinkingContent.replaceAll("\\n\\s*\\n", "\n");

                // trim首尾空白
                return thinkingContent.trim();
            }
        }

        // 未找到thinking标签或内容为空
        return null;
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
     * <p>支持的格式：</p>
     * <ul>
     *   <li>纯JSON：{@code {"action":"BUY"}}</li>
     *   <li>Markdown包裹：{@code ```json {"action":"BUY"} ```}</li>
     *   <li>混合格式：thinking标签 + markdown包裹的JSON</li>
     * </ul>
     *
     * @param aiResponse  AI模型的完整响应，可能包含thinking标签和markdown标记
     * @param onlyDisplay 是否只显示内容，不显示markdown标记
     * @return 清理后的纯JSON字符串，如果响应为空则返回原响应
     * @throws IllegalArgumentException 如果aiResponse为null
     */
    public static String extractDecisionJson(String aiResponse, boolean onlyDisplay) {
        // 参数校验
        if (null == aiResponse) {
            throw new IllegalArgumentException("AI响应内容不能为null");
        }

        if (aiResponse.trim().isEmpty()) {
            return aiResponse;
        }

        // 步骤1：移除thinking标签及其内容
        // 使用正则表达式替换，非贪婪匹配thinking标签及其内容
        String withoutThinking = aiResponse.replaceAll("<thinking>[\\s\\S]*?</thinking>", "");

        // 步骤2：清理markdown代码块标记
        String cleaned = cleanAiResponse(withoutThinking, onlyDisplay);

        log.debug("提取JSON完成，原始长度：{}，清理后长度：{}",
                aiResponse.length(), cleaned.length());

        return cleaned;
    }

    /**
     * 清理AI响应中的markdown代码块标记
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
     * <p>支持的格式：</p>
     * <ul>
     *   <li>{@code ```json\n{...}\n```}</li>
     *   <li>{@code ```\n{...}\n```}</li>
     * </ul>
     *
     * @param aiResponse  可能包含markdown代码块标记的AI响应
     * @param onlyDisplay 是否只显示内容，不显示markdown标记
     * @return 清理后的内容，不包含markdown标记，如果响应为空则返回原响应
     */
    public static String cleanAiResponse(String aiResponse, boolean onlyDisplay) {
        // 参数校验
        if (null == aiResponse || aiResponse.trim().isEmpty()) {
            return aiResponse;
        }
        String cleaned = aiResponse.trim();
        if (!onlyDisplay) {
            cleaned = cleaned.replaceAll("```(([Jj][Ss][Oo][Nn])*|$)", "");
            cleaned = cleaned.substring(cleaned.indexOf("{"), cleaned.lastIndexOf("}") + 1);
        }
        // trim首尾空白
        return cleaned.trim();
    }

    /**
     * 检测响应是否为JSON格式
     * <p>
     * 通过检查响应的开头和结尾字符来判断是否为JSON格式。
     * </p>
     *
     * <p>判定规则：</p>
     * <ul>
     *   <li>JSON对象：以{@code {})包含，即以{@code {}开始，以{@code }}结束</li>
     *   <li>JSON数组：以{@code []}包含，即以{@code [}开始，以{@code ]}结束</li>
     * </ul>
     *
     * <p>注意：</p>
     * <ul>
     *   <li>此方法只进行格式检测，不验证JSON的有效性</li>
     *   <li>不检测JSON内部的语法正确性</li>
     *   <li>空白字符会被忽略</li>
     * </ul>
     *
     * @param response 待检测的响应内容
     * @return true表示响应可能是JSON格式，false表示不是JSON格式或响应为空
     */
    public static boolean isJsonFormat(String response) {
        // 参数校验
        if (null == response || response.trim().isEmpty()) {
            return false;
        }

        String trimmed = response.trim();

        // 检测是否为JSON对象（以{开始，以}结束）
        if (JSON_OBJECT_PATTERN.matcher(trimmed).matches()) {
            return true;
        }

        // 检测是否为JSON数组（以[开始，以]结束）
        return JSON_ARRAY_PATTERN.matcher(trimmed).matches();
    }

    /**
     * 验证JSON是否有效
     * <p>
     * 尝试解析JSON字符串，检查其语法是否正确。
     * </p>
     *
     * <p>验证方法：</p>
     * <ul>
     *   <li>使用Jackson ObjectMapper解析JSON</li>
     *   <li>如果解析成功且结果是JsonNode，则认为有效</li>
     *   <li>如果解析失败（抛出异常），则认为无效</li>
     * </ul>
     *
     * @param jsonString 待验证的JSON字符串
     * @return true表示JSON格式有效，false表示无效或jsonString为空
     */
    public static boolean isValidJson(String jsonString) {
        // 参数校验
        if (null == jsonString || jsonString.trim().isEmpty()) {
            return false;
        }

        try {
            // 尝试解析JSON
            JsonNode jsonNode = OBJECT_MAPPER.readTree(jsonString);
            // 如果能成功解析为JsonNode，则认为有效
            return null != jsonNode;
        } catch (Exception e) {
            // 解析失败，JSON无效
            log.warn("JSON格式验证失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析AI响应为结构化的segments
     * <p>
     * 从fullResponse中提取thinking和json，转换为SegmentModel列表
     * </p>
     *
     * @param fullResponse AI模型的完整响应
     * @return 结构化的segments列表，包含thinking和json段落
     */
    public static List<SegmentModel> parseResponseToSegments(String fullResponse) {
        AiResponseParseResult parseResult = AiResponseParserUtil.parseAiResponse(fullResponse, true);
        List<SegmentModel> segments = new ArrayList<>();

        // 添加thinking段落
        if (parseResult.isHasThinking() && null != parseResult.getThinkingProcess()) {
            segments.add(SegmentModel.createThinkingSegment("AI思考过程", parseResult.getThinkingProcess()));
            log.debug("成功添加thinking segment - 长度: {}", parseResult.getThinkingProcess().length());
        }

        // 添加json段落 - 改进逻辑：直接检查内容而不是仅依赖isHasJson标志
        String jsonContent = parseResult.getJsonContent();
        if (null != jsonContent && !jsonContent.trim().isEmpty()) {
            jsonContent = jsonContent.contains("```json") || jsonContent.contains("```JSON")
                    ? jsonContent : "```json\n %s\n ```".formatted(jsonContent);
            segments.add(SegmentModel.createDataSegment("决策JSON", jsonContent));
            log.debug("成功添加JSON segment - isHasJson: {}, 内容长度: {}",
                    parseResult.isHasJson(), jsonContent.length());
        } else {
            log.debug("JSON segment未添加 - isHasJson: {}, jsonContent为空",
                    parseResult.isHasJson());
        }

        log.debug("parseResponseToSegments完成 - 总segment数量: {}", segments.size());
        return segments;
    }


}

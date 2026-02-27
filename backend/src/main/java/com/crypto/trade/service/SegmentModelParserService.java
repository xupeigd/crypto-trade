package com.crypto.trade.service;

import com.crypto.trade.model.SegmentModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SegmentModelParserService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class SegmentModelParserService {

    /**
     * 段落标题正则表达式
     * 匹配格式：=== 标题 ===
     */
    private static final Pattern SEGMENT_TITLE_PATTERN = Pattern.compile("^={3}\\s*(.+?)\\s*={3}\\s*$", Pattern.MULTILINE);

    /**
     * 思考过程正则表达式
     * 匹配<thinking>...</thinking>标签
     */
    private static final Pattern THINKING_PATTERN = Pattern.compile("<thinking>(.*?)</thinking>", Pattern.DOTALL);

    /**
     * 工具调用JSON正则表达式
     * 匹配工具调用的JSON格式
     */
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile("\\{[^}]*\"action\"[^}]*\\}", Pattern.DOTALL);

    /**
     * 从fullResponse中解析SegmentModel列表
     *
     * @param fullResponse 完整的AI响应文本
     * @return 解析出的SegmentModel列表
     */
    public List<SegmentModel> parseFromFullResponse(String fullResponse) {
        List<SegmentModel> segments = new ArrayList<>();

        if (fullResponse == null || fullResponse.trim().isEmpty()) {
            return segments;
        }

        try {
            // 预处理：标准化换行符
            String normalizedContent = fullResponse.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");

            // 解析思考过程
//            parseThinkingSegments(normalizedContent, segments);

            // 解析带标题的段落
            parseTitledSegments(normalizedContent, segments);

            // 如果没有解析到任何段落，将整个内容作为默认段落
            if (segments.isEmpty()) {
                createDefaultSegment(normalizedContent, segments);
            }

            log.debug("成功解析出 {} 个SegmentModel段落", segments.size());

        } catch (Exception e) {
            log.error("解析SegmentModel失败", e);
            // 出错时创建一个默认段落
            createDefaultSegment(fullResponse, segments);
        }

        return segments;
    }

    /**
     * 解析思考过程段落
     *
     * @param content  内容文本
     * @param segments 段落列表
     */
    private void parseThinkingSegments(String content, List<SegmentModel> segments) {
        Matcher thinkingMatcher = THINKING_PATTERN.matcher(content);

        while (thinkingMatcher.find()) {
            String thinkingContent = thinkingMatcher.group(1).trim();
            if (!thinkingContent.isEmpty()) {
                SegmentModel thinkingSegment = SegmentModel.builder()
                        .title("思考过程")
                        .content(thinkingContent)
                        .category(SegmentModel.Category.THINKING)
                        .priority(SegmentModel.Priority.LOW)
                        .showTitle(true)
                        .addMetadata("source", "thinking_tag")
                        .build();

                segments.add(thinkingSegment);
            }
        }
    }

    /**
     * 解析带标题的段落
     *
     * @param content  内容文本
     * @param segments 段落列表
     */
    private void parseTitledSegments(String content, List<SegmentModel> segments) {
        Matcher titleMatcher = SEGMENT_TITLE_PATTERN.matcher(content);

        int lastEnd = 0;
        String currentTitle = null;
        StringBuilder currentContent = new StringBuilder();

        while (titleMatcher.find()) {
            // 处理前一个段落
            if (currentTitle != null) {
                String segmentContent = content.substring(lastEnd, titleMatcher.start()).trim();
                if (!segmentContent.isEmpty()) {
                    SegmentModel segment = createSegmentFromTitle(currentTitle, segmentContent);
                    segments.add(segment);
                }
            }

            // 开始新段落
            currentTitle = titleMatcher.group(1).trim();
            lastEnd = titleMatcher.end();
            currentContent.setLength(0);
        }

        // 处理最后一个段落
        if (currentTitle != null && lastEnd < content.length()) {
            String segmentContent = content.substring(lastEnd).trim();
            if (!segmentContent.isEmpty()) {
                SegmentModel segment = createSegmentFromTitle(currentTitle, segmentContent);
                segments.add(segment);
            }
        }
    }

    /**
     * 根据标题创建SegmentModel
     *
     * @param title   段落标题
     * @param content 段落内容
     * @return SegmentModel
     */
    private SegmentModel createSegmentFromTitle(String title, String content) {
        // 根据标题确定category和priority
        String category = determineCategory(title);
        int priority = determinePriority(title);

        return SegmentModel.builder()
                .title(title)
                .content(content)
                .category(category)
                .priority(priority)
                .showTitle(true)
                .addMetadata("source", "title_parsing")
                .addMetadata("original_title", title)
                .build();
    }

    /**
     * 根据标题确定内容分类
     *
     * @param title 标题
     * @return 分类
     */
    private String determineCategory(String title) {
        if (title == null) {
            return SegmentModel.Category.DATA;
        }

        String lowerTitle = title.toLowerCase();

        // 数据类信息
        if (lowerTitle.contains("统计") || lowerTitle.contains("账户") || lowerTitle.contains("持仓")
                || lowerTitle.contains("余额") || lowerTitle.contains("保证金") || lowerTitle.contains("权益")) {
            return SegmentModel.Category.DATA;
        }

        // 分析类信息
        if (lowerTitle.contains("技术指标") || lowerTitle.contains("分析") || lowerTitle.contains("风险评估")) {
            return SegmentModel.Category.ANALYSIS;
        }

        // 指令类信息
        if (lowerTitle.contains("决策") || lowerTitle.contains("要求") || lowerTitle.contains("格式")
                || lowerTitle.contains("响应") || lowerTitle.contains("工具调用")) {
            return SegmentModel.Category.INSTRUCTION;
        }

        // 思考类信息
        if (lowerTitle.contains("思考") || lowerTitle.contains("推理") || lowerTitle.contains("过程")) {
            return SegmentModel.Category.THINKING;
        }

        // 市场类信息
        if (lowerTitle.contains("市场") || lowerTitle.contains("行情") || lowerTitle.contains("合约概况")
                || lowerTitle.contains("top30") || lowerTitle.contains("涨跌")) {
            return SegmentModel.Category.MARKET;
        }

        // 默认为数据类
        return SegmentModel.Category.DATA;
    }

    /**
     * 根据标题确定优先级
     *
     * @param title 标题
     * @return 优先级数值
     */
    private int determinePriority(String title) {
        if (title == null) {
            return SegmentModel.Priority.MEDIUM;
        }

        String lowerTitle = title.toLowerCase();

        // 最高优先级 - 统计信息
        if (lowerTitle.contains("统计")) {
            return SegmentModel.Priority.HIGHEST;
        }

        // 高优先级 - 账户信息
        if (lowerTitle.contains("账户") || lowerTitle.contains("余额")) {
            return SegmentModel.Priority.HIGH;
        }

        // 中高优先级 - 持仓信息
        if (lowerTitle.contains("持仓")) {
            return SegmentModel.Priority.MEDIUM_HIGH;
        }

        // 中等优先级 - 技术指标、市场数据
        if (lowerTitle.contains("技术指标") || lowerTitle.contains("市场") || lowerTitle.contains("合约概况")) {
            return SegmentModel.Priority.MEDIUM;
        }

        // 中低优先级 - 市场数据
        if (lowerTitle.contains("top30") || lowerTitle.contains("涨跌")) {
            return SegmentModel.Priority.MEDIUM_LOW;
        }

        // 低优先级 - 思考过程
        if (lowerTitle.contains("思考") || lowerTitle.contains("推理")) {
            return SegmentModel.Priority.LOW;
        }

        // 最低优先级 - 决策要求
        if (lowerTitle.contains("决策") || lowerTitle.contains("要求")) {
            return SegmentModel.Priority.LOWEST;
        }

        // 默认中等优先级
        return SegmentModel.Priority.MEDIUM;
    }

    /**
     * 创建默认段落
     *
     * @param content  内容
     * @param segments 段落列表
     */
    private void createDefaultSegment(String content, List<SegmentModel> segments) {
        SegmentModel defaultSegment = SegmentModel.builder()
                .title("完整响应")
                .content(content)
                .category(SegmentModel.Category.DATA)
                .priority(SegmentModel.Priority.MEDIUM)
                .showTitle(true)
                .addMetadata("source", "default_fallback")
                .build();

        segments.add(defaultSegment);
    }

    /**
     * 清理和优化段落内容
     *
     * @param content 原始内容
     * @return 清理后的内容
     */
    private String cleanContent(String content) {
        if (content == null) {
            return "";
        }

        // 移除多余的空行
        String cleaned = content.replaceAll("\\n\\s*\\n\\s*\\n", "\n\n");

        // 移除首尾空白
        cleaned = cleaned.trim();

        return cleaned;
    }
}
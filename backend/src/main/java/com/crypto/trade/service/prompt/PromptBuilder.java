package com.crypto.trade.service.prompt;

import com.crypto.trade.dto.cex.adapter.CexPositionAdapter;
import com.crypto.trade.dto.cex.model.CexPosition;
import com.crypto.trade.dto.response.BotPromptGenerateResponse;
import com.crypto.trade.model.PositionModel;
import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.service.prompt.processor.*;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PromptBuilder
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class PromptBuilder {

    // 默认处理器列表
    private final List<PromptProcessor> defaultProcessors = new ArrayList<>();

    @Autowired
    AccountInfoProcessor accountInfoProcessor;
    @Autowired
    PositionInfoProcessor positionInfoProcessor;
    @Autowired
    PendingOrdersPromptProcessor pendingOrdersPromptProcessor;
    @Autowired
    PendingOrdersKlinePromptProcessor pendingOrdersKlinePromptProcessor;
    @Autowired
    TechnicalIndicatorProcessor technicalIndicatorProcessor;
    @Autowired
    MarketDataProcessor marketDataProcessor;
    @Autowired
    ThinkingModeProcessor thinkingModeProcessor;
    @Autowired
    DecisionRequirementProcessor decisionRequirementProcessor;
    @Autowired
    TradeRulePromptProcessor tradeRulePromptProcessor;
    @Autowired
    PositionHistoryProcessor positionHistoryProcessor;
    @Autowired
    OrderHistoryProcessor orderHistoryProcessor;
    @Autowired
    StrategySuggestionProcessor strategySuggestionProcessor;
    @Autowired
    AttentionPromptProcessor attentionPromptProcessor;

    /**
     * 初始化方法
     */
    @PostConstruct
    public void init() {
        // 初始化默认处理器
        defaultProcessors.add(accountInfoProcessor);
        defaultProcessors.add(positionInfoProcessor);
        defaultProcessors.add(technicalIndicatorProcessor);
        defaultProcessors.add(pendingOrdersPromptProcessor);
        defaultProcessors.add(pendingOrdersKlinePromptProcessor);
        defaultProcessors.add(marketDataProcessor);
        defaultProcessors.add(positionHistoryProcessor);
        defaultProcessors.add(orderHistoryProcessor);
        defaultProcessors.add(strategySuggestionProcessor);
        defaultProcessors.add(tradeRulePromptProcessor);
        defaultProcessors.add(thinkingModeProcessor);
        defaultProcessors.add(decisionRequirementProcessor);
        defaultProcessors.add(attentionPromptProcessor);
        log.debug("PromptBuilder 初始化完成，加载了 {} 个处理器", defaultProcessors.size());
    }

    /**
     * 构建Prompt内容并返回段落数据
     *
     * @param context 处理上下文
     * @return 包含prompt内容和段落数据的结果
     */
    public PromptBuildResult buildPromptWithSegments(PromptContext context) {
        log.debug("开始构建Prompt并生成段落数据 - apiKeyId: {}", context.getApiKeyId());
        // 获取要执行的处理器列表
        List<SegmentModel> segments = buildSegments(context);
        // 组装最终的Prompt
        String promptContent = assemblePromptFromSegments(segments, context);
        log.debug("Prompt构建完成，总长度: {}, 段落数量: {}", promptContent.length(), segments.size());
        return new PromptBuildResult(promptContent, segments);
    }


    private List<SegmentModel> buildSegments(PromptContext context) {
        // 获取要执行的处理器列表
        List<PromptProcessor> processors = getProcessors(context);
        log.debug("【Prompt构建】开始执行处理器 - 总数: {}, apiKeyId: {}", processors.size(), context.getApiKeyId());

        // 按优先级排序
        processors.sort(Comparator.comparingInt(PromptProcessor::getPriority));

        // 执行各个处理器，收集SegmentModel
        List<SegmentModel> segments = new ArrayList<>();
        for (int i = 0; i < processors.size(); i++) {
            PromptProcessor processor = processors.get(i);
            try {
                if (processor.shouldExecute(context)) {
                    SegmentModel segment = processor.process(context);

                    if (segment == null) {
                        log.warn("【Prompt构建】处理器 {} 返回null，跳过该处理器", processor.getName());
                    } else if (!segment.shouldInclude()) {
                        log.debug("【Prompt构建】处理器 {} 生成的segment被shouldInclude()过滤 - 标题: {}, showTitle: {}, contentLength: {}",
                                processor.getName(), segment.getTitle(), segment.getShowTitle(),
                                segment.getContent() != null ? segment.getContent().length() : 0);
                    } else if (segment.getContent() == null || segment.getContent().trim().isEmpty()) {
                        log.warn("【Prompt构建】处理器 {} 生成的segment内容为空 - 标题: {}, showTitle: {}",
                                processor.getName(), segment.getTitle(), segment.getShowTitle());
                    } else {
                        segments.add(segment);
                        log.debug("【Prompt构建】处理器 {}/{} ({}) 执行完成 - 标题: {}, showTitle: {}, contentLength: {}, priority: {}",
                                i + 1, processors.size(), processor.getName(), segment.getTitle(), segment.getShowTitle(),
                                segment.getContent().length(), processor.getPriority());
                    }
                } else {
                    log.debug("【Prompt构建】处理器 {} 被shouldExecute()跳过", processor.getName());
                }
            } catch (Exception e) {
                log.error("【Prompt构建】处理器 {} 执行失败，跳过该处理器", processor.getName(), e);
                // 继续执行其他处理器，不中断整个构建过程
            }
        }

        log.debug("【Prompt构建】处理器执行完成 - 实际生成segments: {}/{}, apiKeyId: {}",
                segments.size(), processors.size(), context.getApiKeyId());
        return segments;
    }

    /**
     * 构建完整的Prompt
     *
     * @param context 处理上下文
     * @return 完整的Prompt内容
     * @throws PromptProcessException 处理异常
     */
    public String buildPrompts(PromptContext context) throws PromptProcessException {
        log.debug("开始构建Prompt - apiKeyId: {}", context.getApiKeyId());
        List<SegmentModel> segments = buildSegments(context);
        // 组装最终的Prompt
        String result = assemblePromptFromSegments(segments, context);
        log.debug("Prompt构建完成，总长度: {}, 段落数量: {}", result.length(), segments.size());
        return result;
    }

    /**
     * 从SegmentModel列表组装最终的Prompt
     *
     * @param segments 段落列表
     * @param context  处理上下文
     * @return 组装后的Prompt内容
     */
    private String assemblePromptFromSegments(List<SegmentModel> segments, PromptContext context) {
        if (segments.isEmpty()) {
            return "";
        }
        // 获取组装格式配置
        String format = context.getConfiguration("promptFormat") != null
                ? (String) context.getConfiguration("promptFormat")
                : "DEFAULT";
        StringBuilder promptBuilder = new StringBuilder();
        for (SegmentModel segment : segments) {
            String segmentContent = formatSegment(segment, format);

            if (!segmentContent.trim().isEmpty()) {
                if (!promptBuilder.isEmpty()) {
                    promptBuilder.append("\n");
                }
                promptBuilder.append(segmentContent);
            }
        }

        return promptBuilder.toString().trim();
    }

    /**
     * 格式化单个段落
     *
     * @param segment 段落模型
     * @param format  格式类型
     * @return 格式化后的内容
     */
    private String formatSegment(SegmentModel segment, String format) {
        return switch (format.toUpperCase()) {
            case "MARKDOWN" -> formatSegmentAsMarkdown(segment);
            case "HTML" -> formatSegmentAsHtml(segment);
            case "CUSTOM" -> formatSegmentAsCustom(segment);
            default -> formatSegmentAsDefault(segment);
        };
    }

    /**
     * 默认格式化段落
     */
    private String formatSegmentAsDefault(SegmentModel segment) {
        StringBuilder sb = new StringBuilder();

        if (segment.getShowTitle() && segment.getTitle() != null && !segment.getTitle().trim().isEmpty()) {
            sb.append("=== ").append(segment.getTitle()).append(" ===\n");
        }

        if (segment.getContent() != null) {
            sb.append(segment.getContent());
        }

        return sb.toString();
    }

    /**
     * Markdown格式化段落
     */
    private String formatSegmentAsMarkdown(SegmentModel segment) {
        StringBuilder sb = new StringBuilder();

        if (segment.getShowTitle() && segment.getTitle() != null && !segment.getTitle().trim().isEmpty()) {
            sb.append("## ").append(segment.getTitle()).append("\n\n");
        }

        if (segment.getContent() != null) {
            sb.append(segment.getContent());
        }

        return sb.toString();
    }

    /**
     * HTML格式化段落
     */
    private String formatSegmentAsHtml(SegmentModel segment) {
        StringBuilder sb = new StringBuilder();

        sb.append("<div class=\"prompt-segment\">\n");

        if (segment.getShowTitle() && segment.getTitle() != null && !segment.getTitle().trim().isEmpty()) {
            sb.append("  <h2>").append(segment.getTitle()).append("</h2>\n");
        }

        if (segment.getContent() != null) {
            sb.append("  <div class=\"segment-content\">")
                    .append(segment.getContent().replace("\n", "<br/>\n"))
                    .append("</div>\n");
        }

        sb.append("</div>");

        return sb.toString();
    }

    /**
     * 自定义格式化段落
     */
    private String formatSegmentAsCustom(SegmentModel segment) {
        // 这里可以根据需要实现自定义格式化逻辑
        // 目前使用默认格式
        return formatSegmentAsDefault(segment);
    }

    /**
     * 构建Prompt并生成响应对象
     *
     * @param context 处理上下文
     * @param taskId  任务ID
     * @return Prompt生成响应
     */
    public BotPromptGenerateResponse buildPromptWithResponse(PromptContext context, String taskId) {

        long startTime = System.currentTimeMillis();

        try {
            PromptBuildResult buildResult = buildPromptWithSegments(context);
            long processingTime = System.currentTimeMillis() - startTime;

            // 估算token数量（简单计算：字符数/4）
            Integer estimatedTokens = (buildResult.getPromptContent().length() / 4) + 100;

            List<PositionModel> positionModels = null;
            List<CexPosition> positions = context.getCustomData("positions");
            if (!CollectionUtils.isEmpty(positions)) {
                positionModels = CexPositionAdapter.toPositionModelList(positions);
            }

            return BotPromptGenerateResponse.builder()
                    .taskId(taskId)
                    .apiKeyId(context.getApiKeyId())
                    .status("SUCCESS")
                    .message("Prompt生成成功")
                    .promptContent(buildResult.getPromptContent())
                    .segments(buildResult.getSegments())
                    .generateTime(System.currentTimeMillis())
                    .estimatedTokens(estimatedTokens)
                    .positions(positionModels)
                    .processingTimeMs(processingTime)
                    .balanceSnapshotId(context.getCurrentSnapshotId())
                    .success(true)
                    .build();

        } catch (Exception e) {
            long errorTime = System.currentTimeMillis() - startTime;
            log.error("Prompt构建失败 - apiKeyId: {}, taskId: {}", context.getApiKeyId(), taskId, e);

            return BotPromptGenerateResponse.builder()
                    .taskId(taskId)
                    .apiKeyId(context.getApiKeyId())
                    .status("FAILED")
                    .message("Prompt生成失败: " + e.getMessage())
                    .generateTime(System.currentTimeMillis())
                    .processingTimeMs(errorTime)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 获取要执行的处理器列表
     *
     * @param context 处理上下文
     * @return 处理器列表
     */
    private List<PromptProcessor> getProcessors(PromptContext context) {
        // 检查上下文中是否有自定义处理器配置
        @SuppressWarnings("unchecked")
        List<String> enabledProcessors = (List<String>) context.getConfiguration().get("enabledProcessors");

        if (enabledProcessors != null && !enabledProcessors.isEmpty()) {
            // 使用配置的处理器
            return defaultProcessors.stream()
                    .filter(processor -> enabledProcessors.contains(processor.getName()))
                    .collect(Collectors.toList());
        } else {
            // 使用默认处理器列表
            return new ArrayList<>(defaultProcessors);
        }
    }

    /**
     * 设置处理器参数
     *
     * @param processorName 处理器名称
     * @param parameters    参数
     */
    public void setProcessorParameters(String processorName, Map<String, Object> parameters) {
        PromptProcessor processor = defaultProcessors.stream()
                .filter(p -> p.getName().equals(processorName))
                .findFirst()
                .orElse(null);

        if (processor != null) {
            processor.setParameters(parameters);
            log.debug("设置处理器 {} 的参数: {}", processorName, parameters);
        } else {
            log.warn("未找到处理器: {}", processorName);
        }
    }

    /**
     * 获取所有可用的处理器
     *
     * @return 处理器列表
     */
    public List<PromptProcessor> getAllProcessors() {
        return new ArrayList<>(defaultProcessors);
    }

    /**
     * 启用/禁用指定的处理器
     *
     * @param processorName 处理器名称
     * @param enabled       是否启用
     * @return 处理器配置
     */
    public Map<String, Object> createProcessorEnabledConfig(String processorName, boolean enabled) {
        return Map.of("enabledProcessors",
                defaultProcessors.stream()
                        .map(PromptProcessor::getName)
                        .filter(name -> enabled || !name.equals(processorName))
                        .collect(java.util.stream.Collectors.toList())
        );
    }

    /**
     * Prompt构建结果
     * 包含生成的prompt内容和结构化的段落数据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptBuildResult {
        private String promptContent;
        private List<SegmentModel> segments;
    }

}
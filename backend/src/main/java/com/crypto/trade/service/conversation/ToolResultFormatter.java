package com.crypto.trade.service.conversation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * ToolResultFormatter
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class ToolResultFormatter {

    /**
     * 格式化单个工具执行结果为Markdown表格
     *
     * @param result 工具执行结果
     * @return Markdown格式的工具结果
     */
    public String formatToolResultMarkdown(ToolExecutionResult result) {
        if (result == null) {
            return "## 工具执行结果\n\n执行结果为空";
        }

        StringBuilder sb = new StringBuilder();
        String toolName = result.getToolName();

        // 1. 工具标题
        if (toolName != null) {
            sb.append("## ").append(getToolDisplayName(toolName)).append("\n\n");
        } else {
            sb.append("## 工具执行结果\n\n");
        }

        // 2. 查询参数（从data中提取）
        Object dataObj = result.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = dataObj instanceof Map ? (Map<String, Object>) dataObj : null;
        String dataString = dataObj instanceof String ? (String) dataObj : null;

        if (data != null && !data.isEmpty()) {
            sb.append("### 查询参数\n");
            sb.append("| 项目 | 值 |\n");
            sb.append("|------|-----|\n");

            // 工具名称
            sb.append("| 工具名称 | ").append(toolName != null ? toolName : "N/A").append(" |\n");

            // 提取查询参数
            if (data.containsKey("instId")) {
                sb.append("| 合约名称 | ").append(data.get("instId")).append(" |\n");
            }
            if (data.containsKey("timeframe")) {
                sb.append("| 时间帧 | ").append(data.get("timeframe")).append(" |\n");
            }
            if (data.containsKey("limit")) {
                sb.append("| 采样率 | ").append(data.get("limit")).append(" |\n");
            }

            sb.append("\n");
        } else if (dataString != null && !dataString.isEmpty()) {
            // 如果data是String类型，不显示查询参数
            log.debug("工具结果是String类型，跳过查询参数提取");
        }

        // 3. 执行信息
        sb.append("### 执行信息\n");
        sb.append("| 项目 | 值 |\n");
        sb.append("|------|-----|\n");
        sb.append("| 执行状态 | ").append(result.getSuccess() ? "成功" : "失败").append(" |\n");
        if (result.getProcessingTimeMs() != null) {
            sb.append("| 处理时间 | ").append(result.getProcessingTimeMs()).append("ms |\n");
        }
        sb.append("\n");

        // 4. 执行结果数据
        if (result.getSuccess()) {
            String toolResultData = null;

            // 优先处理Map类型的数据
            if (data != null && !data.isEmpty()) {
                toolResultData = extractToolResultData(toolName, data);
            }
            // 处理String类型的数据
            else if (dataString != null && !dataString.isEmpty()) {
                toolResultData = dataString;
                log.debug("工具结果是String类型，直接显示: {}...", dataString.length() > 50 ? dataString.substring(0, 50) : dataString);
            }

            if (toolResultData != null && !toolResultData.isEmpty()) {
                sb.append("### ").append(getResultDataTitle(toolName)).append("\n\n");
                sb.append(toolResultData);
            }
        }

        // 5. 错误信息
        if (!result.getSuccess() && result.getErrorMessage() != null) {
            sb.append("### 错误信息\n\n");
            sb.append("```\n");
            sb.append(result.getErrorMessage());
            sb.append("\n```\n");
        }

        return sb.toString();
    }

    /**
     * 格式化多个工具执行结果为Markdown表格
     *
     * @param results 工具执行结果列表
     * @return Markdown格式的工具结果
     */
    public String formatToolResultsMarkdown(List<ToolExecutionResult> results) {
        if (results == null || results.isEmpty()) {
            return "## 工具执行结果\n\n无工具执行结果";
        }

        if (results.size() == 1) {
            return formatToolResultMarkdown(results.get(0));
        }

        // 多个工具的情况
        StringBuilder sb = new StringBuilder();
        sb.append("## 工具执行结果\n\n");

        int index = 1;
        for (ToolExecutionResult result : results) {
            sb.append("### ").append(index).append(". ")
                    .append(result.getToolName() != null ? getToolDisplayName(result.getToolName()) : "工具")
                    .append("\n\n");

            // 查询参数
            Object dataObj = result.getData();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = dataObj instanceof Map ? (Map<String, Object>) dataObj : null;
            String dataString = dataObj instanceof String ? (String) dataObj : null;

            if (data != null && !data.isEmpty()) {
                sb.append("#### 查询参数\n");
                sb.append("| 项目 | 值 |\n");
                sb.append("|------|-----|\n");
                sb.append("| 工具名称 | ").append(result.getToolName() != null ? result.getToolName() : "N/A").append(" |\n");

                if (data.containsKey("instId")) {
                    sb.append("| 合约名称 | ").append(data.get("instId")).append(" |\n");
                }
                if (data.containsKey("timeframe")) {
                    sb.append("| 时间帧 | ").append(data.get("timeframe")).append(" |\n");
                }
                if (data.containsKey("limit")) {
                    sb.append("| 采样率 | ").append(data.get("limit")).append(" |\n");
                }
                sb.append("\n");
            } else if (dataString != null && !dataString.isEmpty()) {
                // 如果data是String类型，不显示查询参数
                log.debug("多工具结果中，工具结果是String类型，跳过查询参数提取");
            }

            // 执行信息
            sb.append("#### 执行信息\n");
            sb.append("| 项目 | 值 |\n");
            sb.append("|------|-----|\n");
            sb.append("| 执行状态 | ").append(result.getSuccess() ? "成功" : "失败").append(" |\n");
            if (result.getProcessingTimeMs() != null) {
                sb.append("| 处理时间 | ").append(result.getProcessingTimeMs()).append("ms |\n");
            }
            sb.append("\n");

            // 执行结果数据
            if (result.getSuccess()) {
                String toolResultData = null;

                // 优先处理Map类型的数据
                if (data != null && !data.isEmpty()) {
                    toolResultData = extractToolResultData(result.getToolName(), data);
                }
                // 处理String类型的数据
                else if (dataString != null && !dataString.isEmpty()) {
                    toolResultData = dataString;
                    log.debug("多工具结果中，工具结果是String类型，直接显示: {}...", dataString.length() > 50 ? dataString.substring(0, 50) : dataString);
                }

                if (toolResultData != null && !toolResultData.isEmpty()) {
                    sb.append("#### ").append(getResultDataTitle(result.getToolName())).append("\n\n");
                    sb.append(toolResultData);
                }
            }

            // 错误信息
            if (!result.getSuccess() && result.getErrorMessage() != null) {
                sb.append("#### 错误信息\n\n");
                sb.append("```\n");
                sb.append(result.getErrorMessage());
                sb.append("\n```\n");
            }

            // 添加分隔符（除了最后一个）
            if (index < results.size()) {
                sb.append("\n---\n\n");
            }

            index++;
        }

        return sb.toString();
    }

    /**
     * 提取工具结果数据并格式化为表格
     *
     * @param toolName 工具名称
     * @param data     数据
     * @return Markdown表格
     */
    private String extractToolResultData(String toolName, Map<String, Object> data) {
        if ("k_line".equals(toolName)) {
            return formatKLineData(data);
        }

        // 其他工具类型可以在这里扩展
        return "";
    }

    /**
     * 格式化K线数据为表格
     *
     * @param data 数据
     * @return Markdown表格
     */
    private String formatKLineData(Map<String, Object> data) {
        Object candlesObj = data.get("candles");
        if (candlesObj == null) {
            return "";
        }

        List<Map<String, Object>> candles;
        if (candlesObj instanceof List) {
            candles = (List<Map<String, Object>>) candlesObj;
        } else {
            return "";
        }

        if (candles.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("| 时间 | 开盘价 | 最高价 | 最低价 | 收盘价 | 成交量 |\n");
        sb.append("|------|--------|--------|--------|--------|--------|\n");

        for (Map<String, Object> candle : candles) {
            sb.append("| ");
            sb.append(formatValue(candle.get("ts"))).append(" | ");
            sb.append(formatValue(candle.get("o"))).append(" | ");
            sb.append(formatValue(candle.get("h"))).append(" | ");
            sb.append(formatValue(candle.get("l"))).append(" | ");
            sb.append(formatValue(candle.get("c"))).append(" | ");
            sb.append(formatValue(candle.get("vol"))).append(" |\n");
        }

        return sb.toString();
    }

    /**
     * 格式化数值
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "N/A";
        }
        return value.toString();
    }

    /**
     * 获取工具显示名称
     *
     * @param toolName 工具名称
     * @return 显示名称
     */
    private String getToolDisplayName(String toolName) {
        if (toolName == null) {
            return "未知工具";
        }

        return switch (toolName.toLowerCase()) {
            case "k_line" -> "K线查询工具";
            case "attention" -> "关注标记工具";
            default -> toolName;
        };
    }

    /**
     * 获取结果数据标题
     *
     * @param toolName 工具名称
     * @return 标题
     */
    private String getResultDataTitle(String toolName) {
        if (toolName == null) {
            return "工具数据";
        }

        return switch (toolName.toLowerCase()) {
            case "k_line" -> "K线数据";
            case "attention" -> "关注结果";
            default -> "工具数据";
        };
    }
}
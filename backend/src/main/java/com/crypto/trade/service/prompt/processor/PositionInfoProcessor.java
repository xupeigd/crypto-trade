package com.crypto.trade.service.prompt.processor;

import com.crypto.trade.dto.cex.model.CexAlgoOrder;
import com.crypto.trade.dto.cex.model.CexContractInfo;
import com.crypto.trade.dto.cex.model.CexPosition;
import com.crypto.trade.dto.cex.response.CexAlgoOrderResponse;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.service.UnifiedInstrumentService;
import com.crypto.trade.service.UnifiedTradingService;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.prompt.AbstractPromptProcessor;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.service.prompt.PromptProcessException;
import com.crypto.trade.service.unified.UnifiedPositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PositionInfoProcessor
 * 处理器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PositionInfoProcessor
        extends AbstractPromptProcessor {

    private final UnifiedPositionService unifiedPositionService;
    private final UnifiedInstrumentService unifiedInstrumentService;
    private final UnifiedTradingService unifiedTradingService;
    private final ApiKeyService apiKeyService;

    @Override
    public String getName() {
        return "PositionInfoProcessor";
    }

    @Override
    public int getPriority() {
        return 30; // 中等优先级
    }

    @Override
    public boolean shouldExecute(PromptContext context) {
        // 总是尝试执行，让处理器自己决定是否需要获取数据
        return getBooleanParameter("enablePositionInfo", true);
    }

    @Override
    public SegmentModel process(PromptContext context) throws PromptProcessException {
        return safeProcess(context, () -> {
            // 检查是否启用持仓信息
            boolean enablePositionInfo = getBooleanParameter("enablePositionInfo", true);
            if (!enablePositionInfo) {
                return SegmentModel.builder()
                        .content("")
                        .showTitle(false)
                        .build();
            }

            // 检查是否启用详细持仓信息
            boolean enableDetailedPosition = getBooleanParameter("enableDetailedPosition", true);

            // 获取输出格式配置
            String positionFormat = getStringParameter("positionFormat", "TABLE"); // TEXT, TABLE, BOTH

            // 主动获取持仓数据
            List<CexPosition> positions = getPositionData(context);
            if (!CollectionUtils.isEmpty(positions)) {
                context.setCustomData("positions", positions);
            }

            String content;
            if (positions == null || positions.isEmpty()) {
                content = "\n当前无持仓";
                log.debug("无持仓数据");
            } else if ("TABLE".equals(positionFormat)) {
                content = formatStructuredPositionsAsTable(positions, context);
                log.debug("使用表格格式化持仓信息");
            } else if ("BOTH".equals(positionFormat)) {
                String tableContent = formatStructuredPositionsAsTable(positions, context);
                String textContent = formatStructuredPositionsAsText(positions, context);
                content = textContent + "\n\n" + tableContent;
                log.debug("使用文本+表格格式化持仓信息");
            } else {
                content = formatStructuredPositionsAsText(positions, context);
                log.debug("使用传统文本格式化持仓信息");
            }

            return SegmentModel.builder()
                    .title("当前持仓")
                    .content(content)
                    .category(SegmentModel.Category.DATA)
                    .priority(SegmentModel.Priority.MEDIUM_HIGH)
                    .showTitle(true)
                    .addMetadata("processor", getName())
                    .addMetadata("processorPriority", getPriority())
                    .addMetadata("enablePositionInfo", true)
                    .addMetadata("enableDetailedPosition", enableDetailedPosition)
                    .addMetadata("positionFormat", positionFormat)
                    .addMetadata("positionCount", positions != null ? positions.size() : 0)
                    .addMetadata("dataStatus", positions != null ? "SUCCESS" : "UNAVAILABLE")
                    .build();
        });
    }

    /**
     * 获取持仓数据
     * <p>
     * 使用智能刷新方法，优先从缓存获取，缓存未命中时自动刷新。
     * </p>
     */
    private List<CexPosition> getPositionData(PromptContext context) {
        try {
            log.debug("开始获取持仓数据（智能刷新）");
            // ✅ 优化：使用智能刷新方法，服务层会自动处理缓存逻辑
            List<CexPosition> positions = unifiedPositionService.getLatestPositionData(context.getApiKeyId(), false);
            // 将持仓数据存入context供其他处理器使用
            if (positions != null && !positions.isEmpty()) {
                log.debug("持仓数据获取成功，持仓数量: {}", positions.size());
                context.setCustomData("positions", positions);
                return positions;
            } else {
                log.debug("持仓数据为空");
                return null;
            }

        } catch (Exception e) {
            log.error("获取持仓数据失败", e);
            return null;
        }
    }

    /**
     * 根据合约ID获取合约面值(ctVal)
     * <p>
     * 使用UnifiedInstrumentService获取通用CEX合约信息
     * </p>
     *
     * @param instId 合约ID
     * @return 合约面值，获取失败时返回1
     */
    private BigDecimal getCtVal(String instId) {
        try {
            return unifiedInstrumentService.getCexContractInfo(instId)
                    .map(CexContractInfo::getCtVal)
                    .orElse(BigDecimal.ONE);
        } catch (Exception e) {
            log.warn("获取合约ctVal失败，instId: {}, 使用默认值1, 错误: {}", instId, e.getMessage());
            return BigDecimal.ONE;
        }
    }

    /**
     * 计算持仓时长
     * <p>
     * 基于开仓时间(cTime)和当前时间计算，单位为小时(H)
     * </p>
     *
     * @param position 持仓对象
     * @return 持仓时长字符串，格式如"2.5H"、"10.33H"，异常时返回"未知"
     */
    private String calculatePositionDuration(CexPosition position) {
        try {
            Long createTime = position.getCreateTime();
            if (null != createTime && createTime > 0) {
                long currentTime = System.currentTimeMillis();
                // 防止未来时间
                if (createTime > currentTime) {
                    log.warn("开仓时间在未来，symbol: {}, createTime: {}", position.getSymbol(), createTime);
                    return "未知";
                }

                // 计算时长(毫秒)
                long durationMs = currentTime - createTime;
                // 转换为小时
                double durationHours = durationMs / (1000.0 * 60.0 * 60.0);

                // 格式化:最大精度2位小数
                if (durationHours < 0.01) {
                    return "0.01H以下";
                }

                // 使用BigDecimal确保精度
                BigDecimal hours = BigDecimal.valueOf(durationHours)
                        .setScale(2, RoundingMode.HALF_UP)
                        .stripTrailingZeros();
                return hours.toPlainString() + "H";
            }
            return "未知";
        } catch (Exception e) {
            log.warn("计算持仓时长失败，symbol: {}", position.getSymbol(), e);
            return "未知";
        }
    }

    /**
     * 从持仓对象提取止盈止损价格
     * <p>
     * 策略：
     * 1. 优先使用全仓止盈止损(closeOrderAlgo[0])
     * 2. 如果全仓止盈止损未设置，则从算法订单中查找第一个仓位止盈止损
     * 3. 如果都没有设置，返回"未设置"
     * </p>
     *
     * @param position 持仓对象
     * @param apiKeyId API密钥ID（用于调用策略订单接口）
     * @return 数组：[止盈价格字符串, 止损价格字符串]，未设置时返回"未设置"
     */
    private String[] extractTpSlPrices(CexPosition position, Long apiKeyId) {
        String tpPrice = "未设置";
        String slPrice = "未设置";

        try {
            // 策略1: 优先从closeOrderAlgo获取全仓止盈止损
            List<CexAlgoOrder> closeOrderAlgo = position.getCloseOrderAlgo();
            if (!CollectionUtils.isEmpty(closeOrderAlgo)) {
                CexAlgoOrder algo = closeOrderAlgo.get(0);
                if (algo.getTpTriggerPx() != null && algo.getTpTriggerPx().compareTo(BigDecimal.ZERO) > 0) {
                    tpPrice = formatPositionBigDecimal(algo.getTpTriggerPx());
                }
                if (algo.getSlTriggerPx() != null && algo.getSlTriggerPx().compareTo(BigDecimal.ZERO) > 0) {
                    slPrice = formatPositionBigDecimal(algo.getSlTriggerPx());
                }
                log.debug("从全仓止盈止损提取 - symbol: {}, tp: {}, sl: {}",
                        position.getSymbol(), tpPrice, slPrice);
                return new String[]{tpPrice, slPrice};
            }

            // 策略2: 全仓止盈止损未设置，从算法订单中查找第一个仓位止盈止损
            try {
                // 获取API密钥
                ApiKey apiKey = apiKeyService.getDecryptedKey(apiKeyId);
                if (apiKey == null) {
                    log.debug("API密钥不存在 - apiKeyId: {}", apiKeyId);
                    return new String[]{tpPrice, slPrice};
                }

                // 获取算法订单列表
                CexAlgoOrderResponse algoResponse = unifiedTradingService.getAlgoOrders(apiKey, "SWAP");

                if (algoResponse != null && !CollectionUtils.isEmpty(algoResponse.getAlgoOrders())) {
                    // 按合约ID和仓位方向分组
                    String algKey = position.getSymbol() + ":" + position.getSide().toString().toUpperCase();

                    for (CexAlgoOrder algoOrder : algoResponse.getAlgoOrders()) {
                        String orderKey = algoOrder.getSymbol() + ":" + algoOrder.getPosSide().toUpperCase();

                        // 匹配合约和仓位方向
                        if (algKey.equals(orderKey)) {
                            // 跳过数量为0的订单
                            if (algoOrder.getQuantity() != null && algoOrder.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                                if (algoOrder.getTpTriggerPx() != null && algoOrder.getTpTriggerPx().compareTo(BigDecimal.ZERO) > 0) {
                                    tpPrice = formatPositionBigDecimal(algoOrder.getTpTriggerPx());
                                }
                                if (algoOrder.getSlTriggerPx() != null && algoOrder.getSlTriggerPx().compareTo(BigDecimal.ZERO) > 0) {
                                    slPrice = formatPositionBigDecimal(algoOrder.getSlTriggerPx());
                                }
                                log.debug("从仓位止盈止损提取 - symbol: {}, tp: {}, sl: {}", position.getSymbol(), tpPrice, slPrice);
                                return new String[]{tpPrice, slPrice};
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("获取算法订单失败 - symbol: {}", position.getSymbol(), e);
            }

            log.debug("止盈止损未设置 - symbol: {}", position.getSymbol());
            return new String[]{tpPrice, slPrice};

        } catch (Exception e) {
            log.debug("提取止盈止损价格失败 - symbol: {}", position.getSymbol(), e);
            return new String[]{"未设置", "未设置"};
        }
    }

    /**
     * 将持仓信息格式化为文本形式
     */
    private String formatStructuredPositionsAsText(List<CexPosition> positions, PromptContext context) {
        if (positions == null || positions.isEmpty()) {
            return "当前无持仓";
        }

        StringBuilder textContent = new StringBuilder();
        textContent.append("持仓详情:\n\n");

        Long apiKeyId = context.getApiKeyId();

        for (CexPosition position : positions) {
            // 获取ctVal并计算真实持仓数量
            BigDecimal ctVal = getCtVal(position.getSymbol());
            BigDecimal realQuantity = position.getQuantity().multiply(ctVal);

            // 计算持仓时长
            String duration = calculatePositionDuration(position);

            // 提取止盈止损价格
            String[] tpSl = extractTpSlPrices(position, apiKeyId);

            textContent.append(String.format("%s: %s %s单位, 开仓价 %s, 标记价 %s, 止盈价 %s, 止损价 %s, 杠杆 %s, " +
                            "未实现盈亏 %s USDT, 保证金率 %s, 持仓时长 %s\n",
                    position.getSymbol(),
                    getChinesePositionDirection(position.getSide().name()),
                    formatPositionBigDecimal(realQuantity),
                    formatPositionBigDecimal(position.getAvgPrice()),
                    formatPositionBigDecimal(position.getMarkPrice()),
                    tpSl[0],  // 止盈价
                    tpSl[1],  // 止损价
                    formatLeverage(position.getLeverage()),
                    formatPositionBigDecimal(position.getUnrealizedPnl()),
                    "N/A",  // CexPosition暂无mgnRatio字段
                    duration
            ));
        }

        // 添加汇总信息
        textContent.append("\n持仓汇总:\n");
        String summary = calculateAndFormatSummary(positions, null);
        textContent.append(summary.replaceAll("\\|.*?\\| ?", "")
                .replaceAll("\n+", "\n"));

        return textContent.toString();
    }

    /**
     * 将持仓信息格式化为表格形式
     */
    private String formatPositionAsTable(PromptContext context) {
        try {
            // 首先尝试从customData获取结构化的持仓数据
            List<CexPosition> positions = getPositionsFromContext(context);

            if (positions != null && !positions.isEmpty()) {
                return formatStructuredPositionsAsTable(positions, context);
            }

            // 回退到解析文本格式的持仓信息
            return formatTextPositionsAsTable(context);

        } catch (Exception e) {
            log.error("格式化持仓表格失败", e);
            return "持仓表格格式化失败，显示原始内容：\n" + context.getPositionDetails();
        }
    }

    /**
     * 从context获取结构化持仓数据
     */
    @SuppressWarnings("unchecked")
    private List<CexPosition> getPositionsFromContext(PromptContext context) {
        try {
            Object positionsData = context.getCustomData().get("positions");
            if (positionsData instanceof List) {
                return (List<CexPosition>) positionsData;
            }
        } catch (Exception e) {
            log.debug("从context获取持仓数据失败", e);
        }
        return null;
    }

    /**
     * 使用结构化持仓数据格式化表格
     */
    private String formatStructuredPositionsAsTable(List<CexPosition> positions, PromptContext context) {
        StringBuilder tableContent = new StringBuilder();

        // 添加持仓表格标题
        tableContent.append("### 持仓详情\n\n");

        // 创建持仓表格
        tableContent.append("| 合约 | 方向 | 数量 | 开仓价 | 标记价 | 止盈价 | 止损价 | 杠杆 | 未实现盈亏 | 保证金率 | 持仓时长 |\n");
        tableContent.append("|------|------|------|--------|--------|--------|--------|------|------------|----------|----------|\n");

        // 为每个持仓创建表格行
        Long apiKeyId = context.getApiKeyId();
        for (CexPosition position : positions) {
            String row = formatPositionAsTableRow(position, apiKeyId);
            tableContent.append(row).append("\n");
        }

        // 添加汇总信息表格
        tableContent.append("\n### 持仓汇总\n\n");
        tableContent.append("| 指标 | 数值 |\n");
        tableContent.append("|------|------|\n");

        // 计算汇总信息
        String summaryTable = calculateAndFormatSummary(positions, context);
        tableContent.append(summaryTable);

        return tableContent.toString();
    }

    /**
     * 将单个CexPosition格式化为表格行
     */
    private String formatPositionAsTableRow(CexPosition position, Long apiKeyId) {
        try {
            String contractName = position.getSymbol();
            String direction = getChinesePositionDirection(position.getSide().name());

            // 获取ctVal并计算真实持仓数量
            BigDecimal ctVal = getCtVal(position.getSymbol());
            BigDecimal realQuantity = position.getQuantity().multiply(ctVal);
            String quantity = formatPositionBigDecimal(realQuantity);

            String avgPrice = formatPositionBigDecimal(position.getAvgPrice());
            String markPrice = formatPositionBigDecimal(position.getMarkPrice());

            // 提取止盈止损价格
            String[] tpSl = extractTpSlPrices(position, apiKeyId);

            String leverage = formatLeverage(position.getLeverage());
            String upl = formatPositionBigDecimal(position.getUnrealizedPnl());
            String marginRatio = position.getMarginRatio() != null ?
                    formatPercentage(position.getMarginRatio()) : "N/A";

            // 计算持仓时长
            String duration = calculatePositionDuration(position);

            return String.format("| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |",
                    contractName, direction, quantity, avgPrice, markPrice,
                    tpSl[0],  // 止盈价
                    tpSl[1],  // 止损价
                    leverage, upl, marginRatio, duration);

        } catch (Exception e) {
            log.debug("格式化持仓行失败: {}", position.getSymbol(), e);
            return "| - | - | - | - | - | - | - | - | - | - | - |";
        }
    }

    /**
     * 解析文本格式持仓信息并格式化为表格
     */
    private String formatTextPositionsAsTable(PromptContext context) {
        String positionDetails = context.getPositionDetails();

        // 检查是否为无持仓状态
        if (positionDetails == null || positionDetails.trim().isEmpty() ||
                positionDetails.contains("当前无持仓")) {
            return "当前无持仓";
        }

        StringBuilder tableContent = new StringBuilder();

        // 添加持仓表格标题
        tableContent.append("### 持仓详情\n\n");

        // 创建持仓表格
        tableContent.append("| 合约 | 方向 | 数量 | 开仓价 | 标记价 | 杠杆 | 未实现盈亏 | 保证金率 |\n");
        tableContent.append("|------|------|------|--------|--------|------|------------|----------|\n");

        // 解析持仓详情并构建表格行
        String[] lines = positionDetails.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty() || line.startsWith("===") || line.contains("总持仓数量")) {
                continue; // 跳过空行和汇总信息
            }

            // 尝试解析持仓行
            String positionRow = parsePositionLineToTableRow(line);
            if (positionRow != null) {
                tableContent.append(positionRow).append("\n");
            }
        }

        // 添加汇总信息表格
        tableContent.append("\n\n\n");
        tableContent.append("\n### 持仓汇总\n\n");
        tableContent.append("| 指标 | 数值 |\n");
        tableContent.append("|------|------|\n");

        // 解析汇总信息
        String summaryTable = parseSummaryInfoToTable(positionDetails);
        tableContent.append(summaryTable);

        return tableContent.toString();
    }

    /**
     * 解析持仓行为表格行
     */
    private String parsePositionLineToTableRow(String line) {
        try {
            // 示例格式: BTC-USDT-SWAP: 多头 0.5000单位, 开仓价 65000.0000, 标记价 65100.0000, ...
            String[] parts = line.split(":");
            if (parts.length < 2) return null;

            String contractName = parts[0].trim();
            String details = parts[1];

            // 提取各个字段
            String direction = extractValue(details, "多头|空头");
            String quantity = extractValue(details, "\\d+\\.\\d+单位");
            String avgPrice = extractValue(details, "开仓价 \\d+\\.\\d+");
            String markPrice = extractValue(details, "标记价 \\d+\\.\\d+");
            String leverage = extractValue(details, "杠杆 \\d+\\.\\d+x");
            String upl = extractValue(details, "未实现盈亏[+-]?\\d+\\.\\d+ USDT");
            String marginRatio = extractValue(details, "保证金率 \\d+\\.\\d+%");

            // 清理和格式化数据
            direction = cleanDirection(direction);
            quantity = cleanQuantity(quantity);
            avgPrice = cleanPrice(avgPrice);
            markPrice = cleanPrice(markPrice);
            leverage = cleanLeverage(leverage);
            upl = cleanUpl(upl);
            marginRatio = cleanMarginRatio(marginRatio);

            return String.format("| %s | %s | %s | %s | %s | %s | %s | %s |",
                    contractName, direction, quantity, avgPrice, markPrice, leverage, upl, marginRatio);

        } catch (Exception e) {
            log.debug("解析持仓行失败: {}", line, e);
            return null;
        }
    }

    /**
     * 解析汇总信息为表格
     */
    private String parseSummaryInfoToTable(String positionDetails) {
        StringBuilder summary = new StringBuilder();

        try {
            String[] lines = positionDetails.split("\n");
            for (String line : lines) {
                if (line.contains("总持仓数量")) {
                    String value = extractValue(line, "\\d+");
                    summary.append("| 总持仓数量 | ").append(value).append(" |\n");
                } else if (line.contains("多头")) {
                    // 从总持仓数量行提取多头数量
                    String value = extractValue(line, "多头: \\d+");
                    if (!value.isEmpty()) {
                        summary.append("| 多头数量 | ").append(value.replace("多头: ", "")).append(" |\n");
                    }
                } else if (line.contains("空头")) {
                    // 从总持仓数量行提取空头数量
                    String value = extractValue(line, "空头: \\d+");
                    if (!value.isEmpty()) {
                        summary.append("| 空头数量 | ").append(value.replace("空头: ", "")).append(" |\n");
                    }
                } else if (line.contains("总未实现盈亏")) {
                    String value = extractValue(line, "[+-]?\\d+\\.\\d+ USDT");
                    summary.append("| 总未实现盈亏 | ").append(value).append(" |\n");
                } else if (line.contains("保证金使用率")) {
                    String value = extractValue(line, "\\d+\\.\\d+%");
                    summary.append("| 保证金使用率 | ").append(value).append(" |\n");
                } else if (line.contains("风险等级")) {
                    String value = extractValue(line, "风险等级 \\w+");
                    summary.append("| 风险等级 | ").append(value.replace("风险等级 ", "")).append(" |\n");
                }
            }
        } catch (Exception e) {
            log.error("解析汇总信息失败", e);
            summary.append("| 汇总信息解析失败 | - |\n");
        }

        return summary.toString();
    }

    /**
     * 从文本中提取匹配模式的值
     */
    private String extractValue(String text, String pattern) {
        try {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(text);
            return m.find() ? m.group() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 清理方向信息
     */
    private String cleanDirection(String direction) {
        if (direction.isEmpty()) return "未知";
        return direction.contains("多头") ? "多头" :
                direction.contains("空头") ? "空头" : direction;
    }

    /**
     * 清理数量信息
     */
    private String cleanQuantity(String quantity) {
        if (quantity.isEmpty()) return "0";
        return quantity.replaceAll("[^0-9.]", "");
    }

    /**
     * 清理价格信息
     */
    private String cleanPrice(String price) {
        if (price.isEmpty()) return "0";
        return price.replaceAll("[^0-9.]", "");
    }

    /**
     * 清理杠杆信息
     */
    private String cleanLeverage(String leverage) {
        if (leverage.isEmpty()) return "1x";
        String clean = leverage.replaceAll("[^0-9.]", "");
        return clean.isEmpty() ? "1x" : clean + "x";
    }

    /**
     * 清理盈亏信息
     */
    private String cleanUpl(String upl) {
        if (upl.isEmpty()) return "0";
        return upl.replaceAll("USDT", "").trim();
    }

    /**
     * 清理保证金率信息
     */
    private String cleanMarginRatio(String marginRatio) {
        if (marginRatio.isEmpty()) return "0%";
        return marginRatio.replaceAll("[^0-9.%]", "");
    }

    /**
     * 格式化BigDecimal为字符串（持仓专用）
     */
    private String formatPositionBigDecimal(BigDecimal value) {
        if (value == null) return "0";
        return value.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /**
     * 格式化杠杆信息
     */
    private String formatLeverage(BigDecimal leverage) {
        if (leverage == null) return "1x";
        return leverage.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "x";
    }

    /**
     * 格式化百分比
     */
    private String formatPercentage(BigDecimal value) {
        if (value == null) return "0%";
        return value.multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "%";
    }

    /**
     * 获取中文持仓方向
     */
    private String getChinesePositionDirection(String posSide) {
        if (posSide == null) return "未知";
        switch (posSide.toLowerCase()) {
            case "long":
                return "多头";
            case "short":
                return "空头";
            case "net":
                return "净持仓";
            default:
                return posSide;
        }
    }

    /**
     * 计算并格式化汇总信息
     */
    private String calculateAndFormatSummary(List<CexPosition> positions, PromptContext context) {
        StringBuilder summary = new StringBuilder();

        try {
            int totalPositions = positions.size();
            int longPositions = 0;
            int shortPositions = 0;
            BigDecimal totalUpl = BigDecimal.ZERO;
            BigDecimal totalMargin = BigDecimal.ZERO;
            BigDecimal avgMarginRatio = BigDecimal.ZERO;

            // 统计持仓数据
            for (CexPosition position : positions) {
                if (com.crypto.trade.dto.cex.common.PositionSide.LONG == position.getSide()) {
                    longPositions++;
                } else if (com.crypto.trade.dto.cex.common.PositionSide.SHORT == position.getSide()) {
                    shortPositions++;
                }

                if (position.getUnrealizedPnl() != null) {
                    totalUpl = totalUpl.add(position.getUnrealizedPnl());
                }

                if (position.getMargin() != null) {
                    totalMargin = totalMargin.add(position.getMargin());
                }

                // CexPosition暂无mgnRatio字段,跳过
                // if (position.getMgnRatio() != null) {
                //     avgMarginRatio = avgMarginRatio.add(position.getMgnRatio());
                // }
            }

            // 计算平均保证金率
            if (totalPositions > 0) {
                avgMarginRatio = avgMarginRatio.divide(new BigDecimal(totalPositions), 4, RoundingMode.HALF_UP);
            }

            // 构建汇总表格
            summary.append("| 总持仓数量 | ").append(totalPositions).append(" |\n");
            summary.append("| 多头数量 | ").append(longPositions).append(" |\n");
            summary.append("| 空头数量 | ").append(shortPositions).append(" |\n");
            summary.append("| 总未实现盈亏 | ").append(formatPositionBigDecimal(totalUpl)).append(" USDT |\n");
            summary.append("| 平均保证金率 | ").append(formatPercentage(avgMarginRatio)).append(" |\n");

            // 尝试从原始文本获取保证金使用率和风险等级
            String positionDetails = context.getPositionDetails();
            if (positionDetails != null) {
                String marginUsageRate = extractValue(positionDetails, "保证金使用率 \\d+\\.\\d+%");
                if (!marginUsageRate.isEmpty()) {
                    summary.append("| 保证金使用率 | ").append(marginUsageRate).append(" |\n");
                }

                String riskLevel = extractValue(positionDetails, "风险等级 \\w+");
                if (!riskLevel.isEmpty()) {
                    summary.append("| 风险等级 | ").append(riskLevel.replace("风险等级 ", "")).append(" |\n");
                }
            }

        } catch (Exception e) {
            log.error("计算汇总信息失败", e);
            summary.append("| 汇总信息计算失败 | - |\n");
        }

        return summary.toString();
    }
}
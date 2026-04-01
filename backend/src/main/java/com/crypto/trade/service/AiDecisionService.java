package com.crypto.trade.service;

import com.crypto.trade.config.AiTradingRiskControlConfig;
import com.crypto.trade.dto.AiResponseParseResult;
import com.crypto.trade.dto.AttentionInfo;
import com.crypto.trade.dto.ModelCallResult;
import com.crypto.trade.dto.ModelCallTiming;
import com.crypto.trade.dto.cex.model.CexFundingRate;
import com.crypto.trade.dto.cex.model.CexInstrument;
import com.crypto.trade.dto.cex.model.CexPosition;
import com.crypto.trade.dto.response.BotPromptGenerateResponse;
import com.crypto.trade.dto.response.ReplayDecisionResponse;
import com.crypto.trade.dto.response.ReplaySingleActionResponse;
import com.crypto.trade.entity.*;
import com.crypto.trade.model.PositionModel;
import com.crypto.trade.model.PositionRiskModel;
import com.crypto.trade.model.PositionSummaryModel;
import com.crypto.trade.repository.ConversationActionRepository;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import com.crypto.trade.service.conversation.*;
import com.crypto.trade.service.decision.TradeDecisionEngine;
import com.crypto.trade.service.prompt.PromptBuilder;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.service.unified.UnifiedBalanceService;
import com.crypto.trade.service.unified.UnifiedPositionService;
import com.crypto.trade.util.AiResponseParserUtil;
import com.crypto.trade.util.ClearVisionUtils;
import com.crypto.trade.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * AiDecisionService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class AiDecisionService {

    /**
     * 保存状态控制标志 - 防止重复保存TradeAction
     * ThreadLocal确保线程安全
     */
    private final ThreadLocal<Boolean> hasSavedActions = ThreadLocal.withInitial(() -> false);
    @Autowired
    ChatModel chatModel;
    @Autowired
    UnifiedModelFactory unifiedModelFactory;
    @Autowired
    UnifiedBalanceService unifiedBalanceService;
    @Autowired
    UnifiedPositionService unifiedPositionService;
    @Autowired
    UnifiedCexApiService unifiedCexApiService;
    @Autowired
    KLineQueryExecutor kLineQueryExecutor;
    @Autowired
    ConversationActionRepository conversationActionRepository;
    @Autowired
    AuditLogger auditLogger;
    @Autowired
    AiTradingRiskControlConfig aiTradingRiskControlConfig;
    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    PromptBuilder promptBuilder;
    @Autowired
    LlmCallRecordService llmCallRecordService;
    /**
     * 新的决策引擎 - 用于简化决策逻辑（重构版本）
     */
    @Autowired
    TradeDecisionEngine tradeDecisionEngine;
    /**
     * 多轮对话服务
     */
    @Autowired
    MultiTurnConversationService multiTurnConversationService;
    /**
     * 交易动作处理器
     * 负责执行BUY/SELL决策的实际交易
     */
    @Autowired
    TradeActionProcessor tradeActionProcessor;
    @Autowired
    TradeActionService tradeActionService;
    /**
     * 【新增】告警服务
     */
    @Autowired
    AlertService alertService;
    /**
     * ATTENTION队列服务
     */
    @Autowired
    AttentionQueueService attentionQueueService;

    /**
     * 获取资金费率
     */
    private BigDecimal getFundingRate(ApiKey apiKey, String instId) {
        try {
            List<CexFundingRate> fundingRates = unifiedCexApiService.getFundingRate(apiKey, instId);
            if (null != fundingRates && !fundingRates.isEmpty()) {
                return fundingRates.get(0).getFundingRate();
            }
        } catch (Exception e) {
            log.debug("获取资金费率失败 - instId: {}", instId, e);
        }
        return BigDecimal.ZERO;
    }

    /**
     * 计算当前资金费用
     */
    private BigDecimal calculateCurrentFundingFee(ApiKey apiKey, CexPosition position) {
        try {
            BigDecimal fundingRate = getFundingRate(apiKey, position.getSymbol());
            BigDecimal notionalValue = position.getNotionalValue();
            if (null != fundingRate && null != notionalValue && notionalValue.compareTo(BigDecimal.ZERO) != 0) {
                return fundingRate.multiply(notionalValue);
            }
        } catch (Exception e) {
            log.debug("计算资金费用失败 - symbol: {}", position.getSymbol(), e);
        }
        return BigDecimal.ZERO;
    }

    /**
     * 计算持仓时间(x天x小时xx分钟格式)
     */
    private String calculateHoldingTime(CexPosition position) {
        try {
            Long createTime = position.getCreateTime();
            if (null != createTime && createTime > 0) {
                long currentTime = System.currentTimeMillis();
                // 防止未来时间
                if (createTime > currentTime) {
                    log.warn("开仓时间在未来 - symbol: {}, createTime: {}", position.getSymbol(), createTime);
                    return "0天0小时1分钟";
                }

                // 计算时长(毫秒)
                long durationMs = currentTime - createTime;
                // 转换为分钟
                long minutes = durationMs / (1000 * 60);
                long days = minutes / (60 * 24);
                long hours = (minutes % (60 * 24)) / 60;
                long remainingMinutes = minutes % 60;

                // 至少显示1分钟
                if (days == 0 && hours == 0 && remainingMinutes == 0) {
                    return "0天0小时1分钟";
                }

                return String.format("%d天%d小时%d分钟", days, hours, remainingMinutes);
            }
            return "0天0小时1分钟";
        } catch (Exception e) {
            log.debug("计算持仓时间失败 - symbol: {}", position.getSymbol(), e);
            return "0天0小时1分钟";
        }
    }

    /**
     * 获取最小交易单位
     */
    private BigDecimal getMinTradeSize(String instId, ApiKey apiKey) {
        try {
            // 使用通用方法获取合约信息
            List<CexInstrument> instruments = unifiedCexApiService.getInstruments(apiKey, "SWAP", instId);
            if (null != instruments && !instruments.isEmpty()) {
                CexInstrument instrument = instruments.get(0);
                return instrument.getMinOrderSize();
            }
        } catch (Exception e) {
            log.debug("获取最小交易单位失败 - instId: {}", instId, e);
        }
        return new BigDecimal("1");
    }

    /**
     * 获取持仓详情
     */
    private String getPositionDetailsInternal(Long apiKeyId, List<CexPosition> positions) {
        try {
            // ✅ 优化：如果传入的持仓数据为空，使用智能刷新方法获取仓位数据
            // 服务层会自动处理缓存未命中时的刷新逻辑
            if (CollectionUtils.isEmpty(positions)) {
                positions = unifiedPositionService.getLatestPositionData(apiKeyId, false);
            }
            if (CollectionUtils.isEmpty(positions)) {
                return "当前无持仓";
            }
            ApiKey apiKey = apiKeyService.getDecryptedKey(apiKeyId);
            // 格式化持仓信息
            StringBuilder positionDetails = new StringBuilder();
            for (CexPosition position : positions) {
                String direction = getChinesePositionDirection(position.getSide().name());
                String contractName = position.getSymbol(); // 如 "BTC-USDT-SWAP"
                BigDecimal posSize = position.getQuantity(); // 持仓数量
                BigDecimal avgPrice = position.getAvgPrice(); // 开仓均价
                BigDecimal markPrice = position.getMarkPrice(); // 标记价格
                BigDecimal upl = position.getUnrealizedPnl(); // 未实现盈亏
                BigDecimal leverage = position.getLeverage(); // 杠杆倍数
                BigDecimal marginRatio = position.getMarginRatio() != null ? position.getMarginRatio() : BigDecimal.ZERO; // 保证金率

                // 新增增强信息
                BigDecimal fundingRate = getFundingRate(apiKey, position.getSymbol()); // 资金费率
                BigDecimal currentFundingFee = calculateCurrentFundingFee(apiKey, position); // 当前资金费用
                String holdingTime = calculateHoldingTime(position); // 持仓时间
                BigDecimal minTradeSize = getMinTradeSize(position.getSymbol(), apiKey); // 最小交易单位

                positionDetails.append(String.format("%s: %s %.4f单位, 开仓价 %.4f, 标记价 %.4f, 资金费率 %%%.8f, " +
                                "当前资金费用 %.4f , 杠杆 %.1fx, 持仓时间 %s, 最小交易单位 %.4f张, 未实现盈亏 %.4f USDT, 保证金率 %.4f%%\n",
                        contractName, direction, posSize.doubleValue(),
                        null != avgPrice ? avgPrice.doubleValue() : 0.0,
                        null != markPrice ? markPrice.doubleValue() : 0.0,
                        null != fundingRate ? fundingRate.multiply(new BigDecimal("100")).doubleValue() : 0.0,
                        null != currentFundingFee ? currentFundingFee.doubleValue() : 0.0,
                        null != leverage ? leverage.doubleValue() : 1.0,
                        holdingTime,
                        null != minTradeSize ? minTradeSize.doubleValue() : 1.0,
                        null != upl ? upl.doubleValue() : 0.0,
                        null != marginRatio ? marginRatio.multiply(new BigDecimal("100")).doubleValue() : 0.0));
            }

            // 添加仓位汇总和风险信息
            PositionSummaryModel summary = unifiedPositionService.getPositionSummary(apiKeyId);
            PositionRiskModel risk = unifiedPositionService.getPositionRisk(apiKeyId);

            if (null != summary && null != risk) {
                positionDetails.append(String.format(
                        "\n=== 仓位汇总 ===\n" +
                                "总持仓数量: %d, 多头: %d, 空头: %d\n" +
                                "总未实现盈亏: %.2f USDT\n" +
                                "保证金使用率: %.2f%%\n" +
                                "风险等级: %s (评分: %d)\n" +
                                "风险提示: %s\n",
                        summary.getTotalPositions(),
                        summary.getLongPositions(),
                        summary.getShortPositions(),
                        summary.getTotalUnrealizedPnl() != null ? summary.getTotalUnrealizedPnl().doubleValue() : 0.0,
                        summary.getMarginUsageRate() != null ? summary.getMarginUsageRate().doubleValue() : 0.0,
                        risk.getOverallRiskLevel(),
                        risk.getRiskScore(),
                        risk.getRiskWarning()
                ));
            }
            String result = positionDetails.toString().trim();
            log.debug("成功获取持仓信息 - 活跃持仓数量: {}, apiKeyId: {}", positions.size(), apiKeyId);
            return result;
        } catch (Exception e) {
            log.error("获取持仓详情失败 - apiKeyId: {}", apiKeyId, e);
            return "持仓信息获取失败: " + e.getMessage();
        }
    }

    /**
     * 将持仓方向转换为中文显示
     */
    private String getChinesePositionDirection(String posSide) {
        if (null == posSide) {
            return "未知";
        }
        return switch (posSide.toLowerCase()) {
            case "long" -> "多头";
            case "short" -> "空头";
            case "net" -> "净持仓";
            default -> posSide;
        };
    }

    /**
     * 配置处理器的参数
     */
    private void configureProcessorParameters() {
        // 配置统计信息处理器
//        promptBuilder.setProcessorParameters("StatsInfoProcessor",
//                Map.of("enableStatsInfo", true, "statsType", "HORIZONTAL"));

        // 配置持仓价格数据处理器
//        promptBuilder.setProcessorParameters("PositionPriceProcessor",
//                Map.of("enablePositionPrice", true, "timeframes", "4H,1H,5m", "dataCounts", "{\"5m\": 24, \"1H\": 24, \"4H\": 30}"));

        // 配置账户信息处理器
        promptBuilder.setProcessorParameters("AccountInfoProcessor",
                Map.of("enableAccountInfo", true, "outputFormat", "TABLE"));

        // 配置持仓信息处理器
        promptBuilder.setProcessorParameters("PositionInfoProcessor",
                Map.of("enablePositionInfo", true));

        // 配置技术指标处理器
        promptBuilder.setProcessorParameters("TechnicalIndicatorProcessor",
                Map.of(
                        "timeframes", "4H,1H,5m",           // 三个时间周期
                        "dataCount", "30",                   // 每个周期10条数据
                        "rsiPeriods", "5,20,30",            // RSI三个周期
                        "emaPeriods", "5,20,30",            // EMA三个周期
                        "bollPeriods", "20",                  // BOLL周期
                        "enableTechnicalIndicators", true,
                        "outputFormat", "USER_TABLE"    // 用户表格格式
                ));

        // 配置待成交订单处理器
        promptBuilder.setProcessorParameters("PendingOrdersPromptProcessor",
                Map.of("enablePendingOrders", true));

        // 配置市场数据处理器
        promptBuilder.setProcessorParameters("MarketDataProcessor",
                Map.of("marketDataRange", "Top15", "enableMarketData", true));

        // 配置仓位历史处理器
        promptBuilder.setProcessorParameters("PositionHistoryProcessor",
                Map.of("enablePositionHistory", true));

        // 配置订单历史处理器
        promptBuilder.setProcessorParameters("OrderHistoryProcessor",
                Map.of("enableOrderHistory", true));

        // 配置策略建议处理器
        promptBuilder.setProcessorParameters("StrategySuggestionProcessor",
                Map.of("enableStrategySuggestion", true, "strategyType", "momentum"));

        // 配置交易规则处理器
        promptBuilder.setProcessorParameters("TradeRulePromptProcessor",
                Map.of());

        // 配置思考模式处理器
        promptBuilder.setProcessorParameters("ThinkingModeProcessor",
                Map.of("enableThinkingMode", aiTradingRiskControlConfig.isThinkingModeEnabled(),
                        "thinkingSteps", 6));

        // 配置决策要求处理器
        promptBuilder.setProcessorParameters("DecisionRequirementProcessor",
                Map.of("outputFormat", "JSON", "includeToolUsage", true));
    }

    /**
     * 判断当前是否为定时任务调用
     * <p>
     * 通过检查调用栈中是否有 AutomaticTradeService 的 runAutomaticTrade 方法
     * 来判断当前调用是否来自定时任务。
     * </p>
     *
     * @return true表示来自定时任务, false表示来自手动触发或API调用
     */
    private boolean isScheduledTaskCall() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            String methodName = element.getMethodName();

            // 检查是否为 AutomaticTradeService.runAutomaticTrade 方法调用
            if (className.contains("AutomaticTradeService") && "runAutomaticTrade".equals(methodName)) {
                log.debug("检测到定时任务调用栈 - className: {}, methodName: {}", className, methodName);
                return true;
            }
        }
        log.debug("未检测到定时任务调用栈");
        return false;
    }

    /**
     * [重构] 递归处理对话(统一单轮和多轮)
     * <p>
     * 核心设计原则:
     * 1. 递归而非循环 - 每轮对话都是一次递归调用
     * 2. 响应立即入库 - AI响应一旦返回就立即保存到独立事务
     * 3. 延迟创建TradeDecision - 只有最终决策才创建TradeDecision实体
     * 4. 无事务依赖 - 不使用@Transactional,每个操作独立提交
     * </p>
     *
     * @param context 对话上下文(包含所有必要状态)
     * @return RoundResult 本轮处理结果
     */
    private RoundResult processConversationRecursive(ConversationContext context) {
        log.info("【递归】第{}轮对话开始 - sessionId: {}, parentId: {}",
                context.getCurrentRound(), context.getSessionId(), context.getCurrentParentId());

        long startTime = System.currentTimeMillis();
        int currentRound = context.getCurrentRound();

        // 【新增】单轮对话超时保护：3分钟超时限制
        final long SINGLE_ROUND_TIMEOUT_MS = 3 * 60 * 1000L;  // 3分钟

        try {
            // 1. 安全检查 - 防止无限递归
            if (currentRound > MultiTurnConversationService.MAX_ROUNDS) {
                log.warn("【递归】达到最大轮次限制 - 轮次: {}, 终止对话", currentRound);
                llmCallRecordService.markConversationAsTerminated(context.getSessionId(), "达到最大轮次限制");
                return RoundResult.termination("达到最大轮次限制");
            }

            // 【新增】超时检查：检查是否已超时
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > SINGLE_ROUND_TIMEOUT_MS) {
                log.error("【递归超时】第{}轮处理超时 - 已耗时: {}ms, 超过限制: {}ms, sessionId: {}",
                        currentRound, elapsed, SINGLE_ROUND_TIMEOUT_MS, context.getSessionId());

                // 【新增】发送超时告警
                try {
                    alertService.alertTaskTimeout(context.getSessionId().toString(), currentRound, elapsed, SINGLE_ROUND_TIMEOUT_MS,
                            "开始检查");
                } catch (Exception alertEx) {
                    log.error("发送超时告警失败", alertEx);
                }

                llmCallRecordService.markAllProcessingAsTerminated(context.getSessionId(),
                        String.format("第%d轮处理超时(%dms)", currentRound, elapsed));
                return RoundResult.termination("处理超时");
            }

            // 2. 准备本轮prompt
            String currentPrompt;
            if (currentRound == 1) {
                // 第1轮: prompt已在调用处创建Record时保存,这里只需要验证
                // 第1轮的prompt来自初始用户请求,已在generateTradeDecision的239行保存
                currentPrompt = "第1轮prompt已在调用处保存";
                log.debug("【递归】第1轮使用已有prompt和response");
            } else {
                // 第2轮及以后: 基于工具执行结果构建新prompt
                currentPrompt = multiTurnConversationService.buildNextTurnPrompt(
                        context.getSessionId(),
                        context.getCurrentAiResponse(),
                        context.getAllToolResults(),
                        currentRound
                );
                log.debug("【递归】第{}轮构建完成prompt - 长度: {}", currentRound, currentPrompt.length());
            }

            // 3. 调用AI模型(第1轮跳过,因为AI响应已在调用处获取)
            String aiResponse;
            long processingTime;
            Long currentRecordId;
            LocalDateTime callStartTime = null; // 提升到if-else块外，以便后续使用

            if (currentRound == 1) {
                // 第1轮: AI响应已在调用处获取并保存
                aiResponse = context.getCurrentAiResponse();
                processingTime = context.getTotalProcessingTime();
                currentRecordId = context.getFirstCallRecordId();
                log.info("【递归】第1轮使用已有AI响应 - recordId: {}, response长度: {}",
                        currentRecordId, aiResponse.length());
            } else {
                // 第2轮及以后: 调用AI获取新响应
                try {
                    // 记录AI调用开始时间
                    callStartTime = LocalDateTime.now();
                    long aiStartTime = System.currentTimeMillis();
                    aiResponse = chatModel.call(currentPrompt);
                    processingTime = System.currentTimeMillis() - aiStartTime;

                    log.info("【递归】第{}轮AI调用完成 - 耗时: {}ms, response长度: {}",
                            currentRound, processingTime, aiResponse.length());

                    // 获取父记录的调用来源,多轮对话后续轮次继承第一轮的来源类型
                    String parentCallSource = llmCallRecordService.getCallSourceById(context.getCurrentParentId());

                    // AI调用成功后立即创建Record
                    LlmCallRecord newRecord = llmCallRecordService.createCallRecord(
                            context.getApiKeyId(),
                            context.getModelName(),
                            context.getSessionId(),
                            context.getCurrentParentId(),
                            currentRound,
                            parentCallSource,
                            null,  // userMessageId（稍后设置）
                            null   // assistantMessageId（稍后设置）
                    );
                    currentRecordId = newRecord.getId();
                    log.info("【递归】第{}轮Record已创建 - recordId: {}", currentRound, currentRecordId);

                } catch (Exception e) {
                    log.error("【递归】第{}轮AI调用失败", currentRound, e);

                    // AI调用失败,标记会话终止并返回错误
                    llmCallRecordService.markAllProcessingAsTerminated(
                            context.getSessionId(),
                            String.format("第%d轮AI调用失败: %s", currentRound, e.getMessage())
                    );

                    // 返回终止结果
                    return RoundResult.termination("AI调用失败: " + e.getMessage());
                }
            }

            // 【新增】AI调用后的超时检查
            elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > SINGLE_ROUND_TIMEOUT_MS) {
                log.error("【递归超时】第{}轮AI调用后超时 - 已耗时: {}ms, 超过限制: {}ms, sessionId: {}", currentRound, elapsed,
                        SINGLE_ROUND_TIMEOUT_MS, context.getSessionId());

                // 【新增】发送超时告警
                try {
                    alertService.alertTaskTimeout(context.getSessionId().toString(), currentRound,
                            elapsed,
                            SINGLE_ROUND_TIMEOUT_MS,
                            "AI调用后"
                    );
                } catch (Exception alertEx) {
                    log.error("发送超时告警失败", alertEx);
                }

                llmCallRecordService.markAllProcessingAsTerminated(
                        context.getSessionId(),
                        String.format("第%d轮AI调用后超时(%dms)", currentRound, elapsed)
                );
                return RoundResult.termination("AI调用后超时");
            }

            // 4. 解析AI响应
            ActionParser.ActionPack originalActionPack;
            ActionParser.ActionPack actionPack = null; // 在try块外声明,以便后续使用
            boolean responseModified = false; // 在try块外声明,以便后续使用
            try {
                originalActionPack = ActionParser.parseActionPack(
                        AiResponseParserUtil.parseAiResponse(aiResponse, false).getJsonContent());

                // [新增] 使用ClearVisionUtils清除幻觉
                // 获取当前持仓信息
                List<CexPosition> currentCexPositions = unifiedPositionService.getLatestPositionData(context.getApiKeyId(), false);
                List<PositionModel> currentPositions = currentCexPositions.stream()
                        .map(cexPos -> {
                            PositionModel model = new PositionModel();
                            model.setInstId(cexPos.getSymbol());
                            model.setPosSide(cexPos.getSide().name());
                            return model;
                        })
                        .toList();

                actionPack = ClearVisionUtils.clearingIllusions(originalActionPack, currentPositions);

                // [新增] 比较 ClearVisionUtils 处理前后的 ActionPack
                responseModified = !Objects.equals(originalActionPack, actionPack);
                log.info("【递归】第{}轮ClearVisionUtils处理完成 - recordId: {}, 原始动作数: {}, 处理后动作数: {}, responseModified: {}",
                        currentRound, currentRecordId,
                        originalActionPack != null ? originalActionPack.getActions().size() : 0,
                        actionPack != null ? actionPack.getActions().size() : 0,
                        responseModified);

                // [重构] 保存处理后的TradeAction，获得含id的TradeAction列表
                List<TradeAction> savedTradeActions = null;
                if (actionPack != null) {
                    savedTradeActions = tradeActionService.saveTradeActions(currentRecordId, context.getApiKeyId(), context.getModelName(), actionPack);
                    log.info("【递归】第{}轮TradeAction保存完成，获得id - recordId: {}, actionCount: {}",
                            currentRound, currentRecordId, savedTradeActions != null ? savedTradeActions.size() : 0);
                }

                // [重构] 将含id的TradeAction转换为新的ActionPack，并替换aiResponse
                if (savedTradeActions != null && !savedTradeActions.isEmpty()) {
                    // 将含id的TradeAction转换为ParsedAction
                    List<ActionParser.ParsedAction> actionsWithIds = savedTradeActions.stream()
                            .map(ActionParser::convertTradeActionToParsedAction)
                            .collect(Collectors.toList());

                    // 创建新的ActionPack（含id）
                    ActionParser.ActionPack actionPackWithIds = ActionParser.ActionPack.builder()
                            .actions(actionsWithIds)
                            .build();

                    // 提取原始JSON和新的JSON
                    String originalJson = AiResponseParserUtil.parseAiResponse(aiResponse, false).getJsonContent();
                    String processedJson = JsonUtils.toJsonString(actionPackWithIds);

                    // 替换aiResponse中的actions部分
                    aiResponse = aiResponse.replace(originalJson, processedJson);

                    // 更新actionPack引用，后续使用含id的版本
                    actionPack = actionPackWithIds;

                    log.info("【递归】第{}轮aiResponse已更新为含id的版本 - recordId: {}, 原始长度: {}, 新长度: {}",
                            currentRound, currentRecordId, originalJson.length(), processedJson.length());
                }
            } catch (Exception e) {
                log.error("【递归】第{}轮响应解析失败 - recordId: {}", currentRound, currentRecordId, e);

                // 解析失败,响应已保存,终止对话
                llmCallRecordService.markAllProcessingAsTerminated(
                        context.getSessionId(),
                        String.format("第%d轮响应解析失败: %s", currentRound, e.getMessage())
                );

                return RoundResult.termination("响应解析失败: " + e.getMessage());
            }

            // 【修复】在第2轮及以后，ID回填完成后保存aiResponse到数据库
            if (currentRound > 1) {
                try {
                    // 保存含id的aiResponse到数据库
                    llmCallRecordService.updateCallRecordSuccessInNewTransaction(
                            currentRecordId,
                            responseModified ? aiResponse : null,  // 处理后的响应（仅当有变化时）
                            null, null, null, null, null,
                            callStartTime,  // 使用AI调用时记录的开始时间
                            null, null, null,  // 耗时（已在之前设置）
                            null,  // userMessageId（稍后设置）
                            null   // assistantMessageId（稍后设置）
                    );
                    log.info("【递归】第{}轮AI响应已保存（含id） - recordId: {}", currentRound, currentRecordId);
                } catch (Exception e) {
                    log.error("【递归】第{}轮保存AI响应失败 - recordId: {}", currentRound, currentRecordId, e);
                    // 保存失败不影响继续，因为aiResponse已在内存中更新
                }
            }

            // 5. 判断是否需要继续(是否有工具调用)
            if (!ActionParser.containsToolCall(actionPack)) {
                log.info("【递归】第{}轮无工具调用,对话终止 - recordId: {}", currentRound, currentRecordId);

                // 标记会话完成
                llmCallRecordService.markConversationAsCompleted(context.getSessionId());

                // 返回最终决策
                return RoundResult.termination(aiResponse);
            }

            // 6. 执行工具调用
            List<ActionParser.ParsedAction> actions = actionPack.getActions();
            List<ToolExecutionResult> toolResults = new ArrayList<>();

            log.info("【递归】第{}轮执行{}个工具调用", currentRound, actions.size());

            for (ActionParser.ParsedAction action : actions) {
                ToolExecutionResult result;
                try {
                    result = executeTool(action, context.getSessionId().toString(), context.getApiKeyId());
                } catch (Exception e) {
                    log.error("【递归】第{}轮工具执行异常 - actionType: {}, error: {}",
                            currentRound, action.getAction(), e.getMessage(), e);
                    result = ToolExecutionResult.failure("工具执行异常: " + e.getMessage());
                }

                // 立即保存工具执行结果
                toolResults.add(result);
                saveToolAction(context.getSessionId().toString(), action, result);

                if (!result.getSuccess()) {
                    log.warn("【递归】第{}轮工具执行失败 - actionType: {}, error: {}",
                            currentRound, action.getAction(), result.getErrorMessage());
                }
            }

            log.info("【递归】第{}轮工具执行完成 - 成功: {}/{}",
                    currentRound,
                    toolResults.stream().mapToLong(r -> r.getSuccess() ? 1 : 0).sum(),
                    toolResults.size());

            // 【新增】智能终止机制：检测连续相同的QUERY工具调用
            if (actions.size() == 1 && ActionParser.ActionType.QUERY == actions.get(0).getAction()) {
                ActionParser.ParsedAction queryAction = actions.get(0);
                // 生成QUERY签名：instId_timeframe_limit
                String querySignature = String.format("%s_%s_%s",
                        queryAction.getInstId(),
                        queryAction.getTimeframe() != null ? queryAction.getTimeframe() : "1H",
                        queryAction.getLimit() != null ? queryAction.getLimit() : 100);

                String lastSignature = context.getLastQuerySignature();
                if (querySignature.equals(lastSignature)) {
                    // 连续相同的QUERY
                    int consecutiveCount = context.getConsecutiveSameQueryCount() + 1;
                    context.setConsecutiveSameQueryCount(consecutiveCount);

                    log.warn("【智能终止检测】第{}轮检测到连续相同QUERY - signature: {}, 连续次数: {}",
                            currentRound, querySignature, consecutiveCount);

                    if (consecutiveCount >= 3) {
                        log.error("【智能终止】连续3次相同QUERY，终止对话 - signature: {}, sessionId: {}",
                                querySignature, context.getSessionId());

                        // 【新增】发送智能终止告警
                        try {
                            alertService.alertSmartTermination(
                                    context.getSessionId().toString(),
                                    querySignature,
                                    consecutiveCount
                            );
                        } catch (Exception alertEx) {
                            log.error("发送智能终止告警失败", alertEx);
                        }

                        llmCallRecordService.markAllProcessingAsTerminated(
                                context.getSessionId(),
                                String.format("连续%d次相同QUERY工具调用(%s)", consecutiveCount, querySignature)
                        );
                        return RoundResult.termination("连续相同QUERY导致终止");
                    }
                } else {
                    // 不同的QUERY，重置计数
                    context.setLastQuerySignature(querySignature);
                    context.setConsecutiveSameQueryCount(1);
                    log.debug("【智能终止检测】第{}轮新的QUERY - signature: {}", currentRound, querySignature);
                }
            } else {
                // 非QUERY工具，重置计数
                context.setLastQuerySignature(null);
                context.setConsecutiveSameQueryCount(0);
            }

            // 【新增】工具执行后的超时检查
            elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > SINGLE_ROUND_TIMEOUT_MS) {
                log.error("【递归超时】第{}轮工具执行后超时 - 已耗时: {}ms, 超过限制: {}ms, sessionId: {}",
                        currentRound, elapsed, SINGLE_ROUND_TIMEOUT_MS, context.getSessionId());

                // 【新增】发送超时告警
                try {
                    alertService.alertTaskTimeout(
                            context.getSessionId().toString(),
                            currentRound,
                            elapsed,
                            SINGLE_ROUND_TIMEOUT_MS,
                            "工具执行后"
                    );
                } catch (Exception alertEx) {
                    log.error("发送超时告警失败", alertEx);
                }

                llmCallRecordService.markAllProcessingAsTerminated(
                        context.getSessionId(),
                        String.format("第%d轮工具执行后超时(%dms)", currentRound, elapsed)
                );
                return RoundResult.termination("工具执行后超时");
            }

            // 7. 准备递归调用(下一轮)
            ConversationContext nextContext = context.updateForNextRound(
                    aiResponse,
                    toolResults,
                    currentRecordId
            );
            nextContext.setTotalProcessingTime(context.getTotalProcessingTime() + processingTime);

            // 8. 递归调用下一轮
            log.info("【递归】第{}轮完成,准备进入第{}轮", currentRound, currentRound + 1);
            return processConversationRecursive(nextContext);

        } catch (Exception e) {
            log.error("【递归】第{}轮处理异常 - sessionId: {}", currentRound, context.getSessionId(), e);

            // 标记所有PROCESSING记录为TERMINATED
            llmCallRecordService.markAllProcessingAsTerminated(
                    context.getSessionId(),
                    String.format("第%d轮异常: %s", currentRound, e.getMessage())
            );

            return RoundResult.termination("处理异常: " + e.getMessage());
        }
    }

    /**
     * [新增] 公开：执行QUERY工具（供AsyncTradingTaskService调用）
     */
    public ToolExecutionResult executeQueryTool(KLineParameters params, Long apiKeyId) {
        try {
            log.info("执行QUERY工具 - instId: {}, timeframe: {}, limit: {}",
                    params.getInstId(), params.getTimeframe(), params.getLimit());
            return kLineQueryExecutor.execute(params, apiKeyId);
        } catch (Exception e) {
            log.error("QUERY工具执行失败 - instId: {}", params.getInstId(), e);
            return ToolExecutionResult.failure("工具执行失败: " + e.getMessage());
        }
    }

    /**
     * 执行工具调用
     */
    public ToolExecutionResult executeTool(ActionParser.ParsedAction action, String decisionId, Long apiKeyId) {
        try {
            if (ActionParser.ActionType.QUERY == action.getAction()) {
                // 构造KLineParameters
                KLineParameters params = new KLineParameters();
                params.setInstId(action.getInstId());
                params.setTimeframe(action.getTimeframe() != null ? action.getTimeframe() : "1H");
                params.setLimit(action.getLimit() != null ? action.getLimit() : 100);

                log.info("执行QUERY工具 - instId: {}, timeframe: {}, limit: {}",
                        params.getInstId(), params.getTimeframe(), params.getLimit());

                // 执行K线查询
                return kLineQueryExecutor.execute(params, apiKeyId);
            }

            log.warn("不支持的工具类型: {}", action.getAction());
            return ToolExecutionResult.failure("不支持的工具类型: " + action.getAction());

        } catch (Exception e) {
            log.error("工具执行失败 - action: {}, instId: {}", action.getAction(), action.getInstId(), e);
            return ToolExecutionResult.failure("工具执行失败: " + e.getMessage());
        }
    }

    /**
     * [公开] 保存工具调用记录
     * 确保每个工具执行结果都立即保存到数据库
     */
    public void saveToolAction(String decisionId, ActionParser.ParsedAction action, ToolExecutionResult result) {
        try {
            ConversationAction conversationAction = new ConversationAction();
            conversationAction.setSessionId(decisionId);
            conversationAction.setMessageId(UUID.randomUUID().toString());
            conversationAction.setDecisionId(decisionId);
            conversationAction.setActionType(String.valueOf(action.getAction()));
//            conversationAction.setActionParameters(objectMapper.writeValueAsString(action.getParameters()));

            if (result.getSuccess()) {
                conversationAction.setActionResult(JsonUtils.toJsonString(result.getData()));
                conversationAction.setStatus("SUCCESS");
                conversationAction.setProcessingTimeMs(result.getProcessingTimeMs());
            } else {
                conversationAction.setErrorMessage(result.getErrorMessage());
                conversationAction.setStatus("FAILED");
                conversationAction.setProcessingTimeMs(result.getProcessingTimeMs());
            }

            ConversationAction saved = conversationActionRepository.save(conversationAction);
            log.debug("工具调用记录已保存 - actionType: {}, status: {}, recordId: {}",
                    action.getAction(), result.getSuccess() ? "SUCCESS" : "FAILED", saved.getId());

        } catch (Exception e) {
            // 保存失败是严重问题,应该抛出异常让上层处理
            log.error("保存工具调用记录失败 - decisionId: {}, actionType: {}",
                    decisionId, action.getAction(), e);
            throw new RuntimeException("保存工具调用记录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析并保存动作
     *
     * @param aiResponse  AI响应
     * @param apiKeyId    API密钥ID
     * @param callStatsId 调用统计ID (LlmCallRecord ID)
     * @return 解析后的动作包
     */
    private ActionParser.ActionPack parseAndSaveActions(String aiResponse, Long apiKeyId, Long callStatsId) {
        try {
            // 1. 解析AI响应
            AiResponseParseResult parseResult = AiResponseParserUtil.parseAiResponse(aiResponse, false);

            // 2. 提取JSON并解析ActionPack
            String jsonContent = parseResult.getJsonContentOrEmpty();
            if (!parseResult.isHasJson() || !parseResult.isValidJson()) {
                log.warn("AI返回非JSON响应 - callStatsId: {}", callStatsId);
                return null;
            }

            ActionParser.ActionPack actionPack = ActionParser.parseActionPack(jsonContent);

            // 3. 保存ActionPack (如果需要)
            if (actionPack != null && callStatsId != null) {
                // 尝试获取record以获取modelName
                LlmCallRecord record = llmCallRecordService.getRecordById(callStatsId).orElse(null);
                String modelName = (record != null) ? record.getModelName() : "unknown";

                tradeActionService.saveTradeActions(callStatsId, apiKeyId, modelName, actionPack);
            }
            return actionPack;
        } catch (Exception e) {
            log.error("解析并保存动作失败", e);
            return null;
        }
    }


    /**
     * [新增] 检测AI响应是否包含工具调用
     * <p>
     * 供AsyncTradingTaskService等外部服务使用,统一判断是否需要进入多轮对话流程。
     * </p>
     *
     * @param aiResponse AI响应内容
     * @return true表示包含工具调用, 需要多轮对话
     */
    public boolean hasToolCall(AiResponseParseResult aiResponse) {
        if (null == aiResponse) {
            return false;
        }
        return ActionParser.containsToolCall(aiResponse);
    }


    /**
     * 仅生成prompt,不调用AI模型
     * 用于前端预览和编辑功能
     *
     * @param apiKeyId   API Key ID
     * @param attentions 预传递的ATTENTION信息列表（可选，用于Attention触发时避免从数据库查询不到数据）
     */
    public BotPromptGenerateResponse generatePromptOnly(Long apiKeyId, List<AttentionInfo> attentions) {
        try {
            log.debug("开始生成prompt - apiKeyId: {}", apiKeyId);

            // 1. 获取账户状态数据
//            AccountDetailModel accountData = unifiedBalanceService.getAccountUsdtDetail(apiKeyId);

            // 2. 获取持仓数据
//            List<OkxPosition> positions = unifiedPositionService.getLatestPositionData(apiKeyId);
//            String positionDetails = getPositionDetailsInternal(apiKeyId, positions);

            // 3. 构建简化的Prompt上下文 - 处理器会主动获取市场数据和技术指标
            PromptContext context = PromptContext.builder()
                    .apiKeyId(apiKeyId)
                    .thinkingModeEnabled(aiTradingRiskControlConfig.isThinkingModeEnabled())
                    .build();

            // 添加持仓数据到自定义数据供处理器使用
//            if (positions != null && !positions.isEmpty()) {
//                context.setCustomData("positions", positions);
//            }

            // 【ATTENTION】处理ATTENTION信息
            // 优先使用预传递的attentions（从request中获取），否则从数据库查询
            List<AttentionInfo> attentionInfoList = attentions;
            if (attentionInfoList == null || attentionInfoList.isEmpty()) {
                // 从数据库查询（原有逻辑）
                List<AttentionQueue> pendingAttentions = attentionQueueService.getPendingAttentionsByApiKeyId(apiKeyId);
                if (pendingAttentions != null && !pendingAttentions.isEmpty()) {
                    log.info("【ATTENTION】从数据库检测到待处理的ATTENTION - apiKeyId: {}, count: {}", apiKeyId, pendingAttentions.size());
                    attentionInfoList = pendingAttentions.stream()
                            .map(a -> AttentionInfo.builder()
                                    .queueId(a.getId())
                                    .instId(a.getInstId())
                                    .priority(a.getPriority())
                                    .timeframe(a.getTimeframe())
                                    .limit(a.getQueryLimit())
                                    .expectedTriggerTime(a.getExpectedTriggerTime())
                                    .build())
                            .collect(Collectors.toList());
                }
            } else {
                log.info("【ATTENTION】使用预传递的ATTENTION信息 - apiKeyId: {}, count: {}", apiKeyId, attentionInfoList.size());
            }

            // 设置到context中
            if (attentionInfoList != null && !attentionInfoList.isEmpty()) {
                log.info("【ATTENTION】设置到context前 - attentionInfoList: {}", attentionInfoList);
                context.setCustomData("attentions", attentionInfoList);
                List<AttentionInfo> afterSet = context.getCustomData("attentions");
                log.info("【ATTENTION】设置到context后 - attentions: {}", afterSet);
            }

            // 4. 配置处理器参数
            configureProcessorParameters();

            // 5. 判断是否为定时任务调用
            boolean isFromScheduledTask = isScheduledTaskCall();
            String taskIdPrefix = isFromScheduledTask ? "SCHEDULED_" : "PROMPT_";
            String taskId = taskIdPrefix + System.currentTimeMillis();

            if (isFromScheduledTask) {
                log.info("检测到定时任务调用 - 将使用 SCHEDULED_ 前缀 - taskId: {}", taskId);
            }

            // 6. 使用PromptBuilder构建响应(包含segments结构化数据)
            BotPromptGenerateResponse response = promptBuilder.buildPromptWithResponse(context, taskId);

            // 7. 格式化账户数据用于显示
//            String accountDataStr = String.format(
//                    "总权益: %s USDT | 可用余额: %s USDT | 已用保证金: %s USDT | 未实现盈亏: %s USDT | 保证金率: %s%%",
//                    formatBigDecimal(accountData.getTotalEquity()),
//                    formatBigDecimal(accountData.getAvailableBalance()),
//                    formatBigDecimal(accountData.getUsedMargin()),
//                    formatBigDecimal(accountData.getUnrealizedPnl()),
//                    formatBigDecimal(accountData.getMarginRatio().multiply(new BigDecimal("100")))
//            );

            log.debug("Prompt生成完成 - apiKeyId: {}, taskId: {}, estimatedTokens: {}",
                    apiKeyId, response.getTaskId(), response.getEstimatedTokens());
            return response;
        } catch (Exception e) {
            log.error("生成prompt失败 - apiKeyId: {}", apiKeyId, e);

            return BotPromptGenerateResponse.builder()
                    .taskId("PROMPT_" + System.currentTimeMillis())
                    .apiKeyId(apiKeyId)
                    .status("FAILED")
                    .message("Prompt生成失败: " + e.getMessage())
                    .generateTime(System.currentTimeMillis())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 仅生成prompt,不调用AI模型（重载方法，保持向后兼容）
     * 用于前端预览和编辑功能
     */
    public BotPromptGenerateResponse generatePromptOnly(Long apiKeyId) {
        return generatePromptOnly(apiKeyId, null);
    }

    /**
     * 解析AI响应（公共方法，供异步服务使用）
     *
     * @param aiResponse  AI响应
     * @param apiKeyId    API密钥ID
     * @param callStatsId 调用统计ID
     * @return 解析后的动作包
     */
    @Transactional
    public ActionParser.ActionPack parseAndSaveActionsPublic(String aiResponse, Long apiKeyId, Long callStatsId) {
        return parseAndSaveActions(aiResponse, apiKeyId, callStatsId);
    }

    /**
     * 获取持仓详情（公共方法，供异步服务使用）
     *
     * @param apiKeyId API密钥ID
     * @return 持仓详情字符串
     */
    public String getPositionDetails(Long apiKeyId) {
        return getPositionDetailsInternal(apiKeyId, null);
    }

    /**
     * [重构] 使用UnifiedModelFactory调用AI模型（不需要LlmCallStats）
     * 用于异步交易任务等场景
     *
     * @param prompt    提示词
     * @param modelName 模型名称
     * @return AI响应
     */
    public String callAiModelWithUnifiedFactory(String prompt, String modelName) {
        try {
            // 如果没有指定模型名称，使用默认模型
            String actualModelName = (modelName != null && !modelName.trim().isEmpty()) ? modelName : "deepseek-r1:14b";
            log.debug("调用AI模型（统一工厂，无callStats）- 模型: {}", actualModelName);

            // 使用UnifiedModelFactory调用指定模型
            String finalPrompt = prompt;
            if (!"deepseek-r1:14b".equals(actualModelName)) {
                finalPrompt = String.format("[当前使用模型: %s]\n\n%s", actualModelName, prompt);
            }

            String aiResponse = unifiedModelFactory.callWithModel(finalPrompt, actualModelName);

            log.debug("AI模型调用成功（统一工厂，无callStats）- 实际模型: {}, 响应长度: {}",
                    actualModelName, aiResponse.length());
            return aiResponse;

        } catch (Exception e) {
            log.error("AI模型调用失败（统一工厂，无callStats）- 期望模型: {}, 错误: {}",
                    modelName, e.getMessage(), e);

            // 如果指定了特定模型但调用失败，尝试使用默认模型
            if (modelName != null && !"deepseek-r1:14b".equals(modelName)) {
                log.warn("尝试使用默认模型回退（统一工厂，无callStats）");
                try {
                    var defaultConfig = unifiedModelFactory.getDefaultModelConfig();
                    if (null != defaultConfig) {
                        String fallbackResponse = unifiedModelFactory.callWithConfig(prompt, defaultConfig);
                        log.info("默认模型回退成功（统一工厂，无callStats）");
                        return fallbackResponse;
                    }
                } catch (Exception fallbackError) {
                    log.error("默认模型回退也失败（统一工厂，无callStats）", fallbackError);
                }
            }

            throw new RuntimeException("AI模型调用失败（统一工厂），回退模型也失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用AI模型并记录完整耗时信息
     * <p>
     * 在原有callAiModelWithUnifiedFactory基础上,记录Prompt生成、LLM调用和后置处理的耗时。
     * 返回包含响应内容和所有耗时信息的结果对象。
     * </p>
     *
     * @param prompt         提示词
     * @param modelName      模型名称
     * @param promptSupplier Prompt生成函数,用于记录Prompt生成耗时(可为null)
     * @return 包含响应内容和耗时信息的结果对象
     */
    public ModelCallResult callAiModelWithTiming(String prompt, String modelName, Supplier<String> promptSupplier) {

        ModelCallTiming.ModelCallTimingBuilder timingBuilder = ModelCallTiming.builder();

        String finalPrompt = prompt;

        // 1. 记录Prompt生成耗时
        if (null != promptSupplier) {
            long promptGenStartTime = System.currentTimeMillis();
            try {
                finalPrompt = promptSupplier.get();
                long promptGenTimeMs = System.currentTimeMillis() - promptGenStartTime;
                timingBuilder.promptGenerationTimeMs(promptGenTimeMs);
                log.debug("Prompt生成耗时: {}ms", promptGenTimeMs);
            } catch (Exception e) {
                log.error("Prompt生成失败", e);
                throw new RuntimeException("Prompt生成失败: " + e.getMessage(), e);
            }
        }

        // 2. 如果没有指定模型名称，使用默认模型
        String actualModelName = (null == modelName || modelName.trim().isEmpty()) ? "deepseek-r1:14b" : modelName;

        // 使用UnifiedModelFactory调用,获取包含LLM耗时的结果
        ModelCallResult llmResult;
        try {
            // 调整Prompt格式
            String adjustedPrompt = finalPrompt;
            if (!"deepseek-r1:14b".equals(actualModelName)) {
                adjustedPrompt = String.format("[当前使用模型: %s]\n\n%s", actualModelName, finalPrompt);
            }

            // 调用带耗时记录的方法
            llmResult = unifiedModelFactory.callWithModelAndTiming(adjustedPrompt, actualModelName);

            // 合并LLM调用耗时
            if (null != llmResult.getTiming() && null != llmResult.getTiming().getLlmCallTimeMs()) {
                timingBuilder.llmCallTimeMs(llmResult.getTiming().getLlmCallTimeMs());
            }

        } catch (Exception e) {
            log.error("AI模型调用失败(带耗时记录) - 期望模型: {}, 错误: {}", modelName, e.getMessage(), e);
            throw new RuntimeException("AI模型调用失败: " + e.getMessage(), e);
        }

        // 3. 构建最终结果(后置动作耗时由调用方记录)
        ModelCallResult result = ModelCallResult.builder()
                .response(llmResult.getResponse())
                .timing(timingBuilder.build())
                .build();

        return result;
    }

    /**
     * 重放历史决策
     * <p>
     * 从历史AI响应中重新解析决策并执行交易。
     * </p>
     *
     * @param recordId 历史记录ID
     * @return 重放结果 { success, message, executionResults, executedAt }
     * @throws IllegalArgumentException 如果记录不存在
     * @throws RuntimeException         如果解析或执行失败
     */
    public ReplayDecisionResponse replayDecision(Long recordId) {
        log.info("【决策重放】开始重放决策 - recordId: {}", recordId);

        // 1. 查询历史记录
        LlmCallRecord record = llmCallRecordService.getRecordById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在: " + recordId));

        // 2. 获取API Key ID
        Long apiKeyId = record.getApiKeyId();
        log.info("【决策重放】获取到历史记录 - recordId: {}, apiKeyId: {}, modelName: {}",
                recordId, apiKeyId, record.getModelName());

        // 3. 获取AI响应内容
        String aiResponse = record.getResponseContent();
        if (null == aiResponse || aiResponse.trim().isEmpty()) {
            throw new RuntimeException("历史记录中没有AI响应内容");
        }

        // 4. 解析AI响应
        AiResponseParseResult parseResult = AiResponseParserUtil.parseAiResponse(aiResponse, false);
        if (!parseResult.isValidJson()) {
            throw new RuntimeException("AI响应不是有效JSON,无法重放 - hasJson: " + parseResult.isHasJson());
        }

        log.info("【决策重放】AI响应解析成功 - jsonLength: {}",
                parseResult.getJsonContent() != null ? parseResult.getJsonContent().length() : 0);

        // 5. 解析决策动作
        ActionParser.ActionPack actionPack = ActionParser.parseActionPack(parseResult.getJsonContent());
        if (null == actionPack) {
            throw new RuntimeException("无法从AI响应中解析出决策动作");
        }

        // 【修复】只检查QUERY工具调用（ATTENTION应该支持重放，但不会执行交易）
        if (actionPack.getActions().stream().anyMatch(v -> Objects.equals(ActionParser.ActionType.QUERY, v.getAction()))) {
            throw new RuntimeException("AI响应包含QUERY工具调用,不能直接重放交易");
        }

        int actionCount = actionPack.getActions().size();
        log.info("【决策重放】解析到决策动作 - actionCount: {}", actionCount);

        // 6. 记录重放操作日志(审计)
        log.info("【决策重放】准备执行交易 - recordId: {}, apiKeyId: {}, actionCount: {}",
                recordId, apiKeyId, actionCount);

        // 7. 执行交易
        List<TradeExecutionResult> executionResults = null;
        List<TradeAction> replayActions = new ArrayList<>();
        try {
            executionResults = tradeActionProcessor.processTradeActions(actionPack, apiKeyId, null);
            if (null != executionResults && !executionResults.isEmpty()) {
                log.info("【决策重放】交易执行成功 - recordId: {}, resultCount: {}", recordId, executionResults.size());
            } else {
                log.warn("【决策重放】交易执行结果为空（可能包含HOLD等非交易动作）- recordId: {}", recordId);
            }

            // 【修复】保存重放动作记录（无论executionResults是否为空）
            try {
                List<TradeAction> originalActions =
                        tradeActionService.findByRecordIdAndExecutionSource(recordId, "INITIAL");

                if (!originalActions.isEmpty()) {
                    replayActions = tradeActionService.saveReplayActions(originalActions, executionResults);

                    log.info("【决策重放】保存重放动作成功 - recordId: {}, replayCount: {}", recordId, replayActions.size());
                }
            } catch (Exception ex) {
                log.error("【决策重放】保存重放动作失败 - recordId: {}", recordId, ex);
                // 继续执行,不影响重放结果
            }

        } catch (Exception e) {
            log.error("【决策重放】执行交易失败 - recordId: {}", recordId, e);
            throw new RuntimeException("执行交易失败: " + e.getMessage(), e);
        }

        // 8. 构建返回结果
        ReplayDecisionResponse response = ReplayDecisionResponse.builder()
                .success(true)
                .message("重放成功")
                .recordId(recordId)
                .originalModelName(record.getModelName())
                .actionCount(actionCount)
                .executionResults(executionResults)
                .replayActions(replayActions)
                .executedAt(java.time.LocalDateTime.now())
                .build();

        log.info("【决策重放】重放完成 - recordId: {}, success: true, replayCount: {}",
                recordId, replayActions.size());

        return response;
    }

    /**
     * 重放单个action决策
     * <p>
     * 从历史TradeAction中重放指定的单个决策
     * </p>
     *
     * @param recordId 记录ID（用于校验action归属）
     * @param actionId 动作ID
     * @return 重放结果
     */
    @Transactional
    public ReplaySingleActionResponse replaySingleAction(Long recordId, Long actionId) {
        log.info("【单Action重放】开始重放 - recordId: {}, actionId: {}", recordId, actionId);

        // 1. 查询原始action
        TradeAction originalAction = tradeActionService.findById(actionId);
        if (null == originalAction) {
            throw new IllegalArgumentException("Action不存在: " + actionId);
        }

        // 2. 校验action归属（安全机制）
        if (!originalAction.getRecordId().equals(recordId)) {
            String errorMsg = String.format("Action不属于指定的Record - actionRecordId: %d, requestRecordId: %d",
                    originalAction.getRecordId(), recordId);
            log.error("【单Action重放】{}", errorMsg);
            throw new SecurityException(errorMsg);
        }

        log.info("【单Action重放】获取到原始action - actionId: {}, recordId: {}, actionType: {}, instId: {}",
                actionId, recordId, originalAction.getActionType(), originalAction.getInstId());

        // 3. 查询原始record以获取context（可选，用于验证和日志）
        LlmCallRecord record = llmCallRecordService.getRecordById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Record不存在: " + recordId));

        // 4. 将TradeAction转换为ParsedAction
        ActionParser.ParsedAction parsedAction = ActionParser.convertTradeActionToParsedAction(originalAction);

        // 5. 构建单action的ActionPack
        ActionParser.ActionPack singleActionPack = ActionParser.ActionPack.builder()
                .actions(List.of(parsedAction))
                .build();

        // 6. 【修复】验证不是QUERY工具调用（QUERY不应该重放，但ATTENTION应该支持）
        // ATTENTION重放不执行交易，只记录重放动作
        if ("QUERY".equals(parsedAction.getAction().name())) {
            throw new RuntimeException("QUERY动作是查询操作，不能重放交易");
        }

        // 7. 获取API Key ID
        Long apiKeyId = originalAction.getApiKeyId();

        log.info("【单Action重放】准备执行交易 - actionId: {}, apiKeyId: {}, actionType: {}, instId: {}",
                actionId, apiKeyId, parsedAction.getAction(), parsedAction.getInstId());

        // 8. 执行单个交易
        List<TradeExecutionResult> executionResults = null;
        List<TradeAction> replayActions = new ArrayList<>();
        try {
            executionResults = tradeActionProcessor.processTradeActions(singleActionPack, apiKeyId, null);

            if (null != executionResults && !executionResults.isEmpty()) {
                log.info("【单Action重放】交易执行成功 - actionId: {}, resultCount: {}",
                        actionId, executionResults.size());
            } else {
                log.warn("【单Action重放】交易执行结果为空（可能为HOLD等非交易动作）- actionId: {}", actionId);
            }

            // 9. 【修复】保存重放记录（无论executionResults是否为空）
            // saveReplayActions内部会处理executionResults为空的情况（为HOLD创建skip结果）
            try {
                List<TradeAction> originalActions = List.of(originalAction);
                replayActions = tradeActionService.saveReplayActions(originalActions, executionResults);

                log.info("【单Action重放】保存重放动作成功 - actionId: {}, replayCount: {}",
                        actionId, replayActions.size());

            } catch (Exception ex) {
                log.error("【单Action重放】保存重放动作失败 - actionId: {}", actionId, ex);
                // 继续执行,不影响重放结果
            }

        } catch (Exception e) {
            log.error("【单Action重放】执行交易失败 - actionId: {}", actionId, e);
            throw new RuntimeException("执行单个交易失败: " + e.getMessage(), e);
        }

        // 10. 构建返回结果
        ReplaySingleActionResponse response = ReplaySingleActionResponse.builder()
                .success(true)
                .message("单个action重放成功")
                .recordId(recordId)
                .actionId(actionId)
                .originalModelName(record.getModelName())
                .actionType(originalAction.getActionType())
                .instId(originalAction.getInstId())
                .executionResult(executionResults != null && !executionResults.isEmpty()
                        ? executionResults.get(0) : null)
                .replayActions(replayActions)
                .executedAt(java.time.LocalDateTime.now())
                .build();

        log.info("【单Action重放】重放完成 - actionId: {}, success: true, replayCount: {}",
                actionId, replayActions.size());

        return response;
    }

}
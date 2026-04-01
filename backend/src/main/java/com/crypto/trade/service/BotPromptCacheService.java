package com.crypto.trade.service;

import com.crypto.trade.dto.response.BotPromptHistoryResponse;
import com.crypto.trade.dto.response.FlowNodeStatusResponse;
import com.crypto.trade.entity.ChatMessage;
import com.crypto.trade.entity.LlmCallRecord;
import com.crypto.trade.entity.TradeAction;
import com.crypto.trade.entity.TradeBalanceSnapshot;
import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.repository.ChatMessageRepository;
import com.crypto.trade.repository.TradeActionRepository;
import com.crypto.trade.repository.TradeBalanceSnapshotRepository;
import com.crypto.trade.repository.TradingOrderRepository;
import com.crypto.trade.service.conversation.ActionParser;
import com.crypto.trade.util.AiResponseParserUtil;
import com.crypto.trade.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * BotPromptCacheService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class BotPromptCacheService {

    @Autowired
    LlmCallRecordService llmCallRecordService;
    @Autowired
    CacheManager cacheManager;
    @Autowired
    ChatMessageRepository chatMessageRepository;
    @Autowired
    TradeActionRepository tradeActionRepository;
    @Autowired
    TradeBalanceSnapshotRepository tradeBalanceSnapshotRepository;
    @Autowired
    TradingOrderRepository tradingOrderRepository;
    @Autowired
    ObjectMapper objectMapper;

    /**
     * 从缓存获取Prompt历史列表
     *
     * @param apiKeyId API Key ID
     * @param limit    限制数量
     * @return Prompt历史列表
     */
    @SuppressWarnings("unchecked")
    public List<BotPromptHistoryResponse> getCachedPromptHistory(Long apiKeyId, Integer limit) {
        return getCachedPromptHistory(apiKeyId, limit, null);
    }

    /**
     * 从缓存获取Prompt历史列表(支持动作筛选)
     *
     * @param apiKeyId     API Key ID
     * @param limit        限制数量
     * @param actionFilter 决策动作筛选(BUY/SELL/HOLD/QUERY/ATTENTION)
     * @return Prompt历史列表
     */
    @SuppressWarnings("unchecked")
    public List<BotPromptHistoryResponse> getCachedPromptHistory(Long apiKeyId, Integer limit, String actionFilter) {
        return getCachedPromptHistory(apiKeyId, limit, actionFilter, null, null, null);
    }

    /**
     * 从缓存获取Prompt历史列表(支持多重筛选)
     *
     * @param apiKeyId         API Key ID
     * @param limit            限制数量
     * @param actionFilter     决策动作筛选(BUY/SELL/HOLD/QUERY/ATTENTION)
     * @param modelFilter      AI模型筛选
     * @param callSourceFilter 调用来源筛选(SCHEDULED/DIRECT/MANUAL)
     * @param openCloseFilter  开平仓筛选(OPEN/CLOSE)
     * @return Prompt历史列表
     */
    @SuppressWarnings("unchecked")
    public List<BotPromptHistoryResponse> getCachedPromptHistory(Long apiKeyId, Integer limit, String actionFilter,
                                                                 String modelFilter, String callSourceFilter, String openCloseFilter) {
        try {
            // 构建缓存Key,包含所有筛选条件
            String actionFilterKey = actionFilter != null ? actionFilter : "all";
            String modelFilterKey = modelFilter != null ? modelFilter : "all";
            String callSourceFilterKey = callSourceFilter != null ? callSourceFilter : "all";
            String openCloseFilterKey = openCloseFilter != null ? openCloseFilter : "all";
            String cacheKey = String.format("prompt_history_%d_%d_%s_%s_%s_%s", apiKeyId, limit, actionFilterKey, modelFilterKey, callSourceFilterKey, openCloseFilterKey);

            org.springframework.cache.Cache cache = cacheManager.getCache("botPrompts");
            Cache<Object, Object> nativeCache = (Cache<Object, Object>) Objects.requireNonNull(cache).getNativeCache();

            List<BotPromptHistoryResponse> cachedData = (List<BotPromptHistoryResponse>) nativeCache.getIfPresent(cacheKey);

            if (cachedData != null) {
                log.debug("命中Prompt历史缓存 - apiKeyId: {}, limit: {}, actionFilter: {}, modelFilter: {}, callSourceFilter: {}, openCloseFilter: {}",
                        apiKeyId, limit, actionFilter, modelFilter, callSourceFilter, openCloseFilter);
                return cachedData;
            }

            log.debug("未命中Prompt历史缓存，直接查询数据库 - apiKeyId: {}, limit: {}, actionFilter: {}, modelFilter: {}, callSourceFilter: {}, openCloseFilter: {}",
                    apiKeyId, limit, actionFilter, modelFilter, callSourceFilter, openCloseFilter);
            // [重构] 使用 LlmCallRecord 获取历史记录
            List<LlmCallRecord> callRecords = llmCallRecordService.getCallHistory(apiKeyId, limit, actionFilter, modelFilter, callSourceFilter, openCloseFilter);

            List<BotPromptHistoryResponse> result = callRecords.stream()
                    .map(this::convertCallRecordToPromptHistoryResponse)
                    .collect(Collectors.toList());
            applySessionFlowFallback(result);

            // 缓存结果
            nativeCache.put(cacheKey, result);

            return result;

        } catch (Exception e) {
            log.error("获取缓存的Prompt历史失败 - apiKeyId: {}, actionFilter: {}, modelFilter: {}, callSourceFilter: {}, openCloseFilter: {}",
                    apiKeyId, actionFilter, modelFilter, callSourceFilter, openCloseFilter, e);
            // 降级：直接查询数据库
            return llmCallRecordService.getCallHistory(apiKeyId, limit, actionFilter, modelFilter, callSourceFilter, openCloseFilter).stream()
                    .map(this::convertCallRecordToPromptHistoryResponse)
                    .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                        applySessionFlowFallback(list);
                        return list;
                    }));
        }
    }

    /**
     * 从缓存获取最新Prompt
     *
     * @param apiKeyId API Key ID
     * @return 最新Prompt
     */
    @SuppressWarnings("unchecked")
    public BotPromptHistoryResponse getCachedLatestPrompt(Long apiKeyId) {
        try {
            String cacheKey = "latest_prompt_" + apiKeyId;
            org.springframework.cache.Cache cache = cacheManager.getCache("botPrompts");
            Cache<Object, Object> nativeCache = (Cache<Object, Object>) cache.getNativeCache();

            BotPromptHistoryResponse cachedData = (BotPromptHistoryResponse) nativeCache.getIfPresent(cacheKey);

            if (cachedData != null) {
                log.debug("命中最新Prompt缓存 - apiKeyId: {}", apiKeyId);
                return cachedData;
            }

            log.debug("未命中最新Prompt缓存，直接查询数据库 - apiKeyId: {}", apiKeyId);
            // [重构] 使用 LlmCallRecord 获取最新记录
            return llmCallRecordService.getLatestRecord(apiKeyId)
                    .map(this::convertCallRecordToPromptHistoryResponse)
                    .orElse(null);

        } catch (Exception e) {
            log.error("获取缓存的最新Prompt失败 - apiKeyId: {}", apiKeyId, e);
            // 降级：直接查询数据库
            return llmCallRecordService.getLatestRecord(apiKeyId)
                    .map(this::convertCallRecordToPromptHistoryResponse)
                    .orElse(null);
        }
    }

    /**
     * 定时任务：每30秒更新所有活跃API Key的Prompt缓存
     */
    @Scheduled(cron = "0/30 * * * * ?")
    public void refreshPromptCache() {
        log.debug("开始定时更新Prompt缓存");

        try {
            // 这里可以根据需要更新所有API Key的缓存
            // 为了避免过多数据库查询，可以只更新最近活跃的API Key
            // 当前实现：清理所有缓存，下次访问时重新加载
            clearCache();
            log.debug("Prompt缓存已清理，下次访问时重新加载");

        } catch (Exception e) {
            log.error("定时更新Prompt缓存失败", e);
        }
    }

    /**
     * 手动清理缓存
     */
    public void clearCache() {
        try {
            org.springframework.cache.Cache cache = cacheManager.getCache("botPrompts");
            assert cache != null;
            @SuppressWarnings("unchecked") Cache<Object, Object> nativeCache = (Cache<Object, Object>) cache.getNativeCache();
            nativeCache.invalidateAll();
            log.debug("Prompt缓存已手动清理");
        } catch (Exception e) {
            log.error("手动清理Prompt缓存失败", e);
        }
    }

    /**
     * 获取会话链路
     * 根据记录ID递归查询完整的会话链路(从最顶层父级到当前记录)
     *
     * @param recordId 当前记录ID
     * @return 按ID升序排列的会话链路
     */
    public List<BotPromptHistoryResponse> getConversationChain(Long recordId) {
        try {
            log.debug("获取会话链路 - recordId: {}", recordId);

            // 递归查询完整的会话链路
            List<LlmCallRecord> chain = llmCallRecordService.getConversationChain(recordId);

            // 转换为响应对象并按ID升序排序
            List<BotPromptHistoryResponse> result = chain.stream()
                    .map(this::convertCallRecordToPromptHistoryResponse)
                    .sorted((a, b) -> {
                        // 按照decisionId(Long类型)排序
                        try {
                            Long idA = Long.parseLong(a.getDecisionId());
                            Long idB = Long.parseLong(b.getDecisionId());
                            return idA.compareTo(idB);
                        } catch (NumberFormatException e) {
                            log.warn("decisionId格式错误，无法排序 - a: {}, b: {}", a.getDecisionId(), b.getDecisionId());
                            return 0;
                        }
                    })
                    .collect(Collectors.toList());

            log.debug("获取会话链路成功 - recordId: {}, 链路长度: {}", recordId, result.size());
            return result;

        } catch (Exception e) {
            log.error("获取会话链路失败 - recordId: {}", recordId, e);
            throw new RuntimeException("获取会话链路失败: " + e.getMessage(), e);
        }
    }

    /**
     * [新增] 转换LlmCallRecord为BotPromptHistoryResponse
     * <p>
     * 优化说明：不再解析promptContent为segments字段，交由前端从promptContent动态解析
     * 减少数据传输量，统一数据源
     * </p>
     */
    private BotPromptHistoryResponse convertCallRecordToPromptHistoryResponse(LlmCallRecord callRecord) {
        // 通过userMessageId关联ChatMessage获取promptContent
        String promptContent = null;
        if (callRecord.getUserMessageId() != null) {
            Optional<ChatMessage> userMsg = chatMessageRepository.findById(callRecord.getUserMessageId());
            promptContent = userMsg.map(ChatMessage::getContent).orElse(null);
        }

        // 【优化】不再解析promptContent为segments，交由前端处理
        // List<SegmentModel> segments = null;  // 已移除
        // 前端会使用相同的切割逻辑（=== {title} ===）动态生成segments

        // 解析AI响应为responseSegments（保持不变，因为AI响应结构复杂）
        List<SegmentModel> responseSegments = null;
        String responseContent = callRecord.getResponseContent();

        // 如果responseContent为空，尝试从assistantMessageId关联的ChatMessage获取原始响应
        if (responseContent == null || responseContent.trim().isEmpty()) {
            if (callRecord.getAssistantMessageId() != null) {
                Optional<ChatMessage> assistantMsg = chatMessageRepository.findById(callRecord.getAssistantMessageId());
                responseContent = assistantMsg.map(ChatMessage::getContent).orElse(null);
            }
        }

        if (null != responseContent && !responseContent.trim().isEmpty()) {
            try {
                responseSegments = AiResponseParserUtil.parseResponseToSegments(responseContent);
                log.debug("成功解析AI响应为responseSegments - callRecordId: {}, segments数量: {}",
                        callRecord.getId(), responseSegments.size());
            } catch (Exception e) {
                log.warn("解析AI响应为responseSegments失败 - callRecordId: {}, error: {}",
                        callRecord.getId(), e.getMessage());
                // 解析失败时responseSegments保持为null，前端会使用原始fullResponse
            }
        }

        // 如果decisionAction为空,尝试从responseSegments解析
        String action = callRecord.getDecisionAction();
        if (action == null || action.isEmpty()) {
            action = parseActionFromSegments(responseSegments);
        }

        // 查询TradeAction表，获取开平仓类型集合
        String openCloses = buildOpenClosesString(callRecord.getId());

        // 查询TradeBalanceSnapshot表，获取账户状态快照
        TradeBalanceSnapshot snapshot = null;
        List<TradeBalanceSnapshot> snapshots = tradeBalanceSnapshotRepository.findByRecordId(callRecord.getId());
        if (snapshots != null && !snapshots.isEmpty()) {
            snapshot = snapshots.get(0);
        }

        // 统计关联订单数量
        Long relatedOrderCount = tradingOrderRepository.countByRecordId(callRecord.getId());
        List<FlowNodeStatusResponse> flowNodes = parseFlowNodes(callRecord.getFlowNodesJson());
        String currentNodeCode = getCurrentNodeCode(flowNodes);
        boolean flowFinished = isFlowFinished(flowNodes);

        // 【新增】查询 TradeAction 获取风控状态和交易动作状态
        String riskControlStatus = null;
        String tradeActionStatus = null;
        try {
            List<TradeAction> tradeActions = tradeActionRepository.findByRecordIdAndExecutionSource(
                    callRecord.getId(),
                    "INITIAL"
            );

            if (tradeActions != null && !tradeActions.isEmpty()) {
                // 按优先级排序：REJECTED > PENDING > APPROVED > BYPASSED
                tradeActions.sort((a, b) -> {
                    int priorityA = getRiskControlPriority(a.getRiskControlStatus());
                    int priorityB = getRiskControlPriority(b.getRiskControlStatus());
                    return Integer.compare(priorityB, priorityA); // 降序排列
                });

                // 取优先级最高的 TradeAction 状态
                TradeAction primaryAction = tradeActions.get(0);
                riskControlStatus = primaryAction.getRiskControlStatus();
                tradeActionStatus = primaryAction.getStatus();

                log.debug("查询到风控状态 - callRecordId: {}, riskControlStatus: {}, tradeActionStatus: {}, actionCount: {}",
                        callRecord.getId(), riskControlStatus, tradeActionStatus, tradeActions.size());
            }
        } catch (Exception e) {
            log.warn("查询TradeAction风控状态失败 - callRecordId: {}", callRecord.getId(), e);
        }

        return BotPromptHistoryResponse.builder()
                .decisionId(String.valueOf(callRecord.getId())) // [重构] 使用callRecord的ID
                .apiKeyId(callRecord.getApiKeyId())
                .modelName(callRecord.getModelName())
                .createdTime(callRecord.getCallStartTime() != null ?
                        callRecord.getCallStartTime().atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli() : null)
                .action(action) // 优先使用数据库字段,为空时从responseSegments解析
                .instId(callRecord.getTargetInstId()) // LlmCallRecord使用旧字段名
                .price(callRecord.getDecisionPrice()) // LlmCallRecord使用旧字段名
                .quantity(callRecord.getDecisionQuantity()) // LlmCallRecord使用旧字段名
                .takeProfit(null) // LlmCallRecord暂无此字段
                .stopLoss(null) // LlmCallRecord暂无此字段
                .confidence(callRecord.getDecisionConfidence()) // LlmCallRecord使用旧字段名
                .reasoning(null) // LlmCallRecord暂无此字段
                .openCloses(openCloses) // 从TradeAction表查询的开平仓类型集合
                .promptContent(promptContent) // 【优化】只传递promptContent，前端自己解析segments
                // .segments(segments) // 【移除】不再传递segments字段
                .responseSegments(responseSegments) // 解析出的AI响应segments（保持不变）
                .status(callRecord.getStatus())
                .errorMessage(callRecord.getErrorMessage())
                .executed(callRecord.getIsExecuted())
                .executionTime(null) // LlmCallRecord暂无此字段
                .parentId(callRecord.getParentId()) // 父级记录ID
                .chatSessionId(callRecord.getSessionId()) // 聊天会话ID
                .callSource(callRecord.getCallSource()) // 调用来源
                .executionResult(null) // LlmCallRecord暂无此字段
                .processingTimeMs(callRecord.getProcessingTimeMs())
                .inputTotalEquity(snapshot != null ? snapshot.getTotalEquityUsdt() : null)
                .inputAvailableBalance(snapshot != null ? snapshot.getAvailableEquityUsdt() : null)
                .inputUsedMargin(snapshot != null ? snapshot.getUsedMarginUsdt() : null)
                .inputUnrealizedPnl(snapshot != null ? snapshot.getUnrealizedPnlUsdt() : null)
                .inputMarginRatio(snapshot != null ? snapshot.getMarginRatio() : null)
                .relatedOrderCount(relatedOrderCount != null ? relatedOrderCount.intValue() : 0)
                .riskControlStatus(riskControlStatus) // 【新增】风控状态
                .tradeActionStatus(tradeActionStatus) // 【新增】交易动作状态
                .flowNodes(flowNodes)
                .currentNodeCode(currentNodeCode)
                .flowFinished(flowFinished)
                .build();
    }

    private List<FlowNodeStatusResponse> parseFlowNodes(String flowNodesJson) {
        if (!StringUtils.hasText(flowNodesJson)) {
            return null;
        }
        try {
            List<FlowNodeStatusResponse> nodes = objectMapper.readValue(
                    flowNodesJson,
                    new TypeReference<List<FlowNodeStatusResponse>>() {
                    }
            );
            if (CollectionUtils.isEmpty(nodes)) {
                return null;
            }
            return nodes.stream()
                    .sorted(Comparator.comparing(FlowNodeStatusResponse::getOrderNo, Comparator.nullsLast(Integer::compareTo)))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("解析流程节点失败 - flowNodesJson: {}", flowNodesJson, e);
            return null;
        }
    }

    private String getCurrentNodeCode(List<FlowNodeStatusResponse> flowNodes) {
        if (CollectionUtils.isEmpty(flowNodes)) {
            return null;
        }
        Optional<FlowNodeStatusResponse> runningNode = flowNodes.stream()
                .filter(node -> "RUNNING".equals(node.getStatus()))
                .findFirst();
        if (runningNode.isPresent()) {
            return runningNode.get().getNodeCode();
        }
        Optional<FlowNodeStatusResponse> failedNode = flowNodes.stream()
                .filter(node -> "FAILED".equals(node.getStatus()))
                .findFirst();
        if (failedNode.isPresent()) {
            return failedNode.get().getNodeCode();
        }
        return flowNodes.stream()
                .filter(node -> !"PENDING".equals(node.getStatus()) && !"SKIPPED".equals(node.getStatus()))
                .max(Comparator.comparing(FlowNodeStatusResponse::getOrderNo, Comparator.nullsLast(Integer::compareTo)))
                .map(FlowNodeStatusResponse::getNodeCode)
                .orElse(null);
    }

    private boolean isFlowFinished(List<FlowNodeStatusResponse> flowNodes) {
        if (CollectionUtils.isEmpty(flowNodes)) {
            return true;
        }
        return flowNodes.stream().noneMatch(node -> "RUNNING".equals(node.getStatus()) || "PENDING".equals(node.getStatus()))
                && flowNodes.stream().anyMatch(node -> "SUCCESS".equals(node.getStatus()) || "FAILED".equals(node.getStatus()));
    }

    private void applySessionFlowFallback(List<BotPromptHistoryResponse> responses) {
        if (CollectionUtils.isEmpty(responses)) {
            return;
        }
        Map<Long, BotPromptHistoryResponse> sessionFlowSource = new HashMap<>();
        for (BotPromptHistoryResponse response : responses) {
            if (response.getChatSessionId() == null || CollectionUtils.isEmpty(response.getFlowNodes())) {
                continue;
            }
            BotPromptHistoryResponse existed = sessionFlowSource.get(response.getChatSessionId());
            if (existed == null || (response.getCreatedTime() != null && (existed.getCreatedTime() == null || response.getCreatedTime() > existed.getCreatedTime()))) {
                sessionFlowSource.put(response.getChatSessionId(), response);
            }
        }
        for (BotPromptHistoryResponse response : responses) {
            if (response.getChatSessionId() == null || !CollectionUtils.isEmpty(response.getFlowNodes())) {
                continue;
            }
            BotPromptHistoryResponse source = sessionFlowSource.get(response.getChatSessionId());
            if (source == null) {
                continue;
            }
            response.setFlowNodes(source.getFlowNodes());
            response.setCurrentNodeCode(source.getCurrentNodeCode());
            response.setFlowFinished(source.getFlowFinished());
        }
    }

    /**
     * 【新增】获取风控状态的优先级
     * 用于排序显示，REJECTED 优先级最高，BYPASSED 优先级最低
     *
     * @param status 风控状态
     * @return 优先级数值（越大优先级越高）
     */
    private int getRiskControlPriority(String status) {
        if (null == status) {
            return 0;
        }
        return switch (status) {
            case "REJECTED" -> 4;  // 最高优先级
            case "PENDING" -> 3;
            case "APPROVED" -> 2;
            case "BYPASSED" -> 1;  // 最低优先级
            default -> 0;
        };
    }

    /**
     * 构建开平仓类型集合字符串
     * 从TradeAction表中查询该callRecord对应的所有开平仓类型，去重后用英文逗号拼接
     *
     * @param callRecordId LlmCallRecord ID
     * @return 英文逗号分隔的开平仓类型字符串，如："open,close"，如果没有则返回null
     */
    private String buildOpenClosesString(Long callRecordId) {
        try {
            List<TradeAction> tradeActions = tradeActionRepository.findByRecordIdAndExecutionSource(callRecordId, "INITIAL");
            if (tradeActions == null || tradeActions.isEmpty()) {
                return null;
            }

            // 使用Set去重
            Set<String> openCloseSet = new HashSet<>();
            for (TradeAction action : tradeActions) {
                if (action.getOpenClose() != null) {
                    // 将枚举转换为小写字符串
                    openCloseSet.add(action.getOpenClose().name().toLowerCase());
                }
            }

            if (openCloseSet.isEmpty()) {
                return null;
            }

            // 用英文逗号拼接
            return String.join(",", openCloseSet);

        } catch (Exception e) {
            log.warn("查询开平仓类型失败 - callRecordId: {}, error: {}", callRecordId, e.getMessage());
            return null;
        }
    }

    /**
     * 从responseSegments中解析action字段
     * 优先从title="决策JSON"的segment的content中提取
     *
     * @param responseSegments AI响应的结构化段落数据
     * @return action字段值, 如果解析失败则返回null
     */
    private String parseActionFromSegments(List<SegmentModel> responseSegments) {
        if (responseSegments == null || responseSegments.isEmpty()) {
            return null;
        }

        // 查找title="决策JSON"的segment
        for (SegmentModel segment : responseSegments) {
            if ("决策JSON".equals(segment.getTitle())
                    && StringUtils.hasText(segment.getContent())) {
                String content = segment.getContent();
                if (content.contains("{") && content.contains("}")) {
                    content = content.substring(content.indexOf("{"), content.lastIndexOf("}") + 1);
                }
                ActionParser.ActionPack actionPack = JsonUtils.parseTo(content, ActionParser.ActionPack.class);
                if (null != actionPack && !CollectionUtils.isEmpty(actionPack.getActions())) {
                    Set<ActionParser.ActionType> actions = actionPack.getActions().stream()
                            .map(ActionParser.ParsedAction::getAction)
                            .collect(Collectors.toSet());
                    if (!actions.isEmpty()) {
                        return StringUtils.collectionToCommaDelimitedString(actions);
                    }
                }
                break; // 找到决策JSON后就退出
            }
        }

        return null;
    }

}

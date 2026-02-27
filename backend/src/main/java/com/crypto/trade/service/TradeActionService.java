package com.crypto.trade.service;

import com.crypto.trade.config.AiTradingRiskControlConfig;
import com.crypto.trade.entity.TradeAction;
import com.crypto.trade.enums.OpenCloseType;
import com.crypto.trade.exception.DataNotFoundException;
import com.crypto.trade.exception.TradeExecutionException;
import com.crypto.trade.repository.TradeActionRepository;
import com.crypto.trade.service.conversation.ActionParser;
import com.crypto.trade.service.conversation.TradeExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TradeActionService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeActionService {

    private final TradeActionRepository tradeActionRepository;
    private final AiTradingRiskControlConfig aiTradingRiskControlConfig;

    /**
     * 保存大模型返回的动作列表
     * 使用REQUIRES_NEW事务传播级别，确保动作记录能独立保存
     *
     * @param recordId   调用记录ID
     * @param apiKeyId   API Key ID
     * @param modelId    模型ID (可选)
     * @param actionPack 动作包
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<TradeAction> saveTradeActions(Long recordId, Long apiKeyId, String modelId, ActionParser.ActionPack actionPack) {
        if (actionPack == null || actionPack.getActions() == null || actionPack.getActions().isEmpty()) {
            return List.of();
        }

        try {
            List<TradeAction> tradeActions = actionPack.getActions().stream()
                    .map(parsedAction -> mapToEntity(recordId, apiKeyId, modelId, parsedAction))
                    .collect(Collectors.toList());

            List<TradeAction> savedActions = tradeActionRepository.saveAll(tradeActions);
            log.debug("保存了 {} 个交易动作记录 - recordId: {}", savedActions.size(), recordId);
            return savedActions;
        } catch (Exception e) {
            log.error("保存交易动作记录失败 - recordId: {}", recordId, e);
            return List.of();
        }
    }

    private TradeAction mapToEntity(Long recordId, Long apiKeyId, String modelId, ActionParser.ParsedAction action) {
        // 提取actionType，用于后续条件判断
        String actionType = action.getAction() != null ? action.getAction().name() : null;

        // 根据风控模式设置初始风控状态
        String riskControlStatus;
        if (aiTradingRiskControlConfig.isManualMode()) {
            // MANUAL模式: BUY/SELL动作需要风控审核,设置为PENDING
            riskControlStatus = ("BUY".equals(actionType) || "SELL".equals(actionType)) ? "PENDING" : "BYPASSED";  // 非交易动作绕过风控
        } else {
            // AUTO模式: 所有动作绕过风控
            riskControlStatus = "BYPASSED";
        }

        return TradeAction.builder()
                .recordId(recordId)
                .orderId(action.getOrderId())
                .apiKeyId(apiKeyId)
                .modelId(modelId)
                .actionType(actionType)
                .priority(action.getPriority())
                .instId(action.getInstId())
                .posSide(action.getPosSide())
                .timeframe(action.getTimeframe())
                .queryLimit(action.getLimit())
                .price(action.getPrice())
                .lever(action.getLever())
                .amount(action.getAmount())
                .orderType(action.getOrderType())
                .quantity(action.getQuantity())
                .takeProfit(action.getTakeProfit())
                .stopLoss(action.getStopLoss())
                .confidence(action.getConfidence())
                .reasoning(action.getReasoning())
                // 只有BUY/SELL动作才设置openClose默认值
                .openClose(
                        ("BUY".equals(actionType) || "SELL".equals(actionType))
                                ? (action.getOpenClose() != null ? action.getOpenClose() : OpenCloseType.OPEN)
                                : null
                )
                .status("PENDING")
                .executionSource("INITIAL")
                .replayCount(0)
                // 设置风控状态
                .riskControlStatus(riskControlStatus)
                .build();
    }

    /**
     * 批量更新首次执行动作的执行结果
     *
     * @param recordId 调用记录ID
     * @param results  执行结果列表
     */
    @Transactional
    public void batchUpdateInitialExecutionResults(Long recordId, List<TradeExecutionResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }

        try {
            for (TradeExecutionResult result : results) {
                if (result.getSuccess()) {
                    tradeActionRepository.updateInitialExecutionResult(
                            recordId,
                            result.getAction(),
                            result.getInstId(),
                            "SUCCESS",
                            LocalDateTime.now(),
                            result.getExecutionTimeMs(),
                            result.getOrderId(),
                            result.getExecutedPrice(),
                            result.getExecutedSize(),
                            null
                    );
                } else {
                    tradeActionRepository.updateInitialExecutionResult(
                            recordId,
                            result.getAction(),
                            result.getInstId(),
                            "FAILED",
                            LocalDateTime.now(),
                            result.getExecutionTimeMs(),
                            null,
                            null,
                            null,
                            result.getErrorMessage()
                    );
                }
            }
            log.info("批量更新TradeAction执行结果完成 - recordId: {}, count: {}", recordId, results.size());
        } catch (Exception e) {
            log.error("批量更新TradeAction执行结果失败 - recordId: {}", recordId, e);
        }
    }

    /**
     * 保存重放动作记录
     * 优化版本：使用原子操作避免竞态条件
     *
     * @param originalActions  原始动作列表
     * @param executionResults 执行结果列表
     * @return 保存的重放动作列表
     */
    @Transactional
    public List<TradeAction> saveReplayActions(List<TradeAction> originalActions, List<TradeExecutionResult> executionResults) {
        if (originalActions == null || executionResults == null) {
            return new ArrayList<>();
        }

        try {
            // 【修复】保留所有action类型，包括HOLD，不再过滤
            List<TradeAction> executionOriginalActions = CollectionUtils.isEmpty(originalActions)
                    ? Collections.emptyList()
                    : originalActions;
            List<TradeAction> replayActions = new ArrayList<>();
            List<TradeAction> originalActionsToUpdate = new ArrayList<>();

            for (int i = 0; i < executionOriginalActions.size(); i++) {
                TradeAction original = executionOriginalActions.get(i);

                // 【修复】处理executionResults为空的情况（如HOLD action没有执行结果）
                TradeExecutionResult result;
                if (!CollectionUtils.isEmpty(executionResults) && i < executionResults.size()) {
                    result = executionResults.get(i);
                } else {
                    // 对于没有执行结果的action（如HOLD），创建跳过结果
                    result = TradeExecutionResult.skip(
                            original.getInstId(),
                            original.getActionType(),
                            "HOLD动作不执行交易"
                    );
                }

                // 从数据库重新获取最新的原始动作，避免并发问题
                TradeAction latestOriginal = tradeActionRepository.findById(original.getId())
                        .orElseThrow(() -> new DataNotFoundException("TradeAction", original.getId()));

                // 创建重放动作
                TradeAction replay = TradeAction.createReplay(latestOriginal);

                // 设置执行结果
                if (result.getSuccess()) {
                    replay.markAsExecuted(
                            result.getOrderId(),
                            result.getExecutedPrice(),
                            result.getExecutedSize(),
                            result.getExecutionTimeMs()
                    );
                } else {
                    replay.markAsFailed(result.getErrorMessage(), result.getExecutionTimeMs());
                }

                replayActions.add(replay);

                // 原子性地增加重放次数（先计算再设置）
                Integer currentReplayCount = latestOriginal.getReplayCount() != null
                        ? latestOriginal.getReplayCount()
                        : 0;
                latestOriginal.setReplayCount(currentReplayCount + 1);
                originalActionsToUpdate.add(latestOriginal);
            }

            // 先保存所有重放动作
            List<TradeAction> saved = tradeActionRepository.saveAll(replayActions);

            // 然后更新原始动作的重放次数（在同一事务中）
            tradeActionRepository.saveAll(originalActionsToUpdate);

            log.info("保存重放动作完成 - count: {}", saved.size());
            return saved;

        } catch (Exception e) {
            log.error("保存重放动作失败", e);
            throw new TradeExecutionException("保存重放动作失败", e);
        }
    }

    /**
     * 查询重放链路(原始动作 + 所有重放)
     *
     * @param actionId 动作ID
     * @return 重放链路
     */
    public List<TradeAction> getReplayChain(Long actionId) {
        try {
            TradeAction action = tradeActionRepository.findById(actionId).orElse(null);
            if (action == null) {
                return new ArrayList<>();
            }

            // 如果是重放动作,先找到原始动作
            if (action.getParentActionId() != null) {
                action = tradeActionRepository.findById(action.getParentActionId()).orElse(null);
                if (action == null) {
                    return new ArrayList<>();
                }
            }

            List<TradeAction> chain = new ArrayList<>();
            chain.add(action);  // 原始动作

            // 查询所有重放
            List<TradeAction> replays = tradeActionRepository.findByParentActionIdOrderByCreateTimeDesc(action.getId());
            chain.addAll(replays);

            return chain;

        } catch (Exception e) {
            log.error("查询重放链路失败 - actionId: {}", actionId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 根据ID查询动作
     *
     * @param id 动作ID
     * @return 动作实体
     */
    public TradeAction findById(Long id) {
        return tradeActionRepository.findById(id).orElse(null);
    }

    /**
     * 根据recordId和执行来源查询动作列表
     *
     * @param recordId        调用记录ID
     * @param executionSource 执行来源(INITIAL/REPLAY)
     * @return 动作列表
     */
    public List<TradeAction> findByRecordIdAndExecutionSource(Long recordId, String executionSource) {
        return tradeActionRepository.findByRecordIdAndExecutionSource(recordId, executionSource);
    }

    /**
     * 更新风控状态
     * 用于在风控审核通过/驳回后更新TradeAction的风控状态
     *
     * @param actionId          TradeAction ID
     * @param riskControlStatus 风控状态(APPROVED/REJECTED)
     * @param riskControlId     风控订单ID(可选)
     */
    @Transactional
    public void updateRiskControlStatus(Long actionId, String riskControlStatus, Long riskControlId) {
        try {
            TradeAction tradeAction = tradeActionRepository.findById(actionId)
                    .orElseThrow(() -> new DataNotFoundException("TradeAction", actionId));

            tradeAction.setRiskControlStatus(riskControlStatus);
            if (null != riskControlId) {
                tradeAction.setRiskControlId(riskControlId);
            }

            tradeActionRepository.save(tradeAction);
            log.info("TradeAction风控状态已更新 - actionId: {}, status: {}, riskControlId: {}",
                    actionId, riskControlStatus, riskControlId);
        } catch (Exception e) {
            log.error("更新TradeAction风控状态失败 - actionId: {}", actionId, e);
            throw new RuntimeException("更新TradeAction风控状态失败", e);
        }
    }
}

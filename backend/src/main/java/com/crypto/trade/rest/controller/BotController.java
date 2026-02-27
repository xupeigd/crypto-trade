package com.crypto.trade.rest.controller;

import com.crypto.trade.config.AiTradingRiskControlConfig;
import com.crypto.trade.dto.ActionHistoryResponse;
import com.crypto.trade.dto.response.*;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.LlmCallRecord;
import com.crypto.trade.entity.TradeAction;
import com.crypto.trade.entity.TradeBalanceSnapshot;
import com.crypto.trade.enums.TradeBalanceSnapshotSource;
import com.crypto.trade.model.AccountDetailModel;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.model.request.BotCallModelRequest;
import com.crypto.trade.model.request.ReplayDecisionRequest;
import com.crypto.trade.model.request.ReplaySingleActionRequest;
import com.crypto.trade.service.*;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.conversation.TradingConfigProperties;
import com.crypto.trade.service.llm.AutomaticTradeService;
import com.crypto.trade.service.unified.UnifiedBalanceService;
import com.crypto.trade.util.DateTimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * BotController
 * REST控制器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@RestController
@RequestMapping("/bot")
public class BotController {

    @Autowired
    AutomaticTradeService automaticTradeService;
    @Autowired
    AiDecisionService aiDecisionService;
    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    BotPromptCacheService botPromptCacheService;
    @Autowired
    AiTradingRiskControlConfig aiTradingRiskControlConfig;
    @Autowired
    AsyncTradingTaskService asyncTradingTaskService;
    @Autowired
    UnifiedModelFactory unifiedModelFactory;
    @Autowired
    LlmCallRecordService llmCallRecordService;
    @Autowired
    TradeActionService tradeActionService;
    @Autowired
    TradeBalanceSnapshotService tradeBalanceSnapshotService;
    @Autowired
    UnifiedBalanceService unifiedBalanceService;
    @Autowired
    TradingConfigProperties tradingConfigProperties;

    /**
     * 获取BOT状态
     */
    @GetMapping("/status")
    public ApiResponse<BotStatusResponse> getBotStatus(@RequestParam(required = false) Long apiKeyId) {
        try {
            log.debug("获取BOT状态 - apiKeyId: {}", apiKeyId);

            // 如果没有指定API Key，使用默认的第一个活跃API Key
            if (null == apiKeyId) {
                apiKeyId = automaticTradeService.getDefaultActiveApiKey();
                if (null == apiKeyId) {
                    return ApiResponse.fail("未找到可用的API Key");
                }
            }

            // 获取LLM调用记录
            LlmCallRecord latestRecord = llmCallRecordService.getLatestRecord(apiKeyId).orElse(null);

            // 获取API Key信息
            Optional<ApiKey> apiKeyOpt = apiKeyService.getKeyById(apiKeyId);
            if (apiKeyOpt.isEmpty()) {
                return ApiResponse.fail("指定的API Key不存在");
            }
            ApiKey apiKey = apiKeyOpt.get();

            // 构建BOT状态响应
            BotStatusResponse response = BotStatusResponse.builder()
                    .callCount(latestRecord != null ? latestRecord.getCallCount() : 0L)
                    .lastCallTime(latestRecord != null && latestRecord.getCallStartTime() != null ?
                            DateTimeUtils.toTimestamp(latestRecord.getCallStartTime()) : null)
                    .modelName(latestRecord != null ? latestRecord.getModelName() : "deepseek-r1:14b")
                    .apiKeyId(apiKeyId)
                    .apiKeyDescription(apiKey.getCexName())
                    .riskControlMode(aiTradingRiskControlConfig.getCurrentMode().getDescription())
                    .tradingStyle(aiTradingRiskControlConfig.getCurrentTradingStyle().getDescription())
                    .automaticTradeEnabled(aiTradingRiskControlConfig.getAutomaticTradeEnabled())
                    .systemStatus("正常运行")
                    .lastProcessingTime(latestRecord != null && latestRecord.getProcessingTimeMs() != null ? latestRecord.getProcessingTimeMs() : 0L)
                    .build();

            return ApiResponse.ok(response);

        } catch (Exception e) {
            log.error("获取BOT状态失败", e);
            return ApiResponse.fail("获取BOT状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取可用的API Key列表
     */
    @GetMapping("/api-keys")
    public ApiResponse<List<BotStatusResponse.ApiKeyInfo>> getActiveApiKeys() {
        try {
            log.debug("获取可用的API Key列表");

            List<ApiKey> activeKeys = apiKeyService.getActiveKeys();
            List<BotStatusResponse.ApiKeyInfo> apiKeyInfos = activeKeys.stream()
                    .map(key -> BotStatusResponse.ApiKeyInfo.builder()
                            .keyId(key.getKeyId())
                            .keyName(key.getCexName())
                            .vendor(key.getCexName())
                            .status(key.getStatus())
                            .isLiveTrading(key.getIsLiveTrading())
                            .build())
                    .collect(Collectors.toList());

            return ApiResponse.ok(apiKeyInfos);

        } catch (Exception e) {
            log.error("获取API Key列表失败", e);
            return ApiResponse.fail("获取API Key列表失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发BOT生成prompt（异步执行）
     * <p>
     * 修复假死bug：将prompt生成也改为异步执行，避免主线程阻塞
     * </p>
     */
    @PostMapping("/trigger")
    public ApiResponse<BotTriggerResponse> triggerBot(@RequestParam Long apiKeyId,
                                                      @RequestParam(required = false) String modelName) {
        try {
            log.debug("手动触发BOT - apiKeyId: {}, modelName: {}", apiKeyId, modelName);

            // 验证API Key
            if (null == apiKeyId) {
                return ApiResponse.fail("API Key ID不能为空");
            }

            // ========== 生成taskId ==========
            String taskId = "TRIGGER_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);

            // ========== 创建账户余额快照 ==========
            Long balanceSnapshotId = null;
            try {
                // 获取账户详情
                AccountDetailModel accountDetail = unifiedBalanceService.getAccountUsdtDetail(apiKeyId);
                java.util.Optional<ApiKey> apiKeyOpt = apiKeyService.getKeyById(apiKeyId);

                // 常量放在左侧的null检查
                if (null != accountDetail && apiKeyOpt.isPresent()) {
                    // 获取AI交易资金限制配置
                    Double maxAccountBalance = tradingConfigProperties != null
                            ? tradingConfigProperties.getMaxAccountBalance()
                            : null;
                    BigDecimal maxAvailableAmount = (maxAccountBalance != null && maxAccountBalance > 0)
                            ? new BigDecimal(maxAccountBalance)
                            : null;

                    // 计算总盈亏
                    BigDecimal totalPnl = null;
                    if (maxAvailableAmount != null) {
                        totalPnl = tradeBalanceSnapshotService.calculateTotalPnl(
                                accountDetail.getTotalEquity(), apiKeyId);
                    }

                    // 创建快照（应用AI限制逻辑，与INITIAL/REPLY保持一致）
                    TradeBalanceSnapshot snapshot = TradeBalanceSnapshotService.createSnapshotWithAiLimits(
                            apiKeyId,
                            apiKeyOpt.get().getCexName(),
                            accountDetail,
                            TradeBalanceSnapshotSource.INITIAL.name(),
                            maxAvailableAmount,  // 应用AI限制
                            totalPnl             // 使用总盈亏计算
                    );

                    if (null != snapshot) {
                        // 保存快照
                        TradeBalanceSnapshot savedSnapshot = tradeBalanceSnapshotService.saveSnapshot(snapshot);
                        balanceSnapshotId = savedSnapshot.getSnapshotId();
                        log.debug("BOT触发：自动创建快照成功 - snapshotId: {}, apiKeyId: {}, 总盈亏: {}",
                                balanceSnapshotId, apiKeyId, totalPnl);
                    } else {
                        log.warn("BOT触发：createSnapshotWithAiLimits返回null - apiKeyId: {}", apiKeyId);
                    }
                } else {
                    log.warn("BOT触发：获取账户信息失败 - apiKeyId: {}, accountDetail: {}, apiKey存在: {}",
                            apiKeyId, accountDetail != null, apiKeyOpt.isPresent());
                }
            } catch (Exception e) {
                // 快照创建失败不影响主流程
                log.error("BOT触发：创建快照时发生异常 - apiKeyId: {}", apiKeyId, e);
            }

            // ========== 立即提交异步任务（包含prompt生成和AI调用） ==========
            log.info("提交异步BOT任务 - apiKeyId: {}, taskId: {}, balanceSnapshotId: {}",
                    apiKeyId, taskId, balanceSnapshotId);

            // 构造请求对象
            BotCallModelRequest request = BotCallModelRequest.builder()
                    .apiKeyId(apiKeyId)
                    .taskId(taskId)
                    .modelName(modelName)
                    .balanceSnapshotId(balanceSnapshotId)  // 添加快照ID
                    .build();

            // 异步执行：先生成prompt，再调用AI模型
            asyncTradingTaskService.executeAsyncTradingTaskWithPromptGeneration(request);

            // ========== 立即返回响应（不等待异步任务完成） ==========
            BotTriggerResponse response = BotTriggerResponse.builder()
                    .taskId(taskId)
                    .apiKeyId(apiKeyId)
                    .status("SUBMITTED")
                    .message("BOT触发任务已提交，正在后台生成prompt并调用AI模型")
                    .triggerTime(DateTimeUtils.nowUtc8Timestamp())
                    .estimatedDuration(300) // 预估5分钟
                    .queued(true)
                    .promptGenerated(false) // 标记prompt待生成
                    .estimatedTokens(0)
                    .build();

            return ApiResponse.ok(response);

        } catch (Exception e) {
            log.error("手动触发BOT失败", e);
            BotTriggerResponse errorResponse = BotTriggerResponse.builder()
                    .taskId(null)
                    .apiKeyId(apiKeyId)
                    .status("FAILED")
                    .message("触发BOT失败: " + e.getMessage())
                    .triggerTime(DateTimeUtils.nowUtc8Timestamp())
                    .queued(false)
                    .build();
            return ApiResponse.fail("手动触发BOT失败: " + e.getMessage());
        }
    }

    /**
     * 获取prompt历史列表
     * 支持多重筛选：动作类型、AI模型、调用来源
     * 支持分页查询
     */
    @GetMapping("/prompts")
    public ApiResponse<List<BotPromptHistoryResponse>> getPromptHistory(@RequestParam(required = false) Long apiKeyId,
                                                                        @RequestParam(defaultValue = "50") Integer limit,
                                                                        @RequestParam(required = false) String actionFilter,
                                                                        @RequestParam(required = false) String modelFilter,
                                                                        @RequestParam(required = false) String callSourceFilter,
                                                                        @RequestParam(required = false) String openCloseFilter,
                                                                        @RequestParam(defaultValue = "1") Integer page,
                                                                        @RequestParam(defaultValue = "20") Integer size) {
        try {
            log.debug("获取prompt历史 - apiKeyId: {}, limit: {}, actionFilter: {}, modelFilter: {}, callSourceFilter: {}, " +
                            "openCloseFilter: {}, page: {}, size: {}", apiKeyId, limit, actionFilter, modelFilter, callSourceFilter,
                    openCloseFilter, page, size);

            // 如果没有指定API Key，使用默认的第一个活跃API Key
            if (null == apiKeyId) {
                apiKeyId = automaticTradeService.getDefaultActiveApiKey();
                if (null == apiKeyId) {
                    return ApiResponse.fail("未找到可用的API Key");
                }
            }

            // 计算实际查询的limit
            // 使用前端传递的limit（应该足够大以包含所有数据）
            int actualLimit = limit != null && limit > 0 ? limit : 1000;
            if (actualLimit > 1000) {
                actualLimit = 1000; // 防止查询过多数据
            }
            log.debug("查询limit - 原始: {}, 实际: {}", limit, actualLimit);

            // 使用缓存获取决策历史(支持多重筛选)
            List<BotPromptHistoryResponse> allResponses = botPromptCacheService.getCachedPromptHistory(apiKeyId, actualLimit,
                    actionFilter, modelFilter, callSourceFilter, openCloseFilter);

            // 应用分页逻辑
            int startIndex = (page - 1) * size;
            int endIndex = Math.min(startIndex + size, allResponses.size());

            if (startIndex >= allResponses.size()) {
                // 超出范围，返回空列表
                return ApiResponse.ok(new ArrayList<>());
            }

            List<BotPromptHistoryResponse> pagedResponses = allResponses.subList(startIndex, endIndex);

            log.debug("返回分页数据 - page: {}, size: {}, total: {}, returned: {}",
                    page, size, allResponses.size(), pagedResponses.size());

            return ApiResponse.ok(pagedResponses, allResponses.size());

        } catch (Exception e) {
            log.error("获取prompt历史失败", e);
            return ApiResponse.fail("获取prompt历史失败: " + e.getMessage());
        }
    }

    /**
     * 获取最新的prompt
     */
    @GetMapping("/prompts/latest")
    public ApiResponse<BotPromptHistoryResponse> getLatestPrompt(@RequestParam(required = false) Long apiKeyId) {
        try {
            log.debug("获取最新prompt - apiKeyId: {}", apiKeyId);

            // 如果没有指定API Key，使用默认的第一个活跃API Key
            if (null == apiKeyId) {
                apiKeyId = automaticTradeService.getDefaultActiveApiKey();
                if (null == apiKeyId) {
                    return ApiResponse.fail("未找到可用的API Key");
                }
            }

            // 使用缓存获取最新决策
            BotPromptHistoryResponse response = botPromptCacheService.getCachedLatestPrompt(apiKeyId);
            if (response == null) {
                return ApiResponse.fail("未找到prompt历史记录");
            }

            return ApiResponse.ok(response);

        } catch (Exception e) {
            log.error("获取最新prompt失败", e);
            return ApiResponse.fail("获取最新prompt失败: " + e.getMessage());
        }
    }

    /**
     * 生成prompt（不调用AI模型）
     * 用于前端预览和编辑功能
     */
    @PostMapping("/generate-prompt")
    public ApiResponse<BotPromptGenerateResponse> generatePrompt(@RequestParam Long apiKeyId) {
        try {
            log.debug("生成prompt - apiKeyId: {}", apiKeyId);

            // 验证API Key
            if (null == apiKeyId) {
                return ApiResponse.fail("API Key ID不能为空");
            }

            // 验证API Key是否存在
            Optional<ApiKey> apiKeyOpt = apiKeyService.getKeyById(apiKeyId);
            if (apiKeyOpt.isEmpty()) {
                return ApiResponse.fail("指定的API Key不存在");
            }

            // 调用TradeDecisionService生成prompt
            BotPromptGenerateResponse response = aiDecisionService.generatePromptOnly(apiKeyId);

            if (response.getSuccess()) {
                return ApiResponse.ok(response);
            } else {
                return ApiResponse.fail(response.getMessage());
            }

        } catch (Exception e) {
            log.error("生成prompt失败", e);
            return ApiResponse.fail("生成prompt失败: " + e.getMessage());
        }
    }

    /**
     * 使用自定义prompt调用AI模型（异步版本）
     * 支持用户修改后的prompt进行AI调用
     * 立即返回任务ID，通过历史记录查看状态和结果
     */
    @PostMapping("/call-model")
    public ApiResponse<BotCallModelResponse> callModelWithCustomPrompt(@RequestBody BotCallModelRequest request) {
        try {
            log.debug("异步调用AI模型 - apiKeyId: {}, taskId: {}, modelName: '{}'", request.getApiKeyId(), request.getTaskId(),
                    request.getModelName());
            log.debug("完整的请求对象: {}", request);

            // 验证请求参数
            if (null == request.getApiKeyId()) {
                return ApiResponse.fail("API Key ID不能为空");
            }

            if (null == request.getPromptContent() || request.getPromptContent().trim().isEmpty()) {
                return ApiResponse.fail("Prompt内容不能为空");
            }

            // 验证API Key是否存在
            Optional<ApiKey> apiKeyOpt = apiKeyService.getKeyById(request.getApiKeyId());
            if (apiKeyOpt.isEmpty()) {
                return ApiResponse.fail("指定的API Key不存在");
            }

            // 处理默认模型配置
            if (request.getModelName() == null || request.getModelName().trim().isEmpty()) {
                String defaultModelName = unifiedModelFactory.getDefaultModelConfig().getModelId();
                request.setModelName(defaultModelName);
                log.debug("设置默认模型: '{}'", defaultModelName);
            }

            // 生成任务ID（如果没有提供）
            String taskId = request.getTaskId() != null ? request.getTaskId() :
                    "ASYNC_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);

            // 设置任务ID到请求中
            request.setTaskId(taskId);

            // ========== 创建账户余额快照（如果未提供） ==========
            if (null == request.getBalanceSnapshotId()) {
                log.debug("未提供balanceSnapshotId，自动创建快照 - apiKeyId: {}", request.getApiKeyId());
                try {
                    // 获取账户详情
                    AccountDetailModel accountDetail = unifiedBalanceService.getAccountUsdtDetail(request.getApiKeyId());

                    // 常量放在左侧的null检查
                    if (null != accountDetail && apiKeyOpt.isPresent()) {
                        // 获取AI交易资金限制配置
                        Double maxAccountBalance = tradingConfigProperties != null
                                ? tradingConfigProperties.getMaxAccountBalance()
                                : null;
                        BigDecimal maxAvailableAmount = (maxAccountBalance != null && maxAccountBalance > 0)
                                ? new BigDecimal(maxAccountBalance)
                                : null;

                        // 计算总盈亏
                        BigDecimal totalPnl = null;
                        if (maxAvailableAmount != null) {
                            totalPnl = tradeBalanceSnapshotService.calculateTotalPnl(
                                    accountDetail.getTotalEquity(), request.getApiKeyId());
                        }

                        // 创建快照（应用AI限制逻辑，与INITIAL/REPLY保持一致）
                        TradeBalanceSnapshot snapshot = TradeBalanceSnapshotService.createSnapshotWithAiLimits(
                                request.getApiKeyId(),
                                apiKeyOpt.get().getCexName(),
                                accountDetail,
                                "CUSTOM_PROMPT",
                                maxAvailableAmount,  // 应用AI限制
                                totalPnl             // 使用总盈亏计算
                        );

                        if (null != snapshot) {
                            // 保存快照
                            TradeBalanceSnapshot savedSnapshot = tradeBalanceSnapshotService.saveSnapshot(snapshot);
                            request.setBalanceSnapshotId(savedSnapshot.getSnapshotId());
                            log.debug("自定义Prompt：自动创建快照成功 - snapshotId: {}, apiKeyId: {}, 总盈亏: {}",
                                    savedSnapshot.getSnapshotId(), request.getApiKeyId(), totalPnl);
                        } else {
                            log.warn("自定义Prompt：createSnapshotWithAiLimits返回null - apiKeyId: {}", request.getApiKeyId());
                        }
                    } else {
                        log.warn("自定义Prompt：获取账户信息失败 - apiKeyId: {}, accountDetail: {}",
                                request.getApiKeyId(), accountDetail != null);
                    }
                } catch (Exception e) {
                    // 快照创建失败不影响主流程
                    log.error("自定义Prompt：创建快照时发生异常 - apiKeyId: {}", request.getApiKeyId(), e);
                }
            }

            // 异步执行任务
            asyncTradingTaskService.executeAsyncTradingTask(request);

            // 立即返回任务提交成功的响应
            BotCallModelResponse response = BotCallModelResponse.builder()
                    .taskId(taskId)
                    .apiKeyId(request.getApiKeyId())
                    .status("SUBMITTED")
                    .message("AI模型调用任务已提交，正在后台处理中")
                    .callTime(System.currentTimeMillis())
                    .success(true)
                    .build();

            log.debug("AI模型调用任务已提交 - taskId: {}, apiKeyId: {}, 使用模型: {}",
                    taskId, request.getApiKeyId(), request.getModelName());

            return ApiResponse.ok(response);

        } catch (Exception e) {
            log.error("异步调用AI模型失败", e);
            return ApiResponse.fail("提交AI模型调用任务失败: " + e.getMessage());
        }
    }

    /**
     * 查询任务状态
     * 根据任务ID查询AI调用任务的执行状态
     */
    @GetMapping("/task-status")
    public ApiResponse<BotTaskStatusResponse> getTaskStatus(@RequestParam String taskId) {
        try {
            log.debug("查询任务状态 - taskId: {}", taskId);

            if (null == taskId || taskId.trim().isEmpty()) {
                return ApiResponse.fail("任务ID不能为空");
            }

            // 查询任务状态(现在返回LlmCallRecord)
            LlmCallRecord record = asyncTradingTaskService.getTaskStatus(taskId);

            if (null == record) {
                return ApiResponse.fail("任务不存在");
            }

            // 构建任务状态响应,从LlmCallRecord映射到响应结构
            BotTaskStatusResponse response = BotTaskStatusResponse.builder()
                    .taskId(taskId)  // 使用传入的taskId (实际是sessionId)
                    .status(record.getStatus())
                    .submittedTime(record.getCallStartTime() != null ?
                            DateTimeUtils.toTimestamp(record.getCallStartTime()) : null)
                    .startedTime(record.getCallStartTime() != null ?
                            DateTimeUtils.toTimestamp(record.getCallStartTime()) : null)
                    .processingTimeMs(record.getProcessingTimeMs())
                    .errorMessage(record.getErrorMessage())
                    .fullResponse(record.getResponseContent())  // 从LlmCallRecord获取响应内容
                    .build();

            return ApiResponse.ok(response);

        } catch (Exception e) {
            log.error("查询任务状态失败 - taskId: {}", taskId, e);
            return ApiResponse.fail("查询任务状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取会话链路
     * 根据记录ID获取完整的会话链路(从最顶层父级到当前记录)
     *
     * @param recordId 记录ID
     * @return 会话链路列表(按ID升序排列)
     */
    @GetMapping("/conversation-chain")
    public ApiResponse<List<BotPromptHistoryResponse>> getConversationChain(@RequestParam Long recordId) {
        try {
            log.debug("获取会话链路 - recordId: {}", recordId);

            if (null == recordId) {
                return ApiResponse.fail("记录ID不能为空");
            }

            // 获取会话链路
            List<BotPromptHistoryResponse> chain = botPromptCacheService.getConversationChain(recordId);

            log.debug("获取会话链路成功 - recordId: {}, 链路长度: {}", recordId, chain.size());
            return ApiResponse.ok(chain);

        } catch (Exception e) {
            log.error("获取会话链路失败 - recordId: {}", recordId, e);
            return ApiResponse.fail("获取会话链路失败: " + e.getMessage());
        }
    }

    /**
     * 重放历史决策
     * 从历史AI响应中重新解析决策并执行交易
     *
     * @param request 重放请求参数
     * @return 重放结果
     */
    @PostMapping("/replay-decision")
    public ApiResponse<ReplayDecisionResponse> replayDecision(@RequestBody ReplayDecisionRequest request) {
        try {
            Long recordId = request.getRecordId();
            if (null == recordId) {
                return ApiResponse.fail("recordId不能为空");
            }

            log.debug("【接口调用】重放决策 - recordId: {}", recordId);

            // 直接调用Service层获取强类型Response
            ReplayDecisionResponse response = aiDecisionService.replayDecision(recordId);

            return ApiResponse.ok(response);

        } catch (Exception e) {
            log.error("【重放失败】重放决策执行失败", e);
            return ApiResponse.fail("重放决策失败: " + e.getMessage());
        }
    }

    /**
     * 重放单个action决策
     *
     * @param request 重放请求参数
     * @return 重放结果
     */
    @PostMapping("/replay-single-action")
    public ApiResponse<ReplaySingleActionResponse> replaySingleAction(@RequestBody ReplaySingleActionRequest request) {
        try {
            Long recordId = request.getRecordId();
            Long actionId = request.getActionId();

            if (null == recordId) {
                return ApiResponse.fail("recordId不能为空");
            }
            if (null == actionId) {
                return ApiResponse.fail("actionId不能为空");
            }

            log.debug("【接口调用】重放单个action - recordId: {}, actionId: {}", recordId, actionId);

            // 直接调用Service层获取强类型Response
            ReplaySingleActionResponse response = aiDecisionService.replaySingleAction(recordId, actionId);

            return ApiResponse.ok(response);

        } catch (Exception e) {
            log.error("【重放失败】重放单个action执行失败", e);
            return ApiResponse.fail("重放单个action失败: " + e.getMessage());
        }
    }

    /**
     * 获取action执行历史列表
     * 根据actionId查询该action及其所有重放记录
     *
     * @param actionId 动作ID
     * @return action执行历史列表
     */
    @GetMapping("/action-history")
    public ApiResponse<List<ActionHistoryResponse>> getActionHistory(@RequestParam Long actionId) {
        try {
            if (null == actionId) {
                return ApiResponse.fail("actionId不能为空");
            }

            log.debug("【接口调用】查询action执行历史 - actionId: {}", actionId);

            // 调用Service层查询action历史
            List<TradeAction> actionHistory = tradeActionService.getReplayChain(actionId);

            // 转换为DTO
            List<ActionHistoryResponse> response = actionHistory.stream()
                    .map(action -> {
                        // 将LocalDateTime转换为时间戳（毫秒）
                        Long createdTimestamp = action.getCreateTime() != null
                                ? (long) java.time.ZoneId.systemDefault().getRules().getOffset(action.getCreateTime())
                                .getTotalSeconds() * 1000
                                : null;

                        return ActionHistoryResponse.builder()
                                .actionId(action.getId())
                                .executionSource(action.getExecutionSource())
                                .createdTime(createdTimestamp)
                                .status(action.getStatus())
                                .replayCount(action.getReplayCount())
                                .executedTime(action.getExecutedTime())
                                .build();
                    })
                    .toList();

            log.debug("【查询成功】action执行历史 - actionId: {}, count: {}", actionId, response.size());
            return ApiResponse.ok(response);

        } catch (Exception e) {
            log.error("【查询失败】查询action执行历史失败 - actionId: {}", actionId, e);
            return ApiResponse.fail("查询action执行历史失败: " + e.getMessage());
        }
    }

    /**
     * 获取账户余额快照列表
     * 用于前端权益图表展示
     *
     * @param apiKeyId API密钥ID
     * @param limit    限制数量,默认50
     * @return 账户余额快照列表
     */
    @GetMapping("/balance-snapshots")
    public ApiResponse<List<TradeBalanceSnapshotResponse>> getBalanceSnapshots(@RequestParam Long apiKeyId,
                                                                               @RequestParam(defaultValue = "50") int limit) {
        try {
            // 常量放在左侧的null检查
            if (null == apiKeyId) {
                return ApiResponse.fail("apiKeyId不能为空");
            }

            // 限制数量范围检查(1-200)
            if (limit <= 0 || limit > 200) {
                return ApiResponse.fail("limit参数必须在1-200之间");
            }

            log.debug("【接口调用】查询账户余额快照 - apiKeyId: {}, limit: {}", apiKeyId, limit);

            // 调用Service层查询快照数据
            List<TradeBalanceSnapshot> snapshots =
                    tradeBalanceSnapshotService.getRecentSnapshots(apiKeyId, limit);

            // 转换为DTO
            List<TradeBalanceSnapshotResponse> response = snapshots.stream()
                    .map(snapshot -> {
                        // 将LocalDateTime转换为时间戳(毫秒)
                        Long timestamp = null != snapshot.getSnapshotTime()
                                ? DateTimeUtils.toTimestamp(snapshot.getSnapshotTime())
                                : null;

                        return TradeBalanceSnapshotResponse.builder()
                                .snapshotId(snapshot.getSnapshotId())
                                .apiKeyId(snapshot.getApiKeyId())
                                .cexName(snapshot.getCexName())
                                .totalEquityUsdt(snapshot.getTotalEquityUsdt())
                                .availableEquityUsdt(snapshot.getAvailableEquityUsdt())
                                .usedMarginUsdt(snapshot.getUsedMarginUsdt())
                                .unrealizedPnlUsdt(snapshot.getUnrealizedPnlUsdt())
                                .marginRatio(snapshot.getMarginRatio())
                                .maxAvailableAmount(snapshot.getMaxAvailableAmount())
                                .source(snapshot.getSource())
                                .snapshotTime(timestamp)
                                .recordId(snapshot.getRecordId())
                                .build();
                    })
                    .toList();

            log.debug("【查询成功】账户余额快照 - apiKeyId: {}, count: {}", apiKeyId, response.size());
            return ApiResponse.ok(response);

        } catch (Exception e) {
            log.error("【查询失败】查询账户余额快照失败 - apiKeyId: {}, limit: {}", apiKeyId, limit, e);
            return ApiResponse.fail("查询账户余额快照失败: " + e.getMessage());
        }
    }
}

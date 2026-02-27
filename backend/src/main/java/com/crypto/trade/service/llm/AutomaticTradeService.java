package com.crypto.trade.service.llm;

import com.crypto.trade.config.AiTradingRiskControlConfig;
import com.crypto.trade.dto.response.BotPromptGenerateResponse;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.LlmCallRecord;
import com.crypto.trade.model.request.BotCallModelRequest;
import com.crypto.trade.service.AiDecisionService;
import com.crypto.trade.service.AlertService;
import com.crypto.trade.service.AsyncTradingTaskService;
import com.crypto.trade.service.LlmCallRecordService;
import com.crypto.trade.service.cex.ApiKeyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/**
 * AutomaticTradeService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class AutomaticTradeService {

    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    AiDecisionService aiDecisionService;
    @Autowired
    AiTradingRiskControlConfig aiTradingRiskControlConfig;
    @Autowired
    AsyncTradingTaskService asyncTradingTaskService;
    @Autowired
    LlmCallRecordService llmCallRecordService;
    @Autowired
    AlertService alertService;

    /**
     * 定时任务：自动交易决策生成
     * 每15分钟执行一次，复用 /trigger 接口的完整逻辑
     * 流程：
     * 1. 生成prompt
     * 2. 提交异步AI任务（支持多轮会话）
     * 3. 返回taskId便于追踪
     */
    @Scheduled(cron = "17 */15 * * * ?")
    public void runAutomaticTrade() {
        log.debug("=== 开始执行自动交易决策任务 ===");
        try {
            // 检查自动作业开关是否启用
            if (Boolean.FALSE.equals(aiTradingRiskControlConfig.getAutomaticTradeEnabled())) {
                log.debug("自动作业开关已禁用，跳过本次自动交易决策任务");
                return;
            }
            log.debug("自动作业开关已启用，继续执行自动交易决策任务");

            // 1. 获取默认的第一个可用API密钥
            Long defaultApiKeyId = getDefaultActiveApiKey();
            if (null == defaultApiKeyId) {
                log.warn("未找到可用的API密钥，跳过本次自动交易决策任务");
                return;
            }
            log.debug("使用API密钥: {}", defaultApiKeyId);

            // ========== 复用 /trigger 接口的完整逻辑 ==========
            // 步骤1: 生成prompt
            log.info("步骤1: 生成prompt - apiKeyId: {}", defaultApiKeyId);
            BotPromptGenerateResponse promptResponse = aiDecisionService.generatePromptOnly(defaultApiKeyId);

            if (!promptResponse.getSuccess() || promptResponse.getStatus().equals("FAILED")) {
                log.warn("生成prompt失败: {}", promptResponse.getErrorMessage());
                return;
            }

            log.info("自动交易 - Prompt生成成功 - taskId: {}, estimatedTokens: {}",
                    promptResponse.getTaskId(), promptResponse.getEstimatedTokens());

            // 步骤2: 提交异步AI任务
            log.info("步骤2: 提交AI任务 - apiKeyId: {}", defaultApiKeyId);
            BotCallModelRequest request = BotCallModelRequest.builder()
                    .apiKeyId(defaultApiKeyId)
                    .promptContent(promptResponse.getPromptContent())
                    .taskId(promptResponse.getTaskId())
                    .modelName(null)  // 使用系统默认模型
                    .positions(promptResponse.getPositions()) // 附加仓位数据
                    .balanceSnapshotId(promptResponse.getBalanceSnapshotId())
                    .build();

            asyncTradingTaskService.executeAsyncTradingTask(request);

            // 步骤3: 记录任务提交成功
            log.info("自动交易 - AI任务已提交 - taskId: {}", promptResponse.getTaskId());
            log.info("AI交易决策任务已提交 - taskId: {}, apiKeyId: {}",
                    promptResponse.getTaskId(), defaultApiKeyId);

            log.debug("=== 自动交易决策任务执行完成 ===");
        } catch (RejectedExecutionException e) {
            // 【新增】专门处理线程池拒绝异常
            log.error("【定时任务失败】线程池已满，无法提交新任务 - 活跃线程: {}, 队列: {}, 错误: {}", "已满", "100", e.getMessage());
            // 线程池拒绝时，任务不会执行，需要告警通知
            // 可以考虑：发送邮件、钉钉通知、写入告警表等
        } catch (Exception e) {
            // 【增强】详细的错误日志
            log.error("【定时任务失败】自动交易决策任务执行异常 - 错误类型: {}, 错误信息: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            // 其他异常也需要记录，便于后续排查
        }
    }

    /**
     * 获取默认的第一个可用API密钥
     */
    public Long getDefaultActiveApiKey() {
        try {
            List<ApiKey> allKeys = apiKeyService.getAllKeys();
            // 过滤出活跃状态的API密钥
            List<ApiKey> activeKeys = allKeys.stream()
                    .filter(key -> "active".equals(key.getStatus()))
                    .toList();
            if (!activeKeys.isEmpty()) {
                ApiKey firstKey = activeKeys.get(0);
                log.debug("找到可用API密钥: CEX={}, KeyId={}", firstKey.getCexName(), firstKey.getKeyId());
                return firstKey.getKeyId();
            }
        } catch (Exception e) {
            log.error("获取可用API密钥失败", e);
        }
        return null;
    }

    /**
     * 手动触发自动交易决策（用于测试）
     * 复用 /trigger 接口的完整逻辑
     */
    public com.crypto.trade.entity.LlmCallRecord triggerManualDecision(Long apiKeyId) {
        log.info("手动触发AI交易决策生成 - apiKeyId: {}", apiKeyId);

        try {
            // 验证API密钥是否有效
            List<ApiKey> allKeys = apiKeyService.getAllKeys();
            boolean isValidKey = allKeys.stream()
                    .anyMatch(key -> key.getKeyId().equals(apiKeyId) && "active".equals(key.getStatus()));
            if (!isValidKey) {
                throw new IllegalArgumentException("指定的API密钥不存在或未激活");
            }

            // ========== 复用 /trigger 接口的完整逻辑 ==========
            // 步骤1: 生成prompt
            log.info("手动触发 - 步骤1: 生成prompt - apiKeyId: {}", apiKeyId);
            BotPromptGenerateResponse promptResponse = aiDecisionService.generatePromptOnly(apiKeyId);

            if (!promptResponse.getSuccess() || promptResponse.getStatus().equals("FAILED")) {
                throw new RuntimeException("生成prompt失败: " + promptResponse.getErrorMessage());
            }

            log.info("手动触发 - Prompt生成成功 - taskId: {}", promptResponse.getTaskId());

            // 步骤2: 提交异步AI任务
            log.info("手动触发 - 步骤2: 提交AI任务");
            BotCallModelRequest request = BotCallModelRequest.builder()
                    .apiKeyId(apiKeyId)
                    .promptContent(promptResponse.getPromptContent())
                    .taskId(promptResponse.getTaskId())
                    .modelName(null)  // 使用系统默认模型
                    .positions(promptResponse.getPositions()) // 附加仓位数据
                    .balanceSnapshotId(promptResponse.getBalanceSnapshotId()) // 传递快照ID
                    .build();

            asyncTradingTaskService.executeAsyncTradingTask(request);

            log.info("手动触发 - AI任务已提交 - taskId: {}", promptResponse.getTaskId());

            // 注意：由于改为异步执行，不再返回LlmCallRecord
            // 返回null表示已提交异步任务
            return null;

        } catch (Exception e) {
            log.error("手动触发AI交易决策失败 - apiKeyId: {}", apiKeyId, e);
            throw new RuntimeException("手动触发失败: " + e.getMessage(), e);
        }
    }

    /**
     * 手动触发自动交易决策（异步执行）
     * 使用专用线程池执行，不阻塞调用线程
     */
    @Async("botTaskExecutor")
    public void triggerManualDecisionAsync(Long apiKeyId) {
        log.info("异步触发AI交易决策生成 - apiKeyId: {}", apiKeyId);

        try {
            // 异步执行原有的同步方法
            triggerManualDecision(apiKeyId);
            log.info("异步AI交易决策生成完成 - apiKeyId: {}", apiKeyId);
        } catch (Exception e) {
            log.error("异步触发AI交易决策失败 - apiKeyId: {}", apiKeyId, e);
            // 异步方法中不抛出异常，只记录日志
        }
    }

    /**
     * 【新增】定时清理任务：扫描并清理长时间PROCESSING状态的记录
     * 每5分钟执行一次，扫描超过30分钟仍处于PROCESSING状态的记录
     * 自动标记为TERMINATED，防止任务卡住
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void cleanupStuckProcessingRecords() {
        log.debug("=== 开始执行定时清理任务：扫描PROCESSING状态记录 ===");

        try {
            // 查询超过30分钟仍处于PROCESSING状态的记录
            List<LlmCallRecord> stuckRecords = llmCallRecordService.getTimeoutRecords(30);

            if (stuckRecords.isEmpty()) {
                log.debug("定时清理任务：未发现超时的PROCESSING记录");
                return;
            }

            log.warn("【定时清理】发现 {} 条超时的PROCESSING记录，准备清理", stuckRecords.size());

            // 批量标记为TERMINATED
            int cleanedCount = 0;
            for (LlmCallRecord record : stuckRecords) {
                try {
                    String reason = String.format("定时清理：PROCESSING状态超过30分钟（创建时间: %s, 已耗时: %d分钟）", record.getCallStartTime(),
                            Duration.between(record.getCallStartTime(), LocalDateTime.now()).toMinutes());

                    // 调用现有的终止方法,直接传递Long类型的sessionId
                    llmCallRecordService.markConversationAsTerminated(record.getSessionId(), reason);

                    cleanedCount++;
                    log.info("【定时清理】已清理记录 - id: {}, sessionId: {}, 创建时间: {}", record.getId(), record.getSessionId(), record.getCallStartTime());

                } catch (Exception e) {
                    log.error("【定时清理】清理记录失败 - id: {}, sessionId: {}", record.getId(), record.getSessionId(), e);
                }
            }

            log.warn("【定时清理】任务完成 - 扫描: {} 条, 成功清理: {} 条", stuckRecords.size(), cleanedCount);

            // 【新增】发送清理结果告警
            try {
                alertService.alertCleanupResult(stuckRecords.size(), cleanedCount);
            } catch (Exception alertEx) {
                log.error("发送清理结果告警失败", alertEx);
            }

        } catch (Exception e) {
            log.error("【定时清理】任务执行失败", e);
        }

        log.debug("=== 定时清理任务执行完成 ===");
    }

}

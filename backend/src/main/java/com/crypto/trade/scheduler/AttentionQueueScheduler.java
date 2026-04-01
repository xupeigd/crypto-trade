package com.crypto.trade.scheduler;

import com.crypto.trade.dto.AttentionInfo;
import com.crypto.trade.entity.AttentionQueue;
import com.crypto.trade.model.request.BotCallModelRequest;
import com.crypto.trade.service.AsyncTradingTaskService;
import com.crypto.trade.service.AttentionQueueService;
import com.crypto.trade.service.prompt.PromptContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AttentionQueueScheduler
 * ATTENTION队列定时触发器
 * 每分钟检查并触发到期的ATTENTION
 *
 * @author page
 * @date 2026-03-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttentionQueueScheduler {

    private final AttentionQueueService attentionQueueService;
    private final AsyncTradingTaskService asyncTradingTaskService;

    /**
     * 每分钟检查并触发到期的ATTENTION
     */
    @Scheduled(cron = "3 */1 * * * ?")
    public void triggerPendingAttentions() {
        try {
            log.debug("【ATTENTION定时任务】开始检查待触发的ATTENTION");
            List<AttentionQueue> pendingList = attentionQueueService.getPendingAttentions();

            if (pendingList.isEmpty()) {
                log.debug("【ATTENTION定时任务】无待触发的ATTENTION");
                return;
            }

            log.debug("【ATTENTION定时任务】待触发数量: {}", pendingList.size());

            for (AttentionQueue attention : pendingList) {
                try {
                    triggerAttentionDecision(attention);
                    // 更新状态为EXECUTED
                    attentionQueueService.updateStatus(attention.getId(), "EXECUTED");
                    log.info("【ATTENTION定时任务】触发成功 - id: {}, instId: {}", attention.getId(), attention.getInstId());
                } catch (Exception e) {
                    log.error("【ATTENTION定时任务】触发失败 - id: {}, instId: {}", attention.getId(), attention.getInstId(), e);
                }
            }
        } catch (Exception e) {
            log.error("【ATTENTION定时任务】执行异常", e);
        }
    }

    /**
     * 触发ATTENTION决策
     * 根据ATTENTION信息构建新的交易请求，并调用大模型进行决策
     */
    private void triggerAttentionDecision(AttentionQueue attention) {
        log.info("【ATTENTION决策】开始 - recordId: {}, instId: {}", attention.getRecordId(), attention.getInstId());

        // 将AttentionQueue转换为AttentionInfo
        AttentionInfo attentionInfo = AttentionInfo.builder()
                .queueId(attention.getId())
                .instId(attention.getInstId())
                .priority(attention.getPriority())
                .timeframe(attention.getTimeframe())
                .limit(attention.getQueryLimit())
                .expectedTriggerTime(attention.getExpectedTriggerTime())
                .build();

        // 构建PromptContext，设置ATTENTION数据
        PromptContext context = PromptContext.builder()
                .apiKeyId(attention.getApiKeyId())
                .build();
        context.setCustomData("attentions", List.of(attentionInfo));
        context.setCustomData("isAttentionTrigger", true);
        context.setCustomData("originalRecordId", attention.getRecordId());

        // 注意：prompt的生成会在AiDecisionService中自动处理
        // AiDecisionService会自动检查并获取待处理的ATTENTION信息

        // 创建交易请求并调用异步交易服务
        BotCallModelRequest request = BotCallModelRequest.builder()
                .apiKeyId(attention.getApiKeyId())
                .taskId("ATTENTION_" + attention.getId())
                .attentions(List.of(attentionInfo))
                .build();

        // 调用异步交易任务
        asyncTradingTaskService.executeAsyncTradingTask(request);
        log.info("【ATTENTION决策】触发异步交易任务 - recordId: {}", attention.getRecordId());
    }
}

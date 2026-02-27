package com.crypto.trade.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AlertService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class AlertService {

    /**
     * 发送线程池拒绝告警
     *
     * @param activeThreads  活跃线程数
     * @param maxThreads     最大线程数
     * @param queueSize      队列大小
     * @param queueCapacity  队列容量
     * @param completedTasks 已完成任务数
     */
    public void alertThreadPoolRejection(int activeThreads, int maxThreads,
                                         int queueSize, int queueCapacity,
                                         long completedTasks) {
        String message = String.format(
                "【线程池拒绝告警】\n" +
                        "时间: %s\n" +
                        "活跃线程: %d/%d\n" +
                        "队列使用: %d/%d\n" +
                        "已完成任务: %d\n" +
                        "建议：增加线程池容量或优化任务处理逻辑",
                LocalDateTime.now(),
                activeThreads, maxThreads,
                queueSize, queueCapacity,
                completedTasks
        );

        log.error("{}", message);
        sendAlert("线程池拒绝", message);
    }

    /**
     * 发送任务超时告警
     *
     * @param sessionId   会话ID
     * @param roundNumber 轮次
     * @param elapsedMs   已耗时（毫秒）
     * @param timeoutMs   超时限制（毫秒）
     * @param stage       超时阶段
     */
    public void alertTaskTimeout(String sessionId, int roundNumber,
                                 long elapsedMs, long timeoutMs, String stage) {
        String message = String.format(
                "【任务超时告警】\n" +
                        "时间: %s\n" +
                        "会话ID: %s\n" +
                        "轮次: %d\n" +
                        "超时阶段: %s\n" +
                        "已耗时: %d ms (%.2f 分钟)\n" +
                        "超时限制: %d ms (%.2f 分钟)\n" +
                        "建议：检查AI模型响应速度或优化prompt长度",
                LocalDateTime.now(),
                sessionId,
                roundNumber,
                stage,
                elapsedMs, elapsedMs / 1000.0 / 60,
                timeoutMs, timeoutMs / 1000.0 / 60
        );

        log.error("{}", message);
        sendAlert("任务超时", message);
    }

    /**
     * 发送智能终止告警
     *
     * @param sessionId        会话ID
     * @param querySignature   QUERY签名
     * @param consecutiveCount 连续次数
     */
    public void alertSmartTermination(String sessionId, String querySignature, int consecutiveCount) {
        String message = String.format(
                "【智能终止告警】\n" +
                        "时间: %s\n" +
                        "会话ID: %s\n" +
                        "QUERY签名: %s\n" +
                        "连续次数: %d\n" +
                        "原因: AI模型连续调用相同的QUERY工具\n" +
                        "建议：检查AI模型配置或优化prompt",
                LocalDateTime.now(),
                sessionId,
                querySignature,
                consecutiveCount
        );

        log.warn("{}", message);
        sendAlert("智能终止", message);
    }

    /**
     * 发送定时清理告警
     *
     * @param stuckCount   卡住记录数
     * @param cleanedCount 清理成功数
     */
    public void alertCleanupResult(int stuckCount, int cleanedCount) {
        if (stuckCount == 0) {
            return; // 没有卡住的记录，不告警
        }

        String message = String.format(
                "【定时清理告警】\n" +
                        "时间: %s\n" +
                        "发现卡住记录: %d 条\n" +
                        "清理成功: %d 条\n" +
                        "清理失败: %d 条\n" +
                        "建议：检查为何任务会卡住超过30分钟",
                LocalDateTime.now(),
                stuckCount,
                cleanedCount,
                stuckCount - cleanedCount
        );

        log.warn("{}", message);
        sendAlert("定时清理", message);
    }

    /**
     * 发送告警通知
     * <p>
     * 当前实现：仅记录日志
     * 后续可扩展：钉钉、邮件、短信、企业微信等
     * </p>
     *
     * @param alertType 告警类型
     * @param message   告警消息
     */
    private void sendAlert(String alertType, String message) {
        // 【当前实现】仅记录日志
        // 后续可在此处添加钉钉、邮件、短信等告警方式

        // 示例：钉钉告警（需要配置webhook）
        // dingTalkAlertService.send(alertType, message);

        // 示例：邮件告警（需要配置邮件服务）
        // emailService.sendAlert(alertType, message);

        // 示例：写入告警表（需要创建Alert实体）
        // alertRepository.save(Alert.builder()
        //         .type(alertType)
        //         .message(message)
        //         .createTime(LocalDateTime.now())
        //         .build());
    }
}

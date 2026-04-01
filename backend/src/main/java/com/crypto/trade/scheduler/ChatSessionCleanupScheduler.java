package com.crypto.trade.scheduler;

import com.crypto.trade.entity.ChatSession;
import com.crypto.trade.repository.ChatSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ChatSessionCleanupScheduler
 * 聊天会话清理调度器
 * <p>
 * 每5分钟清理创建超过10分钟的pre_active空会话
 *
 * @author page
 * @date 2026-03-30
 */
@Slf4j
@Component("chatSessionCleanupScheduler")
public class ChatSessionCleanupScheduler {

    @Autowired
    private ChatSessionRepository sessionRepository;

    /**
     * 每5分钟执行一次，清理pre_active空会话
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void cleanupEmptyPreActiveSessions() {
        try {
            log.debug("开始清理pre_active空会话...");

            // 查询所有pre_active状态的空会话
            List<ChatSession> emptySessions = sessionRepository.findEmptyPreActiveSessions();

            if (emptySessions.isEmpty()) {
                log.debug("没有需要清理的pre_active空会话");
                return;
            }

            // 计算10分钟前的时间
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);

            int deletedCount = 0;
            for (ChatSession session : emptySessions) {
                // 只清理创建时间超过10分钟的会话
                if (session.getCreatedTime().isBefore(threshold)) {
                    session.setStatus(ChatSession.SessionStatus.ARCHIVED);
                    sessionRepository.save(session);
                    deletedCount++;
                    log.debug("清理pre_active空会话 - sessionId: {}, createdTime: {}",
                            session.getSessionId(), session.getCreatedTime());
                }
            }

            if (deletedCount > 0) {
                log.info("已清理{}个pre_active空会话", deletedCount);
            }
        } catch (Exception e) {
            log.error("清理pre_active空会话时发生错误", e);
        }
    }
}

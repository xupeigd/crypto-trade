package com.crypto.trade.repository;

import com.crypto.trade.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ChatSessionRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface ChatSessionRepository
        extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByUserId(String userId);

    List<ChatSession> findByUserIdAndStatus(String userId, String status);

    Optional<ChatSession> findByUserIdAndStatusOrderByUpdatedTimeDesc(String userId, String status);

    @Query("SELECT s FROM ChatSession s WHERE s.userId = :userId AND s.status = 'active' ORDER BY s.updatedTime DESC")
    List<ChatSession> findActiveSessionsByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(s) FROM ChatSession s WHERE s.userId = :userId AND s.status = 'active'")
    long countActiveSessionsByUserId(@Param("userId") String userId);

    @Query("SELECT s FROM ChatSession s WHERE s.userId = :userId AND s.status = 'active' ORDER BY s.updatedTime DESC LIMIT 1")
    Optional<ChatSession> findLatestActiveSessionByUserId(@Param("userId") String userId);

    Optional<ChatSession> findBySessionIdAndUserId(Long sessionId, String userId);

    @Modifying
    @Query("UPDATE ChatSession s SET s.status = 'archived', s.updatedTime = CURRENT_TIMESTAMP WHERE s.sessionId = :sessionId AND s.userId = :userId")
    void updateSessionStatusArchived(@Param("sessionId") Long sessionId, @Param("userId") String userId);

    @Modifying
    @Query("UPDATE ChatSession s SET s.sessionName = :sessionName, s.updatedTime = CURRENT_TIMESTAMP WHERE s.sessionId = :sessionId AND s.userId = :userId")
    void updateSessionNameAndTime(@Param("sessionId") Long sessionId, @Param("userId") String userId, @Param("sessionName") String sessionName);

    // 根据userId和agentId查询会话（智能体会话）
    List<ChatSession> findByUserIdAndAgentId(String userId, Long agentId);

    // 根据userId查询agentId为null的会话（默认会话）
    List<ChatSession> findByUserIdAndAgentIdIsNull(String userId);

    // 根据userId和agentId查询状态为active的会话
    List<ChatSession> findByUserIdAndAgentIdAndStatus(String userId, Long agentId, String status);

    // 根据userId查询agentId为null且状态为active的会话
    List<ChatSession> findByUserIdAndAgentIdIsNullAndStatus(String userId, String status);

    // 查询非pre_active状态的会话
    @Query("SELECT s FROM ChatSession s WHERE s.userId = :userId AND s.status = 'active' ORDER BY s.updatedTime DESC")
    List<ChatSession> findValidSessionsByUserId(@Param("userId") String userId);

    // 按agentId查询非pre_active状态的会话
    @Query("SELECT s FROM ChatSession s WHERE s.userId = :userId AND s.agentId = :agentId AND s.status = 'active' ORDER BY s.updatedTime DESC")
    List<ChatSession> findValidSessionsByUserIdAndAgentId(@Param("userId") String userId, @Param("agentId") Long agentId);

    // 按agentId为null查询非pre_active状态的会话
    @Query("SELECT s FROM ChatSession s WHERE s.userId = :userId AND s.agentId IS NULL AND s.status = 'active' ORDER BY s.updatedTime DESC")
    List<ChatSession> findValidSessionsByUserIdAndAgentIdIsNull(@Param("userId") String userId);

    // 查询pre_active状态的空会话（没有消息的）
    @Query("SELECT s FROM ChatSession s WHERE s.status = 'pre_active' AND s.sessionId NOT IN " +
            "(SELECT DISTINCT m.session.sessionId FROM ChatMessage m)")
    List<ChatSession> findEmptyPreActiveSessions();

    // 获取最新有效会话
    @Query("SELECT s FROM ChatSession s WHERE s.userId = :userId AND s.status = 'active' ORDER BY s.updatedTime DESC LIMIT 1")
    Optional<ChatSession> findLatestValidSessionByUserId(@Param("userId") String userId);
}
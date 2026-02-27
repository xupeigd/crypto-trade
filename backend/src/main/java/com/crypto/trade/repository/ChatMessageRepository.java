package com.crypto.trade.repository;

import com.crypto.trade.entity.ChatMessage;
import com.crypto.trade.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ChatMessageRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionOrderByCreatedTimeAsc(ChatSession session);

    List<ChatMessage> findBySessionSessionIdOrderByCreatedTimeAsc(Long sessionId);

    @Query("SELECT m FROM ChatMessage m JOIN FETCH m.session WHERE m.session.sessionId = :sessionId ORDER BY m.createdTime ASC")
    List<ChatMessage> findMessagesBySessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.session.sessionId = :sessionId")
    long countMessagesBySessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT m FROM ChatMessage m JOIN FETCH m.session WHERE m.session.sessionId = :sessionId AND m.role = :role ORDER BY m.createdTime ASC")
    List<ChatMessage> findMessagesBySessionIdAndRole(@Param("sessionId") Long sessionId, @Param("role") String role);
}
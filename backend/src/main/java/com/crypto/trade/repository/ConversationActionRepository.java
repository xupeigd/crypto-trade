package com.crypto.trade.repository;

import com.crypto.trade.entity.ConversationAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ConversationActionRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface ConversationActionRepository extends JpaRepository<ConversationAction, Long> {

    /**
     * 根据会话ID查询所有动作
     */
    List<ConversationAction> findBySessionIdOrderByCreatedTimeAsc(String sessionId);

    /**
     * 根据决策ID查询所有动作
     */
    List<ConversationAction> findByDecisionIdOrderByCreatedTimeAsc(String decisionId);

    /**
     * 根据会话ID和动作类型查询动作
     */
    List<ConversationAction> findBySessionIdAndActionTypeOrderByCreatedTimeAsc(String sessionId, String actionType);

    /**
     * 根据会话ID和状态查询动作
     */
    List<ConversationAction> findBySessionIdAndStatusOrderByCreatedTimeAsc(String sessionId, String status);

    /**
     * 查询最近的N个动作
     */
    @Query("SELECT ca FROM ConversationAction ca WHERE ca.sessionId = :sessionId ORDER BY ca.createdTime DESC LIMIT :limit")
    List<ConversationAction> findRecentActionsBySessionId(@Param("sessionId") String sessionId, @Param("limit") int limit);

    /**
     * 统计会话中的动作数量
     */
    @Query("SELECT COUNT(ca) FROM ConversationAction ca WHERE ca.sessionId = :sessionId")
    long countBySessionId(@Param("sessionId") String sessionId);

    /**
     * 统计会话中各状态的动作数量
     */
    @Query("SELECT ca.status, COUNT(ca) FROM ConversationAction ca WHERE ca.sessionId = :sessionId GROUP BY ca.status")
    List<Object[]> countStatusBySessionId(@Param("sessionId") String sessionId);

    /**
     * 删除指定会话的所有动作
     */
    void deleteBySessionId(String sessionId);
}
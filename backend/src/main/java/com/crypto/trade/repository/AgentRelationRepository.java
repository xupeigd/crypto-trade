package com.crypto.trade.repository;

import com.crypto.trade.entity.AgentRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AgentRelationRepository
 * 智能体关系数据访问层
 *
 * @author page
 * @date 2026-03-22
 */
@Repository
public interface AgentRelationRepository
        extends JpaRepository<AgentRelation, Long> {

    /**
     * 查询指定Agent的所有子Agent（按优先级排序）
     */
    List<AgentRelation> findByAgentIdAndIsActiveTrueOrderByPriorityAsc(Long agentId);

    /**
     * 查询指定子Agent被哪些主Agent引用
     */
    List<AgentRelation> findBySubAgentIdAndIsActiveTrue(Long subAgentId);

    /**
     * 查询指定Agent和子Agent的关系
     */
    Optional<AgentRelation> findByAgentIdAndSubAgentId(Long agentId, Long subAgentId);

    /**
     * 检查关系是否存在
     */
    boolean existsByAgentIdAndSubAgentId(Long agentId, Long subAgentId);

    /**
     * 删除指定Agent的所有关系
     */
    void deleteByAgentId(Long agentId);
}

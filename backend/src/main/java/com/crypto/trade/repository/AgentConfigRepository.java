package com.crypto.trade.repository;

import com.crypto.trade.entity.AgentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AgentConfigRepository
 * 智能体配置数据访问层
 *
 * @author page
 * @date 2026-03-09
 */
@Repository
public interface AgentConfigRepository
        extends JpaRepository<AgentConfig, Long> {

    Optional<AgentConfig> findByName(String name);

    List<AgentConfig> findByIsActiveTrue();

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    boolean existsByExecutionMode(com.crypto.trade.entity.ExecutionMode executionMode);
}

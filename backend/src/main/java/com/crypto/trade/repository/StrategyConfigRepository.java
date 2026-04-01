package com.crypto.trade.repository;

import com.crypto.trade.entity.StrategyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * StrategyConfigRepository
 * 策略配置数据访问层
 *
 * @author page
 * @date 2026-03-23
 */
@Repository
public interface StrategyConfigRepository
        extends JpaRepository<StrategyConfig, Long> {

    /**
     * 查询所有启用的配置
     */
    List<StrategyConfig> findByIsActiveTrue();

    /**
     * 根据名称查询
     */
    Optional<StrategyConfig> findByStrategyName(String strategyName);

    /**
     * 检查名称是否存在
     */
    boolean existsByStrategyName(String strategyName);

    /**
     * 检查名称是否存在（排除指定ID）
     */
    boolean existsByStrategyNameAndIdNot(String strategyName, Long id);
}

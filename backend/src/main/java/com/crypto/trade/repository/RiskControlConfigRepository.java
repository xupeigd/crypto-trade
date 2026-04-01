package com.crypto.trade.repository;

import com.crypto.trade.entity.RiskControlConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * RiskControlConfigRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface RiskControlConfigRepository
        extends JpaRepository<RiskControlConfig, Long> {

    /**
     * 根据配置ID查找配置
     *
     * @param configId 配置ID
     * @return 配置信息
     */
    Optional<RiskControlConfig> findByConfigId(Long configId);
}
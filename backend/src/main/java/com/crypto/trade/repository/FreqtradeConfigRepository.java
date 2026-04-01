package com.crypto.trade.repository;

import com.crypto.trade.entity.FreqtradeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * FreqtradeConfigRepository
 * Freqtrade配置数据访问层
 *
 * @author page
 * @date 2026-03-22
 */
@Repository
public interface FreqtradeConfigRepository
        extends JpaRepository<FreqtradeConfig, Long> {

    /**
     * 查询所有启用的配置
     */
    List<FreqtradeConfig> findByIsActiveTrue();

    /**
     * 根据名称查询
     */
    Optional<FreqtradeConfig> findByConfigName(String configName);

    /**
     * 检查名称是否存在
     */
    boolean existsByConfigName(String configName);

    /**
     * 检查名称是否存在（排除指定ID）
     */
    boolean existsByConfigNameAndIdNot(String configName, Long id);
}

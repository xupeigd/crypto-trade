package com.crypto.trade.repository;

import com.crypto.trade.entity.FreqtradeInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * FreqtradeInstanceRepository
 * Freqtrade运行实例数据访问层
 *
 * @author page
 * @date 2026-03-23
 */
@Repository
public interface FreqtradeInstanceRepository
        extends JpaRepository<FreqtradeInstance, Long> {

    /**
     * 根据实例ID查询
     */
    Optional<FreqtradeInstance> findByInstanceId(String instanceId);

    /**
     * 检查实例ID是否存在
     */
    boolean existsByInstanceId(String instanceId);

    /**
     * 根据实例名称查询
     */
    Optional<FreqtradeInstance> findByInstanceName(String instanceName);

    /**
     * 检查实例名称是否存在
     */
    boolean existsByInstanceName(String instanceName);

    /**
     * 根据状态查询实例列表
     */
    List<FreqtradeInstance> findByStatus(String status);

    /**
     * 查询所有运行中的实例
     */
    @Query("SELECT i FROM FreqtradeInstance i WHERE i.status = 'RUNNING' OR i.status = 'STARTING'")
    List<FreqtradeInstance> findActiveInstances();

    /**
     * 根据API Key ID查询实例
     */
    List<FreqtradeInstance> findByApiKeyId(Long apiKeyId);

    /**
     * 根据FreqtradeConfigId查询实例
     */
    List<FreqtradeInstance> findByFreqtradeConfigId(Long freqtradeConfigId);

    /**
     * 根据StrategyConfigId查询实例
     */
    List<FreqtradeInstance> findByStrategyConfigId(Long strategyConfigId);

    /**
     * 根据API Key、FreqtradeConfig和StrategyConfig查询实例
     */
    Optional<FreqtradeInstance> findByApiKeyIdAndFreqtradeConfigIdAndStrategyConfigId(
            Long apiKeyId, Long freqtradeConfigId, Long strategyConfigId);

    /**
     * 根据API Key、FreqtradeConfig、StrategyConfig和isDryRun查询实例（用于区分模拟/实盘）
     */
    Optional<FreqtradeInstance> findByApiKeyIdAndFreqtradeConfigIdAndStrategyConfigIdAndIsDryRun(Long apiKeyId,
                                                                                                 Long freqtradeConfigId,
                                                                                                 Long strategyConfigId,
                                                                                                 Boolean isDryRun);

    /**
     * 查询指定端口是否被占用
     */
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM FreqtradeInstance i WHERE i.apiPort = :port " +
            "AND i.status IN ('RUNNING', 'STARTING')")
    boolean isPortInUse(Integer port);

    /**
     * 查询最大API端口
     */
    @Query("SELECT MAX(i.apiPort) FROM FreqtradeInstance i")
    Optional<Integer> findMaxApiPort();

    /**
     * 根据容器ID查询
     */
    Optional<FreqtradeInstance> findByContainerId(String containerId);
}
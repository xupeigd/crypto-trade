package com.crypto.trade.repository;

import com.crypto.trade.entity.ProxyServiceConfig;
import com.crypto.trade.entity.ScheduledTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ProxyServiceConfigRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Repository
public interface ProxyServiceConfigRepository extends JpaRepository<ProxyServiceConfig, Long> {

    /**
     * 根据代理名称查找代理配置
     */
    Optional<ProxyServiceConfig> findByProxyName(String proxyName);

    /**
     * 查找所有活跃的代理配置
     */
    List<ProxyServiceConfig> findByStatus(String status);

    /**
     * 查找所有活跃的代理配置
     */
    @Query("SELECT p FROM ProxyServiceConfig p WHERE p.status = 'active'")
    List<ProxyServiceConfig> findActiveConfigs();

    /**
     * 检查代理名称是否已存在（排除指定ID）
     */
    @Query("SELECT COUNT(p) > 0 FROM ProxyServiceConfig p WHERE p.proxyName = :proxyName AND p.proxyId != :proxyId")
    boolean existsByProxyNameAndProxyIdNot(String proxyName, Long proxyId);

    /**
     * 检查代理名称是否已存在
     */
    boolean existsByProxyName(String proxyName);

    /**
     * 统计代理配置关联的任务数量
     */
    @Query("SELECT COUNT(d) FROM DataFetchConfig d WHERE d.proxyId = :proxyId")
    Long countAssociatedTasks(@Param("proxyId") Long proxyId);

    /**
     * 获取代理配置关联的任务列表
     */
    @Query("SELECT t FROM ScheduledTask t WHERE t.taskId IN " +
            "(SELECT d.taskId FROM DataFetchConfig d WHERE d.proxyId = :proxyId)")
    List<ScheduledTask> findAssociatedTasks(@Param("proxyId") Long proxyId);
}
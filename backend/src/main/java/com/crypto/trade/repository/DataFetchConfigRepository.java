package com.crypto.trade.repository;

import com.crypto.trade.entity.DataFetchConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * DataFetchConfigRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Repository
public interface DataFetchConfigRepository extends JpaRepository<DataFetchConfig, Long> {

    DataFetchConfig findByTaskId(Long taskId);

    @Query("SELECT d FROM DataFetchConfig d WHERE d.cexBaseUrl LIKE %:cexName%")
    List<DataFetchConfig> findByCexName(@Param("cexName") String cexName);

    @Query("SELECT d FROM DataFetchConfig d WHERE d.requiresAuth = true")
    List<DataFetchConfig> findAuthRequiredConfigs();

    @Query("SELECT d FROM DataFetchConfig d JOIN ScheduledTask t ON d.taskId = t.taskId " +
            "WHERE t.status = 'active'")
    List<DataFetchConfig> findActiveConfigs();

    @Query("SELECT d FROM DataFetchConfig d WHERE d.authKeyId = :keyId")
    List<DataFetchConfig> findByAuthKeyId(@Param("keyId") Long keyId);
}
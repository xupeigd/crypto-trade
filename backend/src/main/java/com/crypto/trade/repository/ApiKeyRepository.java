package com.crypto.trade.repository;

import com.crypto.trade.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ApiKeyRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    List<ApiKey> findByCexName(String cexName);

    List<ApiKey> findByStatus(String status);

    @Query("SELECT k FROM ApiKey k WHERE k.cexName = :cexName AND k.status = 'active'")
    List<ApiKey> findActiveKeysByCexName(@Param("cexName") String cexName);

    @Query("SELECT DISTINCT k.cexName FROM ApiKey k WHERE k.status = 'active'")
    List<String> findActiveCexNames();

    @Query("SELECT COUNT(k) FROM ApiKey k WHERE k.status = 'active'")
    long countActiveApiKeys();

    boolean existsByCexNameAndAccessKey(String cexName, String accessKey);

    // 基于实盘交易标识的查询方法
    List<ApiKey> findByIsLiveTrading(Boolean isLiveTrading);

    @Query("SELECT COUNT(k) FROM ApiKey k WHERE k.isLiveTrading = :isLiveTrading")
    long countByIsLiveTrading(@Param("isLiveTrading") Boolean isLiveTrading);

    @Query("SELECT k FROM ApiKey k WHERE k.isLiveTrading = :isLiveTrading AND k.status = 'active'")
    List<ApiKey> findActiveKeysByIsLiveTrading(@Param("isLiveTrading") Boolean isLiveTrading);
}
package com.crypto.trade.repository;

import com.crypto.trade.entity.AIModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AIModelConfigRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface AIModelConfigRepository extends JpaRepository<AIModelConfig, Long> {

    Optional<AIModelConfig> findByModelId(String modelId);

    List<AIModelConfig> findByProvider(String provider);

    List<AIModelConfig> findByModelType(String modelType);

    List<AIModelConfig> findByIsActiveTrue();

    List<AIModelConfig> findByIsActiveFalse();

    Optional<AIModelConfig> findByDefaultModelTrue();

    @Query("SELECT a FROM AIModelConfig a WHERE a.isActive = true ORDER BY a.defaultModel DESC, a.displayName ASC")
    List<AIModelConfig> findActiveModelsOrderByDefault();

    @Query("SELECT COUNT(a) FROM AIModelConfig a WHERE a.isActive = true")
    long countActiveModels();

    @Query("SELECT COUNT(a) FROM AIModelConfig a WHERE a.provider = :provider AND a.isActive = true")
    long countActiveModelsByProvider(@Param("provider") String provider);

    @Modifying
    @Query("UPDATE AIModelConfig a SET a.defaultModel = false WHERE a.configId != :configId AND a.defaultModel = true")
    void unsetOtherDefaultModels(@Param("configId") Long configId);

    @Modifying
    @Query("UPDATE AIModelConfig a SET a.defaultModel = false WHERE a.defaultModel = true")
    void unsetAllDefaultModels();

    @Modifying
    @Query("UPDATE AIModelConfig a SET a.isActive = false, a.defaultModel = false, a.updatedTime = CURRENT_TIMESTAMP WHERE a.configId = :configId")
    void deactivateModel(@Param("configId") Long configId);

    boolean existsByModelId(String modelId);

    boolean existsByModelIdAndConfigIdNot(String modelId, Long configId);
}
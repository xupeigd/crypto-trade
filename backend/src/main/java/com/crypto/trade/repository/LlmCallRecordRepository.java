package com.crypto.trade.repository;

import com.crypto.trade.entity.LlmCallRecord;
import com.crypto.trade.enums.OpenCloseType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * LlmCallRecordRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface LlmCallRecordRepository
        extends JpaRepository<LlmCallRecord, Long> {

    /**
     * 根据API Key ID查询调用记录
     */
    List<LlmCallRecord> findByApiKeyIdOrderByCallStartTimeDesc(Long apiKeyId);

    /**
     * 根据API Key ID查询调用记录（限制数量）
     */
    List<LlmCallRecord> findByApiKeyIdOrderByCallStartTimeDesc(Long apiKeyId, Pageable pageable);

    /**
     * 根据API Key和决策动作筛选查询记录
     *
     * @deprecated 该方法直接查询decisionAction字段, 无法正确筛选包含特定action的记录
     * 建议使用 findByApiKeyIdAndActionTypeFromTradeAction() 代替
     */
    @Deprecated
    @Query("SELECT r FROM LlmCallRecord r WHERE r.apiKeyId = :apiKeyId " +
            "AND (:actionFilter IS NULL OR r.decisionAction = :actionFilter) " +
            "ORDER BY r.callStartTime DESC")
    List<LlmCallRecord> findByApiKeyIdAndActionFilter(@Param("apiKeyId") Long apiKeyId, @Param("actionFilter") String actionFilter,
                                                      Pageable pageable);

    /**
     * 根据API Key和动作类型筛选查询记录(通过TradeAction关联)
     * 使用JOIN查询,支持去重和分页
     *
     * @param apiKeyId   API密钥ID
     * @param actionType 动作类型(BUY/SELL/HOLD/ATTENTION/CANCEL_ORDER)
     * @param pageable   分页参数
     * @return 调用记录列表(已去重)
     */
    @Query("SELECT DISTINCT r FROM LlmCallRecord r " +
            "INNER JOIN TradeAction t ON r.id = t.recordId " +
            "WHERE r.apiKeyId = :apiKeyId " +
            "AND t.actionType = :actionType " +
            "ORDER BY r.callStartTime DESC")
    List<LlmCallRecord> findByApiKeyIdAndActionTypeFromTradeAction(@Param("apiKeyId") Long apiKeyId, @Param("actionType") String actionType,
                                                                   Pageable pageable);

    /**
     * 根据API Key和多重筛选条件查询记录(通过TradeAction关联)
     * 使用JOIN查询,支持actionFilter和openCloseFilter的组合筛选,支持去重和分页
     *
     * @param apiKeyId        API密钥ID
     * @param actionFilter    动作类型筛选(BUY/SELL/HOLD/ATTENTION/CANCEL_ORDER),可为null
     * @param openCloseFilter 开平仓筛选(OPEN/CLOSE枚举),可为null
     * @param pageable        分页参数
     * @return 调用记录列表(已去重)
     */
    @Query("SELECT DISTINCT r FROM LlmCallRecord r " +
            "INNER JOIN TradeAction t ON r.id = t.recordId " +
            "WHERE r.apiKeyId = :apiKeyId " +
            "AND (:actionFilter IS NULL OR t.actionType = :actionFilter) " +
            "AND (:openCloseFilter IS NULL OR t.openClose = :openCloseFilter) " +
            "ORDER BY r.callStartTime DESC")
    List<LlmCallRecord> findByApiKeyIdAndFiltersFromTradeAction(@Param("apiKeyId") Long apiKeyId,
                                                                @Param("actionFilter") String actionFilter,
                                                                @Param("openCloseFilter") OpenCloseType openCloseFilter,
                                                                Pageable pageable);

    /**
     * 根据API Key和模型名称筛选查询记录
     *
     * @param apiKeyId  API密钥ID
     * @param modelName 模型名称
     * @param pageable  分页参数
     * @return 调用记录列表
     */
    @Query("SELECT r FROM LlmCallRecord r " +
            "WHERE r.apiKeyId = :apiKeyId " +
            "AND (:modelName IS NULL OR r.modelName = :modelName) " +
            "ORDER BY r.callStartTime DESC")
    List<LlmCallRecord> findByApiKeyIdAndModelName(@Param("apiKeyId") Long apiKeyId, @Param("modelName") String modelName,
                                                   Pageable pageable);

    /**
     * 根据API Key和调用来源筛选查询记录
     *
     * @param apiKeyId   API密钥ID
     * @param callSource 调用来源(SCHEDULED/DIRECT/MANUAL)
     * @param pageable   分页参数
     * @return 调用记录列表
     */
    @Query("SELECT r FROM LlmCallRecord r " +
            "WHERE r.apiKeyId = :apiKeyId " +
            "AND (:callSource IS NULL OR r.callSource = :callSource) " +
            "ORDER BY r.callStartTime DESC")
    List<LlmCallRecord> findByApiKeyIdAndCallSource(@Param("apiKeyId") Long apiKeyId, @Param("callSource") String callSource,
                                                    Pageable pageable);

    /**
     * 根据API Key、模型名称和调用来源筛选查询记录
     *
     * @param apiKeyId   API密钥ID
     * @param modelName  模型名称(可选)
     * @param callSource 调用来源(可选)
     * @param pageable   分页参数
     * @return 调用记录列表
     */
    @Query("SELECT r FROM LlmCallRecord r " +
            "WHERE r.apiKeyId = :apiKeyId " +
            "AND (:modelName IS NULL OR r.modelName = :modelName) " +
            "AND (:callSource IS NULL OR r.callSource = :callSource) " +
            "ORDER BY r.callStartTime DESC")
    List<LlmCallRecord> findByApiKeyIdAndModelNameAndCallSource(@Param("apiKeyId") Long apiKeyId,
                                                                @Param("modelName") String modelName,
                                                                @Param("callSource") String callSource,
                                                                Pageable pageable);

    /**
     * 根据会话ID查询所有轮次
     */
    List<LlmCallRecord> findBySessionIdOrderByCallStartTimeAsc(Long sessionId);

    /**
     * 根据父ID查询子调用
     */
    List<LlmCallRecord> findByParentIdOrderByCallStartTimeAsc(Long parentId);

    /**
     * 根据状态查询记录
     */
    List<LlmCallRecord> findByStatus(String status);

    /**
     * 查询指定API Key的最新一条记录
     */
    @Query("SELECT r FROM LlmCallRecord r WHERE r.apiKeyId = :apiKeyId ORDER BY r.updatedAt DESC LIMIT 1 ")
    Optional<LlmCallRecord> findLatestByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 查询指定API Key的统计信息
     */
    @Query("SELECT COUNT(r), SUM(CASE WHEN r.status = 'SUCCESS' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN r.status = 'FAILED' THEN 1 ELSE 0 END), " +
            "AVG(r.processingTimeMs) " +
            "FROM LlmCallRecord r WHERE r.apiKeyId = :apiKeyId")
    Object[] getStatisticsByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 查询指定时间范围的记录
     */
    @Query("SELECT r FROM LlmCallRecord r WHERE r.apiKeyId = :apiKeyId " +
            "AND r.callStartTime BETWEEN :startTime AND :endTime " +
            "ORDER BY r.callStartTime DESC")
    List<LlmCallRecord> findByApiKeyIdAndTimeRange(@Param("apiKeyId") Long apiKeyId, @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 查询处理中的记录
     */
    @Query("SELECT r FROM LlmCallRecord r WHERE r.status = 'PROCESSING' " +
            "AND r.callStartTime < :timeoutThreshold")
    List<LlmCallRecord> findProcessingRecords(@Param("timeoutThreshold") LocalDateTime timeoutThreshold);

    /**
     * 根据记录ID查询记录
     */
    Optional<LlmCallRecord> findById(Long id);
}

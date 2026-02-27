package com.crypto.trade.repository;

import com.crypto.trade.entity.LlmAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * LlmAuditLogRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface LlmAuditLogRepository extends JpaRepository<LlmAuditLog, Long> {

    /**
     * 根据会话ID查找审计日志
     */
    List<LlmAuditLog> findBySessionId(String sessionId);

    /**
     * 根据调用统计ID查找审计日志
     */
    Optional<LlmAuditLog> findByCallStatsId(Long callStatsId);

    /**
     * 根据API密钥ID查找审计日志
     */
    List<LlmAuditLog> findByApiKeyId(Long apiKeyId);

    /**
     * 根据模型名称查找审计日志
     */
    List<LlmAuditLog> findByModelName(String modelName);

    /**
     * 根据调用状态查找审计日志
     */
    List<LlmAuditLog> findByCallStatus(LlmAuditLog.CallStatus callStatus);

    /**
     * 查找指定时间范围内的审计日志
     */
    @Query("SELECT a FROM LlmAuditLog a WHERE a.createdTime BETWEEN :startTime AND :endTime ORDER BY a.createdTime DESC")
    List<LlmAuditLog> findByTimeRange(@Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);

    /**
     * 查找指定时间之后的审计日志
     */
    @Query("SELECT a FROM LlmAuditLog a WHERE a.createdTime > :since ORDER BY a.createdTime DESC")
    List<LlmAuditLog> findAuditLogsSince(@Param("since") LocalDateTime since);

    /**
     * 查找最近的N次审计日志
     */
    @Query("SELECT a FROM LlmAuditLog a ORDER BY a.createdTime DESC LIMIT :limit")
    List<LlmAuditLog> findRecentAuditLogs(@Param("limit") int limit);

    /**
     * 根据API密钥ID和调用状态查找审计日志
     */
    @Query("SELECT a FROM LlmAuditLog a WHERE a.apiKeyId = :apiKeyId AND a.callStatus = :callStatus ORDER BY a.createdTime DESC")
    List<LlmAuditLog> findByApiKeyIdAndCallStatus(@Param("apiKeyId") Long apiKeyId,
                                                  @Param("callStatus") LlmAuditLog.CallStatus callStatus);

    /**
     * 统计指定API密钥的总审计日志数量
     */
    @Query("SELECT COUNT(a) FROM LlmAuditLog a WHERE a.apiKeyId = :apiKeyId")
    long countByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 统计指定API密钥的成功调用数量
     */
    @Query("SELECT COUNT(a) FROM LlmAuditLog a WHERE a.apiKeyId = :apiKeyId AND a.callStatus = 'SUCCESS'")
    long countSuccessCallsByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 统计指定API密钥的失败调用数量
     */
    @Query("SELECT COUNT(a) FROM LlmAuditLog a WHERE a.apiKeyId = :apiKeyId AND a.callStatus = 'FAILED'")
    long countFailedCallsByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 统计指定模型的总调用数量
     */
    @Query("SELECT COUNT(a) FROM LlmAuditLog a WHERE a.modelName = :modelName")
    long countByModelName(@Param("modelName") String modelName);

    /**
     * 计算指定API密钥的平均处理时间
     */
    @Query("SELECT AVG(a.processingTimeMs) FROM LlmAuditLog a WHERE a.apiKeyId = :apiKeyId AND a.processingTimeMs IS NOT NULL")
    Double getAverageProcessingTimeByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 查找失败的审计日志
     */
    @Query("SELECT a FROM LlmAuditLog a WHERE a.callStatus = 'FAILED' ORDER BY a.createdTime DESC")
    List<LlmAuditLog> findFailedAuditLogs();

    /**
     * 查找指定API密钥在指定时间范围内的审计日志
     */
    @Query("SELECT a FROM LlmAuditLog a WHERE a.apiKeyId = :apiKeyId AND a.createdTime BETWEEN :startTime AND :endTime ORDER BY a.createdTime DESC")
    List<LlmAuditLog> findByApiKeyIdAndTimeRange(@Param("apiKeyId") Long apiKeyId,
                                                 @Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 查找处理时间超过指定阈值的审计日志
     */
    @Query("SELECT a FROM LlmAuditLog a WHERE a.processingTimeMs > :threshold ORDER BY a.processingTimeMs DESC")
    List<LlmAuditLog> findSlowCalls(@Param("threshold") Long threshold);

    /**
     * 根据会话ID查找最新的审计日志
     */
    @Query("SELECT a FROM LlmAuditLog a WHERE a.sessionId = :sessionId ORDER BY a.createdTime DESC LIMIT 1")
    Optional<LlmAuditLog> findLatestBySessionId(@Param("sessionId") String sessionId);
}
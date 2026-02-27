package com.crypto.trade.repository;

import com.crypto.trade.entity.TradeBalanceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TradeBalanceSnapshotRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface TradeBalanceSnapshotRepository
        extends JpaRepository<TradeBalanceSnapshot, Long> {

    /**
     * 根据API密钥ID查找快照列表
     *
     * @param apiKeyId API密钥ID
     * @return 快照列表，按快照时间降序排列
     */
    List<TradeBalanceSnapshot> findByApiKeyIdOrderBySnapshotTimeDesc(Long apiKeyId);

    /**
     * 根据API密钥ID和来源查找快照列表
     *
     * @param apiKeyId API密钥ID
     * @param source   来源（INITIAL/REPLAY）
     * @return 快照列表，按快照时间降序排列
     */
    List<TradeBalanceSnapshot> findByApiKeyIdAndSourceOrderBySnapshotTimeDesc(Long apiKeyId, String source);

    /**
     * 根据API密钥ID列表查找快照
     *
     * @param apiKeyIds API密钥ID列表
     * @return 快照列表，按快照时间降序排列
     */
    List<TradeBalanceSnapshot> findByApiKeyIdInOrderBySnapshotTimeDesc(List<Long> apiKeyIds);

    /**
     * 查找指定时间范围内的快照
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 快照列表，按快照时间降序排列
     */
    List<TradeBalanceSnapshot> findBySnapshotTimeBetweenOrderBySnapshotTimeDesc(LocalDateTime startTime,
                                                                                LocalDateTime endTime);

    /**
     * 查找最近的N条快照
     *
     * @param apiKeyId API密钥ID
     * @param limit    限制数量
     * @return 快照列表，按快照时间降序排列
     */
    @Query(value = "SELECT * FROM t_trade_balance_snapshots " +
            "WHERE api_key_id = :apiKeyId " +
            "ORDER BY snapshot_time DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<TradeBalanceSnapshot> findRecentSnapshotsByApiKeyId(@Param("apiKeyId") Long apiKeyId,
                                                             @Param("limit") int limit);

    /**
     * 删除指定时间之前的快照数据
     * 用于数据清理和历史数据管理
     *
     * @param before 时间阈值
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM TradeBalanceSnapshot s WHERE s.snapshotTime < :before")
    int deleteOldSnapshots(@Param("before") LocalDateTime before);

    /**
     * 统计指定API密钥的快照数量
     *
     * @param apiKeyId API密钥ID
     * @return 快照数量
     */
    long countByApiKeyId(Long apiKeyId);

    /**
     * 统计指定来源的快照数量
     *
     * @param source 来源（INITIAL/REPLAY）
     * @return 快照数量
     */
    long countBySource(String source);

    /**
     * 查找指定API密钥的最新快照
     *
     * @param apiKeyId API密钥ID
     * @return 最新的快照，如果不存在则返回空
     */
    @Query("SELECT s FROM TradeBalanceSnapshot s " +
            "WHERE s.apiKeyId = :apiKeyId " +
            "ORDER BY s.snapshotTime DESC " +
            "LIMIT 1")
    TradeBalanceSnapshot findLatestByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 查找指定API密钥的最早快照
     *
     * @param apiKeyId API密钥ID
     * @return 最早的快照，如果不存在则返回null
     */
    @Query("SELECT s FROM TradeBalanceSnapshot s " +
            "WHERE s.apiKeyId = :apiKeyId " +
            "ORDER BY s.snapshotTime ASC " +
            "LIMIT 1")
    TradeBalanceSnapshot findEarliestByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 查找指定API密钥和来源的最早快照
     *
     * @param apiKeyId API密钥ID
     * @param source   来源（INITIAL/REPLAY）
     * @return 最早的快照，如果不存在则返回null
     */
    @Query("SELECT s FROM TradeBalanceSnapshot s " +
            "WHERE s.apiKeyId = :apiKeyId AND s.source = :source " +
            "ORDER BY s.snapshotTime ASC " +
            "LIMIT 1")
    TradeBalanceSnapshot findEarliestByApiKeyIdAndSource(@Param("apiKeyId") Long apiKeyId,
                                                         @Param("source") String source);

    /**
     * 查找指定API密钥和来源的最新快照
     *
     * @param apiKeyId API密钥ID
     * @param source   来源（INITIAL/REPLAY）
     * @return 最新的快照，如果不存在则返回空
     */
    @Query("SELECT s FROM TradeBalanceSnapshot s " +
            "WHERE s.apiKeyId = :apiKeyId AND s.source = :source " +
            "ORDER BY s.snapshotTime DESC " +
            "LIMIT 1")
    TradeBalanceSnapshot findLatestByApiKeyIdAndSource(@Param("apiKeyId") Long apiKeyId,
                                                       @Param("source") String source);

    /**
     * 根据recordId查找快照
     *
     * @param recordId LlmCallRecord ID
     * @return 快照列表
     */
    List<TradeBalanceSnapshot> findByRecordId(Long recordId);

    /**
     * 更新快照的recordId
     *
     * @param snapshotId 快照ID
     * @param recordId   LlmCallRecord ID
     * @return 更新影响的记录数
     */
    @Modifying
    @Query("UPDATE TradeBalanceSnapshot s SET s.recordId = :recordId WHERE s.snapshotId = :snapshotId AND s.recordId IS NULL")
    int updateRecordId(@Param("snapshotId") Long snapshotId, @Param("recordId") Long recordId);

    /**
     * 查找没有关联recordId的快照
     * 用于数据清理和验证
     *
     * @return 快照列表
     */
    List<TradeBalanceSnapshot> findByRecordIdIsNull();

    /**
     * 统计没有关联recordId的快照数量
     *
     * @return 快照数量
     */
    long countByRecordIdIsNull();

    /**
     * 查找指定API密钥的最新且未关联recordId的快照
     * 用于并发场景下避免重复更新
     *
     * @param apiKeyId API密钥ID
     * @return 最新的未关联recordId的快照，如果不存在则返回null
     */
    @Query("SELECT s FROM TradeBalanceSnapshot s " +
            "WHERE s.apiKeyId = :apiKeyId " +
            "AND s.recordId IS NULL " +
            "ORDER BY s.snapshotTime DESC " +
            "LIMIT 1")
    TradeBalanceSnapshot findLatestByApiKeyIdAndRecordIdIsNull(@Param("apiKeyId") Long apiKeyId);

    /**
     * 查找指定API密钥和来源的最新且未关联recordId的快照
     * 用于并发场景下避免重复更新，同时考虑来源类型
     *
     * @param apiKeyId API密钥ID
     * @param source   来源（INITIAL/REPLAY）
     * @return 最新的未关联recordId的快照，如果不存在则返回null
     */
    @Query("SELECT s FROM TradeBalanceSnapshot s " +
            "WHERE s.apiKeyId = :apiKeyId " +
            "AND s.source = :source " +
            "AND s.recordId IS NULL " +
            "ORDER BY s.snapshotTime DESC " +
            "LIMIT 1")
    TradeBalanceSnapshot findLatestByApiKeyIdAndSourceAndRecordIdIsNull(
            @Param("apiKeyId") Long apiKeyId,
            @Param("source") String source);
}
